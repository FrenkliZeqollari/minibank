package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Helpers for API tests. Tests share one database and the ledger is append-only, so each test
 * creates its own fresh accounts instead of cleaning up.
 */
abstract class AbstractApiTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    UUID createAccount(String ownerName) {
        ResponseEntity<AccountResponse> response = rest.postForEntity("/accounts",
                Map.of("ownerName", ownerName, "currency", "EUR"), AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    void deposit(UUID accountId, long amount) {
        ResponseEntity<String> response = rest.postForEntity("/deposits",
                Map.of("accountId", accountId, "amount", amount, "currency", "EUR"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    long balanceOf(UUID accountId) {
        return rest.getForObject("/accounts/" + accountId, AccountResponse.class).balance();
    }

    /** The sum of every entry in the whole ledger. Must always be exactly 0. */
    long ledgerTotal() {
        return jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM entries", Long.class);
    }
}
