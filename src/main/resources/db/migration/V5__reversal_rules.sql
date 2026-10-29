-- V5: database rules for reversals.

-- A transaction can be reversed at most once: at most one reversal may point to it. (UNIQUE
-- allows any number of NULLs, so ordinary transactions are not affected.)
ALTER TABLE transactions
    ADD CONSTRAINT transactions_reversed_once UNIQUE (reverses_transaction_id);

-- A REVERSAL always says what it reverses, and nothing else may claim to reverse something.
ALTER TABLE transactions
    ADD CONSTRAINT transactions_reversal_link
    CHECK ((type = 'REVERSAL') = (reverses_transaction_id IS NOT NULL));
