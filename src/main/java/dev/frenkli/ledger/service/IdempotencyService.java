package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.LedgerTransaction;
import dev.frenkli.ledger.repository.EntryRepository;
import dev.frenkli.ledger.repository.IdempotencyKeyLocks;
import dev.frenkli.ledger.repository.LedgerTransactionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain rule 7: a request repeated with the same Idempotency-Key must not move money twice.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyLocks keyLocks;
    private final LedgerTransactionRepository transactions;
    private final EntryRepository entries;

    public IdempotencyService(IdempotencyKeyLocks keyLocks, LedgerTransactionRepository transactions,
                              EntryRepository entries) {
        this.keyLocks = keyLocks;
        this.transactions = transactions;
        this.entries = entries;
    }

    /**
     * Locks the key, then looks for a transaction already recorded with it.
     *
     * <ul>
     *   <li>none: returns empty, the caller does the real work (still holding the key lock);
     *   <li>same request: returns the original transaction, to be sent back unchanged;
     *   <li>different request: the client reused a key for something else, which is an error.
     * </ul>
     *
     * <p>Must run before any business check: a retry must get the original answer even if, say,
     * the balance has changed since.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RecordedTransaction> findPrevious(String idempotencyKey, String requestHash) {
        keyLocks.lock(idempotencyKey);
        Optional<LedgerTransaction> previous = transactions.findByIdempotencyKey(idempotencyKey);
        if (previous.isEmpty()) {
            return Optional.empty();
        }
        LedgerTransaction transaction = previous.get();
        if (!transaction.getRequestHash().equals(requestHash)) {
            throw new BusinessRuleException(BusinessRule.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key '" + idempotencyKey + "' was already used for a different request");
        }
        return Optional.of(new RecordedTransaction(transaction, entries.findByTransactionId(transaction.getId())));
    }
}
