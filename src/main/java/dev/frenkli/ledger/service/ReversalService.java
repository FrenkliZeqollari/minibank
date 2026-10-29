package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountType;
import dev.frenkli.ledger.domain.Entry;
import dev.frenkli.ledger.domain.LedgerTransaction;
import dev.frenkli.ledger.domain.Posting;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.repository.EntryRepository;
import dev.frenkli.ledger.repository.LedgerTransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain rule 3: a booked transaction is never changed or deleted. To cancel it, we book a new
 * REVERSAL transaction whose entries mirror the original with opposite signs. Original plus
 * reversal sum to 0 on every account, and both stay in the history.
 */
@Service
public class ReversalService {

    private final LedgerTransactionRepository transactions;
    private final EntryRepository entries;
    private final AccountLocks accountLocks;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;

    public ReversalService(LedgerTransactionRepository transactions, EntryRepository entries,
                           AccountLocks accountLocks, LedgerService ledger, IdempotencyService idempotency) {
        this.transactions = transactions;
        this.entries = entries;
        this.accountLocks = accountLocks;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Transactional
    public MovementResult reverse(String idempotencyKey, UUID transactionId) {
        String requestHash = RequestFingerprint.of("REVERSAL", transactionId);
        Optional<RecordedTransaction> previous = idempotency.findPrevious(idempotencyKey, requestHash);
        if (previous.isPresent()) {
            return new MovementResult(previous.get(), true);
        }

        LedgerTransaction original = transactions.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        if (original.getType() == TransactionType.REVERSAL) {
            // To undo a reversal, book the original operation again as a new transaction.
            throw new BusinessRuleException(BusinessRule.REVERSAL_NOT_REVERSIBLE,
                    "Transaction " + transactionId + " is itself a reversal");
        }

        // The mirror image: the same accounts, each amount with the opposite sign.
        List<Posting> mirrored = entries.findByTransactionId(transactionId).stream()
                .map(e -> new Posting(e.getAccountId(), Math.negateExact(e.getAmount())))
                .toList();

        // Lock every account involved before checking anything (domain rule 8). Two reversals of
        // the same transaction involve the same accounts, so the second one waits here until the
        // first has committed, and then sees it in the check below.
        Map<UUID, Account> locked = accountLocks.lockInIdOrder(mirrored.stream().map(Posting::accountId).toList());

        if (transactions.existsByReversesTransactionId(transactionId)) {
            throw new AlreadyReversedException(transactionId);
        }

        // No overdrafts, even for a reversal: if Bob already spent the money he received, taking
        // it back would make his balance negative. The bank must settle that some other way.
        for (Posting posting : mirrored) {
            Account account = locked.get(posting.accountId());
            if (account.getType() == AccountType.CUSTOMER && account.getBalance() + posting.amount() < 0) {
                throw new BusinessRuleException(BusinessRule.INSUFFICIENT_FUNDS,
                        "Account " + account.getId() + " has " + account.getBalance()
                                + " cents, reversal needs " + -posting.amount());
            }
        }

        return new MovementResult(ledger.record(TransactionType.REVERSAL,
                "Reversal of transaction " + transactionId, idempotencyKey, requestHash,
                transactionId, mirrored), false);
    }
}
