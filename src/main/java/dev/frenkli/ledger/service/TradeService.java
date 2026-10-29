package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.Instrument;
import dev.frenkli.ledger.domain.InstrumentPrice;
import dev.frenkli.ledger.domain.Posting;
import dev.frenkli.ledger.domain.SystemAccounts;
import dev.frenkli.ledger.domain.Trade;
import dev.frenkli.ledger.domain.TradeSide;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.repository.InstrumentPriceRepository;
import dev.frenkli.ledger.repository.InstrumentRepository;
import dev.frenkli.ledger.repository.TradeRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buys and sells whole shares at the instrument's current price. Every trade has two sides, booked
 * in one DB transaction: the shares (a row in trades) and the cash (a balanced ledger transaction
 * against SECURITIES_SETTLEMENT). Either both are recorded or neither.
 */
@Service
public class TradeService {

    private final InstrumentRepository instruments;
    private final InstrumentPriceRepository prices;
    private final TradeRepository trades;
    private final AccountLocks accountLocks;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;

    public TradeService(InstrumentRepository instruments, InstrumentPriceRepository prices, TradeRepository trades,
                        AccountLocks accountLocks, LedgerService ledger, IdempotencyService idempotency) {
        this.instruments = instruments;
        this.prices = prices;
        this.trades = trades;
        this.accountLocks = accountLocks;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Transactional
    public TradeResult trade(String idempotencyKey, UUID accountId, TradeSide side, String symbol, long quantity) {
        String requestHash = RequestFingerprint.of("TRADE", accountId, side, symbol, quantity);
        Optional<RecordedTransaction> previous = idempotency.findPrevious(idempotencyKey, requestHash);
        if (previous.isPresent()) {
            UUID transactionId = previous.get().transaction().getId();
            return new TradeResult(trades.findByTransactionId(transactionId).orElseThrow(), true);
        }

        if (quantity <= 0) {
            throw new BusinessRuleException(BusinessRule.NON_POSITIVE_QUANTITY,
                    "Quantity must be at least 1 share, got " + quantity);
        }
        Instrument instrument = instruments.findById(symbol).orElseThrow(() -> new InstrumentNotFoundException(symbol));

        // Lock the customer's account (and the settlement account) before any check. All trades of
        // one account therefore run one after another, which makes both the cash check and the
        // holdings check below safe under concurrency, exactly like transfers (domain rule 8).
        Map<UUID, Account> locked = accountLocks.lockInIdOrder(List.of(accountId, SystemAccounts.SECURITIES_SETTLEMENT_ID));
        Account account = AccountRules.requireUsableCustomerAccount(locked.get(accountId));
        AccountRules.requireSameCurrency(account, instrument.getCurrency());

        // The trade executes at the current market price; the client cannot choose it.
        InstrumentPrice price = prices.findFirstBySymbolOrderBySeqDesc(symbol)
                .orElseThrow(() -> new BusinessRuleException(BusinessRule.NO_MARKET_PRICE,
                        "No market price has been recorded for " + symbol));
        long grossAmount;
        try {
            grossAmount = Math.multiplyExact(quantity, price.getPrice());
        } catch (ArithmeticException e) {
            throw new BusinessRuleException(BusinessRule.AMOUNT_TOO_LARGE,
                    quantity + " × " + price.getPrice() + " cents does not fit into the ledger");
        }

        List<Posting> postings;
        if (side == TradeSide.BUY) {
            // No overdrafts: the customer must have the cash.
            if (account.getBalance() < grossAmount) {
                throw new BusinessRuleException(BusinessRule.INSUFFICIENT_FUNDS,
                        "Account " + accountId + " has " + account.getBalance() + " cents, buying needs " + grossAmount);
            }
            postings = List.of(new Posting(accountId, -grossAmount),
                    new Posting(SystemAccounts.SECURITIES_SETTLEMENT_ID, grossAmount));
        } else {
            // No short selling: only shares the customer actually holds can be sold.
            long holding = trades.holding(accountId, symbol);
            if (holding < quantity) {
                throw new BusinessRuleException(BusinessRule.INSUFFICIENT_HOLDINGS,
                        "Account " + accountId + " holds " + holding + " " + symbol + ", selling needs " + quantity);
            }
            postings = List.of(new Posting(SystemAccounts.SECURITIES_SETTLEMENT_ID, -grossAmount),
                    new Posting(accountId, grossAmount));
        }

        String description = side + " " + quantity + " " + symbol + " @ " + displayAmount(price.getPrice(), instrument.getCurrency());
        RecordedTransaction cash = ledger.record(TransactionType.TRADE, description, idempotencyKey, requestHash, postings);
        Trade trade = trades.save(new Trade(accountId, symbol, side, quantity, price.getPrice(), grossAmount,
                cash.transaction().getId(), cash.transaction().getCreatedAt()));
        return new TradeResult(trade, false);
    }

    /** 1500 -> "15.00 EUR", for the human-readable description only. Integer arithmetic, no floats. */
    private static String displayAmount(long cents, String currency) {
        return String.format("%d.%02d %s", cents / 100, cents % 100, currency);
    }
}
