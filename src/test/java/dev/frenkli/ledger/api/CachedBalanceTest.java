package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.SystemAccounts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain rule 4: the cached balance always equals SUM(entries.amount). */
class CachedBalanceTest extends AbstractApiTest {

    @Test
    void cachedBalanceEqualsSumOfEntriesAfterManyRandomOperations() {
        List<UUID> accounts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            accounts.add(createAccount("Customer " + i));
        }
        // Fixed seed: random-looking but the same every run, so a failure can be reproduced.
        Random random = new Random(42);

        for (int i = 0; i < 200; i++) {
            UUID a = accounts.get(random.nextInt(accounts.size()));
            UUID b = accounts.get(random.nextInt(accounts.size()));
            long amount = 1 + random.nextInt(5_000);
            if (random.nextInt(3) == 0) {
                postWithNewKey("/deposits",
                        Map.of("accountId", a, "amount", amount, "currency", "EUR"), String.class);
            } else {
                // Deliberately includes transfers that fail (same account, insufficient funds):
                // a rejected transfer must leave no trace in either the entries or the cache.
                postWithNewKey("/transfers",
                        Map.of("fromAccountId", a, "toAccountId", b, "amount", amount, "currency", "EUR"),
                        String.class);
            }
        }

        for (UUID account : accounts) {
            assertThat(balanceOf(account)).as("cache of %s", account).isEqualTo(entriesSumOf(account));
            assertThat(balanceOf(account)).isNotNegative();
        }
        assertThat(balanceOf(SystemAccounts.EXTERNAL_FUNDING_ID))
                .isEqualTo(entriesSumOf(SystemAccounts.EXTERNAL_FUNDING_ID));
        assertThat(ledgerTotal()).isZero();
    }

    @Test
    void cacheFollowsEveryStepOfADepositAndTransfer() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        long fundingBefore = balanceOf(SystemAccounts.EXTERNAL_FUNDING_ID);

        deposit(alice, 10_000);
        assertThat(balanceOf(alice)).isEqualTo(10_000);
        assertThat(balanceOf(SystemAccounts.EXTERNAL_FUNDING_ID)).isEqualTo(fundingBefore - 10_000);

        transfer(alice, bob, 3_000);
        assertThat(balanceOf(alice)).isEqualTo(7_000).isEqualTo(entriesSumOf(alice));
        assertThat(balanceOf(bob)).isEqualTo(3_000).isEqualTo(entriesSumOf(bob));
    }
}
