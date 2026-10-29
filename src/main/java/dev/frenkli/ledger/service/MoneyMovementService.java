package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountStatus;
import dev.frenkli.ledger.domain.AccountType;
import dev.frenkli.ledger.domain.Posting;
import dev.frenkli.ledger.domain.SystemAccounts;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.repository.AccountRepository;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deposits and transfers. Checks the business rules, then records a balanced transaction. */
@Service
public class MoneyMovementService {

    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;

    public MoneyMovementService(AccountRepository accounts, LedgerService ledger, IdempotencyService idempotency) {
        this.accounts = accounts;
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
        Map<UUID, Account> locked = lockInIdOrder(SystemAccounts.EXTERNAL_FUNDING_ID, accountId);
        Account account = requireUsableCustomerAccount(locked.get(accountId));
        requireSameCurrency(account, currency);

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
        Map<UUID, Account> locked = lockInIdOrder(fromAccountId, toAccountId);
        Account from = requireUsableCustomerAccount(locked.get(fromAccountId));
        Account to = requireUsableCustomerAccount(locked.get(toAccountId));
        requireSameCurrency(from, currency);
        requireSameCurrency(to, currency);

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

    /**
     * Locks the accounts' rows one by one, always in ascending id order, whatever order the
     * request names them in. If one transfer locked A then B while another locked B then A, each
     * could end up holding one lock and waiting forever for the other (a deadlock). With one
     * global order, whoever gets the first lock also gets the second.
     *
     * <p>Must use the same order as the balance updates in {@link LedgerService#record}.
     */
    private Map<UUID, Account> lockInIdOrder(UUID... accountIds) {
        Map<UUID, Account> locked = new HashMap<>();
        Arrays.stream(accountIds).sorted().forEach(id -> locked.put(id,
                accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id))));
        return locked;
    }

    private static Account requireUsableCustomerAccount(Account account) {
        // System accounts are moved only by the bank's own bookings, never through this API.
        if (account.getType() != AccountType.CUSTOMER || account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(BusinessRule.ACCOUNT_NOT_USABLE,
                    "Account " + account.getId() + " is not an active customer account");
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
