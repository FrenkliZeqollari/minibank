package dev.frenkli.ledger.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Whole-ledger consistency checks, in plain SQL. */
@Repository
public class LedgerHealthQueries {

    private final JdbcTemplate jdbc;

    public LedgerHealthQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The sum of every entry in the ledger. Postgres returns SUM(bigint) as NUMERIC, so even a
     * broken ledger cannot overflow here; it is returned as a BigDecimal for the same reason.
     */
    public BigDecimal sumOfAllEntries() {
        return jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM entries", BigDecimal.class);
    }

    public List<BalanceMismatch> findBalanceMismatches() {
        return jdbc.query("""
                SELECT a.id, a.balance, coalesce(sum(e.amount), 0) AS entries_sum
                  FROM accounts a
                  LEFT JOIN entries e ON e.account_id = a.id
                 GROUP BY a.id, a.balance
                HAVING a.balance <> coalesce(sum(e.amount), 0)
                 ORDER BY a.id
                """,
                (rs, rowNum) -> new BalanceMismatch(
                        rs.getObject("id", UUID.class),
                        rs.getLong("balance"),
                        rs.getBigDecimal("entries_sum").longValueExact()));
    }
}
