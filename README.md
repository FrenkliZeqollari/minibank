# ledger-core

A double-entry bank ledger in Java 21 and Spring Boot 3. A learning/portfolio project, built the
way a bank's backend team would: boring, correct, well-tested.

The full spec (domain rules, data model, API, milestones) lives in [`CLAUDE.md`](CLAUDE.md).
The banking concepts behind each milestone are explained in [`docs/concepts.md`](docs/concepts.md).

## Status

| Milestone | Status |
|---|---|
| 0. Setup: Spring Boot skeleton, Postgres, Flyway, Testcontainers, CI | done |
| 1. Accounts + double-entry core | done |
| 2. Balances & statements | done |
| 3. Overdraft protection under concurrency | done |
| 4. Idempotency | done |
| 5. Reversals | done |
| 6. OpenAPI docs (springdoc) | done |

## Concepts it demonstrates (so far)

- Money as whole cents in a `long`, never floating point
- Double-entry bookkeeping: every transaction's entries sum to 0, checked in the service layer
  and again by a deferred constraint trigger in Postgres
- Money enters through a system account (`EXTERNAL_FUNDING`), so the whole ledger sums to 0
- One database transaction per money movement: all entries are written, or none
- Append-only history: the database rejects updates and deletes of entries and transactions
- A cached balance per account that always equals the sum of its entries: updated in the same
  database transaction, with an atomic `balance = balance + x`, in a fixed lock order
- Reconciliation: `/ledger/health` checks that all entries sum to 0 and every cache matches
- No overdrafts under concurrency: account rows are locked (`SELECT ... FOR UPDATE`) before the
  balance check, always in account-id order so parallel transfers cannot deadlock; a database
  `CHECK` refuses negative customer balances as a safety net
- Idempotency: money-moving POSTs need an `Idempotency-Key`; a retry returns the original
  result without booking again, even when the duplicates arrive in parallel
- Corrections without erasing history: a reversal books the mirror image of a transaction,
  at most once (`409` on a second attempt), never overdrawing the customer
- Account statements with stable paging (newest first, unique tie-breaker)
- An API contract generated from the code (OpenAPI 3, Swagger UI), with tests that pin the
  parts clients rely on
- RFC 7807 Problem Details errors: `404` unknown account, `422` broken business rule
- Schema changes as versioned, reviewed SQL migrations (Flyway), never Hibernate `ddl-auto`
- Integration tests against a real PostgreSQL 16 (Testcontainers), not an in-memory imitation
- CI that runs every test on every push and pull request
- Configuration through environment variables, no secrets in git

## Tech stack

Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Actuator), springdoc-openapi, PostgreSQL 16, Flyway,
JUnit 5, AssertJ, Testcontainers, Maven (wrapper included), GitHub Actions.

## How to run

Prerequisites: JDK 21 and Docker. In GitHub Codespaces both are already set up by
`.devcontainer/devcontainer.json`.

Start the local Postgres:

```bash
docker compose up -d
```

Run the app (on port 8080):

```bash
./mvnw spring-boot:run
```

Check that it is up:

```bash
curl localhost:8080/actuator/health
```

Expected response: `{"status":"UP"}`.

Run the build with all tests (needs Docker; Testcontainers starts its own Postgres, the
docker-compose one does not need to be running):

```bash
./mvnw verify
```

Stop the local Postgres and delete its data:

```bash
docker compose down -v
```

## Configuration

| Variable | Default (local only) |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ledger` |
| `DB_USERNAME` | `ledger` |
| `DB_PASSWORD` | `ledger` |
| `PORT` | `8080` |

The defaults match `docker-compose.yml` and are not secrets. Set real values through the
environment anywhere else.

## Project layout

```
src/main/java/dev/frenkli/ledger      application code: api → service → repository, entities in domain
src/main/resources/application.yml    configuration
src/main/resources/db/migration       Flyway SQL migrations (V1__..., V2__...)
src/test/java/dev/frenkli/ledger      tests; integration tests extend AbstractIntegrationTest
docs/concepts.md                      the banking "why" behind each milestone
```

## API documentation

With the app running:

- Swagger UI (try every endpoint in the browser): http://localhost:8080/swagger-ui.html
- OpenAPI 3 document (JSON): http://localhost:8080/v3/api-docs

## API examples

Amounts are integers in cents: `10000` is €100.00. Only `EUR` is supported for now.

`POST /deposits` and `POST /transfers` require an `Idempotency-Key` header: a unique id you pick
for the operation (e.g. `uuidgen`) and send again unchanged if you retry it.

Open an account:

```bash
curl -s -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName": "Alice", "currency": "EUR"}'
```

```json
{"id":"3f0c…","ownerName":"Alice","type":"CUSTOMER","currency":"EUR","balance":0,"status":"ACTIVE","createdAt":"…"}
```

Deposit €100.00 (booked as `EXTERNAL_FUNDING -10000 / Alice +10000`):

```bash
curl -s -X POST localhost:8080/deposits -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6c4f3c2e-1b7d-4a55-9a51-0d7e1f2b8a10' \
  -d '{"accountId": "<alice-id>", "amount": 10000, "currency": "EUR", "description": "Salary"}'
```

Transfer €25.00 to Bob:

```bash
curl -s -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 9b2d7e41-5f0a-4c3e-8d6b-2a1f4e7c9d30' \
  -d '{"fromAccountId": "<alice-id>", "toAccountId": "<bob-id>", "amount": 2500, "currency": "EUR", "description": "Rent"}'
```

```json
{"id":"…","type":"TRANSFER","description":"Rent","createdAt":"…","entries":[{"accountId":"<alice-id>","amount":-2500},{"accountId":"<bob-id>","amount":2500}]}
```

Sending exactly the same transfer again with the same key books nothing new: you get the same
`201` and body back, with the header `Idempotent-Replayed: true`. The same key with a different
body is refused:

```json
{"type":"urn:problem:ledger:idempotency-key-reused","title":"Idempotency-Key reused for a different request","status":422,"detail":"Idempotency-Key '9b2d7e41-5f0a-4c3e-8d6b-2a1f4e7c9d30' was already used for a different request","instance":"/transfers"}
```

Get an account and its balance:

```bash
curl -s localhost:8080/accounts/<alice-id>
```

Get the statement, newest first (`page` starts at 0, `size` is 1–100, default 20):

```bash
curl -s 'localhost:8080/accounts/<alice-id>/entries?page=0&size=20'
```

```json
{"accountId":"<alice-id>","entries":[{"entryId":"…","transactionId":"…","transactionType":"TRANSFER","description":"Rent","amount":-2500,"createdAt":"…"},{"entryId":"…","transactionId":"…","transactionType":"DEPOSIT","description":"Salary","amount":10000,"createdAt":"…"}],"page":0,"size":20,"totalEntries":2,"totalPages":1}
```

Check that the books balance:

```bash
curl -s localhost:8080/ledger/health
```

```json
{"healthy":true,"sumOfAllEntries":0,"balanceMismatches":[]}
```

Reverse a transaction (the transfer's `id` from above). This books its mirror image as a new
`REVERSAL` transaction; the original stays in the history:

```bash
curl -s -X POST localhost:8080/transactions/<transfer-id>/reversal \
  -H 'Idempotency-Key: 3e8a1f6c-7b2d-4c9e-a5f0-1d6b8c2e4a73'
```

```json
{"id":"…","type":"REVERSAL","description":"Reversal of transaction <transfer-id>","reversesTransactionId":"<transfer-id>","createdAt":"…","entries":[{"accountId":"<bob-id>","amount":-2500},{"accountId":"<alice-id>","amount":2500}]}
```

A transaction can be reversed only once; a second attempt (with a new key) gets `409`:

```json
{"type":"urn:problem:ledger:already-reversed","title":"Already reversed","status":409,"detail":"Transaction <transfer-id> has already been reversed","instance":"/transactions/<transfer-id>/reversal"}
```

A transfer larger than the balance is refused with `422` (here €900.00 from Alice's €100.00):

```json
{"type":"urn:problem:ledger:insufficient-funds","title":"Insufficient funds","status":422,"detail":"Account <alice-id> has 10000 cents, transfer needs 90000","instance":"/transfers"}
```
