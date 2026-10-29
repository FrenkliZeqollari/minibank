package dev.frenkli.ledger.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.AbstractIntegrationTest;
import dev.frenkli.ledger.service.LedgerHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Starts the app with the demo profile (own database) and checks the seeded bank. */
@ActiveProfiles("demo")
class DemoDataSeederTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LedgerHealthService health;

    @Autowired
    DemoDataSeeder seeder;

    @Test
    void seedsABalancedBankWithHistory() {
        assertThat(count("SELECT count(*) FROM accounts WHERE type = 'CUSTOMER'")).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM transactions WHERE type = 'REVERSAL'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM trades")).isGreaterThan(5);
        assertThat(count("SELECT count(DISTINCT symbol) FROM instrument_prices")).isEqualTo(3);
        // History really is spread over months, not booked all at once.
        assertThat(count("SELECT count(DISTINCT date_trunc('month', created_at)) FROM entries")).isGreaterThanOrEqualTo(6);
        assertThat(count("SELECT count(*) FROM entries WHERE created_at > now()")).isZero();
        assertThat(health.check().healthy()).isTrue();
    }

    @Test
    void runningAgainDoesNothing() {
        long before = count("SELECT count(*) FROM transactions");

        seeder.run(null);

        assertThat(count("SELECT count(*) FROM transactions")).isEqualTo(before);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
