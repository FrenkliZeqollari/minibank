# ledger-core

A double-entry bank ledger in Java & Spring Boot. Learning/portfolio project: the owner is a
software engineering student exploring whether he likes software + finance/banking. Build it the
way a backend team at a German bank would: boring, correct, well-tested.

## Teaching rule (important)
The owner wants to learn the banking concepts, not just get working code.
- For every milestone, add a section to `docs/concepts.md` explaining the *why* in plain English:
  the banking problem, how the code solves it, and what would go wrong without it.
- In each PR description, summarize the concepts introduced and point to the key lines.
- Prefer clear, explicit code over clever code. Comment the non-obvious financial rules.

## Stack
- Java 21 (LTS), Spring Boot 3.x, Maven
- Spring Web, Spring Data JPA, Bean Validation
- PostgreSQL 16, schema managed with **Flyway** migrations (no `ddl-auto`)
- JUnit 5, AssertJ, **Testcontainers** (real Postgres in integration tests, no H2)
- `docker-compose.yml` for local Postgres
- GitHub Actions: build + all tests on every push and PR
- Dev environment: GitHub Codespaces (`.devcontainer/devcontainer.json` already in repo)
- Package root: `dev.frenkli.ledger`

## Domain rules (never break these)
1. **Money is a `long` in minor units (cents).** Never `double`/`float`. Amounts in the API are
   integers in cents plus a currency code. v1 supports `EUR` only, but store currency anyway.
2. **Double-entry:** every transaction has ≥ 2 entries and its entries sum to exactly 0.
   Enforced in the service layer AND verified by a DB-level check (deferred constraint trigger
   or equivalent).
3. **Append-only:** entries and transactions are never updated or deleted. Corrections are new
   `REVERSAL` transactions that mirror the original entries with opposite signs.
4. **Balance = SUM(entries.amount) for the account.** A cached `balance` column on `accounts`
   is allowed for speed, but it must be updated in the same DB transaction as the entries and a
   test must prove cache == sum.
5. **No overdrafts** for customer accounts. System accounts (see below) may go negative.
6. **Money enters/leaves through system accounts.** A deposit is
   `EXTERNAL_FUNDING -X / customer +X`, so the whole ledger still sums to 0.
7. **Idempotency:** every money-moving POST requires an `Idempotency-Key` header.
   - Same key + same payload → return the original result, no new entries.
   - Same key + different payload → `422` error.
8. **Concurrency safety:** lock the involved account rows (`SELECT ... FOR UPDATE`) inside the
   DB transaction before checking balances. Always lock in a consistent order (by account id)
   to avoid deadlocks.

## Data model (Flyway V1)
- `accounts`: `id UUID PK`, `owner_name`, `type` (`CUSTOMER` | `SYSTEM`), `currency CHAR(3)`,
  `balance BIGINT` (cache), `status` (`ACTIVE` | `CLOSED`), `created_at`
- `transactions`: `id UUID PK`, `type` (`DEPOSIT` | `TRANSFER` | `REVERSAL`), `description`,
  `idempotency_key UNIQUE`, `request_hash`, `reverses_transaction_id UUID NULL FK`, `created_at`
- `entries`: `id UUID PK`, `transaction_id FK`, `account_id FK`, `amount BIGINT` (signed),
  `created_at`
- Seed a `SYSTEM` account `EXTERNAL_FUNDING` in a migration.

## REST API
Errors use RFC 7807 Problem Details (`application/problem+json`).

| Method | Path | Notes |
|---|---|---|
| POST | `/accounts` | create customer account |
| GET | `/accounts/{id}` | account + balance |
| GET | `/accounts/{id}/entries?page=&size=` | statement, newest first |
| POST | `/deposits` | `Idempotency-Key` required |
| POST | `/transfers` | `Idempotency-Key` required; `{fromAccountId, toAccountId, amount, currency, description}` |
| POST | `/transactions/{id}/reversal` | `Idempotency-Key` required; a transaction can be reversed once |
| GET | `/ledger/health` | returns total sum of all entries (must be 0) and cache-vs-sum mismatches |

Status codes: `404` unknown account, `422` insufficient funds / currency mismatch /
idempotency payload mismatch / same-account transfer / non-positive amount, `409` already
reversed.

## Milestones (one PR each, merged before the next starts)
0. **Setup:** Spring Boot skeleton, docker-compose Postgres, Flyway, Testcontainers base test,
   GitHub Actions CI, README with how-to-run. `/actuator/health` works.
1. **Accounts + double-entry core:** V1 schema, create/get account, deposits, transfers inside one
   DB transaction, zero-sum enforcement. Unit + integration tests.
2. **Balances & statements:** cached balance, entries endpoint with paging, `/ledger/health`.
   Test: cache always equals sum.
3. **Overdraft protection under concurrency:** row locking in consistent order. Test fires 50
   concurrent transfers from one account (ExecutorService + CountDownLatch) and asserts the
   balance never goes negative and the ledger still sums to 0.
4. **Idempotency:** header handling, request hashing, replay returns the original response.
   Tests for retry, parallel duplicate requests, and payload mismatch.
5. **Reversals:** mirror entries, reverse-once rule, link to original.
6. *Later / optional:* multi-currency, OpenAPI docs (springdoc), small frontend, portfolio
   tracker on top.

## Conventions
- Layers: `api` (controllers, DTOs) → `service` (business rules, `@Transactional`) →
  `repository`. Domain rules live in the service layer, not controllers.
- DTOs are Java `record`s. Validate input with Bean Validation.
- Every bug fix gets a test that reproduces it first.
- Conventional commits (`feat:`, `fix:`, `test:`, `docs:`). Branch per milestone, open a PR,
  owner reviews and merges.
- Never commit secrets; config via environment variables with safe local defaults.
- Keep the README current: what it is, the concepts it demonstrates, how to run, API examples.
