package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class LedgerHealthApiTest extends AbstractApiTest {

    @Test
    void healthyLedgerIs200() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 5_000);
        transfer(alice, bob, 1_234);

        ResponseEntity<LedgerHealthResponse> response = rest.getForEntity("/ledger/health", LedgerHealthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LedgerHealthResponse health = response.getBody();
        assertThat(health.healthy()).isTrue();
        assertThat(health.sumOfAllEntries()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(health.balanceMismatches()).isEmpty();
    }

    @Test
    void corruptedLedgerIs503WithTheDetailsInTheBody() {
        UUID alice = createAccount("Alice");
        deposit(alice, 5_000);
        // Simulate a bug that changed the cache without writing entries. Undone below, because the
        // other tests share this database and expect a healthy ledger.
        jdbc.update("UPDATE accounts SET balance = balance + 1 WHERE id = ?", alice);
        try {
            ResponseEntity<LedgerHealthResponse> response =
                    rest.getForEntity("/ledger/health", LedgerHealthResponse.class);

            // 503 so that monitoring, load balancers and uptime checks notice without parsing JSON.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            LedgerHealthResponse health = response.getBody();
            assertThat(health.healthy()).isFalse();
            assertThat(health.balanceMismatches())
                    .containsExactly(new LedgerHealthResponse.Mismatch(alice, 5_001, 5_000));
        } finally {
            jdbc.update("UPDATE accounts SET balance = balance - 1 WHERE id = ?", alice);
        }

        assertThat(rest.getForEntity("/ledger/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
