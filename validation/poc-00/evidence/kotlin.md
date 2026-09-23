# Kotlin Candidate Evidence

## Environment

- OS: macOS (Darwin), Apple Silicon / arm64.
- JDK: OpenJDK 21.0.12.1; compiler `javac 21.0.12.1`.
- Gradle Wrapper: 8.14.3.
- Spring Boot: 3.5.16.
- PostgreSQL: `postgres:17.11`, PostgreSQL 17; local Compose service healthy, bound to `127.0.0.1:55432`.
- Testcontainers: 1.21.4; ArchUnit: 1.4.1.
- Docker Engine: 29.8.0; Compose: 5.5.1.
- Bruno CLI: 4.1.0.
- Local `.local/secrets/db_password` existed and was not read to the terminal or included in output. A first check made relative to `validation/poc-00` incorrectly reported it absent; a root-relative recheck confirmed it exists. No password value is recorded here.

## Frozen Baseline

The shared specification, Java candidate, shared V1/V2 migrations and seed, Bruno collection, PostgreSQL-only Compose file, Java evidence, Spring/Gradle/JDK/PostgreSQL versions remained unchanged. Kotlin uses the existing shared SQL migration resources. No Kotlin A/B comparison or winner conclusion is made.

## Kotlin Toolchain

- Kotlin compiler and Gradle plugins: 2.4.20 (stable release; no preview/experimental language features enabled).
- Plugins: Kotlin JVM and Kotlin Spring. JVM plugin compiles Kotlin for JDK 21. Kotlin Spring opens Spring-annotated classes/methods as required for proxy-based Spring behavior, including transaction advice.
- Kotlin-specific dependencies: `kotlin-reflect` for Spring Kotlin runtime integration; Jackson Kotlin module for Kotlin constructor/property/nullability-aware JSON binding.
- No Kotlin JPA/no-arg tooling, Lombok, coroutine, ORM, or reactive persistence dependencies were added.
- Official compatibility/integration references checked: [Kotlin releases](https://kotlinlang.org/docs/releases.html), [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [Spring Boot Kotlin support](https://docs.spring.io/spring-boot/3.5/reference/features/kotlin.html).
- Gradle emitted its upstream compatibility notice that the frozen Gradle 8.14.3 will be deprecated by Kotlin 2.5.0, which requires Gradle 8.14.4+. Kotlin 2.4.20 itself built on the frozen wrapper; no version was changed.

## Implementation

- Added `kotlin-candidate` as an independent Gradle project. The root project shares the existing SQL migration resource directory; there is no copied migration set.
- Kotlin code follows domain/application/infrastructure/interfaces packages. Reservation uses Spring JDBC with explicit SQL and a conditional atomic PostgreSQL `UPDATE ... RETURNING`; the application service has an explicit public `@Transactional` operation.
- Flyway auto-migration is disabled at application startup. `:kotlin-candidate:migrateLocal` is the explicit local migration task.
- Local database settings use loopback URL, fixed POC database/user defaults, and require `VRA_DB_PASSWORD` at runtime. Settings are validated to the POC database/loopback target; no password is logged or committed.
- HTTP endpoint/error/request-ID behavior mirrors the Java contract. The existing Bruno collection was run without modification.
- Small shared changes: Gradle project inclusion; ignore Kotlin compiler output; clarify the two-candidate workflow in the two READMEs.

## Java/Kotlin Scenario Mapping

| Frozen Java scenario | Kotlin scenario / evidence |
|---|---|
| Money construction, negative value, arithmetic, currency mismatch | `DomainTest.money...`; includes `BigDecimal` scale-independent equality and exact `0.1 + 0.2` behavior |
| Quantity and inventory constraints/available | `DomainTest.quantity...`, `inventory...` |
| Order transitions and terminal-state rejection | `DomainTest.order...`; transition graph and CANCELLED-to-CONFIRMED rejection |
| Idempotency key validation | `DomainTest.idempotency...` |
| Immutable item/order snapshots and optional values | `DomainTest.orderItem...`, `order...`; defensive copy and unmodifiable snapshot |
| Architecture direction | `ArchitectureTest` inspects compiled Kotlin production classes and checks domain/application/interfaces dependencies |
| PostgreSQL persistence and constraints | `PostgresIntegrationTest` against disposable PostgreSQL 17.11 Testcontainers |
| Populated V1-to-V2 migration | `PostgresIntegrationTest` applies V1, inserts data, then applies V2 and validates history/checksums and retained/null data |
| Transaction rollback | PostgreSQL integration test forces failure after mutation and verifies rollback |
| Real HTTP + PostgreSQL API flow | `ReservationIntegrationTest` starts Spring against Testcontainers and tests mutation and rejection paths |
| Local HTTP contract / safe error behavior | `HttpContractTest` covers request validation, request ID, safe servlet fallback, and error mapping |
| Local settings target guard | `LocalDatabaseSettingsTest` |

Kotlin source types prevent ordinary Kotlin callers from passing null for non-null parameters. Java/reflection/framework boundaries can still supply null; construction/transport validation remains where applicable. The frozen required cases are represented across type-level and runtime tests rather than omitted.

## Commands Executed

Commands below were run from the repository root unless a working directory is stated. A failed first attempt is preserved as historical evidence, followed by its repair and successful rerun.

| Command | Result | Factual result |
|---|---|---|
| `java -version`, `javac -version`, `GRADLE_USER_HOME=.gradle ./gradlew --version`, `docker version`, `docker info`, `docker compose version`, `bru --version` | PASS | Toolchain versions recorded above; Docker daemon responsive. |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileKotlin :kotlin-candidate:compileTestKotlin` | Initial FAIL, then PASS | Initial compiler diagnostics identified Java/Kotlin logger name clashes and Java interop nullability/generic typing. Those implementation/build defects were fixed; main and test Kotlin compilation then passed. |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:test` | PASS | 30 Kotlin unit/architecture/HTTP/settings tests; 0 failed, 0 skipped. |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:integrationTest` | Initial FAIL, then PASS | Initial failure was an incorrect AssertJ assertion expecting a wrapped root cause although the JDBC exception was direct. The assertion was corrected to test the actual thrown exception; all 8 PostgreSQL-backed integration tests passed on rerun. |
| `GRADLE_USER_HOME=.gradle ./gradlew clean build` | PASS | Clean multi-project build; Java and Kotlin test/integration suites executed. 18 Gradle tasks; approximately 15 seconds. |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:test :java-candidate:integrationTest` | PASS | Candidate A regression task passed (up-to-date immediately after the clean build had executed both Java suites). |
| `docker compose config --quiet` | PASS | Compose configuration valid. |
| `docker compose ps` | PASS | Only `postgres:17.11` service, healthy, bound to `127.0.0.1:55432`; no application/frontend containers. |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:migrateLocal` with runtime DB settings from local secret | PASS | Flyway validated existing V1/V2 schema history at version 2; no migration pending. Password was not printed. |
| `psql ... -f validation/poc-00/shared/dev/seed.sql` via the documented Compose PostgreSQL target | PASS | `INSERT 0 0`; both idempotent synthetic inventory rows already existed. No non-POC database was used. |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:bootRun` with local runtime settings | PASS | Started manually outside Compose, connected to local POC DB, Tomcat bound to `127.0.0.1:8080`; application startup completed. Process was stopped after smoke tests. |
| `curl --silent --show-error --include http://127.0.0.1:8080/actuator/health` | PASS | HTTP 200, `{"status":"UP"}`, generated `X-Request-Id` response header. |
| `bru run --env local` (working directory `validation/poc-00/bruno`) | PASS | Existing collection unchanged: 4 requests passed, 2 tests and 9 assertions passed. Status/codes matched 200 health, 200 reservation, 400 `request.invalid`, 409 `inventory.insufficient_stock`; request ID correlation assertion passed. |
| Read-only PostgreSQL invariant/history query via `docker compose exec ... psql` | PASS | Success SKU: on_hand 1,000,000, reserved 4, available 999,996, invariant true, version 2. Low-stock SKU: on_hand 1, reserved 0, available 1, invariant true, version 0. V1/V2 history rows successful with checksums. |
| `git diff --check` | PASS | No whitespace errors. |
| `git diff --exit-code -- <all frozen files>` | PASS | No changes to shared spec/migrations/seed, Java source/tests/evidence, Compose, brand, or Bruno. |

## Tests Added

- Domain: 16 tests.
- Architecture: 1 test.
- HTTP contract (MockMvc): 9 tests.
- Local database target settings: 4 tests.
- PostgreSQL integration suite: 4 tests.
- Full reservation HTTP integration suite: 4 tests.
- Total Kotlin test invocations: 38; 0 failed, 0 skipped in final clean build.
- Java regression suite remained unchanged and passed: 27 standard tests + 8 PostgreSQL integration tests.
- Across the final clean build: 73 Java+Kotlin test invocations; 0 failures and 0 skips.

## Invariants Verified

- Non-negative exact money and scale-independent amount equality; exact same-currency addition and typed currency mismatch.
- Positive quantities; inventory bounds, version, and available calculation.
- Explicit order transition graph; terminal states; confirmed/cancellation optional-state constraints; immutable defensive order item snapshots.
- Idempotency key nonblank and bounded.
- Reservation SQL mutation condition enforces sufficient availability atomically, with DB CHECK constraint as defense in depth.
- PostgreSQL data round trips and CHECK constraints; migration retains V1 data and allows nullable cancellation reason after V2.
- Reservation rollback leaves no partial database mutation.
- Invalid transport payloads do not mutate rows; stable safe API codes and request ID behavior.

## Migration / PostgreSQL

- Both candidates use the same `shared/db/migration/V1__initial.sql` and `V2__add_order_cancellation_reason.sql`; Kotlin shares the resource directory.
- Isolated disposable `postgres:17.11` Testcontainers are used by automated integration suites; persistent Compose DB is not used for cleanup or tests.
- V1-only populated migration test inserts representative rows, applies V2, verifies prior data, nullable new column, and Flyway schema history/checksum behavior.
- Latest schema, inventory/order/money/state/timestamp round trips, nullable reason, inventory/order-item checks, and rollback were covered in PG-backed tests.
- Local explicit migration task validated the existing version-2 schema and made no changes. Application startup emitted no automatic Flyway migration.
- Local seed command was idempotent (`INSERT 0 0`); seeded synthetic records were already present.

## Transaction

`ReservationApplicationService.reserve` is the public Spring transaction boundary. Kotlin Spring plugin opens the annotated service for Spring proxying; full Spring/PostgreSQL integration tests exercised transaction commit and rollback. No external network call is present in the transaction flow. SQL is in the JDBC repository and the mutation is one conditional PostgreSQL update. This does not select VRA's final concurrency strategy.

## HTTP / Bruno

- Full Spring plus real PostgreSQL integration tests cover success, unknown SKU, insufficient stock, invalid quantities and invalid JSON shapes.
- Bruno collection remained unchanged and passed all four requests / nine assertions.
- Manual health endpoint returned 200 and a generated request ID. Bruno verified success balance response, safe 400 code, 409 stable code, and error/header request ID correlation.
- Backend was manually started on loopback port 8080, never added to Compose, and stopped after verification.
- Manual authoritative DB state after Bruno showed `available = on_hand - reserved`, `reserved <= on_hand`, and `available >= 0`; insufficient-stock row remained unchanged.
- Runtime logs contained request correlation and status/duration metadata; observed smoke logs contained no credentials, SQL, request bodies, or headers.

## Architecture

ArchUnit rules run against compiled Kotlin production classes and verify domain has no dependencies on Spring Web/Servlet/JDBC or application/infrastructure/interfaces; application does not depend outward on interfaces/infrastructure; interfaces does not depend on infrastructure. Package matching was checked against the actual Kotlin package names. Rule test passed.

## Security

- Local password is required via `VRA_DB_PASSWORD`; there is no committed default. Password is in the ignored `.local` path and was never printed or recorded.
- Compose DB remains loopback-only at `127.0.0.1:55432`; backend smoke bound to `127.0.0.1:8080`.
- No frontend, additional infrastructure, or application container exists.
- Local settings constrain the database URL to the loopback POC database. No staging, production, safe-host, or other project DB was contacted.
- The source/config review and smoke-log review found no hardcoded credential or token and no secret/body leakage.

## Kotlin Language Observations

- Nullability is explicit in Kotlin types; optional order values use nullable `Instant?` / `String?`, unlike Java `Optional`. Kotlin checks non-null calls at compile time, while Java/reflection/framework boundaries can still introduce null.
- Data classes were useful for immutable value-like records. `BigDecimal` equality required an explicit `compareTo`-based equality/hash implementation to preserve scale-independent Money semantics.
- Inline value classes express strong IDs without introducing ORM coupling; JDBC and Jackson boundaries unwrap/map them explicitly.
- Sealed domain failures and exhaustive `when` make expected cases and order transitions visible to the compiler.
- Kotlin `List` is read-only by interface, not necessarily defensively immutable; Order copies input and exposes an unmodifiable snapshot to prevent caller mutation.
- Spring proxying required the Kotlin Spring plugin; Jackson required the Kotlin module. No extra transaction proxy defect remained in integration testing.
- `JdbcClient` remains Java-oriented; Kotlin named parameters and nullable JDBC mappings were readable but needed explicit Java interop handling. Initial compilation surfaced platform-type/generic issues, which were corrected without suppressing warnings broadly.
- Tests are concise with Kotlin assertions; exception assertions needed to match the direct JDBC exception rather than assume wrapping.
- Compiler diagnostics identified the interoperability issues directly. No broad warning suppression or experimental feature was used.
- Reviewability observations are limited to this small candidate; this does not claim production maintainability.

## Java Regression Verification

The multi-project clean build executed Java Candidate A unchanged. The explicit Java test/integration task also passed. Java tests remained 27 standard and 8 PostgreSQL integration tests. No Java candidate files or Java evidence were modified.

## Not Executed

- No stock=1 / 500-buyer concurrency test; this is reserved for POC-02 and is explicitly outside this candidate's conclusion.
- No production-load/performance benchmark, authentication integration, or final persistence/concurrency strategy comparison.
- No Bruno CLI scenarios beyond the existing four-request collection; richer invalid JSON shapes are covered by automated HTTP integration tests.

## Known Limitations

- Candidate B is a small technology-validation POC, not production code.
- It does not select final inventory concurrency strategy or final persistence technology.
- Local database identity/privileges are not the final production least-privilege design.
- No production performance capacity or authentication is validated.
- Integration tests use disposable local Testcontainers and do not establish production operational readiness.
- Gradle 8.14.3 has a forward compatibility notice for Kotlin 2.5.0; the frozen current Kotlin 2.4.20 build passes.

## Git Status

Read-only status at evidence preparation: modified `.gitignore`, root `README.md`, POC `README.md`, and POC `settings.gradle.kts`; untracked `kotlin-candidate/` and this evidence file. No Git-mutating command was run. Final status is reported in the task handoff.

## Independent Review Corrections — 2026-09-24

This is a follow-up correction pass after the earlier runtime-verified Candidate B work; it does not replace or erase the historical verification above.

- Independent source review found that the Kotlin application repository port exposed `java.util.Optional<InventoryBalance>`, which was comparison drift from Kotlin-native nullability.
- The port now returns `InventoryBalance?`. `ReservationApplicationService` uses a direct null check and retains the same follow-up existence query and `UnknownSku` versus `InsufficientStock` classification.
- `JdbcClient` still yields Java `Optional` at the SQL adapter boundary; `JdbcInventoryRepository` converts it to Kotlin nullable using `orElse(null)`. Optional no longer appears in the application-facing Kotlin contract; the unused test import was removed.
- `flyway-database-postgresql` and `postgresql` are now `runtimeOnly`; `flyway-core` remains `implementation`. Dependency reports showed Flyway core on compile classpath, and PostgreSQL driver plus Flyway PostgreSQL module on runtime classpath. Kotlin main and test compilation passed with those scopes, so neither additional module was needed on compile classpath.
- POC README now explicitly distinguishes Java `Optional` from Kotlin nullable domain fields and identifies that difference as intentional. It also distinguishes manual/Bruno smoke on the persistent local Compose DB from automated disposable PostgreSQL 17.11 Testcontainers; automated tests do not clean/reuse the Compose DB. Verification examples now show targeted commands for both candidates.
- The first compile invocation from repository root returned `./gradlew: no such file or directory`; the wrapper is in `validation/poc-00`, and all subsequent wrapper commands ran there. The first correctly located compile found that the infrastructure override signature still declared `Optional`; changing it to `InventoryBalance?` fixed compilation. No test was disabled or weakened.

### Correction-pass verification

| Command / scenario | Result | Factual result |
|---|---|---|
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileKotlin :kotlin-candidate:compileTestKotlin` (from `validation/poc-00`) | Initial FAIL, then PASS | Initial correctly located compile identified the stale infrastructure return type; after correction, Kotlin main and test compilation passed. |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:test :kotlin-candidate:integrationTest` | PASS | 30 regular and 8 PostgreSQL/Testcontainers tests passed; no failures or skips. Unknown/insufficient classification and rollback tests passed. |
| `GRADLE_USER_HOME=.gradle ./gradlew clean build` | PASS | Clean multi-project build passed, 18 tasks executed; Java and Kotlin suites both ran. |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:test :java-candidate:integrationTest` | PASS | Explicit Java regression gate passed; tasks were up-to-date because the preceding clean build had executed them. |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:dependencies --configuration compileClasspath` and `... --configuration runtimeClasspath` | PASS | Flyway core appeared on compile classpath; Flyway PostgreSQL module and JDBC driver appeared on runtime classpath, not compile classpath. |
| `docker compose config --quiet`; `docker compose ps` | PASS | Valid Compose config; sole PostgreSQL 17.11 service remained healthy at `127.0.0.1:55432`. Local secret file presence was checked without reading its contents; port 8080 was free before smoke. |
| `VRA_DB_PASSWORD="$(< ../../.local/secrets/db_password)" VRA_DB_URL=jdbc:postgresql://127.0.0.1:55432/vra_poc00 GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:migrateLocal` | PASS | Flyway validated both shared migrations against PostgreSQL 17.11; version 2 was current, with no migration pending. Password was not printed. |
| `VRA_DB_PASSWORD="$(< ../../.local/secrets/db_password)" VRA_DB_URL=jdbc:postgresql://127.0.0.1:55432/vra_poc00 GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:bootRun` | PASS | Kotlin backend ran manually outside Compose, connected to persistent POC DB, and listened on port 8080; process stopped after smoke. |
| `curl --silent --show-error --include http://127.0.0.1:8080/actuator/health`; `bru run --env local` from `validation/poc-00/bruno` | PASS | Health returned HTTP 200 with generated `X-Request-Id`; unchanged Bruno collection passed 4/4 requests and 9/9 assertions (success 200, invalid 400 `request.invalid`, insufficient 409 `inventory.insufficient_stock`, request-ID correlation). Unknown SKU remains covered by the passing automated real-HTTP integration test (404). |
| Read-only PostgreSQL query after Bruno | PASS | Success SKU: on_hand 1,000,000, reserved 6, available 999,994, invariant true, version 3. Low-stock SKU: on_hand 1, reserved 0, available 1, invariant true, version 0. Both Flyway history rows remained successful with checksums. |
| Source and frozen-file diff review | PASS | Conditional atomic `UPDATE ... RETURNING` and DB-check migrations unchanged. No diff to shared spec/source/migrations/seed, Java candidate/evidence, Bruno collection, Compose, or brand. |

The correction did not change externally observable behavior, database state rules, migration files, Java Candidate A, or the pinned toolchain. The local Compose volume was neither reset nor deleted.

### Final-tree rerun after removing the last unused Optional import

After the table above, source inspection found one remaining unused `import java.util.Optional` in the JDBC adapter; it was removed. The complete required gate sequence was rerun against that final source tree:

- `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileKotlin :kotlin-candidate:compileTestKotlin` — PASS.
- `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:test :kotlin-candidate:integrationTest` — PASS, 30 regular + 8 PostgreSQL-backed tests; no failures or skips.
- `GRADLE_USER_HOME=.gradle ./gradlew clean build` — PASS, all 18 tasks; both Java (27 + 8) and Kotlin (30 + 8) test suites ran.
- `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:test :java-candidate:integrationTest` — PASS; up-to-date after the clean build had just run Java tests.
- Explicit Kotlin `migrateLocal`, manual `bootRun`, health request, and the unchanged Bruno collection were repeated on the final tree. Bruno again passed 4/4 requests and 9/9 assertions. The manually started backend was stopped afterward.
- Final local DB inspection: success SKU on_hand 1,000,000, reserved 8, available 999,992, invariant true, version 4; low-stock SKU on_hand 1, reserved 0, available 1, invariant true, version 0. Flyway V1/V2 checksums and success state remained unchanged. This row reflects prior smoke reservations plus the final successful Bruno request; no volume reset or cleanup occurred.
- Final `git diff --check` passed; frozen-file diff check passed; no explicit `java.util.Optional` import or `Optional<InventoryBalance>` remains in Kotlin source/tests.
