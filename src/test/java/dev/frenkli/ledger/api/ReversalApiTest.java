package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.SystemAccounts;
import dev.frenkli.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/** Domain rule 3: corrections are new REVERSAL transactions, and a transaction is reversed once. */
class ReversalApiTest extends AbstractApiTest {

    @Test
    void reversingATransferBooksTheMirrorImageAndRestoresBalances() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 2_500);

        ResponseEntity<TransactionResponse> response = reverse(transferId, TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse reversal = response.getBody();
        assertThat(reversal.type()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversal.reversesTransactionId()).isEqualTo(transferId);
        assertThat(reversal.entries()).containsExactlyInAnyOrder(
                new TransactionResponse.EntryResponse(bob, -2_500),
                new TransactionResponse.EntryResponse(alice, 2_500));
        assertThat(balanceOf(alice)).isEqualTo(10_000).isEqualTo(entriesSumOf(alice));
        assertThat(balanceOf(bob)).isZero();
        assertThat(ledgerTotal()).isZero();
    }

    @Test
    void theOriginalStaysInTheHistory() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 2_500);

        reverse(transferId, String.class);

        // Bob's statement shows both: the money arriving and the reversal taking it back.
        StatementResponse statement = rest.getForObject("/accounts/" + bob + "/entries", StatementResponse.class);
        assertThat(statement.entries()).extracting(StatementResponse.Line::transactionType)
                .containsExactly(TransactionType.REVERSAL, TransactionType.TRANSFER);
        assertThat(statement.entries()).extracting(StatementResponse.Line::amount).containsExactly(-2_500L, 2_500L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries WHERE transaction_id = ?", Integer.class,
                transferId)).isEqualTo(2);
    }

    @Test
    void reversingADepositGivesTheMoneyBackToExternalFunding() {
        UUID alice = createAccount("Alice");
        long fundingBefore = balanceOf(SystemAccounts.EXTERNAL_FUNDING_ID);
        UUID depositId = postWithNewKey("/deposits",
                Map.of("accountId", alice, "amount", 4_000, "currency", "EUR"), TransactionResponse.class)
                .getBody().id();

        reverse(depositId, String.class);

        assertThat(balanceOf(alice)).isZero();
        assertThat(balanceOf(SystemAccounts.EXTERNAL_FUNDING_ID)).isEqualTo(fundingBefore);
    }

    @Test
    void secondReversalIs409() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 2_500);
        reverse(transferId, String.class);

        ResponseEntity<Map> second = reverse(transferId, Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("type", "urn:problem:ledger:already-reversed");
        assertThat(balanceOf(alice)).isEqualTo(10_000);
    }

    @Test
    void reversingAReversalIs422() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 2_500);
        UUID reversalId = reverse(transferId, TransactionResponse.class).getBody().id();

        ResponseEntity<Map> response = reverse(reversalId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:reversal-not-reversible");
    }

    @Test
    void unknownTransactionIs404() {
        ResponseEntity<Map> response = reverse(UUID.randomUUID(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:transaction-not-found");
    }

    @Test
    void reversalThatWouldOverdrawTheRecipientIs422AndBooksNothing() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        UUID carol = createAccount("Carol");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 2_500);
        transfer(bob, carol, 2_000); // Bob already spent most of it

        ResponseEntity<Map> response = reverse(transferId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:insufficient-funds");
        assertThat(balanceOf(bob)).isEqualTo(500);
        assertThat(balanceOf(alice)).isEqualTo(7_500);
    }

    @Test
    void retryWithTheSameKeyReplaysTheReversal() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 2_500);
        String key = UUID.randomUUID().toString();

        ResponseEntity<String> first = postWithKey("/transactions/" + transferId + "/reversal", null, key, String.class);
        ResponseEntity<String> retry = postWithKey("/transactions/" + transferId + "/reversal", null, key, String.class);

        // A retry is not a second reversal: it gets the original 201, not a 409.
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(balanceOf(alice)).isEqualTo(10_000);
    }

    @Test
    void sameKeyForAnotherTransactionIs422() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID first = transferAndGetId(alice, bob, 100);
        UUID second = transferAndGetId(alice, bob, 200);
        String key = UUID.randomUUID().toString();
        postWithKey("/transactions/" + first + "/reversal", null, key, String.class);

        ResponseEntity<Map> response = postWithKey("/transactions/" + second + "/reversal", null, key, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:idempotency-key-reused");
    }

    @Test
    void missingIdempotencyKeyIs400() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 100);

        ResponseEntity<Map> response = rest.postForEntity("/transactions/" + transferId + "/reversal", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void parallelReversalsWithDifferentKeysReverseOnlyOnce() throws Exception {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        UUID transferId = transferAndGetId(alice, bob, 2_500);

        // Ten different clients (different keys) try to reverse the same transfer at once.
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatusCode>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return reverse(transferId, String.class).getStatusCode();
                }));
            }
            ready.await();
            start.countDown();
            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.CREATED)).hasSize(1);
            assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.CONFLICT)).hasSize(threads - 1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(balanceOf(alice)).isEqualTo(10_000);
        assertThat(balanceOf(bob)).isZero();
        LedgerHealthResponse health = rest.getForObject("/ledger/health", LedgerHealthResponse.class);
        assertThat(health.healthy()).isTrue();
        assertThat(health.sumOfAllEntries()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private UUID transferAndGetId(UUID from, UUID to, long amount) {
        ResponseEntity<TransactionResponse> response = postWithNewKey("/transfers",
                Map.of("fromAccountId", from, "toAccountId", to, "amount", amount, "currency", "EUR"),
                TransactionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private <T> ResponseEntity<T> reverse(UUID transactionId, Class<T> responseType) {
        return postWithNewKey("/transactions/" + transactionId + "/reversal", null, responseType);
    }
}
