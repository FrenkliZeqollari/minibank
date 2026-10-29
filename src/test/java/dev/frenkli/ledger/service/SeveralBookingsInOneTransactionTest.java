package dev.frenkli.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression test: several bookings inside one DB transaction must each see the balance the
 * previous one left. Balances are updated with a direct SQL UPDATE; an Account object that JPA
 * cached earlier in the same transaction must not hide that change.
 */
class SeveralBookingsInOneTransactionTest extends AbstractIntegrationTest {

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    AccountService accounts;

    @Autowired
    MoneyMovementService money;

    @Test
    void transferSeesTheDepositMadeEarlierInTheSameTransaction() {
        UUID alice = accounts.open("Alice", "EUR").getId();
        UUID bob = accounts.open("Bob", "EUR").getId();

        transaction.executeWithoutResult(status -> {
            money.deposit(key(), alice, 100, "EUR", null);         // loads Alice, then raises her balance
            money.transfer(key(), alice, bob, 100, "EUR", null);   // must see the 100, not a cached 0
        });

        assertThat(accounts.get(alice).getBalance()).isZero();
        assertThat(accounts.get(bob).getBalance()).isEqualTo(100);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
