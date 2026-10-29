-- V3: the database's own safety net for "no overdrafts". The service layer checks the balance
-- while holding a row lock, so this should never fire; if a bug ever gets past that check, the
-- DB transaction fails instead of committing a negative customer balance.
-- System accounts may go negative: EXTERNAL_FUNDING is minus all money ever deposited.
ALTER TABLE accounts
    ADD CONSTRAINT accounts_customer_no_overdraft CHECK (type = 'SYSTEM' OR balance >= 0);
