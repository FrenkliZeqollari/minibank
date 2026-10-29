package dev.frenkli.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A market price of an instrument at a point in time. Append-only: a new price is a new row, so
 * the history is kept. The row with the highest {@code seq} is the current price.
 */
@Entity
@Table(name = "instrument_prices")
public class InstrumentPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Assigned by the database on insert; orders prices exactly, even with equal timestamps. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(nullable = false)
    private String symbol;

    /** Minor units (cents) per share. */
    @Column(nullable = false)
    private long price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InstrumentPrice() {
        // for JPA
    }

    public InstrumentPrice(String symbol, long price, Instant createdAt) {
        this.symbol = symbol;
        this.price = price;
        this.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS); // Postgres precision
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getPrice() {
        return price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
