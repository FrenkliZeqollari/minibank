package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Domain rule 7: Idempotency-Key handling on money-moving POSTs. */
class IdempotencyApiTest extends AbstractApiTest {

    @Test
    void retryWithSameKeyAndPayloadReturnsTheOriginalAndBooksNothingNew() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = transferBody(alice, bob, 2_500);

        ResponseEntity<String> first = postWithKey("/transfers", body, key, String.class);
        ResponseEntity<String> retry = postWithKey("/transfers", body, key, String.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(transactionsWithKey(key)).isEqualTo(1);
        assertThat(balanceOf(alice)).isEqualTo(7_500);
        assertThat(balanceOf(bob)).isEqualTo(2_500);
    }

    @Test
    void depositRetryIsAlsoIdempotent() {
        UUID alice = createAccount("Alice");
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("accountId", alice, "amount", 500, "currency", "EUR");

        ResponseEntity<String> first = postWithKey("/deposits", body, key, String.class);
        ResponseEntity<String> retry = postWithKey("/deposits", body, key, String.class);

        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(balanceOf(alice)).isEqualTo(500);
    }

    @Test
    void replayReturnsTheOriginalResultEvenIfTheBalanceHasChangedSince() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 1_000);
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = transferBody(alice, bob, 1_000);
        ResponseEntity<String> first = postWithKey("/transfers", body, key, String.class);
        assertThat(balanceOf(alice)).isZero();

        // Alice could not afford this transfer any more, but it is not a new transfer: it is the
        // retry of one that already happened, so the client must get the original success back.
        ResponseEntity<String> retry = postWithKey("/transfers", body, key, String.class);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody()).isEqualTo(first.getBody());
    }

    @Test
    void sameKeyWithDifferentPayloadIs422AndBooksNothing() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        String key = UUID.randomUUID().toString();
        postWithKey("/transfers", transferBody(alice, bob, 2_500), key, String.class);

        ResponseEntity<Map> response = postWithKey("/transfers", transferBody(alice, bob, 9_000), key, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:idempotency-key-reused");
        assertThat(balanceOf(alice)).isEqualTo(7_500);
        assertThat(transactionsWithKey(key)).isEqualTo(1);
    }

    @Test
    void keyUsedForADepositCannotBeReusedForATransfer() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        String key = UUID.randomUUID().toString();
        postWithKey("/deposits", Map.of("accountId", alice, "amount", 5_000, "currency", "EUR"), key, String.class);

        ResponseEntity<Map> response = postWithKey("/transfers", transferBody(alice, bob, 100), key, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balanceOf(bob)).isZero();
    }

    @Test
    void rejectedRequestIsNotStoredSoTheSameKeyCanSucceedLater() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = transferBody(alice, bob, 1_000);

        ResponseEntity<Map> rejected = postWithKey("/transfers", body, key, Map.class);
        deposit(alice, 1_000);
        ResponseEntity<String> accepted = postWithKey("/transfers", body, key, String.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accepted.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
        assertThat(balanceOf(bob)).isEqualTo(1_000);
    }

    @Test
    void missingIdempotencyKeyIs400() {
        UUID alice = createAccount("Alice");

        ResponseEntity<Map> response = rest.postForEntity("/deposits",
                Map.of("accountId", alice, "amount", 100, "currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(balanceOf(alice)).isZero();
    }

    @Test
    void blankIdempotencyKeyIs400() {
        UUID alice = createAccount("Alice");

        ResponseEntity<Map> response = postWithKey("/deposits",
                Map.of("accountId", alice, "amount", 100, "currency", "EUR"), "   ", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void parallelDuplicatesOfOneTransferBookItExactlyOnce() throws Exception {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = transferBody(alice, bob, 1_000);

        // A client that retries aggressively: 20 copies of the same request at the same moment.
        List<ResponseEntity<String>> responses = runAtOnce(20,
                () -> postWithKey("/transfers", body, key, String.class));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED));
        assertThat(responses).extracting(ResponseEntity::getBody).containsOnly(responses.get(0).getBody());
        assertThat(responses).filteredOn(r -> "false".equals(r.getHeaders().getFirst("Idempotent-Replayed")))
                .hasSize(1);
        assertThat(transactionsWithKey(key)).isEqualTo(1);
        assertThat(balanceOf(alice)).isEqualTo(9_000);
        assertThat(balanceOf(bob)).isEqualTo(1_000);
        assertThat(ledgerTotal()).isZero();
    }

    @Test
    void parallelRequestsWithOneKeyButDifferentPayloadsBookOnlyOne() throws Exception {
        UUID alice = createAccount("Alice");
        List<UUID> recipients = List.of(createAccount("Bob"), createAccount("Carol"));
        deposit(alice, 10_000);
        String key = UUID.randomUUID().toString();
        int[] next = {0};

        List<ResponseEntity<String>> responses = runAtOnce(20, () -> {
            UUID recipient;
            synchronized (next) {
                recipient = recipients.get(next[0]++ % 2);
            }
            return postWithKey("/transfers", transferBody(alice, recipient, 1_000), key, String.class);
        });

        // Whichever payload came first wins; copies of it are replays, the other payload is a 422.
        // Never a 500 from the unique constraint, and never two bookings.
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode())
                .isIn(HttpStatus.CREATED, HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(transactionsWithKey(key)).isEqualTo(1);
        assertThat(balanceOf(alice)).isEqualTo(9_000);
        assertThat(ledgerTotal()).isZero();
    }

    private static Map<String, Object> transferBody(UUID from, UUID to, long amount) {
        return Map.of("fromAccountId", from, "toAccountId", to, "amount", amount, "currency", "EUR");
    }

    private int transactionsWithKey(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM transactions WHERE idempotency_key = ?", Integer.class, key);
    }

    /** Runs the same task on {@code count} threads, released together by a latch. */
    private static <T> List<T> runAtOnce(int count, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
