package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Domain rule 5 (no overdrafts) and rule 8 (lock accounts, in a consistent order) under real
 * concurrency: many HTTP requests at once, against a real Postgres.
 */
class ConcurrentTransferTest extends AbstractApiTest {

    private static final int THREADS = 50;

    @Test
    void fiftyConcurrentTransfersFromOneAccountNeverOverdraw() throws Exception {
        UUID source = createAccount("Source");
        deposit(source, 10_000);
        List<UUID> recipients = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            recipients.add(createAccount("Recipient " + i));
        }

        // Watch the committed balance while the transfers run and remember the lowest value seen.
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong lowestBalanceSeen = new AtomicLong(Long.MAX_VALUE);
        Thread watcher = new Thread(() -> {
            while (running.get()) {
                lowestBalanceSeen.accumulateAndGet(cachedBalanceInDb(source), Math::min);
            }
        });
        watcher.start();

        // 50 transfers of 10.00 EUR from an account holding 100.00 EUR: exactly 10 can succeed.
        List<HttpStatusCode> statuses = runAtOnce(THREADS, i -> () -> rest.postForEntity("/transfers",
                Map.of("fromAccountId", source,
                        "toAccountId", recipients.get(i % recipients.size()),
                        "amount", 1_000,
                        "currency", "EUR"),
                String.class).getStatusCode());

        running.set(false);
        watcher.join();

        assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.CREATED)).hasSize(10);
        assertThat(statuses).filteredOn(s -> s.equals(HttpStatus.UNPROCESSABLE_ENTITY)).hasSize(40);
        assertThat(balanceOf(source)).isZero();
        assertThat(lowestBalanceSeen.get()).isNotNegative();
        assertThat(entriesSumOf(source)).isZero();
        long received = recipients.stream().mapToLong(this::balanceOf).sum();
        assertThat(received).isEqualTo(10_000);
        assertThat(ledgerTotal()).isZero();
    }

    @Test
    void transfersInBothDirectionsAtOnceDoNotDeadlock() throws Exception {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 100_000);
        deposit(bob, 100_000);

        // Half the requests go A -> B, half B -> A. Locking in request order (from, then to)
        // would deadlock: one transaction holds A and waits for B, the other holds B, waits for A.
        List<HttpStatusCode> statuses = runAtOnce(THREADS, i -> () -> rest.postForEntity("/transfers",
                Map.of("fromAccountId", i % 2 == 0 ? alice : bob,
                        "toAccountId", i % 2 == 0 ? bob : alice,
                        "amount", 100,
                        "currency", "EUR"),
                String.class).getStatusCode());

        assertThat(statuses).allMatch(s -> s.equals(HttpStatus.CREATED));
        assertThat(balanceOf(alice)).isEqualTo(100_000).isEqualTo(entriesSumOf(alice));
        assertThat(balanceOf(bob)).isEqualTo(100_000).isEqualTo(entriesSumOf(bob));
        assertThat(ledgerTotal()).isZero();
    }

    interface TaskFactory {
        Callable<HttpStatusCode> create(int index);
    }

    /**
     * Starts {@code count} tasks on their own threads, lets them all wait at a latch, then releases
     * them together so the requests really overlap.
     */
    private static List<HttpStatusCode> runAtOnce(int count, TaskFactory factory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HttpStatusCode>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<HttpStatusCode> task = factory.create(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();

            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private long cachedBalanceInDb(UUID accountId) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", Long.class, accountId);
    }
}
