# Banking concepts, milestone by milestone

This file explains the *why* behind each milestone in plain English: the banking problem, how
the code solves it, and what would go wrong without it.

## Milestone 0: Setup — why a bank's backend starts this boring

There is no money in the code yet. This milestone sets up the things that make the later money
code trustworthy.

### Schema migrations (Flyway) instead of `ddl-auto`

**Problem.** A bank's database is the record of who owns what. Its structure (tables, columns,
constraints) must change in a controlled, reviewable, repeatable way, exactly the same on every
laptop, in CI and in production.

**How the code solves it.** Every schema change is a numbered SQL file in
`src/main/resources/db/migration` (`V1__...sql`, `V2__...sql`, ...). On startup Flyway applies
the files that have not run yet and records each one in the `flyway_schema_history` table, with
a checksum. Hibernate is set to `ddl-auto: validate` (`application.yml`): it may only *check*
that the Java entities match the schema, never change the schema itself.

**Without it.** With `ddl-auto: update`, Hibernate guesses schema changes from Java classes. It
can silently skip changes it does not understand (it never drops or narrows a column), it cannot
create the database-level safety checks this ledger needs later (e.g. "entries of a transaction
sum to 0"), and nobody reviews the SQL that actually runs against the money tables.

### Testing against real Postgres (Testcontainers) instead of H2

**Problem.** The ledger's correctness will depend on database behaviour: row locks
(`SELECT ... FOR UPDATE`), deferred constraint triggers, transaction isolation. An in-memory
database like H2 imitates Postgres SQL but not those behaviours.

**How the code solves it.** `TestcontainersConfiguration` starts a throwaway `postgres:16`
Docker container for the test run, and `@ServiceConnection` points Spring at it. Integration
tests extend `AbstractIntegrationTest`. `ApplicationSmokeTest` proves the app boots, Flyway runs,
and the database really is Postgres 16.

**Without it.** Tests could pass on H2 while the real concurrency or constraint logic is broken
on Postgres, which is the worst kind of bug for a bank: it shows up only in production, with
real money.

### Continuous integration

**Problem.** "It works on my machine" is not evidence. A change to money-moving code must be
proven by tests before it is merged.

**How the code solves it.** `.github/workflows/ci.yml` runs `./mvnw verify` (compile + every
test, including the Testcontainers ones) on every push and pull request.

**Without it.** A broken invariant could be merged and only discovered when balances are wrong.

### Configuration through environment variables

**Problem.** Database passwords must never be in git, but a new developer should still be able
to run the app with zero setup.

**How the code solves it.** `application.yml` reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`PORT` from the environment, with defaults that only match the local `docker-compose.yml`
Postgres. Production sets real values via the environment.

## Milestone 1: Accounts and the double-entry core

### Money as whole cents in a `long`

**Problem.** `0.1 + 0.2` is `0.30000000000000004` in a `double`. Floating-point numbers cannot
store most decimal fractions exactly, so sums of euro amounts drift by fractions of a cent. In a
bank those fractions add up across millions of bookings, and the books stop balancing.

**How the code solves it.** Every amount is a whole number of cents: `long` in Java
(`Entry.amount`), `BIGINT` in Postgres, an integer in the JSON API. `€12.34` is `1234`.
`DoubleEntry` adds amounts with `Math.addExact`, which throws on overflow instead of silently
wrapping around (`DoubleEntryTest.rejectsASumThatWouldOverflowInsteadOfWrappingAround`).

**Without it.** Rounding errors, and balances that differ by a cent depending on the order in
which entries were added.

### Double-entry bookkeeping

**Problem.** If a transfer were stored as "Alice −€25" in one place and "Bob +€25" somewhere
else, a crash between the two writes would make €25 vanish, or appear out of nowhere. The bank
also needs to *prove* that no money was created or destroyed.

**How the code solves it.** Every money movement is one `transactions` row with at least two
`entries` rows. Each entry is a signed amount on one account (positive = in, negative = out),
and the entries of a transaction always sum to exactly 0. A transfer of €25 from Alice to Bob is:

| account | amount |
|---|---|
| Alice | −2500 |
| Bob | +2500 |

An account's balance is just the sum of its entries (`EntryRepository.sumAmountByAccountId`).

The rule is enforced twice:

1. **Service layer.** `LedgerService.record` is the only code that writes entries, and it calls
   `DoubleEntry.requireBalanced` first.
2. **Database.** `V1__ledger_schema.sql` has *constraint triggers* that re-check the sum when the
   DB transaction commits. They are `DEFERRABLE INITIALLY DEFERRED`: checking after each single
   row would always fail, because the first entry alone never sums to 0. If the check fails the
   whole DB transaction rolls back (`LedgerDatabaseRulesTest`, which writes raw SQL to get
   past the service layer on purpose).

**Without it.** A bug in one code path could create money, and nothing would notice.

### Money enters through a system account

**Problem.** A deposit brings money *into* the bank. If it were a single "+€100" entry, the
transaction would not sum to 0.

**How the code solves it.** The migration seeds a `SYSTEM` account, `EXTERNAL_FUNDING`. A
deposit of €100 is `EXTERNAL_FUNDING −10000 / customer +10000`
(`MoneyMovementService.deposit`). `EXTERNAL_FUNDING`'s negative balance is exactly the total
money customers have paid in, and the ledger as a whole always sums to 0 (the API tests check
`ledgerTotal()` is 0). Customers cannot send money to or from system accounts through the API.

**Without it.** The ledger could not distinguish "money that came from outside" from "money
created by a bug".

### One DB transaction per money movement

**Problem.** The business checks (does the account exist? enough money?) and the writes must
happen as one unit. Otherwise a failure halfway could leave a transaction with only some of its
entries.

**How the code solves it.** `deposit` and `transfer` are `@Transactional`. `LedgerService.record`
uses `Propagation.MANDATORY`, so it refuses to run unless the caller already opened the DB
transaction that its checks run in. Either everything commits, or nothing does.

**Without it.** Half-written transfers. (Note: two transfers from the *same* account at the same
time can still both pass the balance check. Row locking in Milestone 3 fixes that.)

### Append-only history

**Problem.** In accounting, you never erase a booking. Auditors (and customers disputing a
charge) need to see exactly what happened, including mistakes.

**How the code solves it.** The entities have no setters, and database triggers reject every
`UPDATE`, `DELETE` and `TRUNCATE` on `transactions` and `entries`. Mistakes will be corrected by
a new `REVERSAL` transaction (Milestone 5).

**Without it.** Anyone with database access, or a buggy query, could silently change history.

### Business rule errors are 422, not 400

A request that is well-formed but breaks a banking rule (not enough money, wrong currency,
transfer to yourself, amount ≤ 0) gets `422 Unprocessable Entity`. A malformed request (missing
field, not JSON) gets `400 Bad Request`. Both use the RFC 7807 Problem Details format
(`ApiExceptionHandler`), with a `type` such as `urn:problem:ledger:insufficient-funds` that a
client can act on. Amounts must be positive because the direction of money is set by `from` and
`to`: a negative transfer would quietly pull money out of the recipient.

## Milestone 2: Balances and statements

### A cached balance that can never drift

**Problem.** An account's true balance is the sum of all its entries. That is correct, but an
account with ten years of history has thousands of entries, and the bank needs its balance on
every transfer. Banks therefore keep a running balance next to the history. The danger: the
two can drift apart, and then the bank does not know how much money a customer has.

**How the code solves it.** `accounts.balance` is a *cache* of `SUM(entries.amount)`:

- Only `LedgerService.record` changes it, right after writing the entries, inside the same DB
  transaction (`Propagation.MANDATORY`). If anything fails, the entries and the balance changes
  roll back together, so they cannot disagree.
- The update is one SQL statement, `balance = balance + :amount`
  (`AccountRepository.addToBalance`). Postgres adds to the *latest* value. Reading the balance
  into Java, adding, and writing it back would let two simultaneous bookings overwrite each
  other ("lost update").
- Each `UPDATE` locks the account row until commit. Updates always happen in the same order (by
  account id), so two transactions never wait on each other in a circle (A waits for B while B
  waits for A, a deadlock).
- `V2__backfill_cached_balance.sql` filled in the cache once for entries written before this
  milestone.

`CachedBalanceTest` runs 200 random deposits and transfers, including ones that fail, and then
checks cache == sum for every account.

**Without it.** Either every balance read scans the full history (slow), or a cache that is
updated separately silently drifts after the first crash or bug.

### Reconciliation: `/ledger/health`

**Problem.** Rules in code can still be broken by a bug, a manual SQL fix, or a bad migration.
Banks reconcile: they regularly check, independently of the code that wrote the data, that the
books balance.

**How the code solves it.** `GET /ledger/health` (`LedgerHealthService`, `LedgerHealthQueries`)
checks two invariants straight in SQL:

1. The sum of *all* entries is 0. Money only moves; it is never created or destroyed.
2. No account's cached balance differs from the sum of its entries. Any that do are listed.

Both queries run in one `REPEATABLE_READ` transaction, so they see the same snapshot. Under the
default `READ_COMMITTED`, a transfer committing between the two queries could make a healthy
ledger look broken. The total is returned as a `BigDecimal` (Postgres sums `BIGINT` as
`NUMERIC`), so even a broken ledger cannot overflow the check itself.

`LedgerHealthApiTest` corrupts one cached balance on purpose and checks that it is reported.

**Without it.** A broken ledger would be noticed only when a customer complains.

### Statements and stable paging

**Problem.** A statement is the account's history, newest first, delivered a page at a time.
Paging needs a *stable* order: if two rows tie on the sort key, the database may return them in
a different order for each query, and a line can appear on two pages, or on none.

**How the code solves it.** `GET /accounts/{id}/entries?page=&size=` (`EntryRepository
.findStatement`) sorts by `created_at DESC, id DESC`. Both entries of one transaction share a
timestamp, so the unique entry id breaks the tie. Each line includes the transaction's type and
description, so a customer can see what it was. `size` is limited to 1–100 so one request cannot
load a whole history.

**Without it.** Missing or duplicated lines on a statement, which a customer would rightly
treat as an error in their account.

### A note on the system account

Every deposit now also updates `EXTERNAL_FUNDING`'s cached balance, so all deposits briefly lock
that same row and run one after another. That is fine at this scale. Real banks avoid such a
"hot" account, for example by splitting it into several sub-accounts or updating it in batches.
