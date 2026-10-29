package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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

    /** POSTs with the given Idempotency-Key header. */
    <T> ResponseEntity<T> postWithKey(String path, Object body, String idempotencyKey, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.postForEntity(path, new HttpEntity<>(body, headers), responseType);
    }

    /** POSTs as a brand-new operation: a fresh, never used Idempotency-Key. */
    <T> ResponseEntity<T> postWithNewKey(String path, Object body, Class<T> responseType) {
        return postWithKey(path, body, UUID.randomUUID().toString(), responseType);
    }

    UUID createAccount(String ownerName) {
        ResponseEntity<AccountResponse> response = rest.postForEntity("/accounts",
                Map.of("ownerName", ownerName, "currency", "EUR"), AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    void deposit(UUID accountId, long amount) {
        ResponseEntity<String> response = postWithNewKey("/deposits",
                Map.of("accountId", accountId, "amount", amount, "currency", "EUR"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    void transfer(UUID from, UUID to, long amount) {
        ResponseEntity<String> response = postWithNewKey("/transfers",
                Map.of("fromAccountId", from, "toAccountId", to, "amount", amount, "currency", "EUR"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** Creates an EUR instrument with a fresh, unique symbol and the given price; returns the symbol. */
    String createInstrument(long priceInCents) {
        String symbol = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        ResponseEntity<String> created = rest.postForEntity("/instruments",
                Map.of("symbol", symbol, "name", "Test " + symbol, "currency", "EUR"), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        setPrice(symbol, priceInCents);
        return symbol;
    }

    void setPrice(String symbol, long priceInCents) {
        ResponseEntity<String> response = rest.postForEntity("/instruments/" + symbol + "/prices",
                Map.of("price", priceInCents), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** Places a trade with a fresh Idempotency-Key. */
    <T> ResponseEntity<T> trade(UUID accountId, String side, String symbol, long quantity, Class<T> responseType) {
        return postWithNewKey("/accounts/" + accountId + "/trades",
                Map.of("side", side, "symbol", symbol, "quantity", quantity), responseType);
    }

    PortfolioResponse portfolioOf(UUID accountId) {
        return rest.getForObject("/accounts/" + accountId + "/portfolio", PortfolioResponse.class);
    }

    /** The cached balance, as the API returns it. */
    long balanceOf(UUID accountId) {
        return rest.getForObject("/accounts/" + accountId, AccountResponse.class).balance();
    }

    /** The balance computed from the full history: SUM(entries.amount). */
    long entriesSumOf(UUID accountId) {
        return jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM entries WHERE account_id = ?",
                Long.class, accountId);
    }

    /** The sum of every entry in the whole ledger. Must always be exactly 0. */
    long ledgerTotal() {
        return jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM entries", Long.class);
    }
}
