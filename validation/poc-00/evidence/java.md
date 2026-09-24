# Java Candidate Evidence

Initial implementation session: 2026-09-23. Candidate A only. This records source/configuration checks
and environment blockers; **Java compilation, tests, migrations and application startup were not executed**.
No successful runtime validation or language-selection conclusion can be drawn.

## Environment

- OS: macOS 27.0, build 26A428 (`sw_vers`).
- Architecture: Darwin arm64 (`uname -sm`).
- JDK: target **21 LTS** fallback; actual installed JDK **none found**. `java -version` reports
  “Unable to locate a Java Runtime.” Java 25 is not properly available; no JDK was installed by this task.
  Vendor/patch cannot be recorded until execution; freeze the same actual vendor/patch for both candidates then.
- Java compiler: `javac -version` fails with the same missing-runtime message.
- Gradle: system command absent; official **8.14.3** Wrapper added, but not runnable without Java.
- Spring Boot: **3.5.16** pinned; Maven Central POM retrieval succeeded.
- PostgreSQL image/version: **postgres:17.11**, major 17, configured identically in Compose and Testcontainers;
  no image started and no `SELECT version()` executed.
- Docker CLI: **29.8.0**, build 88096ef. Daemon unavailable: expected Unix socket does not exist.
- Docker Compose: **v5.5.1**.
- Bruno CLI: **4.1.0**, installed. Collection syntax parsed using its installed language parser;
  actual API requests NOT EXECUTED because the backend cannot start.
- Repository initially contained only `README.md` with `# VRA-Fastform`; initial Git status was clean.
  No repository/ancestor AGENTS.md instructions were found in the inspected project paths.

Baseline sources: [Spring Boot requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html),
[Gradle 8.14.3 release](https://docs.gradle.org/8.14.3/release-notes.html),
[PostgreSQL support policy](https://www.postgresql.org/support/versioning/).
These support the selected stable versions; they do not prove this candidate builds.
Boot BOM inspection shows Flyway 11.7.2, PostgreSQL JDBC 42.7.11, Spring Framework 6.2.19 and
Testcontainers 1.21.4. ArchUnit is pinned to 1.4.1. Dependency resolution itself has not run.

## Implementation

- Root README, ignore/editor settings, PostgreSQL-only Compose, and docs/BRAND.md.
- Frozen language-neutral SHARED_SPEC.md, local workflow README, single java-candidate Gradle subproject,
  official wrapper scripts/JAR/properties, and disabled automatic JDK download.
- Shared V1 and additive V2 SQL migrations; synthetic idempotent seed; minimal Bruno collection.
- Domain records: Money, OrderId, SkuId, IdempotencyKey, InventoryBalance, InventoryReservation, OrderItem,
  Order. Enum state transitions and sealed typed domain rejections.
- Application repository port and explicit Spring `@Transactional` reservation service; JDBC conditional
  UPDATE/RETURNING adapter; interface DTO validation, safe exception/fallback translation, UUID request IDs.
- Explicit Flyway `migrateLocal` process; runtime schema mutation disabled; local database URL guard.
- Test suites described below; no Kotlin candidate, production backend/frontend, or deferred infrastructure.

Records make value fields final; List.copyOf protects historical item collections; BigDecimal avoids
floating-point prices; Optional exposes absence intentionally; exhaustive switches keep state/error
mapping visible. Sealed typed exceptions retain Spring rollback behavior without generic rejection errors.
Actuator is the only optional-category dependency: safe health plus Micrometer HTTP observation plumbing.
No telemetry exporter/collector is configured. Testcontainers and ArchUnit exist for required validation.

## Commands Executed

Commands below ran from the repository root unless a working directory is stated. FAIL on a launcher
does not mean test assertions failed: in this session no test JVM started. Repeated equivalent probes are grouped.

| Exact command | Result | Factual result |
| --- | --- | --- |
| `git status --short` | PASS | Initially clean; final source changes listed below. |
| `cat README.md` | PASS | One-line initial repository README. |
| `uname -sm` | PASS | Darwin arm64. |
| `sw_vers` | PASS | macOS 27.0 / 26A428. |
| `java -version` | FAIL | No Java runtime. |
| `javac -version` | FAIL | No Java runtime/compiler. |
| `gradle --version` | FAIL | Command not found. |
| `docker --version` | PASS | CLI 29.8.0. |
| `docker compose version` | PASS | Compose v5.5.1. |
| `docker info --format '{{.ServerVersion}}'` | FAIL | Docker socket absent; daemon unavailable. |
| `bru --version` | PASS | 4.1.0. |
| `bru run --help` | PASS | CLI invocation/help works; no API requests performed. |
| `docker compose config --quiet` | PASS | Compose model validates without reading/printing password contents. |
| `docker compose config --services` | PASS | Exactly `postgres`. |
| `docker compose config --format json` | PASS | Image 17.11; 127.0.0.1:55432 -> 5432; named volume, healthcheck, file secret. |
| `VRA_PG_PORT=55433 docker compose config --format json` | PASS | Override produces 127.0.0.1:55433 -> 5432. |
| `sh -n validation/poc-00/gradlew` | PASS | Official wrapper shell syntax. |
| `shasum -a 256 validation/poc-00/gradle/wrapper/gradle-wrapper.jar` | PASS | Matches published hash `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172`. |
| `git check-ignore .local/secrets/db_password` | PASS | Local secret path is ignored; no actual secret was created. |
| `git diff --check` | PASS | No whitespace errors in tracked diff (new untracked files are outside this command's scope). |
| `./validation/poc-00/gradlew -p validation/poc-00 clean build` | FAIL | Launcher stops: missing Java runtime. |
| `./gradlew clean build` (validation/poc-00) | FAIL | Launcher stops before compilation. |
| `./gradlew :java-candidate:test --tests '*DomainTest' --tests '*ArchitectureTest' --tests '*HttpContractTest' --tests '*LocalDatabaseSettingsTest'` (validation/poc-00) | FAIL | Launcher stops before all requested test categories. |
| `./gradlew :java-candidate:integrationTest` (validation/poc-00) | FAIL | Launcher stops before PostgreSQL/migration/full HTTP tests; Docker also unavailable. |

Official build assets were retrieved, not executed as installers. Initial sandbox network probe
`curl -I -L --max-time 20 https://raw.githubusercontent.com/gradle/gradle/v8.14.3/gradlew`
failed DNS resolution. Authorized network retries succeeded:

```sh
curl -fL --max-time 30 https://raw.githubusercontent.com/gradle/gradle/v8.14.3/gradlew
curl -fL --max-time 30 https://raw.githubusercontent.com/gradle/gradle/v8.14.3/gradlew -o validation/poc-00/gradlew
curl -fL --max-time 30 https://raw.githubusercontent.com/gradle/gradle/v8.14.3/gradlew.bat -o validation/poc-00/gradlew.bat
curl -fL --max-time 30 https://raw.githubusercontent.com/gradle/gradle/v8.14.3/gradle/wrapper/gradle-wrapper.jar -o validation/poc-00/gradle/wrapper/gradle-wrapper.jar
curl -fL --max-time 30 https://services.gradle.org/distributions/gradle-8.14.3-bin.zip.sha256
curl -fL --max-time 30 https://services.gradle.org/distributions/gradle-8.14.3-wrapper.jar.sha256
curl -fL --max-time 20 https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom -o /private/tmp/vra-poc00-boot-3.5.16.pom
curl -fIL --max-time 20 https://repo.maven.apache.org/maven2/org/testcontainers/postgresql/1.21.4/postgresql-1.21.4.pom
curl -fIL --max-time 20 https://repo.maven.apache.org/maven2/com/tngtech/archunit/archunit-junit5/1.4.1/archunit-junit5-1.4.1.pom
curl -fL --max-time 20 https://raw.githubusercontent.com/testcontainers/testcontainers-java/1.21.4/modules/postgresql/src/main/java/org/testcontainers/containers/PostgreSQLContainer.java
```

All retries above: PASS (HTTP retrieval only). Distribution hash stored in wrapper properties:
`bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531`.
The Gradle distribution was not downloaded/executed. Pinned Testcontainers source inspection identified
its loggerLevel query parameter; the application integration test supplies a query-free URL to the local guard.

Bruno parser verification, exact command (PASS: four requests, environment and manifest parsed):

```sh
node -e 'const fs=require("fs"); const lang=require("/Users/siwakornbundi/.npm-global/lib/node_modules/@usebruno/cli/node_modules/@usebruno/lang"); const root="validation/poc-00/bruno/"; for(const name of fs.readdirSync(root).filter(n=>n.endsWith(".bru"))) { const parsed=lang.bruToJsonV2(fs.readFileSync(root+name,"utf8")); if(!parsed.http) throw Error(name); console.log("PASS syntax: "+name); } lang.bruToEnvJsonV2(fs.readFileSync(root+"environments/local.bru","utf8")); JSON.parse(fs.readFileSync(root+"bruno.json","utf8")); console.log("PASS syntax: local environment and collection manifest");'
```

This is syntax verification, not Bruno API or assertion execution.

## Tests Added

- Domain/unit: exact valid/invalid Money, null requirements, same-currency arithmetic, currency mismatch,
  non-positive quantities, valid/invalid balances and availability, insufficient stock, state-transition graph,
  cancelled-confirm rejection, idempotency key bounds, snapshot/list immutability, optional timestamps/reasons.
- Configuration: missing password and remote/non-POC URL rejection.
- Database integration: latest migration and validation, inventory read/write, order/item mapping, exact high-scale
  Money/currency, enum and timestamp round trips, nullable reason, invalid inventory and item quantity CHECKs.
- Migration: populate V1 inventory/order/item, verify reason column absent, apply V2, validate version 2,
  verify preserved values, NULL reason and new cancellation reason write/read. Same classpath SQL as local migration.
- HTTP: isolated MVC success, non-positive/malformed inputs, unknown SKU, insufficient stock, safe internal error,
  generated/correlated request ID; full HTTP-to-PostgreSQL integration, configured JSON rejection and error fallback.
- Transaction: proxied application service joins an outer transaction; subsequent stock rejection must roll back
  the first reservation and version increment.
- Architecture: ArchUnit prohibits outward domain dependencies, inner-to-interface dependencies, application-to-
  infrastructure, and controller-to-infrastructure dependencies.
- Bruno: health, seeded success, invalid quantity, insufficient stock; syntax verified only.

No tests use a substitute DB. `check` includes integrationTest; missing Docker is not silently skipped.
Disposable test database ports are explicitly bound to 127.0.0.1. Tests require local Docker.

## Invariants Verified

Actual verification in this session proves configuration/syntax facts only:

- Compose has exactly one PostgreSQL service, loopback binding, working port substitution, named volume,
  healthcheck and file-secret reference.
- The secret path is ignored by Git.
- Wrapper JAR hash matches the official published hash; wrapper shell syntax is valid.
- Bruno request/environment syntax and JSON manifest parse successfully.

Business invariants are expressed in domain constructors/commands, conditional SQL, CHECK constraints,
and test assertions, but **none has runtime test evidence yet**. Compilation, Spring wiring, rollback,
database enforcement, HTTP behavior, migration preservation and architecture rules remain unverified.

## Not Executed

- Java compilation/clean build and all JUnit suites: attempted wrapper commands above failed before execution
  because no JDK is installed. There are no passing/failed/skipped test counts to report.
- PostgreSQL Testcontainers migration, persistence, transaction and full HTTP scenarios: no Java; additionally
  no running Docker daemon. The same blocker covers the populated V1 -> V2 experiment.
- `docker compose up -d postgres`, local DB health and `SELECT version()`: daemon unavailable; no local secret
  was generated because no local service could be started. No unrelated container/database was touched.
- `./gradlew :java-candidate:migrateLocal` and `./gradlew :java-candidate:bootRun`: no JDK or running POC DB.
- Seed execution through pgAdmin or `docker compose exec -T postgres psql ...`: no running POC database.
- `bru run --env local` in bruno/: CLI is installed, but backend unavailable. Do not confuse parser PASS with API PASS.
- OTel export/tracing, startup duration, build/test duration, process memory and performance: no running JVM;
  exporter/performance experiments are also outside scope.

Completion status: implementation artifacts are present, but experimental execution is blocked. Provide
JDK 21 and a running local Docker daemon, then execute the documented commands before evaluating the candidate.

## Known Limitations

- This does not select final inventory concurrency strategy; POC-02 owns contention evaluation.
- This does not select final persistence technology; JDBC is the shared experimental control.
- Local DB identity separation is not the final production privilege design. The local initialization login
  is powerful; production requires migration identity != runtime identity and least privilege.
- This does not validate production performance.
- This does not validate authentication; no auth implemented.
- No compiler/test runner has validated these sources. Dependency compatibility and Spring behavior remain risks.
- JDK vendor/patch and actual PostgreSQL runtime version are not measured yet.
- The endpoint is not idempotent and persists no reservation ledger/identity; model type is representative only.
- Immutable snapshots are protected through domain values; a privileged SQL writer can still edit rows.
- CHECKs constrain stored state, not the complete historical order transition graph.
- Failure classification uses a subsequent existence query; SKU deletion races are not modeled.
- No external observability exporter or production log-pipeline validation; safe internal logs omit exception
  payloads, trading detailed diagnostics for avoiding accidental secret/SQL leakage in this POC.
- Toolchain freeze was selected without installed Java; future comparison needs equivalent actual execution.

## Java Language Observations

Source-level observations only; none implies a language winner:

- Nullability: constructor guards protect required values; Optional marks two domain absences, while JDBC
  boundaries explicitly convert SQL NULL. Java still permits null references at call sites; tests are needed.
- Immutability: records provide final components but are shallow; Order explicitly copies its item list.
- Sealed/state modeling: enum transition switch and sealed failure switch enumerate currently permitted cases.
- Error modeling: typed rejection subclasses avoid generic expected exceptions and preserve unchecked rollback
  semantics. Transport mapping is centralized outside the domain.
- Spring friction: transaction service is non-final for proxying; HTTP DTOs are records; runtime integration is untested.
- SQL ergonomics: text blocks keep UPDATE/RETURNING visible, named JDBC parameters carry values; explicit test
  row mapping exposes UUID, decimal, enum, UTC and nullable conversions.
- Test readability: domain assertions describe behavior; PostgreSQL tests visibly apply shared migrations.
- Compiler assistance: source uses exhaustive switches/strong IDs; actual compiler diagnostics are unavailable.
- Reviewability: transaction boundary, SQL, business guards and HTTP translation occupy separate small files;
  no code generation or Lombok hides those behaviors. Human comparative review remains future work.

## Security

- No real password was created, printed or placed in source/evidence. `.local/` is ignored.
- Compose uses a file secret and POSTGRES_PASSWORD_FILE, not a password value in YAML.
- Runtime VRA_DB_PASSWORD has no committed default; missing/blank values fail explicitly.
- Local environment variables and shared DB login are POC-only conveniences, not production architecture.
- Runtime/migration settings restrict target to loopback and vra_poc00; no remote/shared DB was connected.
- Compose binds PostgreSQL to 127.0.0.1; tests also request loopback ports. Backend defaults to loopback.
- Request IDs are server-generated. Application request logs omit URLs/bodies/headers/credentials;
  safe exception responses omit SQL, paths and stack traces. No environment dumps were performed.
- Only health is exposed through Actuator; no observability stack or external network call in a business transaction.
- No Git-mutating operations, installers, database/container deletions or unrelated service operations occurred.

## Git Status

Read-only status summary: modified README.md; untracked .editorconfig, .gitignore, docker-compose.yml,
docs/ and validation/. No files staged, committed or pushed by this task. Git remains user-controlled.

## Runtime verification — 2026-09-23, prepared local environment

This section appends a second run. The initial blocked run above remains historically accurate for its time.
Candidate A was compiled, tested and smoke-checked on the frozen baseline. No Kotlin work or shared-spec
change occurred. The POC PostgreSQL Compose service remains running and healthy for local inspection;
the backend process started for this check was stopped with Ctrl-C.

### Environment and initial failures

- macOS 27.0, Darwin arm64; Homebrew OpenJDK **21.0.12.1** runtime/compiler (`java -version`,
  `javac -version`); Gradle Wrapper **8.14.3** (`GRADLE_USER_HOME=.gradle ./gradlew --version`).
  Use the same vendor/patch for the later candidate comparison.
- Spring Boot **3.5.16** unchanged; PostgreSQL server **17.11**
  (`docker compose exec -T postgres postgres --version`). Docker CLI/Engine **29.8.0**;
  Docker Compose **v5.5.1**; Bruno **4.1.0**.
- Initial `./gradlew --version` could not write the default home Gradle wrapper lock under the sandbox.
  The build used ignored project-local `GRADLE_USER_HOME=.gradle` instead. Initial sandboxed Docker API
  request was denied; approved Docker access returned Engine 29.8.0. The first compile attempt with the
  local cache failed to resolve `services.gradle.org` inside the network sandbox; the authorized retry
  downloaded the pinned distribution and dependencies. These were environment access failures, not source fixes.
- The first authorized compile attempt failed at `compileTestJava`: wildcard JUnit import exposed
  `org.junit.jupiter.api.Order`, ambiguous with domain `Order` in PostgresIntegrationTest.
  Replaced that wildcard with explicit BeforeEach/Tag/Test imports. Same compile command then passed.
  No production code, migration SQL, assertion, pinned version, or SHARED_SPEC content was changed.
- The POC secret file existed and was nonempty; its contents were never printed or written to evidence.
  VRA_PG_PORT was unset, so the configured default **55432** applied. The port had no listener before
  Compose startup. The dedicated Compose project had no running containers before startup.

### Commands and final results

All Gradle commands below ran from `validation/poc-00`. Local database commands targeted only the
dedicated `vra-poc00` Compose project. A local environment value for VRA_DB_PASSWORD was loaded from
`../../.local/secrets/db_password` for migration/backend processes without shell tracing or output.
No password value appears in these commands or results.

| Exact command | Result | Factual result |
| --- | --- | --- |
| `java -version` | PASS | OpenJDK 21.0.12.1 Homebrew. |
| `javac -version` | PASS | javac 21.0.12.1. |
| `GRADLE_USER_HOME=.gradle ./gradlew --version` | PASS | Gradle 8.14.3, launcher JVM 21.0.12.1. |
| `docker version` and `docker info --format '{{.ServerVersion}}'` | PASS with approved Docker access | Client/Engine 29.8.0. Initial sandbox access was denied. |
| `docker compose version` | PASS | v5.5.1. |
| `bru --version` | PASS | 4.1.0. |
| `GRADLE_USER_HOME=.gradle ./gradlew clean compileJava compileTestJava` | FAIL, then PASS | First authorized run found ambiguous `Order` test import. Explicit JUnit imports fixed it; rerun compiled main and test sources. |
| `GRADLE_USER_HOME=.gradle ./gradlew clean build` | PASS | All 35 tests ran: 27 regular + 8 real PostgreSQL; zero failures/errors/skips. Build finished in 29 seconds. |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:test --tests '*DomainTest' --tests '*ArchitectureTest'` | PASS | 15 domain cases and 1 architecture case executed; zero skips. |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:integrationTest` | PASS, UP-TO-DATE | Same 8 integration cases had executed in preceding clean build; Gradle correctly reused that result. Do not count this second invocation as another execution. |
| `docker compose config --quiet` and `docker compose config --services` | PASS | Valid config; sole service `postgres`. |
| `docker compose ps` before startup | PASS | Dedicated Compose project empty. |
| `docker compose up -d postgres` | PASS | Created only POC network, named volume, PostgreSQL container. |
| `docker compose ps` after startup | PASS | postgres:17.11 healthy, `127.0.0.1:55432->5432/tcp`; no backend/frontend service. |
| `docker compose exec -T postgres postgres --version` | PASS | PostgreSQL 17.11 (Debian image build). |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:migrateLocal` (with local runtime DB environment) | PASS | Explicit Flyway applied V1 then V2; schema at v2. |
| Same `migrateLocal` command, second run | PASS | Flyway validated both checksums, found schema at version 2, applied zero migrations. |
| `docker compose exec -T postgres psql -U vra_poc00 -d vra_poc00 -v ON_ERROR_STOP=1 -Atc "SELECT version || ':' || success || ':' || checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank"` | PASS | V1 and V2 successful; checksums `-704051064`, `-61360372` unchanged after backend startup and HTTP operations. |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:bootRun` (with local runtime DB environment) | PASS | Terminal-run backend connected to migrated DB, started Tomcat on loopback port 8080; no startup migration log/history change. Backend stopped by Ctrl-C after checks. |
| `curl --silent --show-error --include http://127.0.0.1:8080/actuator/health` | PASS with approved localhost access | 200, `UP`, generated `X-Request-Id`. First sandboxed localhost attempt failed to connect; approved retry passed. |
| `docker compose exec -T postgres psql -U vra_poc00 -d vra_poc00 -v ON_ERROR_STOP=1 < validation/poc-00/shared/dev/seed.sql` | PASS | Inserted two synthetic inventory rows. |
| `bru run --env local` from `validation/poc-00/bruno` | PASS | Four requests passed; 2/2 scripted tests and 9/9 assertions passed. |
| `curl --silent --show-error --include -X POST http://127.0.0.1:8080/poc/reservations -H 'Content-Type: application/json' --data '{"skuId":"00000000-0000-0000-0000-000000000099","quantity":1}'` | PASS | 404 `inventory.unknown_sku`; response body requestId matched header; no SQL or trace. |
| `git diff --check` | PASS | No tracked whitespace errors. |

The focused test XML from the clean build recorded DomainTest 15, ArchitectureTest 1,
HttpContractTest 9, LocalDatabaseSettingsTest 2, PostgresIntegrationTest 4, and
ReservationIntegrationTest 4. Every suite recorded **skipped=0, failures=0, errors=0**.
The later focused Gradle run replaced only the regular test report with its filtered result;
the complete clean-build counts above were read before that run.

### Migration and database evidence

V1 uses explicit CREATE TABLE statements and CHECK constraints. V2 only adds nullable
`cancellation_reason` plus a constraint accepting NULL legacy values; it drops no column or row.
The populated V1→V2 Testcontainer case ran and passed against `postgres:17.11`, preserving its
inventory, order, order-item price/quantity and timestamp, then reading NULL and a new reason.
Latest migration, order/inventory/money/state/time round trips and invalid-row CHECK tests passed.
The same shared migration resources are on the application/migration classpath. Runtime configuration has
`spring.flyway.enabled=false` and SQL init `mode=never`; only explicit `migrateLocal` changed local history.
The second migration validated stored checksums and found no pending change.

After Bruno, read-only PostgreSQL inspection of the authoritative rows returned:

| Synthetic SKU suffix | on_hand | reserved | available | invariant SQL predicate | version |
| --- | ---: | ---: | ---: | --- | ---: |
| `...0001` | 1,000,000 | 2 | 999,998 | true | 1 |
| `...0002` | 1 | 0 | 1 | true | 0 |

The `available` column above was calculated in the SELECT as `on_hand-reserved`; the predicate was
`reserved <= on_hand AND on_hand-reserved >= 0`. The success request changed exactly the first row.
Bruno's invalid quantity and insufficient-stock requests did not change the low-stock row.

### Implementation, transaction, architecture and security review

- ReservationApplicationService has a public `@Transactional` method reached through a Spring proxy.
  The PostgreSQL integration test asserted proxy presence and forced a second reservation failure after
  a first successful update in one outer transaction; both reserved and version returned to zero.
- JdbcInventoryRepository uses one conditional PostgreSQL `UPDATE ... WHERE on_hand - reserved >= :quantity
  RETURNING` rather than read/check/write. The table CHECK separately rejects reserved > on_hand.
  The application transaction invokes only repository SQL, with no external network call or JVM lock.
  This verifies the representative invariant, not POC-02 contention behavior.
- Typed domain failures propagate through the application service and centralized API translator.
  Tests passed for invalid quantity, unknown SKU, 409 insufficient stock, internal safe 500 and request-ID
  correlation. Bruno and direct 404 inspection confirmed stable codes and safe bodies. The internal 500
  representation was checked by an isolated HTTP test; no real internal error was induced during smoke.
- The ArchUnit test executed and its inspected rules import `dev.vra.poc00` production classes, then
  prohibit domain dependencies on Spring, servlet, SQL, application, interfaces or infrastructure,
  and prohibit inward layers depending on interfaces. Its package patterns match the actual package tree.
- Compose is PostgreSQL-only, file-secret based and loopback-bound. `.local/secrets/db_password` is ignored;
  Git status lists no secret. Source/config search found no hardcoded real password/token; the only password
  assignments are from the supplied runtime value. Captured backend logs contained request IDs, status,
  duration and startup lifecycle data, without DB password, Authorization data, SQL or request bodies.
  No frontend, Kotlin candidate, or deferred infrastructure was found.

### Remaining limits

All automated acceptance gates passed; the Java candidate is **RUNTIME VERIFIED** for POC-00.
This is not a production readiness or language-selection conclusion. The local DB account still combines
migration/runtime privileges; production requires separate identities. Reservation retries remain
non-idempotent and no reservation ledger is persisted. Final inventory concurrency, persistence policy,
authentication, production performance and end-to-end telemetry remain outside this experiment.
The POC PostgreSQL container and named volume were left running for pgAdmin inspection;
no unrelated container was stopped or deleted. No Git-mutating operation occurred.
