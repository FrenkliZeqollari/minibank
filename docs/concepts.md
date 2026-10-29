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

## Milestone 3: Overdraft protection under concurrency

### The race condition ("check-then-act")

**Problem.** A transfer does two things: it *checks* the balance, then it *books* the money.
When two requests for the same account arrive at the same moment, both can do their check
before either has booked:

| time | request 1 | request 2 |
|---|---|---|
| 1 | reads balance: 100 | |
| 2 | | reads balance: 100 |
| 3 | 80 ≤ 100, books −80 | |
| 4 | | 80 ≤ 100, books −80 |
| 5 | **balance: −60** | |

Each check was correct when it ran, but the combination overdraws the account. This is not
theory: before this milestone, `ConcurrentTransferTest` sent 50 transfers of €10.00 at once
from an account holding €100.00, and **18 succeeded**, leaving the account at −€80.00.

### Row locks

**How the code solves it.** `MoneyMovementService.transfer` locks the account rows *before*
reading the balance (`AccountRepository.findByIdForUpdate`, a `SELECT ... FOR UPDATE`). A
second transaction that wants the same row waits at that line until the first one commits. Then
it reads the balance *after* the first transfer, so its check is based on the real number. In
the table above, request 2 would wait at time 2 and read 20 at time 5, and its transfer would be
refused.

On Postgres, Hibernate sends `FOR NO KEY UPDATE`, a slightly weaker variant of `FOR UPDATE`. It
still blocks every other transaction that wants to lock or change the row, but it does not block
inserting entries that merely *reference* the account. That is exactly what is needed here.

Locks are released only at commit or rollback, so a transaction should hold them as briefly as
possible: lock, check, book, commit.

### Lock ordering prevents deadlocks

**Problem.** Alice sends Bob money while Bob sends Alice money. If each transfer locked its
"from" account first, transfer 1 would hold Alice and wait for Bob, while transfer 2 holds Bob
and waits for Alice. Neither can continue: a deadlock. Postgres notices and kills one of them
with an error, which the customer would see as a failed payment.

**How the code solves it.** `MoneyMovementService.lockInIdOrder` always locks in ascending
account-id order, no matter which account is "from" and which is "to". Whoever gets the first
lock is guaranteed to also get the second. The balance updates in `LedgerService.record` use
the same order. `ConcurrentTransferTest.transfersInBothDirectionsAtOnceDoNotDeadlock` sends 50
transfers in both directions at once. With locking in request order instead, it fails with
Postgres reporting `deadlock detected`.

Deposits lock too (the customer account and `EXTERNAL_FUNDING`, in the same order), so every
money movement follows one locking rule.

### A database safety net for overdrafts

`V3__no_customer_overdraft.sql` adds `CHECK (type = 'SYSTEM' OR balance >= 0)` to `accounts`.
With the lock in place it should never fire. But if a future bug skipped the lock or the check,
the database would refuse to commit a negative customer balance instead of quietly allowing it.
The same layering as in Milestone 1: the service enforces the rule, the database double-checks.

### How the test makes requests really overlap

`ConcurrentTransferTest` starts 50 threads (`ExecutorService`). Each one waits at a
`CountDownLatch` until all are ready, then they are released at once, so the requests hit the
server together instead of one after another. While they run, a watcher thread keeps reading the
account's committed balance and records the lowest value it ever sees, which must never be below
0. Afterwards the test checks that exactly 10 transfers succeeded and 40 got `422`, that the
money arrived, and that the whole ledger still sums to 0.

**Without all this.** Customers could spend the same money twice by sending requests in parallel
(a classic attack on payment systems), and payments would randomly fail with deadlocks under
load.

## Milestone 4: Idempotency

### Why a payment API must be safe to retry

**Problem.** A client sends "transfer €25 to Bob", and the connection drops before the response
arrives. Did the transfer happen? The client cannot know. If it does not retry, Bob may never
get paid. If it retries blindly, Bob may get paid twice. Networks fail like this all the time,
and mobile apps and payment providers retry automatically.

**How the code solves it.** Every `POST /deposits` and `POST /transfers` must carry an
`Idempotency-Key` header: a unique id the *client* picks for this one operation (usually a
UUID) and sends again, unchanged, on every retry. The server stores the key with the
transaction (`transactions.idempotency_key`, `UNIQUE`) and handles a repeated key like this
(`IdempotencyService.findPrevious`):

| situation | response |
|---|---|
| key never seen | do the work normally, store the key with the transaction |
| same key, same request | return the original response (`201`, same body), book nothing |
| same key, different request | `422 idempotency-key-reused`: the client has a bug |
| no key | `400` |

A replayed response carries the header `Idempotent-Replayed: true`, so a client can tell. The
body is identical to the first one, so the client cannot tell otherwise and does not need to.

"Idempotent" is a maths word: doing it once or many times has the same effect.

### Recognising "the same request": the request hash

**Problem.** If a client reuses a key for a *different* transfer (another amount, another
recipient), silently returning the old result would hide a real payment the client wanted.

**How the code solves it.** `RequestFingerprint` computes a SHA-256 hash of the request's
fields (operation, accounts, amount, currency, description), stored in
`transactions.request_hash`. A repeated key is a replay only if the hash matches. The hash is
built from the parsed fields, not the raw JSON, so the same request with fields in another
order or with extra spaces is still recognised. Each field is length-prefixed, so values that
contain the separator cannot make two different requests look alike
(`RequestFingerprintTest`).

### A replay must not re-check the business rules

The idempotency check runs *before* any business check. Example: Alice transfers her whole
balance, the response is lost, and the client retries. A fresh check would now say
"insufficient funds", but this is not a new transfer: it is the retry of one that succeeded. The
client must get the original success back
(`IdempotencyApiTest.replayReturnsTheOriginalResultEvenIfTheBalanceHasChangedSince`).

Requests that were *rejected* (e.g. `422 insufficient-funds`) are not stored: their database
transaction rolled back. Retrying them simply tries again, which is what a client expects once
the problem is fixed (`rejectedRequestIsNotStoredSoTheSameKeyCanSucceedLater`).

### Parallel duplicates: locking the key itself

**Problem.** An impatient client sends the same request twice at the same moment. Both arrive,
both look up the key, neither finds it (neither has committed yet), and both try to book.
The `UNIQUE` constraint stops the second booking, but that request then fails with a `500`
instead of returning the result.

**How the code solves it.** Before looking the key up, `IdempotencyKeyLocks.lock` takes a
Postgres *advisory lock* on a hash of the key: `pg_advisory_xact_lock(hashtextextended(key, 0))`.
An advisory lock is a lock on a number the application chooses, not on a table row (there is no
row for the key yet). It is released automatically at commit or rollback. The second request
waits there, then finds the committed transaction and returns it as a replay.
`IdempotencyApiTest` fires 20 identical requests at once and checks exactly one booking, 20
identical `201` responses, and only one of them not marked as a replay. Without the advisory
lock this test fails with `duplicate key` errors.

The key lock is always taken before the account locks of Milestone 3, so the locking order stays
consistent and cannot deadlock.

### Old rows and `NOT VALID`

`V4__require_idempotency_key.sql` makes key and hash mandatory for new transactions. Rows from
before this milestone have neither, and append-only rows cannot be filled in afterwards. The
constraint is therefore added `NOT VALID`: Postgres enforces it for every new row but does not
check the existing ones.

**Without all this.** Every network glitch could turn into a double payment, which is exactly
the kind of error that makes customers lose trust in a bank.

## Milestone 5: Reversals

### Correcting mistakes without erasing history

**Problem.** Money was sent to the wrong account, or a deposit was booked twice by an operator.
It must be undone. But the ledger is append-only (Milestone 1): deleting the transaction would
destroy the evidence that it ever happened, and auditors, customers and courts need to see the
full story: "€25 went to Bob on Monday and was taken back on Tuesday".

**How the code solves it.** `POST /transactions/{id}/reversal` (`ReversalService.reverse`)
books a new `REVERSAL` transaction whose entries are the *mirror image* of the original: the
same accounts, every amount with the opposite sign.

| transaction | Alice | Bob |
|---|---|---|
| original transfer | −2500 | +2500 |
| reversal | +2500 | −2500 |
| **sum** | **0** | **0** |

The reversal is itself a normal, balanced double-entry transaction, so every rule from the
earlier milestones applies to it unchanged: zero-sum check, cached balances, row locks,
idempotency. Its `reverses_transaction_id` points back to the original, and both appear on the
statement. This is how accountants have corrected ledgers for centuries ("Storno" in German
banking): you never use an eraser, you book a correction.

### Reverse once

**Problem.** Reversing the same transfer twice would give Alice her money back twice.

**How the code solves it.** Three layers:

1. **Service:** after locking the accounts, `ReversalService` checks whether a reversal already
   points to this transaction and answers `409 Conflict` (`already-reversed`). 409 rather than
   422: the request is fine in itself, but it conflicts with the current state of the resource.
2. **Locking order:** two simultaneous reversals of one transaction involve the same accounts,
   so the second waits at the lock until the first has committed, and only *then* checks. If the
   check came before the lock, both would see "not reversed yet". The second would then fail on a
   different rule (Bob has no money left) and report a misleading error.
   `ReversalApiTest.parallelReversalsWithDifferentKeysReverseOnlyOnce` sends 10 at once and
   expects exactly one `201` and nine `409`.
3. **Database:** `V5__reversal_rules.sql` adds `UNIQUE (reverses_transaction_id)`, so even a
   bug cannot commit a second reversal. A second constraint ties the `REVERSAL` type and the link
   together: a reversal must say what it reverses, and nothing else may claim to reverse
   something.

A reversal itself cannot be reversed (`422 reversal-not-reversible`). "Undo the undo" is just
booking the original operation again, as a new, clearly labelled transaction.

### A reversal can be refused

**Problem.** Alice sends Bob €25, Bob spends €20 of it, then Alice's transfer is reversed.
Taking €25 back from Bob would leave him at −€5, an overdraft he never agreed to.

**How the code solves it.** Domain rule 5 (no overdrafts for customers) applies to reversals
like to anything else: the reversal is refused with `422 insufficient-funds`, and nothing is
booked. In a real bank this case goes to a human, who settles it outside the ledger (asking Bob,
writing it off, and so on). System accounts such as `EXTERNAL_FUNDING` may still go negative, so
reversing a deposit is only refused if the customer no longer has the money.

### Idempotent like every money movement

A reversal needs an `Idempotency-Key` too. A retry with the same key returns the original
`201`, not a `409`: the client is not asking for a *second* reversal, it is asking again for the
*same* one.

**Without all this.** Either history gets rewritten (no audit trail), or corrections can be
applied twice or push customers into an overdraft.
