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
 * One executed buy or sell of whole shares. The shares side lives here; the cash side is the
 * ledger transaction {@code transactionId}. Append-only, like the ledger.
 */
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Assigned by the database on insert: the exact order of an account's trades. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeSide side;

    /** Number of shares, always positive; {@link #side} gives the direction. */
    @Column(nullable = false)
    private long quantity;

    /** Execution price in minor units (cents) per share. */
    @Column(nullable = false)
    private long price;

    /** quantity × price, in minor units: the cash that moved. */
    @Column(name = "gross_amount", nullable = false)
    private long grossAmount;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Trade() {
        // for JPA
    }

    public Trade(UUID accountId, String symbol, TradeSide side, long quantity, long price, long grossAmount,
                 UUID transactionId, Instant createdAt) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.grossAmount = grossAmount;
        this.transactionId = transactionId;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeSide getSide() {
        return side;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getPrice() {
        return price;
    }

    public long getGrossAmount() {
        return grossAmount;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
