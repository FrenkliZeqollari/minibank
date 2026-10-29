-- V2: from now on accounts.balance is a cache of SUM(entries.amount), kept up to date by the
-- application in the same DB transaction as the entries. Until V1 it was always 0, so fill it in
-- once for any entries that already exist.
UPDATE accounts a
   SET balance = coalesce((SELECT sum(e.amount) FROM entries e WHERE e.account_id = a.id), 0);
