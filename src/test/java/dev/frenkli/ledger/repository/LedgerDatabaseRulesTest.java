package dev.frenkli.ledger.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.frenkli.ledger.AbstractIntegrationTest;
import dev.frenkli.ledger.domain.SystemAccounts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The database's own safety net, tested with raw SQL that bypasses the service layer: even buggy
 * application code cannot commit an unbalanced transaction or rewrite history.
 */
class LedgerDatabaseRulesTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    void balancedTransactionCommits() {
        UUID customer = insertCustomerAccount();
        UUID transactionId = UUID.randomUUID();

        tx.executeWithoutResult(status -> {
            insertTransaction(transactionId);
            insertEntry(transactionId, SystemAccounts.EXTERNAL_FUNDING_ID, -700);
            insertEntry(transactionId, customer, 700);
        });

        assertThat(entryCount(transactionId)).isEqualTo(2);
    }

    @Test
    void unbalancedTransactionIsRejectedAtCommitAndFullyRolledBack() {
        UUID customer = insertCustomerAccount();
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            insertTransaction(transactionId);
            insertEntry(transactionId, SystemAccounts.EXTERNAL_FUNDING_ID, -700);
            insertEntry(transactionId, customer, 699);
        })).isInstanceOf(DataAccessException.class).hasMessageContaining("unbalanced");

        assertThat(transactionExists(transactionId)).isFalse();
        assertThat(entryCount(transactionId)).isZero();
    }

    @Test
    void transactionWithASingleEntryIsRejected() {
        UUID customer = insertCustomerAccount();
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            insertTransaction(transactionId);
            insertEntry(transactionId, customer, 700);
        })).isInstanceOf(DataAccessException.class).hasMessageContaining("unbalanced");
    }

    @Test
    void transactionWithoutEntriesIsRejected() {
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> insertTransaction(transactionId)))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("unbalanced");
    }

    @Test
    void entriesCannotBeUpdatedOrDeleted() {
        UUID customer = insertCustomerAccount();
        UUID transactionId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            insertTransaction(transactionId);
            insertEntry(transactionId, SystemAccounts.EXTERNAL_FUNDING_ID, -700);
            insertEntry(transactionId, customer, 700);
        });

        assertThatThrownBy(() -> jdbc.update("UPDATE entries SET amount = 1000000 WHERE account_id = ?", customer))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM entries WHERE transaction_id = ?", transactionId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM transactions WHERE id = ?", transactionId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE entries"))
                .hasMessageContaining("append-only");
    }

    @Test
    void zeroAmountEntryIsRejected() {
        UUID customer = insertCustomerAccount();
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            insertTransaction(transactionId);
            insertEntry(transactionId, customer, 0);
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void customerBalanceCannotGoNegative() {
        UUID customer = insertCustomerAccount();

        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance = -1 WHERE id = ?", customer))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("accounts_customer_no_overdraft");
    }

    @Test
    void systemAccountMayGoNegative() {
        UUID customer = insertCustomerAccount();
        UUID transactionId = UUID.randomUUID();

        tx.executeWithoutResult(status -> {
            insertTransaction(transactionId);
            insertEntry(transactionId, SystemAccounts.EXTERNAL_FUNDING_ID, -1_000);
            insertEntry(transactionId, customer, 1_000);
        });

        Long fundingBalance = jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?",
                Long.class, SystemAccounts.EXTERNAL_FUNDING_ID);
        assertThat(fundingBalance).isNegative();
    }

    @Test
    void newTransactionWithoutIdempotencyKeyIsRejected() {
        UUID customer = insertCustomerAccount();
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO transactions (id, type, created_at) VALUES (?, 'DEPOSIT', now())", transactionId);
            insertEntry(transactionId, SystemAccounts.EXTERNAL_FUNDING_ID, -100);
            insertEntry(transactionId, customer, 100);
        })).isInstanceOf(DataAccessException.class).hasMessageContaining("transactions_idempotency_required");
    }

    @Test
    void idempotencyKeyIsUnique() {
        UUID customer = insertCustomerAccount();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO transactions (id, type, idempotency_key, request_hash, created_at) "
                    + "VALUES (?, 'DEPOSIT', 'same-key-' || ?, 'h', now())", first, customer.toString());
            insertEntry(first, SystemAccounts.EXTERNAL_FUNDING_ID, -100);
            insertEntry(first, customer, 100);
        });

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO transactions (id, type, idempotency_key, request_hash, created_at) "
                    + "VALUES (?, 'DEPOSIT', 'same-key-' || ?, 'h', now())", second, customer.toString());
            insertEntry(second, SystemAccounts.EXTERNAL_FUNDING_ID, -100);
            insertEntry(second, customer, 100);
        })).isInstanceOf(DataAccessException.class);
    }

    @Test
    void externalFundingSystemAccountIsSeeded() {
        String type = jdbc.queryForObject("SELECT type FROM accounts WHERE id = ? AND owner_name = 'EXTERNAL_FUNDING'",
                String.class, SystemAccounts.EXTERNAL_FUNDING_ID);

        assertThat(type).isEqualTo("SYSTEM");
    }

    private UUID insertCustomerAccount() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, owner_name, type, currency, balance, status, created_at) "
                + "VALUES (?, 'Test', 'CUSTOMER', 'EUR', 0, 'ACTIVE', now())", id);
        return id;
    }

    private void insertTransaction(UUID id) {
        jdbc.update("INSERT INTO transactions (id, type, idempotency_key, request_hash, created_at) "
                + "VALUES (?, 'DEPOSIT', ?, 'test-hash', now())", id, "test-" + id);
    }

    /** Like the application does: write the entry and update the cached balance together. */
    private void insertEntry(UUID transactionId, UUID accountId, long amount) {
        jdbc.update("INSERT INTO entries (id, transaction_id, account_id, amount, created_at) "
                + "VALUES (?, ?, ?, ?, now())", UUID.randomUUID(), transactionId, accountId, amount);
        jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, accountId);
    }

    private boolean transactionExists(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM transactions WHERE id = ?", Integer.class, id) > 0;
    }

    private int entryCount(UUID transactionId) {
        return jdbc.queryForObject("SELECT count(*) FROM entries WHERE transaction_id = ?", Integer.class, transactionId);
    }
}
