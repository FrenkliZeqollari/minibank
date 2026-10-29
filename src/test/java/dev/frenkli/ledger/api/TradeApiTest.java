package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.SystemAccounts;
import dev.frenkli.ledger.domain.TradeSide;
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

class TradeApiTest extends AbstractApiTest {

    @Test
    void buyingMovesCashToSettlementAndAddsShares() {
        UUID alice = createAccount("Alice");
        deposit(alice, 100_000);
        String acme = createInstrument(1_234);
        long settlementBefore = balanceOf(SystemAccounts.SECURITIES_SETTLEMENT_ID);

        ResponseEntity<TradeResponse> response = trade(alice, "BUY", acme, 10, TradeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TradeResponse trade = response.getBody();
        assertThat(trade.side()).isEqualTo(TradeSide.BUY);
        assertThat(trade.price()).isEqualTo(1_234);
        assertThat(trade.grossAmount()).isEqualTo(12_340);
        assertThat(balanceOf(alice)).isEqualTo(100_000 - 12_340).isEqualTo(entriesSumOf(alice));
        assertThat(balanceOf(SystemAccounts.SECURITIES_SETTLEMENT_ID)).isEqualTo(settlementBefore + 12_340);
        assertThat(portfolioOf(alice).positions()).singleElement()
                .satisfies(p -> assertThat(p.quantity()).isEqualTo(10));
        assertThat(ledgerTotal()).isZero();
    }

    @Test
    void cashSideIsAnOrdinaryLedgerTransactionOnTheStatement() {
        UUID alice = createAccount("Alice");
        deposit(alice, 100_000);
        String acme = createInstrument(500);

        UUID transactionId = trade(alice, "BUY", acme, 4, TradeResponse.class).getBody().transactionId();

        StatementResponse statement = rest.getForObject("/accounts/" + alice + "/entries", StatementResponse.class);
        StatementResponse.Line line = statement.entries().get(0);
        assertThat(line.transactionId()).isEqualTo(transactionId);
        assertThat(line.transactionType()).isEqualTo(TransactionType.TRADE);
        assertThat(line.amount()).isEqualTo(-2_000);
        assertThat(line.description()).isEqualTo("BUY 4 " + acme + " @ 5.00 EUR");
    }

    @Test
    void sellingMovesCashBackAndRemovesShares() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        String acme = createInstrument(1_000);
        trade(alice, "BUY", acme, 5, String.class);
        setPrice(acme, 1_500);

        ResponseEntity<TradeResponse> response = trade(alice, "SELL", acme, 2, TradeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().grossAmount()).isEqualTo(3_000);
        assertThat(balanceOf(alice)).isEqualTo(10_000 - 5_000 + 3_000);
        assertThat(portfolioOf(alice).positions().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void buyingWithoutEnoughCashIs422AndBooksNothing() {
        UUID alice = createAccount("Alice");
        deposit(alice, 1_000);
        String acme = createInstrument(1_001);

        ResponseEntity<Map> response = trade(alice, "BUY", acme, 1, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:insufficient-funds");
        assertThat(balanceOf(alice)).isEqualTo(1_000);
        assertThat(portfolioOf(alice).positions()).isEmpty();
    }

    @Test
    void sellingMoreThanHeldIs422() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        String acme = createInstrument(100);
        trade(alice, "BUY", acme, 3, String.class);

        ResponseEntity<Map> response = trade(alice, "SELL", acme, 4, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:insufficient-holdings");
    }

    @Test
    void sellingSharesOfAnotherAccountIs422() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);
        String acme = createInstrument(100);
        trade(alice, "BUY", acme, 3, String.class);

        ResponseEntity<Map> response = trade(bob, "SELL", acme, 1, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void tradingWithoutAMarketPriceIs422() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        rest.postForEntity("/instruments", Map.of("symbol", "NOPRICE2", "name", "No price", "currency", "EUR"), String.class);

        ResponseEntity<Map> response = trade(alice, "BUY", "NOPRICE2", 1, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:no-market-price");
    }

    @Test
    void zeroQuantityIs422() {
        UUID alice = createAccount("Alice");
        String acme = createInstrument(100);

        ResponseEntity<Map> response = trade(alice, "BUY", acme, 0, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:non-positive-quantity");
    }

    @Test
    void unknownInstrumentIs404() {
        UUID alice = createAccount("Alice");

        ResponseEntity<Map> response = trade(alice, "BUY", "NOSUCH", 1, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownAccountIs404() {
        String acme = createInstrument(100);

        ResponseEntity<Map> response = trade(UUID.randomUUID(), "BUY", acme, 1, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidSideIs400() {
        UUID alice = createAccount("Alice");

        ResponseEntity<Map> response = postWithNewKey("/accounts/" + alice + "/trades",
                Map.of("side", "HOLD", "symbol", "ACME", "quantity", 1), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void retryWithTheSameKeyReplaysTheTradeEvenAfterThePriceChanged() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        String acme = createInstrument(1_000);
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("side", "BUY", "symbol", acme, "quantity", 2);

        ResponseEntity<String> first = postWithKey("/accounts/" + alice + "/trades", body, key, String.class);
        setPrice(acme, 9_999);
        ResponseEntity<String> retry = postWithKey("/accounts/" + alice + "/trades", body, key, String.class);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(balanceOf(alice)).isEqualTo(8_000);
    }

    @Test
    void sameKeyForADifferentTradeIs422() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        String acme = createInstrument(100);
        String key = UUID.randomUUID().toString();
        postWithKey("/accounts/" + alice + "/trades", Map.of("side", "BUY", "symbol", acme, "quantity", 2), key, String.class);

        ResponseEntity<Map> response = postWithKey("/accounts/" + alice + "/trades",
                Map.of("side", "BUY", "symbol", acme, "quantity", 3), key, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:idempotency-key-reused");
    }

    @Test
    void aTradeCannotBeReversed() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        String acme = createInstrument(100);
        UUID transactionId = trade(alice, "BUY", acme, 1, TradeResponse.class).getBody().transactionId();

        ResponseEntity<Map> response = postWithNewKey("/transactions/" + transactionId + "/reversal", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:trade-not-reversible");
    }

    @Test
    void concurrentSellsNeverSellMoreThanHeld() throws Exception {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        String acme = createInstrument(100);
        trade(alice, "BUY", acme, 5, String.class);

        // 20 parallel orders to sell 1 share each, while only 5 are held.
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<HttpStatusCode> statuses = new ArrayList<>();
        try {
            List<Future<HttpStatusCode>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return trade(alice, "SELL", acme, 1, String.class).getStatusCode();
                }));
            }
            ready.await();
            start.countDown();
            for (Future<HttpStatusCode> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.CREATED)).hasSize(5);
        assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.UNPROCESSABLE_ENTITY)).hasSize(15);
        assertThat(portfolioOf(alice).positions()).isEmpty();
        assertThat(balanceOf(alice)).isEqualTo(10_000);
        LedgerHealthResponse health = rest.getForObject("/ledger/health", LedgerHealthResponse.class);
        assertThat(health.healthy()).isTrue();
        assertThat(health.sumOfAllEntries()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
