package dev.frenkli.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One business event that moves money (a deposit, a transfer, ...). The money itself is in its
 * {@link Entry entries}. Named LedgerTransaction to avoid confusion with database transactions.
 *
 * <p>Append-only: there are no setters, and the database rejects UPDATE and DELETE.
 */
@Entity
@Table(name = "transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    private String description;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "reverses_transaction_id")
    private UUID reversesTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
        // for JPA
    }

    public LedgerTransaction(TransactionType type, String description, String idempotencyKey, String requestHash,
                             UUID reversesTransactionId, Instant createdAt) {
        this.type = type;
        this.reversesTransactionId = reversesTransactionId;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        // Postgres stores microseconds. Truncating here means the value we return now is exactly
        // the value a replay will later read back from the database.
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    /** For a REVERSAL: the transaction it cancels. Null for every other type. */
    public UUID getReversesTransactionId() {
        return reversesTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
