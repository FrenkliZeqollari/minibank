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
