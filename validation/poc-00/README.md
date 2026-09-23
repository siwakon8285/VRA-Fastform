# POC-00 — Candidate A: Java

This is the first controlled JVM/core technology experiment for VRA (วีล่า), not the
production application. Read [SHARED_SPEC.md](SHARED_SPEC.md) before changing behavior.
There is no Kotlin candidate or frontend. [Evidence](evidence/java.md) records actual execution limits.

Baseline: JDK 21 LTS, Spring Boot 3.5.16, Gradle Wrapper 8.14.3, PostgreSQL 17.11.
JDK 25 was unavailable, so the Java 21 fallback is frozen. A JDK must be supplied by the user;
the wrapper does not install Java. Do not use preview/incubator features.
First wrapper use downloads the pinned Gradle distribution; Maven dependencies also need network access.

## Local workflow

Run these from the **repository root**, using a shell without command tracing (`set -x`).
Use only this dedicated local POC database. Do not connect these commands to another project database.

1. Create a local secret once; do not overwrite it on subsequent runs:

   ```sh
   mkdir -p .local/secrets
   chmod 700 .local .local/secrets
   if [ ! -e .local/secrets/db_password ]; then
     (umask 077; openssl rand -base64 32 > .local/secrets/db_password)
   fi
   chmod 600 .local/secrets/db_password
   ```

   The whole `.local/` directory is ignored. No password is supplied in committed configuration.
   Changing this file does not change a password in an already initialized PostgreSQL volume.

2. Choose an unused port and start PostgreSQL only:

   ```sh
   export VRA_PG_PORT=55432
   docker compose up -d postgres
   docker compose ps
   ```

   If the port is occupied, choose another `VRA_PG_PORT` and use the same value below and in pgAdmin.
   Do not stop/kill an existing database. Compose binds only 127.0.0.1.
   Wait for healthy status before migration.

3. Export local runtime settings without printing the password:

   ```sh
   export VRA_DB_URL="jdbc:postgresql://127.0.0.1:${VRA_PG_PORT:-55432}/vra_poc00"
   export VRA_DB_USERNAME=vra_poc00
   export VRA_DB_PASSWORD="$(< .local/secrets/db_password)"
   ```

   This environment-variable use is a development POC convenience, not the production secret architecture.
   Missing/blank VRA_DB_PASSWORD fails clearly. URL and username are restricted to the dedicated local POC.
   Do not use environment dumps, shell tracing, or verbose credential logging.

4. Run the separate migration process:

   ```sh
   cd validation/poc-00
   ./gradlew :java-candidate:migrateLocal
   ```

5. Run the backend manually in this terminal:

   ```sh
   ./gradlew :java-candidate:bootRun
   ```

   It listens at http://127.0.0.1:8080. Another terminal can run tests/Bruno.
   Stop the backend with Ctrl-C. Compose never runs the backend.

6. Load `validation/poc-00/shared/dev/seed.sql` in pgAdmin Query Tool, connected as below.
   Alternative, from the **repository root**:

   ```sh
   docker compose exec -T postgres psql -U vra_poc00 -d vra_poc00 -v ON_ERROR_STOP=1 < validation/poc-00/shared/dev/seed.sql
   ```

   The seed is synthetic and idempotent: it never resets existing reservations. Success requests consume
   two units each; the low-stock SKU always rejects quantity 2. This endpoint has no retry idempotency.

7. Verify from `validation/poc-00`:

   ```sh
   ./gradlew clean build
   ./gradlew :java-candidate:test
   ./gradlew :java-candidate:integrationTest
   ```

   `test` contains domain, architecture, configuration, and isolated MVC tests.
   `integrationTest` contains migration, database and full HTTP tests using disposable PostgreSQL
   Testcontainers; requires a running Docker daemon, independently of Compose.
   `clean build` includes both suites through `check`. No Docker-unavailable auto-skip is configured.
   Reports: `java-candidate/build/reports/tests/{test,integrationTest}/index.html`.
   No production/local database is cleaned by these tests.

8. Once the backend and seed are ready, run Bruno:

   ```sh
   cd bruno
   bru run --env local
   ```

   Bruno CLI is optional; absence means **NOT EXECUTED — bru CLI not installed**, never passed.
   The collection contains no credentials and uses localhost only.

9. Stop infrastructure from the repository root:

   ```sh
   docker compose down
   ```

   This preserves the database volume.
   **DESTRUCTIVE — `docker compose down -v` deletes the POC Docker database volume.**
   Use that command only for intentionally discarding this POC database, never a non-POC environment.
   Clear the runtime password with `unset VRA_DB_PASSWORD` after use.

## pgAdmin

Use your existing pgAdmin desktop/client; it is not a Compose service.

| Connection field | Value |
| --- | --- |
| Host | localhost |
| Port | 55432, or your VRA_PG_PORT |
| Database / maintenance database | vra_poc00 |
| Username | vra_poc00 |
| Password | The local value you created in .local/secrets/db_password |

The PostgreSQL container is local infrastructure. Inspect inventory_balance, orders, order_items and
flyway_schema_history. Execute the synthetic seed through Query Tool. Never paste the password into
tracked files or evidence. Desktop pgAdmin resolves localhost to the host; a separately containerized
pgAdmin would need a separately approved topology.

## Architecture and dependencies

`interfaces` validates requests and maps errors; `application` owns the transaction and repository port;
`domain` contains immutable values/invariants; `infrastructure` contains explicit JDBC SQL and migration setup.
The conditional UPDATE returns post-update counters; CHECK constraints provide independent integrity defense.
No external service is called within the transaction. No order HTTP API, checkout, payments or auth.

Spring Web/JDBC/Validation supply the representative slice. Flyway core/PostgreSQL module perform explicit
migrations. PostgreSQL JDBC is the sole driver. Actuator supplies safe health and Micrometer HTTP observations;
only health is exposed. This supports future instrumentation but no OTel exporter or trace pipeline is validated.
Boot Test, PostgreSQL Testcontainers and ArchUnit implement the required tests. No speculative dependencies.

Flyway startup is disabled; SQL auto-init is disabled. Both migration paths load the same shared SQL resources.
`migrateLocal` disables clean. Test cleanup enables clean only against disposable Testcontainers.
The local PostgreSQL image login is an initialization/superuser convenience: **production requires
migration identity != runtime identity**, with least-privilege runtime grants. No production role design is claimed.

Money is non-negative exact commerce value, not finance ledger money. Optional domain fields use Optional,
adapted at JDBC boundaries. Order items and copied item lists preserve snapshots in ordinary domain use;
the local superuser could still modify rows directly. Database state checks do not enforce all transition history.
An insufficient-stock classification uses an existence query after a failed UPDATE; SKU deletion races are
outside this slice (there is no deletion API). Successful updates and inventory constraints remain atomic.

## Interpretation

This does not select final inventory concurrency or persistence technology, validate authentication,
prove production performance, or select Java. POC-02 owns contention evaluation. Future comparison must keep
the same controls and scenarios; do not add the deferred evolution changes to Candidate A now.
