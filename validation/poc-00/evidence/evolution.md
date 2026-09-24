# POC-00-C Controlled Evolution Evidence

## Baseline

- Java checkpoint: `febaaa0`.
- Kotlin checkpoint: `6d562d8`.
- Starting `HEAD`: `6d562d8 poc: add verified Kotlin candidate for JVM language validation`.
- Starting `git status --short`: clean (no output), confirmed before any edits.
- Host: macOS 27.0, arm64. JDK/Javac: OpenJDK 21.0.12.1. Gradle Wrapper: 8.14.3. Spring Boot: 3.5.16. PostgreSQL: `postgres:17.11`; Testcontainers: 1.21.4; ArchUnit: 1.4.1; Kotlin: 2.4.20.
- Docker Compose: 5.5.1. Bruno CLI: 4.1.0.
- The frozen `SHARED_SPEC.md`, Java/Kotlin historical evidence, V1, and V2 remain unchanged.

## Evolution Specification

`EVOLUTION_SPEC.md` was created before candidate changes and treated as frozen thereafter. It specifies the EXPIRED state and typed reason-code evolution, nil-UUID SKU rejection, additive shared V3 rules and migration scenarios, HTTP behavior, and verification controls. No requirement was relaxed during implementation.

## Diagnostic E1 — EXPIRED Exhaustiveness

### Java

- Command: `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:compileJava`
- Result: intentional compile failure after adding only `EXPIRED` to `OrderState`.
- Location: `java-candidate/src/main/java/dev/vra/poc00/domain/OrderState.java:7`.
- Diagnostic: `error: the switch expression does not cover all possible input values` at the transition switch expression. The compiler forced explicit attention to the new state. No default branch was added; `PENDING_PAYMENT -> EXPIRED` was added and EXPIRED was made terminal.

### Kotlin

- Command: `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileKotlin`
- Result: intentional compile failure after adding only `EXPIRED` to `OrderState`.
- Location: `kotlin-candidate/src/main/kotlin/dev/vra/poc00/kotlin/domain/OrderState.kt:7:23`.
- Diagnostic: `'when' expression must be exhaustive. Add the 'EXPIRED' branch or an 'else' branch.` The compiler forced explicit attention to the new state. No `else` branch was added; the transition and terminal-state rules were expressed explicitly.

## Diagnostic E2 — Order reasonCode Call-Site Impact

### Java

- The required `Optional<OrderReasonCode>` component was added without a default.
- `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:compileJava` failed at Order factory/transition construction sites in `Order.java` (lines 28, 32, 37, 42, and 47 in that diagnostic revision): the constructor required the new `Optional<OrderReasonCode>` argument but the calls still supplied the old argument list.
- After internal calls were updated, `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:compileTestJava` failed at `DomainTest.java:82` and `PostgresIntegrationTest.java:99` for the same missing constructor argument. These test-source call sites were then updated explicitly.
- Main and test compilation together surfaced all legitimate constructor call sites; no Kotlin default or Java overload was used to hide impact.

### Kotlin

- The required nullable `OrderReasonCode?` constructor parameter was added without a default.
- `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileKotlin` reported missing constructor arguments at `Order.kt` lines 39, 44, 49, 54, and 59. Diagnostics included `No value passed for parameter 'version'` and an argument mismatch where the old positional `List<OrderItem>` now occupied the `OrderReasonCode?` position.
- After internal calls were updated, `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileTestKotlin` reported affected constructions in `DomainTest.kt` lines 120, 161, 164, and 167, plus `PostgresIntegrationTest.kt:154`, with the same required-parameter/positional-argument issue. All were updated explicitly.
- Compiling both production and test source sets identified the affected call sites; no default parameter was added.

## Diagnostic E3 — Sealed InvalidSkuId Exhaustiveness

### Java

- Command: `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:compileJava`
- Result: after staging `DomainFailure.InvalidSkuId` before changing HTTP handling, the final diagnostic run failed at `java-candidate/src/main/java/dev/vra/poc00/interfaces/ApiErrors.java:21` with `the switch expression does not cover all possible input values`.
- A preceding staged attempt also had a temporary extra constructor argument left from E2 call-site repair; that was corrected before the diagnostic run recorded above. The final missing-switch diagnostic established that the sealed failure switch required explicit mapping.
- Added an explicit `InvalidSkuId` mapping to HTTP 400 / `request.invalid_sku_id`; no catch-all branch was added.

### Kotlin

- Command: `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileKotlin`
- Result: compile failed at `kotlin-candidate/src/main/kotlin/dev/vra/poc00/kotlin/interfaces/ApiErrors.kt:21:9` with `'when' expression must be exhaustive. Add the 'is InvalidSkuId' branch or an 'else' branch.`
- Added an explicit sealed-hierarchy branch mapping to HTTP 400 / `request.invalid_sku_id`; no `else` branch was added.

## Final Implementation

Both candidates now have `OrderState.EXPIRED`, the same transition graph, typed `OrderReasonCode`, equivalent state-specific invariants, a typed `InvalidSkuId` failure for the nil UUID, and an explicit safe HTTP mapping. Java represents the optional reason code with `Optional<OrderReasonCode>`; Kotlin uses `OrderReasonCode?`. Both retain the existing conditional PostgreSQL inventory update. The HTTP JSON shape and the original four Bruno requests are unchanged.

## Java-Specific Changes

- Domain: `DomainFailure.java`, new `OrderReasonCode.java`, `Order.java`, `OrderState.java`, `SkuId.java`.
- Interface: `ApiErrors.java`.
- Tests: `DomainTest.java`, `HttpContractTest.java`, `PostgresIntegrationTest.java`, `ReservationIntegrationTest.java`.
- Java `Order` construction now validates the reason-code/state matrix, expiration uses `PAYMENT_TIMEOUT`, and `SkuId` rejects the nil UUID with `DomainFailure.InvalidSkuId`.

## Kotlin-Specific Changes

- Domain: `DomainFailure.kt`, new `OrderReasonCode.kt`, `Identifiers.kt`, `Order.kt`, `OrderState.kt`.
- Interface: `ApiErrors.kt`.
- Tests: `DomainTest.kt`, `HttpContractTest.kt`, `PostgresIntegrationTest.kt`, `ReservationIntegrationTest.kt`.
- Kotlin `Order` requires a non-default nullable reason-code parameter, expiration uses `PAYMENT_TIMEOUT`, and `SkuId` rejects the nil UUID with the typed sealed failure.

## Shared Migration V3

Added only `shared/db/migration/V3__add_order_expiry_and_reason_code.sql`; V1 and V2 were not modified. V3 replaces the generated state check with a named state check including EXPIRED, adds nullable `reason_code varchar(32)`, and adds a named CASE-based reason check. EXPIRED explicitly requires a non-NULL `PAYMENT_TIMEOUT`, so SQL CHECK's acceptance of NULL cannot satisfy the expired-state rule. Existing `confirmed_at` and `cancellation_reason` constraints remain in effect. The migration does not recreate tables or remove rows.

Both candidates' PostgreSQL 17.11 migration tests applied V1+V2, inserted legacy states including CANCELLED with a free-text reason, then applied V3. They verified version 3/history validation, preservation of the legacy rows, nullable legacy reason codes, acceptance of valid EXPIRED/PAYMENT_TIMEOUT, and rejection of invalid reason/state combinations, EXPIRED confirmation timestamps, and EXPIRED cancellation text.

## Tests Added

Java and Kotlin domain suites cover the EXPIRED transition and terminal rejections, expiration fields, cancellation reason-code acceptance/rejection, legacy CANCELLED construction, reason-code/state mismatches, and typed nil-SKU rejection while accepting normal UUIDs. Both HTTP suites check nil-UUID 400/error code/request-ID correlation, safe error content, and unchanged inventory. Both PostgreSQL integration suites exercise the same V3 scenarios against disposable real PostgreSQL containers.

Scenario mapping:

| Java scenario | Kotlin equivalent |
| --- | --- |
| `DomainTest` transition/reason/SKU scenarios | `DomainTest` transition/reason/SKU scenarios |
| `PostgresIntegrationTest` populated V1+V2 → V3 and constraint checks | `PostgresIntegrationTest` same shared migration and checks |
| `ReservationIntegrationTest` nil-SKU HTTP/database non-mutation case | `ReservationIntegrationTest` equivalent HTTP/database case |
| `HttpContractTest` safe nil-SKU response/request-ID behavior | `HttpContractTest` equivalent response behavior |
| `ArchitectureTest` inward dependency rules | `ArchitectureTest` equivalent rules |

Final JUnit XML reports: Java regular tests 32, Java PostgreSQL integration tests 10; Kotlin regular tests 35, Kotlin PostgreSQL integration tests 10. Total: 87 test invocations, zero failures, zero errors, zero skipped. The domain invocation counts are Java 19 and Kotlin 20. The one-invocation difference comes from a standalone Kotlin Order optional-state/empty-items constructor test that was already present at the starting checkpoint; its call sites were updated for the new required constructor argument, but the test itself was not added for this evolution. Kotlin also had two additional local-database-settings test invocations at baseline. Counts are not treated as a quality score; the required evolution behaviors are represented in both.

## Verification

Final required commands, run from `validation/poc-00`:

| Command | Result |
| --- | --- |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:compileJava :java-candidate:compileTestJava` | PASS |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:compileKotlin :kotlin-candidate:compileTestKotlin` | PASS |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:test :java-candidate:integrationTest` | PASS |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:test :kotlin-candidate:integrationTest` | PASS |
| `GRADLE_USER_HOME=.gradle ./gradlew clean build` | PASS; 18 tasks, all candidate test suites executed |
| `docker compose config --quiet` | PASS |
| `docker compose up -d postgres` / `docker compose ps` | PASS; only the `postgres:17.11` service was present and healthy on `127.0.0.1:55432` |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:migrateLocal` (twice) | PASS; first run applied V3, second validated history and reported no migration pending |
| `GRADLE_USER_HOME=.gradle ./gradlew :java-candidate:bootRun` then Bruno `bru run --env local` | PASS; manual Java process only, five requests / 12 assertions |
| `GRADLE_USER_HOME=.gradle ./gradlew :kotlin-candidate:bootRun` then Bruno `bru run --env local` | PASS; Java process had been stopped, five requests / 12 assertions |
| `git diff --check` | PASS |

An early Java integration run failed `populatedV1ToV2PreservesAllRepresentativeData`: its V1/V2-only checkpoint called latest Flyway validation while V3 was intentionally pending. The staged V1/V2 checkpoint was corrected to validate through version 2 (`flyway("2").validate()`), in both candidates. The dedicated V3 scenario then validates latest schema/history/checksums. Both candidate suites passed afterward; no migration or assertion was weakened.

Gradle and Docker access from the restricted shell initially encountered workspace-lock/socket permission errors. The prescribed Gradle commands and Docker-backed checks were rerun with the required environment/approved access; the final verification above used the frozen versions. This was an execution-environment limitation, not a candidate code change.

## Java Change Surface

Read-only `git diff --stat -- validation/poc-00/java-candidate`: 9 tracked files changed, 193 insertions, 17 deletions. Corresponding `git diff --numstat` totals: 193 added / 17 removed. The new untracked `OrderReasonCode.java` is an additional 5-line file and is not included in ordinary `git diff --stat` until staged (it was not staged).

## Kotlin Change Surface

Read-only `git diff --stat -- validation/poc-00/kotlin-candidate`: 9 tracked files changed, 235 insertions, 19 deletions. Corresponding `git diff --numstat` totals: 235 added / 19 removed. The new untracked `OrderReasonCode.kt` is an additional 5-line file and is not included in ordinary `git diff --stat` until staged (it was not staged).

## Compiler Assistance Observed

Both compilers stopped at the enum evolution when exhaustive transition handling was stale. Both also found constructor call sites after the reason-code parameter was introduced, including test-source call sites when test compilation ran. After the new sealed failure subtype was introduced, both required an explicit HTTP mapping. These diagnostics were observed before the corresponding logic was repaired; no default/catch-all was used to suppress them.

## Nullability / Optional Observations

The order reason-code API is `Optional<OrderReasonCode>` in Java and `OrderReasonCode?` in Kotlin. Kotlin's required nullable constructor parameter still forces a call-site argument; nullable does not mean defaulted. Java tests express absence through `Optional.empty()`. Kotlin tests express absence as `null`; a reflection-based Java boundary test confirms framework/Java callers supplying null to non-null Money constructor parameters fail with a null check. Database nullability for legacy CANCELLED rows remains equivalent.

## State Modeling Observations

Java's enum transition logic uses an exhaustive switch expression; Kotlin uses an exhaustive `when`. The observed compiler diagnostics required a new explicit EXPIRED branch in each. Both models preserve the same transition graph and terminal states. The state-specific reason-code invariant is enforced in each domain `Order` constructor and separately by PostgreSQL V3 constraints.

## Spring / JDBC Observations

No Spring, JDBC, or dependency baseline changes were needed. Both candidates continue to use Spring JDBC and the same explicit SQL repository behavior. Reservation remains a conditional atomic PostgreSQL `UPDATE ... RETURNING`; application transaction boundaries and request handling were not changed by this evolution. No new library or infrastructure component was added.

## Test / Reviewability Observations

The same named test categories and representative evolution behaviors were exercised for each candidate. Java and Kotlin test method structures are not identical; the XML invocation counts above include pre-existing baseline test-count differences, not just tests introduced by POC-00-C. Both ArchUnit suites and full Spring/PostgreSQL reservation integration suites passed. Domain, HTTP, migration, and DB-constraint assertions remain in their respective candidate test suites, and V3 itself is shared rather than copied.

## Shared Change Surface

- `README.md`: read-only `git diff --stat` / `--numstat` reports 1 file, 12 insertions, 2 deletions.
- New `EVOLUTION_SPEC.md`: 91 added lines (`git diff --no-index --numstat /dev/null ...`).
- New shared V3 migration: 21 added lines.
- New Bruno request: 29 added lines.
- New evolution evidence: 190 added lines, measured with `git diff --no-index --numstat /dev/null ...` after final edits.
- These shared/documentation/collection changes are reported separately and are not included in the Java or Kotlin candidate totals above.

## Bruno / HTTP

`validation/poc-00/bruno/invalid-sku-id.bru` was the only Bruno collection addition; the existing four requests were unchanged. Against each manually started backend, the five-request existing collection plus new request passed: 5/5 requests and 12/12 assertions. Health and reserve success returned 200; invalid quantity returned 400; insufficient stock returned 409; nil UUID returned 400 with `request.invalid_sku_id`, the safe message, and matching body/header request ID. Both backends behaved equivalently. Nil SKU did not mutate inventory.

## Security

The local `.local/secrets/db_password` file was checked for presence but never read to output, and no password was written to evidence. PostgreSQL remained bound to `127.0.0.1:55432`; each manually started backend listened on `127.0.0.1:8080` and was stopped before starting the other. No Compose application service, frontend, or deferred infrastructure was added. Logs contained request correlation/status/timing without request bodies or secrets. HTTP errors did not expose SQL, stack traces, or filesystem paths. No non-POC database or safe-host was accessed.

## Migration / Data Preservation

Compose PostgreSQL was healthy before and after smoke. Java `migrateLocal` applied V3 to the persistent POC database; a second run validated all three migrations and reported no migration necessary. Flyway history reported V1, V2, and V3 successful. The Docker volume was not reset or deleted. After both smoke runs, the success SKU had `on_hand=1000000`, `reserved=12`, `available=999988`, `version=6`; the low-stock SKU remained `on_hand=1`, `reserved=0`, `available=1`, `version=0`. The database checks `reserved <= on_hand` and `available = on_hand - reserved` held. No row existed for the nil UUID. This smoke state includes prior preserved Java POC use and is not a clean initial database.

## Not Executed

- No POC-02 stock=1 / 500-buyer contention experiment; explicitly out of scope.
- No performance comparison, production load test, authentication validation, or final language selection; explicitly out of scope.
- No separate destructive/reset operation on the persistent Compose volume; intentionally not performed.

## Known Limitations

- The evolution experiment does not choose a language or establish production readiness.
- POC-00-C does not validate high-contention inventory behavior; POC-02 owns that test.
- The local database/migration identity arrangement is still a POC convenience, not production least-privilege role separation.
- The migration tests prove the enumerated state/data constraints and preservation scenarios, not every future schema-evolution case.

## Git Status

All Git inspection was read-only. No staging, commit, branch, remote, or history operation was performed. At evidence finalization the expected implementation files are modified/untracked relative to the clean `6d562d8` baseline; the exact final status is reported by the task handoff.
