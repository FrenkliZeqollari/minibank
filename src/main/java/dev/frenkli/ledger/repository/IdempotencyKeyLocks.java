package dev.frenkli.ledger.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyKeyLocks {

    private final JdbcTemplate jdbc;

    public IdempotencyKeyLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks the idempotency key itself until the DB transaction ends, using a Postgres
     * transaction-level advisory lock. An advisory lock is a lock on a number the application
     * chooses, not on a row: here a 64-bit hash of the key. A second request with the same key
     * waits here until the first one has committed or rolled back.
     *
     * <p>Two different keys could share a hash. They would then just wait for each other
     * briefly, which is harmless.
     */
    public void lock(String idempotencyKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, idempotencyKey);
    }
}
