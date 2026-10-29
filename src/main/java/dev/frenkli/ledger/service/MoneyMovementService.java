package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.Posting;
import dev.frenkli.ledger.domain.SystemAccounts;
import dev.frenkli.ledger.domain.TransactionType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deposits and transfers. Checks the business rules, then records a balanced transaction. */
@Service
public class MoneyMovementService {

    private final AccountLocks accountLocks;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;

    public MoneyMovementService(AccountLocks accountLocks, LedgerService ledger, IdempotencyService idempotency) {
        this.accountLocks = accountLocks;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    /**
     * Money enters the bank: EXTERNAL_FUNDING -amount / customer +amount. The system account
     * goes negative, so the ledger as a whole still sums to 0.
     */
    @Transactional
    public MovementResult deposit(String idempotencyKey, UUID accountId, long amount, String currency,
                                  String description) {
        String requestHash = RequestFingerprint.of("DEPOSIT", accountId, amount, currency, description);
        Optional<RecordedTransaction> previous = idempotency.findPrevious(idempotencyKey, requestHash);
        if (previous.isPresent()) {
            return new MovementResult(previous.get(), true);
        }

        requirePositive(amount);
        Currencies.requireSupported(currency);
        Map<UUID, Account> locked = accountLocks.lockInIdOrder(List.of(SystemAccounts.EXTERNAL_FUNDING_ID, accountId));
        Account account = AccountRules.requireUsableCustomerAccount(locked.get(accountId));
        AccountRules.requireSameCurrency(account, currency);

        return new MovementResult(ledger.record(TransactionType.DEPOSIT, description, idempotencyKey, requestHash,
                List.of(new Posting(SystemAccounts.EXTERNAL_FUNDING_ID, -amount),
                        new Posting(account.getId(), amount))), false);
    }

    /** Money moves between two customer accounts: from -amount / to +amount. */
    @Transactional
    public MovementResult transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId, long amount,
                                   String currency, String description) {
        String requestHash = RequestFingerprint.of("TRANSFER", fromAccountId, toAccountId, amount, currency,
                description);
        Optional<RecordedTransaction> previous = idempotency.findPrevious(idempotencyKey, requestHash);
        if (previous.isPresent()) {
            return new MovementResult(previous.get(), true);
        }

        requirePositive(amount);
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessRuleException(BusinessRule.SAME_ACCOUNT_TRANSFER,
                    "Cannot transfer from an account to itself");
        }
        Currencies.requireSupported(currency);
        // Lock both accounts BEFORE reading the balance. Without the lock, two transfers could
        // both read a balance of 100, both decide that 80 is affordable, and both book it.
        Map<UUID, Account> locked = accountLocks.lockInIdOrder(List.of(fromAccountId, toAccountId));
        Account from = AccountRules.requireUsableCustomerAccount(locked.get(fromAccountId));
        Account to = AccountRules.requireUsableCustomerAccount(locked.get(toAccountId));
        AccountRules.requireSameCurrency(from, currency);
        AccountRules.requireSameCurrency(to, currency);

        // No overdrafts for customer accounts. Safe under concurrency: we hold the lock on
        // 'from', so no other transaction can change its balance until we commit.
        long balance = from.getBalance();
        if (balance < amount) {
            throw new BusinessRuleException(BusinessRule.INSUFFICIENT_FUNDS,
                    "Account " + from.getId() + " has " + balance + " cents, transfer needs " + amount);
        }

        return new MovementResult(ledger.record(TransactionType.TRANSFER, description, idempotencyKey, requestHash,
                List.of(new Posting(from.getId(), -amount),
                        new Posting(to.getId(), amount))), false);
    }

    private static void requirePositive(long amount) {
        // The direction of money is given by which account is 'from' and which is 'to'. A
        // negative amount would silently reverse it, e.g. pull money out of the recipient.
        if (amount <= 0) {
            throw new BusinessRuleException(BusinessRule.NON_POSITIVE_AMOUNT,
                    "Amount must be greater than 0 cents, got " + amount);
        }
    }
}
