-- V1: accounts, transactions, entries, and the database-level ledger rules.
--
-- Money is BIGINT in minor units (cents). An entry's amount is signed: positive = money into the
-- account, negative = money out of it. Every transaction's entries must sum to exactly 0.

CREATE TABLE accounts (
    id          UUID        PRIMARY KEY,
    owner_name  VARCHAR(255) NOT NULL,
    type        VARCHAR(16) NOT NULL CHECK (type IN ('CUSTOMER', 'SYSTEM')),
    currency    CHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    -- Cached balance. Kept in sync with SUM(entries.amount) from Milestone 2 on.
    balance     BIGINT      NOT NULL DEFAULT 0,
    status      VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE transactions (
    id                       UUID        PRIMARY KEY,
    type                     VARCHAR(16) NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER', 'REVERSAL')),
    description              VARCHAR(255),
    -- Filled in from Milestone 4 (idempotency). UNIQUE allows many NULLs in Postgres.
    idempotency_key          VARCHAR(255) UNIQUE,
    request_hash             VARCHAR(64),
    reverses_transaction_id  UUID REFERENCES transactions (id),
    created_at               TIMESTAMPTZ NOT NULL
);

CREATE TABLE entries (
    id              UUID        PRIMARY KEY,
    transaction_id  UUID        NOT NULL REFERENCES transactions (id),
    account_id      UUID        NOT NULL REFERENCES accounts (id),
    -- A zero entry moves no money and only adds noise to statements.
    amount          BIGINT      NOT NULL CHECK (amount <> 0),
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX entries_account_id_idx ON entries (account_id);
CREATE INDEX entries_transaction_id_idx ON entries (transaction_id);

-- ---------------------------------------------------------------------------------------------
-- Double-entry rule, enforced by the database as a second line of defence behind the service
-- layer: every transaction has at least 2 entries and they sum to exactly 0.
--
-- The triggers are DEFERRABLE INITIALLY DEFERRED, so they run at COMMIT, after all entries of the
-- transaction have been inserted. Checking after each single row would always fail, because the
-- first entry on its own never sums to 0. If the check fails, the whole DB transaction is rolled
-- back: no half-written transfer can ever be committed.
-- ---------------------------------------------------------------------------------------------
CREATE FUNCTION assert_transaction_balanced(tx_id UUID) RETURNS void AS $$
DECLARE
    entry_count INT;
    entry_sum   NUMERIC; -- NUMERIC so a huge sum cannot overflow BIGINT and wrap around
BEGIN
    SELECT count(*), coalesce(sum(amount), 0)
      INTO entry_count, entry_sum
      FROM entries
     WHERE transaction_id = tx_id;

    IF entry_count < 2 OR entry_sum <> 0 THEN
        RAISE EXCEPTION 'transaction % is unbalanced: % entries summing to %',
            tx_id, entry_count, entry_sum
            USING ERRCODE = 'check_violation';
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION check_entry_transaction_balanced() RETURNS trigger AS $$
BEGIN
    PERFORM assert_transaction_balanced(NEW.transaction_id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION check_transaction_balanced() RETURNS trigger AS $$
BEGIN
    PERFORM assert_transaction_balanced(NEW.id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Catches entries that do not sum to 0.
CREATE CONSTRAINT TRIGGER entries_balanced
    AFTER INSERT ON entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_entry_transaction_balanced();

-- Catches a transaction committed with no entries at all (the entries trigger never fires then).
CREATE CONSTRAINT TRIGGER transactions_balanced
    AFTER INSERT ON transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_transaction_balanced();

-- ---------------------------------------------------------------------------------------------
-- Append-only rule: committed money movements are history and are never changed or removed.
-- Mistakes are corrected with a new REVERSAL transaction (Milestone 5).
-- ---------------------------------------------------------------------------------------------
CREATE FUNCTION reject_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER entries_append_only
    BEFORE UPDATE OR DELETE ON entries
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER entries_no_truncate
    BEFORE TRUNCATE ON entries
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();

CREATE TRIGGER transactions_append_only
    BEFORE UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

CREATE TRIGGER transactions_no_truncate
    BEFORE TRUNCATE ON transactions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();

-- ---------------------------------------------------------------------------------------------
-- System account through which money enters and leaves the bank. A deposit of X is
-- EXTERNAL_FUNDING -X / customer +X, so the whole ledger still sums to 0. It may go negative:
-- its balance is minus the total money customers have deposited.
-- The id is fixed so the application can find it (see SystemAccounts.EXTERNAL_FUNDING_ID).
-- ---------------------------------------------------------------------------------------------
INSERT INTO accounts (id, owner_name, type, currency, balance, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'EXTERNAL_FUNDING', 'SYSTEM', 'EUR', 0, 'ACTIVE', now());
