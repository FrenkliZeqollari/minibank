package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.repository.AccountInsightQueries;
import dev.frenkli.ledger.repository.AccountInsightQueries.DayTotals;
import dev.frenkli.ledger.repository.AccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Balance over time and money in/out per month, for the dashboard. Calendar days and months are
 * those of the bank's business time zone, not UTC: a payment at 00:30 in Berlin belongs to that
 * day, although it is still the previous day in UTC.
 */
@Service
public class AccountInsightService {

    private final AccountRepository accounts;
    private final AccountInsightQueries queries;
    private final Clock clock;
    private final ZoneId businessZone;

    public AccountInsightService(AccountRepository accounts, AccountInsightQueries queries, Clock clock,
                                 @Value("${ledger.business-zone}") ZoneId businessZone) {
        this.accounts = accounts;
        this.queries = queries;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    /** End-of-day balance in cents. */
    public record DailyBalance(LocalDate date, long balance) {
    }

    /** @param moneyOut a positive number: how much left the account */
    public record MonthlyTotals(YearMonth month, long moneyIn, long moneyOut) {
    }

    /**
     * The closing balance of each of the last {@code days} business days, today included. One
     * snapshot (REPEATABLE_READ), so the starting balance and the daily sums agree.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<DailyBalance> balanceHistory(UUID accountId, int days) {
        requireAccount(accountId);
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        LocalDate first = today.minusDays(days - 1L);
        var start = first.atStartOfDay(businessZone).toInstant();

        long balance = queries.balanceBefore(accountId, start);
        Map<LocalDate, DayTotals> totals = queries.dailyTotals(accountId, start, businessZone);
        List<DailyBalance> history = new ArrayList<>(days);
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            DayTotals t = totals.get(day);
            if (t != null) {
                balance = Math.addExact(balance, t.net());
            }
            history.add(new DailyBalance(day, balance));
        }
        return history;
    }

    /** Money in and out for each of the last {@code months} calendar months, the current one included. */
    @Transactional(readOnly = true)
    public List<MonthlyTotals> monthlySummary(UUID accountId, int months) {
        requireAccount(accountId);
        YearMonth current = YearMonth.now(clock.withZone(businessZone));
        YearMonth first = current.minusMonths(months - 1L);
        var start = first.atDay(1).atStartOfDay(businessZone).toInstant();

        Map<LocalDate, DayTotals> totals = queries.dailyTotals(accountId, start, businessZone);
        List<MonthlyTotals> summary = new ArrayList<>(months);
        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            long in = 0;
            long out = 0;
            for (var e : totals.entrySet()) {
                if (YearMonth.from(e.getKey()).equals(month)) {
                    in = Math.addExact(in, e.getValue().moneyIn());
                    out = Math.addExact(out, -e.getValue().moneyOut());
                }
            }
            summary.add(new MonthlyTotals(month, in, out));
        }
        return summary;
    }

    private Account requireAccount(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
