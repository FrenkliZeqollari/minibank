package dev.frenkli.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Something a customer can hold shares of, e.g. a stock or a fund, identified by its symbol. */
@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Instrument() {
        // for JPA
    }

    public Instrument(String symbol, String name, String currency) {
        this.symbol = symbol;
        this.name = name;
        this.currency = currency;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS); // Postgres precision
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
