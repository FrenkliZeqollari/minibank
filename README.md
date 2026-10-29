# ledger-core

A double-entry bank ledger in Java 21 and Spring Boot 3. A learning/portfolio project, built the
way a bank's backend team would: boring, correct, well-tested.

The full spec (domain rules, data model, API, milestones) lives in [`CLAUDE.md`](CLAUDE.md).
The banking concepts behind each milestone are explained in [`docs/concepts.md`](docs/concepts.md).

## Status

| Milestone | Status |
|---|---|
| 0. Setup: Spring Boot skeleton, Postgres, Flyway, Testcontainers, CI | done |
| 1. Accounts + double-entry core | next |
| 2. Balances & statements | |
| 3. Overdraft protection under concurrency | |
| 4. Idempotency | |
| 5. Reversals | |

## Concepts it demonstrates (so far)

- Schema changes as versioned, reviewed SQL migrations (Flyway), never Hibernate `ddl-auto`
- Integration tests against a real PostgreSQL 16 (Testcontainers), not an in-memory imitation
- CI that runs every test on every push and pull request
- Configuration through environment variables, no secrets in git

## Tech stack

Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, Actuator), PostgreSQL 16, Flyway,
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
src/main/java/dev/frenkli/ledger      application code (api → service → repository)
src/main/resources/application.yml    configuration
src/main/resources/db/migration       Flyway SQL migrations (V1__..., V2__...)
src/test/java/dev/frenkli/ledger      tests; integration tests extend AbstractIntegrationTest
docs/concepts.md                      the banking "why" behind each milestone
```

## API examples

None yet: the ledger endpoints arrive in Milestone 1. See the API table in `CLAUDE.md`.
