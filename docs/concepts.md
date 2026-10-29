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
