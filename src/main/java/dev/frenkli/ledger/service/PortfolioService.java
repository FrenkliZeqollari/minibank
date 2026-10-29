package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.InstrumentPrice;
import dev.frenkli.ledger.repository.AccountRepository;
import dev.frenkli.ledger.repository.InstrumentPriceRepository;
import dev.frenkli.ledger.repository.InstrumentRepository;
import dev.frenkli.ledger.repository.TradeRepository;
import dev.frenkli.ledger.service.AverageCostBook.Holding;
import dev.frenkli.ledger.service.AverageCostBook.TradeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private final AccountRepository accounts;
    private final TradeRepository trades;
    private final InstrumentRepository instruments;
    private final InstrumentPriceRepository prices;

    public PortfolioService(AccountRepository accounts, TradeRepository trades, InstrumentRepository instruments,
                            InstrumentPriceRepository prices) {
        this.accounts = accounts;
        this.trades = trades;
        this.instruments = instruments;
        this.prices = prices;
    }

    /**
     * Values the account's holdings at the latest market prices. REPEATABLE_READ: cash, trades and
     * prices are all read from one snapshot, so a trade committing in between cannot make the
     * numbers disagree with each other.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Portfolio portfolio(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        List<TradeEvent> events = trades.findByAccountIdOrderBySeqAsc(accountId).stream()
                .map(t -> new TradeEvent(t.getSymbol(), t.getSide(), t.getQuantity(), t.getGrossAmount()))
                .toList();

        List<Portfolio.Position> positions = new ArrayList<>();
        long marketValue = 0;
        long costBasis = 0;
        long realizedPnl = 0;
        for (Holding holding : AverageCostBook.replay(events).values()) {
            realizedPnl = Math.addExact(realizedPnl, holding.realizedPnl());
            if (holding.quantity() == 0) {
                continue; // closed position: only its realized profit or loss still counts
            }
            // Every traded instrument has a price: a trade cannot happen without one.
            InstrumentPrice price = prices.findFirstBySymbolOrderBySeqDesc(holding.symbol()).orElseThrow();
            long value = Math.multiplyExact(holding.quantity(), price.getPrice());
            positions.add(new Portfolio.Position(holding.symbol(),
                    instruments.findById(holding.symbol()).orElseThrow().getName(),
                    holding.quantity(), holding.costBasis(), price.getPrice(), price.getCreatedAt(), value,
                    value - holding.costBasis(), holding.realizedPnl()));
            marketValue = Math.addExact(marketValue, value);
            costBasis = Math.addExact(costBasis, holding.costBasis());
        }
        return new Portfolio(accountId, account.getCurrency(), account.getBalance(), positions, marketValue,
                costBasis, marketValue - costBasis, realizedPnl, Math.addExact(account.getBalance(), marketValue));
    }
}
