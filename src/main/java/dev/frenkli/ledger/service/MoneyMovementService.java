package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountStatus;
import dev.frenkli.ledger.domain.AccountType;
import dev.frenkli.ledger.domain.Posting;
import dev.frenkli.ledger.domain.SystemAccounts;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.repository.AccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deposits and transfers. Checks the business rules, then records a balanced transaction. */
@Service
public class MoneyMovementService {

    private final AccountRepository accounts;
    private final LedgerService ledger;

    public MoneyMovementService(AccountRepository accounts, LedgerService ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    /**
     * Money enters the bank: EXTERNAL_FUNDING -amount / customer +amount. The system account
     * goes negative, so the ledger as a whole still sums to 0.
     */
    @Transactional
    public RecordedTransaction deposit(UUID accountId, long amount, String currency, String description) {
        requirePositive(amount);
        Currencies.requireSupported(currency);
        Account account = loadUsableCustomerAccount(accountId);
        requireSameCurrency(account, currency);

        return ledger.record(TransactionType.DEPOSIT, description, List.of(
                new Posting(SystemAccounts.EXTERNAL_FUNDING_ID, -amount),
                new Posting(account.getId(), amount)));
    }

    /** Money moves between two customer accounts: from -amount / to +amount. */
    @Transactional
    public RecordedTransaction transfer(UUID fromAccountId, UUID toAccountId, long amount,
                                        String currency, String description) {
        requirePositive(amount);
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessRuleException(BusinessRule.SAME_ACCOUNT_TRANSFER,
                    "Cannot transfer from an account to itself");
        }
        Currencies.requireSupported(currency);
        Account from = loadUsableCustomerAccount(fromAccountId);
        Account to = loadUsableCustomerAccount(toAccountId);
        requireSameCurrency(from, currency);
        requireSameCurrency(to, currency);

        // No overdrafts for customer accounts. NOTE: not yet safe when two transfers from the
        // same account run at the same time; Milestone 3 adds row locking for that.
        long balance = from.getBalance();
        if (balance < amount) {
            throw new BusinessRuleException(BusinessRule.INSUFFICIENT_FUNDS,
                    "Account " + from.getId() + " has " + balance + " cents, transfer needs " + amount);
        }

        return ledger.record(TransactionType.TRANSFER, description, List.of(
                new Posting(from.getId(), -amount),
                new Posting(to.getId(), amount)));
    }

    private static void requirePositive(long amount) {
        // The direction of money is given by which account is 'from' and which is 'to'. A
        // negative amount would silently reverse it, e.g. pull money out of the recipient.
        if (amount <= 0) {
            throw new BusinessRuleException(BusinessRule.NON_POSITIVE_AMOUNT,
                    "Amount must be greater than 0 cents, got " + amount);
        }
    }

    private Account loadUsableCustomerAccount(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        // System accounts are moved only by the bank's own bookings, never through this API.
        if (account.getType() != AccountType.CUSTOMER || account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(BusinessRule.ACCOUNT_NOT_USABLE,
                    "Account " + accountId + " is not an active customer account");
        }
        return account;
    }

    private static void requireSameCurrency(Account account, String currency) {
        if (!account.getCurrency().equals(currency)) {
            throw new BusinessRuleException(BusinessRule.CURRENCY_MISMATCH,
                    "Account " + account.getId() + " is in " + account.getCurrency() + ", request is in " + currency);
        }
    }
}
