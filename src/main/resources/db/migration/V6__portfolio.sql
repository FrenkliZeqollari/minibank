-- V6: a securities portfolio on top of the ledger.
--
-- Cash still lives only in the ledger: buying shares is a normal double-entry transaction
-- (type TRADE) that moves cash from the customer to the SECURITIES_SETTLEMENT system account,
-- selling moves it back. The shares themselves are tracked in the append-only trades table:
-- like an account balance, a holding is the sum of its history.

-- New transaction type for the cash side of a trade.
ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_type_check CHECK (type IN ('DEPOSIT', 'TRANSFER', 'REVERSAL', 'TRADE'));

-- The bank's side of every trade: where customers' cash goes when they buy, and comes from when
-- they sell. Its balance is minus the net amount customers have invested. It may go negative.
-- Fixed id, see SystemAccounts.SECURITIES_SETTLEMENT_ID.
INSERT INTO accounts (id, owner_name, type, currency, balance, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000002', 'SECURITIES_SETTLEMENT', 'SYSTEM', 'EUR', 0, 'ACTIVE', now());

CREATE TABLE instruments (
    symbol      VARCHAR(12)  PRIMARY KEY CHECK (symbol ~ '^[A-Z0-9.]{1,12}$'),
    name        VARCHAR(255) NOT NULL,
    currency    CHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at  TIMESTAMPTZ  NOT NULL
);

-- Market prices, append-only: a new price is a new row, so the history is kept. The latest row
-- (highest seq) is the current price. price = minor units (cents) per share.
CREATE TABLE instrument_prices (
    id          UUID         PRIMARY KEY,
    seq         BIGINT       GENERATED ALWAYS AS IDENTITY UNIQUE,
    symbol      VARCHAR(12)  NOT NULL REFERENCES instruments (symbol),
    price       BIGINT       NOT NULL CHECK (price > 0),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX instrument_prices_symbol_seq_idx ON instrument_prices (symbol, seq DESC);

-- One row per executed trade. seq gives the exact order in which an account's trades happened,
-- which the average-cost calculation depends on.
CREATE TABLE trades (
    id              UUID         PRIMARY KEY,
    seq             BIGINT       GENERATED ALWAYS AS IDENTITY UNIQUE,
    account_id      UUID         NOT NULL REFERENCES accounts (id),
    symbol          VARCHAR(12)  NOT NULL REFERENCES instruments (symbol),
    side            VARCHAR(4)   NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity        BIGINT       NOT NULL CHECK (quantity > 0),
    price           BIGINT       NOT NULL CHECK (price > 0),
    -- The cash amount of the trade. The database checks the multiplication, too.
    gross_amount    BIGINT       NOT NULL CHECK (gross_amount = quantity * price),
    -- The ledger transaction that moved the cash. Exactly one per trade.
    transaction_id  UUID         NOT NULL UNIQUE REFERENCES transactions (id),
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX trades_account_symbol_idx ON trades (account_id, symbol, seq);

-- Append-only, like the ledger itself.
CREATE TRIGGER trades_append_only
    BEFORE UPDATE OR DELETE ON trades
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
CREATE TRIGGER trades_no_truncate
    BEFORE TRUNCATE ON trades
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();
CREATE TRIGGER instrument_prices_append_only
    BEFORE UPDATE OR DELETE ON instrument_prices
    FOR EACH ROW EXECUTE FUNCTION reject_modification();
CREATE TRIGGER instrument_prices_no_truncate
    BEFORE TRUNCATE ON instrument_prices
    FOR EACH STATEMENT EXECUTE FUNCTION reject_modification();

-- Safety net for "no short selling": a holding can never go below 0 shares. Checked at commit,
-- like the zero-sum rule, as a second line of defence behind the service layer.
CREATE FUNCTION check_holding_not_negative() RETURNS trigger AS $$
DECLARE
    holding NUMERIC;
BEGIN
    SELECT coalesce(sum(CASE side WHEN 'BUY' THEN quantity ELSE -quantity END), 0)
      INTO holding
      FROM trades
     WHERE account_id = NEW.account_id AND symbol = NEW.symbol;

    IF holding < 0 THEN
        RAISE EXCEPTION 'holding of % in account % would be negative: %', NEW.symbol, NEW.account_id, holding
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trades_holding_not_negative
    AFTER INSERT ON trades
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_holding_not_negative();
