-- V4: every new transaction must carry the Idempotency-Key it was created with and the hash of
-- its request. Transactions written before Milestone 4 have neither, and since transactions are
-- append-only they cannot be filled in afterwards. NOT VALID means: enforce the rule for every
-- new row, but do not check the rows that already exist.
ALTER TABLE transactions
    ADD CONSTRAINT transactions_idempotency_required
    CHECK (idempotency_key IS NOT NULL AND request_hash IS NOT NULL) NOT VALID;
