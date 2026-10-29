package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerHealthApiTest extends AbstractApiTest {

    @Test
    void ledgerIsHealthyAfterRealOperations() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 5_000);
        transfer(alice, bob, 1_234);

        LedgerHealthResponse health = rest.getForObject("/ledger/health", LedgerHealthResponse.class);

        assertThat(health.healthy()).isTrue();
        assertThat(health.sumOfAllEntries()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(health.balanceMismatches()).isEmpty();
    }

    @Test
    void reportsAnAccountWhoseCachedBalanceWasCorrupted() {
        UUID alice = createAccount("Alice");
        deposit(alice, 5_000);
        // Simulate a bug that changed the cache without writing entries. Undone below, because the
        // other tests share this database and expect a healthy ledger.
        jdbc.update("UPDATE accounts SET balance = balance + 1 WHERE id = ?", alice);
        try {
            LedgerHealthResponse health = rest.getForObject("/ledger/health", LedgerHealthResponse.class);

            assertThat(health.healthy()).isFalse();
            assertThat(health.balanceMismatches())
                    .containsExactly(new LedgerHealthResponse.Mismatch(alice, 5_001, 5_000));
        } finally {
            jdbc.update("UPDATE accounts SET balance = balance - 1 WHERE id = ?", alice);
        }

        assertThat(rest.getForObject("/ledger/health", LedgerHealthResponse.class).healthy()).isTrue();
    }
}
