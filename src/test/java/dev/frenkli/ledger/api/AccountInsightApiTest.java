package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.SystemAccounts;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AccountInsightApiTest extends AbstractApiTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void listsCustomerAccountsButNotSystemAccounts() {
        UUID alice = createAccount("Alice");

        AccountPageResponse page = rest.getForObject("/accounts?size=100&page=0", AccountPageResponse.class);
        // Other tests create accounts too; page through until Alice is found.
        boolean found = false;
        for (int p = 0; p < page.totalPages() && !found; p++) {
            AccountPageResponse current = rest.getForObject("/accounts?size=100&page=" + p, AccountPageResponse.class);
            found = current.accounts().stream().anyMatch(a -> a.id().equals(alice));
            assertThat(current.accounts()).noneMatch(a -> a.id().equals(SystemAccounts.EXTERNAL_FUNDING_ID));
        }
        assertThat(found).isTrue();
    }

    @Test
    void balanceHistoryShowsTheClosingBalanceOfEachDay() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        transfer(alice, createAccount("Bob"), 2_500);

        BalanceHistoryResponse history = rest.getForObject("/accounts/" + alice + "/balance-history?days=3",
                BalanceHistoryResponse.class);

        LocalDate today = LocalDate.now(BERLIN);
        assertThat(history.points()).extracting(BalanceHistoryResponse.Point::date)
                .containsExactly(today.minusDays(2), today.minusDays(1), today);
        assertThat(history.points()).extracting(BalanceHistoryResponse.Point::balance).containsExactly(0L, 0L, 7_500L);
    }

    @Test
    void daysAreBusinessDaysInBerlinNotUtc() {
        UUID alice = createAccount("Alice");
        // 00:30 in Berlin two days ago is still the day before in UTC (22:30 or 23:30).
        LocalDate day = LocalDate.now(BERLIN).minusDays(2);
        ZonedDateTime justAfterMidnight = ZonedDateTime.of(day, LocalTime.of(0, 30), BERLIN);
        assertThat(justAfterMidnight.withZoneSameInstant(ZoneId.of("UTC")).toLocalDate()).isEqualTo(day.minusDays(1));
        bookDepositAt(alice, 4_200, justAfterMidnight);

        BalanceHistoryResponse history = rest.getForObject("/accounts/" + alice + "/balance-history?days=4",
                BalanceHistoryResponse.class);

        // The deposit counts for `day` (Berlin), so the day before still closes at 0.
        assertThat(history.points()).extracting(BalanceHistoryResponse.Point::balance)
                .containsExactly(0L, 4_200L, 4_200L, 4_200L);
        assertThat(history.points().get(1).date()).isEqualTo(day);
    }

    @Test
    void monthlySummarySeparatesMoneyInAndOut() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        transfer(alice, createAccount("Bob"), 2_500);

        MonthlySummaryResponse summary = rest.getForObject("/accounts/" + alice + "/monthly-summary?months=2",
                MonthlySummaryResponse.class);

        YearMonth now = YearMonth.now(BERLIN);
        assertThat(summary.months()).hasSize(2);
        assertThat(summary.months().get(0)).isEqualTo(new MonthlySummaryResponse.Month(now.minusMonths(1).toString(), 0, 0));
        assertThat(summary.months().get(1)).isEqualTo(new MonthlySummaryResponse.Month(now.toString(), 10_000, 2_500));
    }

    @Test
    void outOfRangeParametersAre400AndUnknownAccountIs404() {
        UUID alice = createAccount("Alice");

        assertThat(rest.getForEntity("/accounts/" + alice + "/balance-history?days=0", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/accounts/" + alice + "/monthly-summary?months=25", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> unknown = rest.getForEntity("/accounts/" + UUID.randomUUID() + "/balance-history", Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void priceHistoryIsOldestFirstAndLimited() {
        String symbol = createInstrument(1_000);
        setPrice(symbol, 1_100);
        setPrice(symbol, 1_200);

        PriceResponse[] lastTwo = rest.getForObject("/instruments/" + symbol + "/prices?limit=2", PriceResponse[].class);

        assertThat(lastTwo).extracting(PriceResponse::price).containsExactly(1_100L, 1_200L);
    }

    /** A balanced deposit with an explicit booking time, written with SQL like an old booking would be. */
    private void bookDepositAt(UUID account, long amount, ZonedDateTime at) {
        UUID tx = UUID.randomUUID();
        var ts = java.sql.Timestamp.from(at.toInstant());
        jdbc.execute((java.sql.Connection c) -> {
            c.setAutoCommit(false);
            try (var t = c.prepareStatement("INSERT INTO transactions (id, type, idempotency_key, request_hash, created_at) "
                    + "VALUES (?, 'DEPOSIT', ?, 'h', ?)");
                 var e = c.prepareStatement("INSERT INTO entries (id, transaction_id, account_id, amount, created_at) "
                         + "VALUES (?, ?, ?, ?, ?)");
                 var b = c.prepareStatement("UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                t.setObject(1, tx);
                t.setString(2, "test-" + tx);
                t.setTimestamp(3, ts);
                t.executeUpdate();
                for (Object[] row : new Object[][] {{SystemAccounts.EXTERNAL_FUNDING_ID, -amount}, {account, amount}}) {
                    e.setObject(1, UUID.randomUUID());
                    e.setObject(2, tx);
                    e.setObject(3, row[0]);
                    e.setLong(4, (Long) row[1]);
                    e.setTimestamp(5, ts);
                    e.executeUpdate();
                    b.setLong(1, (Long) row[1]);
                    b.setObject(2, row[0]);
                    b.executeUpdate();
                }
                c.commit();
            } finally {
                c.setAutoCommit(true);
            }
            return null;
        });
    }
}
