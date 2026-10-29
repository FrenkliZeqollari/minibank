package dev.frenkli.ledger.service;

import dev.frenkli.ledger.repository.LedgerHealthQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerHealthService {

    private final LedgerHealthQueries queries;

    public LedgerHealthService(LedgerHealthQueries queries) {
        this.queries = queries;
    }

    /**
     * REPEATABLE_READ makes both queries see the same snapshot of the database. Under the default
     * READ_COMMITTED, a transfer committing between the two queries could make a healthy ledger
     * look broken.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LedgerHealth check() {
        return new LedgerHealth(queries.sumOfAllEntries(), queries.findBalanceMismatches());
    }
}
