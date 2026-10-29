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
    void aTransactionCanBeReversedOnlyOnce() {
        UUID customer = insertCustomerAccount();
        UUID original = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            insertTransaction(original);
            insertEntry(original, SystemAccounts.EXTERNAL_FUNDING_ID, -300);
            insertEntry(original, customer, 300);
        });
        insertReversal(original, customer, 100);

        assertThatThrownBy(() -> insertReversal(original, customer, 100))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("transactions_reversed_once");
    }

    @Test
    void reversalMustSayWhatItReverses() {
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO transactions (id, type, idempotency_key, request_hash, "
                + "created_at) VALUES (?, 'REVERSAL', ?, 'h', now())", transactionId, "test-" + transactionId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("transactions_reversal_link");
    }

    /** A (deliberately simplified) reversal of {@code original}, booked with raw SQL. */
    private void insertReversal(UUID original, UUID customer, long amount) {
        UUID reversal = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO transactions (id, type, idempotency_key, request_hash, reverses_transaction_id, "
                    + "created_at) VALUES (?, 'REVERSAL', ?, 'h', ?, now())", reversal, "test-" + reversal, original);
            insertEntry(reversal, customer, -amount);
            insertEntry(reversal, SystemAccounts.EXTERNAL_FUNDING_ID, amount);
        });
    }

    @Test
    void tradeGrossAmountMustBeQuantityTimesPrice() {
        UUID customer = insertCustomerAccount();
        String symbol = insertInstrument();
        UUID cash = insertTradeCash(customer, -1_000);

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                insertTrade(customer, symbol, "BUY", 10, 100, 999, cash)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("trades_check");
    }

    @Test
    void holdingCannotGoNegative() {
        UUID customer = insertCustomerAccount();
        String symbol = insertInstrument();
        UUID buyCash = insertTradeCash(customer, -1_000);
        tx.executeWithoutResult(status -> insertTrade(customer, symbol, "BUY", 10, 100, 1_000, buyCash));
        UUID sellCash = insertTradeCash(customer, 1_100);

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                insertTrade(customer, symbol, "SELL", 11, 100, 1_100, sellCash)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("would be negative");
    }

    @Test
    void tradesAndPricesAreAppendOnly() {
        UUID customer = insertCustomerAccount();
        String symbol = insertInstrument();
        UUID cash = insertTradeCash(customer, -1_000);
        tx.executeWithoutResult(status -> insertTrade(customer, symbol, "BUY", 10, 100, 1_000, cash));

        assertThatThrownBy(() -> jdbc.update("UPDATE trades SET quantity = 1 WHERE symbol = ?", symbol))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM instrument_prices WHERE symbol = ?", symbol))
                .hasMessageContaining("append-only");
    }

    private String insertInstrument() {
        String symbol = "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO instruments (symbol, name, currency, created_at) VALUES (?, 'Test', 'EUR', now())", symbol);
        jdbc.update("INSERT INTO instrument_prices (id, symbol, price, created_at) VALUES (?, ?, 100, now())",
                UUID.randomUUID(), symbol);
        return symbol;
    }

    /** Commits a balanced cash transaction for a trade (customerAmount < 0 buys, > 0 sells). */
    private UUID insertTradeCash(UUID customer, long customerAmount) {
        UUID transactionId = UUID.randomUUID();
        if (customerAmount < 0) {
            // Fund the customer first, so the buy does not overdraw the account.
            UUID funding = UUID.randomUUID();
            tx.executeWithoutResult(status -> {
                insertTransaction(funding);
                insertEntry(funding, SystemAccounts.EXTERNAL_FUNDING_ID, customerAmount);
                insertEntry(funding, customer, -customerAmount);
            });
        }
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO transactions (id, type, idempotency_key, request_hash, created_at) "
                    + "VALUES (?, 'TRADE', ?, 'h', now())", transactionId, "test-" + transactionId);
            insertEntry(transactionId, customer, customerAmount);
            insertEntry(transactionId, SystemAccounts.SECURITIES_SETTLEMENT_ID, -customerAmount);
        });
        return transactionId;
    }

    private void insertTrade(UUID account, String symbol, String side, long quantity, long price, long gross,
                             UUID transactionId) {
        jdbc.update("INSERT INTO trades (id, account_id, symbol, side, quantity, price, gross_amount, transaction_id, "
                + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), account, symbol, side, quantity, price, gross, transactionId);
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
