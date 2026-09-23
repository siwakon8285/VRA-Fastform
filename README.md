# VRA-Fastform

VRA (Thai: วีล่า) is beginning architecture validation for a future modular transactional core.
This repository is an experiment, not a production application. See [brand direction](docs/BRAND.md).

## POC-00 — JVM Language + Core Technology Validation

Candidate A is Java 21 / Spring Boot 3.5.16 / Gradle 8.14.3 / PostgreSQL 17.11.
There is no Kotlin candidate or frontend yet. Read the [frozen shared specification](validation/poc-00/SHARED_SPEC.md)
and [actual Java evidence](validation/poc-00/evidence/java.md).

Follow the [complete local commands and pgAdmin connection guide](validation/poc-00/README.md):

1. Create the ignored `.local/secrets/db_password` file securely (commands in the guide).
2. Start PostgreSQL only: `docker compose up -d postgres`.
3. Check health: `docker compose ps`.
4. Export `VRA_DB_URL`, `VRA_DB_USERNAME`, and `VRA_DB_PASSWORD` locally; never commit or print the password.
5. From `validation/poc-00`, migrate explicitly: `./gradlew :java-candidate:migrateLocal`.
6. Run the backend manually: `./gradlew :java-candidate:bootRun`.
7. Load `shared/dev/seed.sql` through pgAdmin or the documented container `psql` command.
8. Run `./gradlew clean build` (unit, HTTP, architecture and PostgreSQL integration tests).
9. With the backend running, optionally run `bru run --env local` from `validation/poc-00/bruno`.
10. From the repository root, stop infrastructure with `docker compose down`.

PostgreSQL binds only `127.0.0.1`, host port `${VRA_PG_PORT:-55432}`, database/user `vra_poc00`.
Use another port on conflict; do not stop an existing database. Compose contains no application services.
Install/provide a JDK 21 and start Docker yourself if missing; no system installers are part of this experiment.

**DESTRUCTIVE — `docker compose down -v` deletes the POC Docker database volume.**
Use only when intentionally discarding this local POC, never against another environment.

Production requires separate migration/runtime identities. The shared local login and runtime password
environment variable are POC conveniences, not the final privilege or secrets design.
