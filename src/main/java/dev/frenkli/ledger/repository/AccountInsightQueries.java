package dev.frenkli.ledger.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only aggregations over an account's entries, for charts. Plain SQL, grouped by day. */
@Repository
public class AccountInsightQueries {

    private final JdbcTemplate jdbc;

    public AccountInsightQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Sum of all entries booked before {@code before}: the balance at that moment. */
    public long balanceBefore(UUID accountId, Instant before) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM entries WHERE account_id = ? AND created_at < ?",
                Long.class, accountId, java.sql.Timestamp.from(before));
    }

    /** Per business day: the sum of money in (positive entries) and out (negative entries, as a negative number). */
    public Map<LocalDate, DayTotals> dailyTotals(UUID accountId, Instant from, ZoneId zone) {
        Map<LocalDate, DayTotals> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT (created_at AT TIME ZONE ?)::date AS day,
                       coalesce(sum(amount) FILTER (WHERE amount > 0), 0) AS money_in,
                       coalesce(sum(amount) FILTER (WHERE amount < 0), 0) AS money_out
                  FROM entries
                 WHERE account_id = ? AND created_at >= ?
                 GROUP BY day
                 ORDER BY day
                """,
                rs -> {
                    result.put(rs.getObject("day", LocalDate.class),
                            new DayTotals(rs.getLong("money_in"), rs.getLong("money_out")));
                },
                zone.getId(), accountId, java.sql.Timestamp.from(from));
        return result;
    }

    /** @param moneyOut negative or 0 */
    public record DayTotals(long moneyIn, long moneyOut) {

        public long net() {
            return moneyIn + moneyOut;
        }
    }
}
