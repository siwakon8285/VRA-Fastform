# POC-01 Evidence Results

Status: CLOSED — final exit review passed
Date: 2026-09-24
Branch: `poc/01-transactional-core`
Evidence HEAD: `d39e532`

## 1. Objective

POC-01 validates the production-oriented transactional foundation for VRA
using Inventory Reservation as the representative write path.

The POC is intended to prove:

- explicit domain/application/adapter boundaries,
- PostgreSQL as the authoritative transactional store,
- a least-privilege migration/runtime identity split,
- an explicit migration step outside runtime startup,
- a critical conditional inventory write,
- JDBC + JPA participation in one Spring transaction,
- rollback on downstream persistence failure,
- database-enforced invariants,
- a stable HTTP boundary,
- server-controlled request correlation,
- liveness/readiness semantics,
- runtime behavior for missing or incompatible schema,
- and a real built-artifact smoke path.

This document records what was actually demonstrated. It does not extend the
scope of POC-01.

## 2. Validated stack

The evidence run used:

| Component | Version / decision |
| --- | --- |
| Java | OpenJDK 21.0.12.1 |
| Gradle | 9.7.1 |
| Spring Boot | 4.1.1 |
| PostgreSQL | 17.11 |
| Testcontainers | 2.0.5 |
| Flyway | 12.4.0 |
| PostgreSQL JDBC | 42.7.13 |
| Hibernate ORM | 7.4.5.Final |
| ArchUnit | 1.4.1 |
| Primary JVM language | Java |

The local evidence host was macOS on ARM64 with Docker Desktop 4.91.0 and Docker Engine 29.8.0.

## 3. Build and module boundary

POC-01 uses two Gradle modules:

- `backend/runtime`
- `backend/migration`

The split is a real security/operations boundary:

- `migration` owns Flyway execution,
- `runtime` does not contain Flyway,
- runtime startup does not perform migrations,
- runtime connects with the restricted runtime identity.

The final dependency check confirmed that runtime contains neither Flyway nor
jOOQ.

## 4. Database security model

The validated identity model is:

- `vra_owner` — `NOLOGIN`, owns schema objects.
- `vra_migrator` — login identity with controlled ability to `SET ROLE
  vra_owner`.
- `vra_runtime` — login identity with only the DML privileges required by the
  runtime path.

The migration integration proof demonstrated:

- migrated objects are owned by `vra_owner`,
- `vra_migrator` without the controlled role switch cannot create objects as
  the owner,
- `vra_runtime` can perform its required DML,
- `vra_runtime` cannot create, alter, or drop schema objects,
- `vra_runtime` cannot access Flyway history,
- `vra_runtime` cannot run the migration,
- checksum drift is detected,
- rerunning a valid migration reports the schema as up to date.

The manual runtime smoke independently reconfirmed that `vra_runtime` cannot
create a table.

## 5. Inventory reservation model and invariants

Representative model:

```text
InventoryKey(
  skuId,
  ownerId,
  locationId,
  stockStatus
)

InventoryBalance(
  inventoryKey,
  onHand,
  reserved,
  version
)

Reservation(
  reservationId,
  inventoryKey,
  quantity,
  createdAt
)
```

Validated invariants include:

```text
on_hand >= 0
reserved >= 0
reserved <= on_hand
quantity > 0
available = on_hand - reserved
available >= 0
only reservable stock status may be reserved
reservation inventory key matches the target balance
```

`AVAILABLE` is reservable. `QUARANTINED` is not.

Database constraints were explicitly proven for:

- negative `on_hand`,
- negative `reserved`,
- `reserved > on_hand`,
- invalid stock status,
- non-positive reservation quantity.

## 6. Critical transactional write

The critical inventory mutation is a conditional PostgreSQL update rather than
a read-then-unconditional-write flow:

```sql
UPDATE vra.inventory_balance
SET reserved = reserved + :quantity,
    version = version + 1
WHERE sku_id = :skuId
  AND owner_id = :ownerId
  AND location_id = :locationId
  AND stock_status = :stockStatus
  AND stock_status = 'AVAILABLE'
  AND version = :expectedVersion
  AND on_hand - reserved >= :quantity
RETURNING version
```

The application transaction is:

```text
BEGIN
→ conditional JDBC inventory update
→ JPA reservation persistence
→ COMMIT
```

The PostgreSQL integration suite proved:

- successful reservation updates the balance and persists the reservation in
  one transaction,
- insufficient stock performs no mutation,
- quarantined inventory cannot be reserved,
- owner/location dimensions remain isolated,
- optimistic version conflict performs no mutation,
- a reservation insert failure rolls back the preceding JDBC inventory update.

This validates that `JdbcClient + JPA + @Transactional + PostgreSQL` share the
required transaction semantics for the POC-01 path.

## 7. Architecture boundary evidence

ArchUnit currently enforces:

- domain code does not depend on application, adapters, platform, Spring, JPA,
  or JDBC,
- application code does not depend on adapters,
- JPA entities remain in the persistence adapter,
- web adapters do not depend on persistence adapters,
- REST controllers remain in `..adapter.in.web..`.

The POC intentionally uses package-level architecture enforcement inside the
runtime module rather than creating additional Gradle modules for each layer.

## 8. HTTP contract

Validated endpoint:

```http
POST /api/v1/inventory/reservations
```

Validated behavior includes:

- `201` for successful reservation,
- `400` for malformed or invalid requests,
- `404` for unknown inventory,
- `409` for non-reservable inventory,
- `409` for insufficient stock,
- `409` for optimistic version conflict,
- `500` for unexpected internal failure using a safe generic public error,
- stable public error codes/messages,
- persistence entities are not used as the API response model,
- request IDs are generated by the server,
- a client-supplied `X-Request-Id` is not trusted,
- response header/body request IDs are consistent,
- unexpected exception messages are not returned to the client.

Representative error contract:

```json
{
  "code": "INVENTORY_INSUFFICIENT_STOCK",
  "message": "Insufficient stock",
  "request_id": "server-generated-uuid"
}
```

## 9. Real HTTP-to-PostgreSQL proof

A real Spring Boot server was started on a random port and exercised through a
real Java HTTP client against PostgreSQL 17.11.

The proof demonstrated:

```text
real HTTP
→ embedded Tomcat
→ RequestIdFilter
→ REST controller
→ application service
→ Spring transaction
→ JdbcClient + JPA
→ PostgreSQL
```

The success path persisted both the inventory change and reservation row.
The insufficient-stock path returned the stable `409` error and left database
state unchanged.

## 10. Liveness and readiness

Actuator health exposure is intentionally limited to health.

Validated semantics:

```text
liveness
→ livenessState

readiness
→ readinessState + db
```

The database-outage integration test proved:

```text
DB available:
  liveness  = UP / HTTP 200
  readiness = UP / HTTP 200

DB unavailable after startup:
  liveness  = UP / HTTP 200
  readiness = DOWN / HTTP 503
```

The application therefore does not convert a database outage into an
application restart loop, while still becoming unavailable for new traffic.

## 11. Manual built-artifact runtime smoke

`validation/poc-01/scripts/manual-runtime-smoke.sh` validated the
operator-visible path using the built migration and runtime JARs:

```text
start dedicated PostgreSQL
→ bootstrap owner / migrator / runtime identities
→ build artifacts
→ execute migration artifact explicitly
→ start runtime using vra_runtime credentials
→ readiness UP
→ liveness UP
→ seed inventory as owner
→ real HTTP reservation returns 201
→ database state is correct
→ client request ID is not trusted
→ insufficient stock returns stable 409
→ failed reservation does not mutate inventory
→ runtime DDL is denied
→ clean runtime shutdown
→ clean Compose shutdown
```

The dedicated POC-01 PostgreSQL volume is intentionally preserved.

During the final smoke the database was already at schema version 1, therefore
the explicit migration step correctly reported zero new migrations and a
schema that was already up to date.

## 12. Missing-schema startup behavior

A temporary empty PostgreSQL database was created with the runtime identity
able to connect but without the `vra` schema.

Observed behavior:

```text
PostgreSQL reachable
→ Hibernate schema validation runs
→ required table is missing
→ ApplicationContext startup fails
→ runtime process exits
```

The failure explicitly identified the missing
`vra.inventory_reservation` table.

After the failed startup attempt:

```text
vra schema                 absent
inventory_balance          absent
inventory_reservation      absent
flyway_schema_history      absent
```

Therefore runtime did not self-migrate or create schema objects.

## 13. Incompatible-schema startup behavior

A temporary database was deliberately created with an incompatible mapping:

```text
vra.inventory_reservation.quantity = TEXT
expected runtime mapping           = BIGINT
```

Observed behavior:

```text
runtime exit code = 1
Hibernate schema validation fails explicitly
wrong column type:
  found     TEXT / VARCHAR
  expected  BIGINT
```

After the failure:

```text
existing incompatible table        preserved
inventory_balance                  absent
flyway_schema_history              absent
quantity column type               still TEXT
```

Runtime did not repair, alter, or migrate the incompatible schema.

## 14. Final automated evidence

Final confirmed automated results:

| Suite | Tests | Skipped | Failures | Errors |
| --- | ---: | ---: | ---: | ---: |
| `:runtime:test` | 26 | 0 | 0 | 0 |
| `:runtime:integrationTest` | 16 | 0 | 0 | 0 |
| `:migration:integrationTest` | 1 | 0 | 0 | 0 |
| **Total** | **43** | **0** | **0** | **0** |

The migration security test belongs to `:migration:integrationTest`, not
`:migration:test`.

The final closure evidence run at commit `d39e532` confirmed:

```text
:runtime:test                  26 / 26 PASS
:runtime:integrationTest       16 / 16 PASS
:migration:integrationTest      1 /  1 PASS
                               ────────────
TOTAL                           43 / 43 PASS

skipped                         0
failures                        0
errors                          0
```

The migration security test belongs to `:migration:integrationTest`; the final run executed it and confirmed `provesMigrationOwnershipAndRuntimeLeastPrivilege()` as PASS.

## 15. Evidence commits

Relevant POC-01 implementation checkpoints:

```text
aaf3ca3 build: establish POC-01 backend foundation
3ce4df7 feat: establish secure database migration foundation
956aaae test: add POC-01 local database smoke harness
9a60022 feat: add transactional inventory reservation core
1aec9b4 test: enforce inventory architecture and database constraints
bd277cc feat: expose inventory reservation HTTP API
27743ba test: verify reservation HTTP flow against PostgreSQL
7b0d945 feat: add database-aware readiness and liveness probes
d07a89e test: add POC-01 manual runtime smoke
2038f22 docs: record POC-01 evidence results
2430f52 feat: add safe internal errors and structured request logging
1ee1193 feat: validate runtime database configuration
d39e532 ci: add backend verification workflow
```

Planning checkpoints immediately preceding implementation:

```text
e530ba5 docs: freeze POC-01 execution plan
e631457 docs: approve POC-01 implementation plan
```

## 16. What POC-01 proves

POC-01 provides evidence that the selected foundation can support a
production-oriented transactional core with:

- explicit modular boundaries,
- PostgreSQL transactional authority,
- least-privilege runtime/migration separation,
- explicit external migration execution,
- safe conditional inventory mutation,
- mixed JDBC/JPA transactional participation,
- rollback correctness,
- database invariant enforcement,
- stable HTTP behavior,
- real HTTP-to-database execution,
- operational health semantics,
- fail-fast schema validation,
- safe generic handling of unexpected HTTP failures,
- server-controlled request correlation in structured ECS logs,
- fail-fast runtime database configuration validation,
- a CI-compatible backend verification workflow with explicit PostgreSQL no-skip guards,
- and real built-artifact execution.

## 17. Deliberately not proven by POC-01

POC-01 does not claim to prove:

- high-contention reservation behavior,
- throughput or latency targets,
- retry behavior under contention,
- reservation release/expiry/allocation lifecycle,
- transactional outbox delivery,
- cross-domain workflows,
- authentication/authorization,
- API idempotency-key semantics,
- production reverse-proxy/TLS deployment,
- production backup/restore,
- high availability,
- horizontal scaling,
- multi-region behavior,
- or specialized derived/data planes.

Those concerns require their own later evidence and must not be inferred from
POC-01.

## 18. Exit state

Final evidence collection is complete through executable/CI commit `d39e532`.

The remaining step before POC-01 can be declared closed is the final independent
exit review against the frozen `validation/poc-01/SHARED_SPEC.md`, the approved
implementation plan, the current implementation, and this reconciled evidence
document.

POC-01 is therefore **not yet marked CLOSED in this document**.

## 19. Gate 8 remediation evidence

The first independent exit review found four evidence/implementation gaps. They
were handled as narrow remediation gates rather than by weakening the frozen
specification.

### 19.1 Gate 8A — safe unexpected 5xx and structured logging

Commit:

```text
2430f52 feat: add safe internal errors and structured request logging
```

Validated behavior:

- an unexpected application exception maps to HTTP `500`,
- the public response uses stable code `INTERNAL_ERROR`,
- the public message is `Internal server error`,
- internal exception text is not returned to the client,
- the server-generated request ID is preserved in the response,
- console logging uses ECS structured JSON,
- `request_id` is placed in MDC for request-scoped log correlation,
- unexpected failures carry `error_code=INTERNAL_ERROR`,
- request completion logs carry HTTP method/path/status,
- MDC request-ID state is removed after request processing.

The controller/HTTP suite increased from 8 to 9 tests while the overall regular
suite increased from 20 to 21 tests at this gate.

### 19.2 Gate 8B — runtime database configuration validation

Commit:

```text
1ee1193 feat: validate runtime database configuration
```

Runtime configuration now rejects:

- blank URL,
- non-PostgreSQL JDBC URL,
- malformed PostgreSQL JDBC URL,
- PostgreSQL JDBC URL without a host,
- PostgreSQL JDBC URL without a database name,
- credentials embedded in the JDBC URL,
- blank username,
- blank password.

A valid `jdbc:postgresql://<host>/<database>` configuration remains accepted.

The configuration test class increased from 4 to 9 tests and the complete
regular suite reached 26 tests.

### 19.3 Gate 8C — CI-compatible backend baseline

Commit:

```text
d39e532 ci: add backend verification workflow
```

`.github/workflows/backend-ci.yml` now provides a minimal backend workflow for:

```text
pull requests
+
pushes to main
```

The workflow:

- configures Java 21,
- verifies Docker availability before Testcontainers-dependent work,
- executes `:runtime:check`,
- executes `:migration:check`,
- builds runtime and migration Boot JARs,
- parses PostgreSQL integration XML results,
- fails if either runtime or migration integration suites execute zero tests,
- fails if PostgreSQL integration results contain skips/failures/errors,
- uses read-only repository permission,
- disables persisted checkout credentials.

The same workflow commands and no-skip guard were rehearsed locally and passed.

GitHub-hosted execution is **NOT EXECUTED / NOT YET VERIFIED** at this evidence
checkpoint because the workflow has not yet been exercised by an authorized
remote push/PR. Branch-protection required-check configuration is likewise
**NOT EXECUTED / NOT YET VERIFIED**. These are not claimed as completed
production operations.

## 20. Commands executed

The evidence set includes the following reproducible command classes.

### 20.1 Clean automated verification and artifact build

```bash
./backend/gradlew   -p backend   --no-daemon   --warning-mode all   :runtime:check   :migration:check   :runtime:bootJar   :migration:bootJar   --rerun-tasks
```

### 20.2 PostgreSQL integration no-skip verification

JUnit XML under:

```text
backend/runtime/build/test-results/integrationTest/
backend/migration/build/test-results/integrationTest/
```

was parsed after the run to require:

```text
tests > 0
skipped = 0
failures = 0
errors = 0
```

Final result:

```text
runtime PostgreSQL   tests=16 skipped=0 failures=0 errors=0
migration PostgreSQL tests=1  skipped=0 failures=0 errors=0
```

### 20.3 Runtime dependency boundary

```bash
./backend/gradlew   -p backend   :runtime:dependencies   --configuration runtimeClasspath
```

The result was inspected for Flyway/jOOQ and confirmed neither is present in the
runtime classpath.

### 20.4 Dependency/version evidence

Gradle `dependencyInsight` / resolved runtime-test dependency reports were used
to record the actual POC-01 dependency baseline, including Testcontainers
`2.0.5`.

### 20.5 Manual built-artifact smoke

```bash
bash validation/poc-01/scripts/manual-runtime-smoke.sh
```

This covered explicit migration, runtime startup, readiness/liveness, real HTTP
reservation, stable insufficient-stock behavior, database verification, runtime
DDL denial, and clean shutdown.

### 20.6 Schema failure observations

Dedicated temporary PostgreSQL databases were used to observe runtime startup
against:

```text
missing schema
incompatible schema
```

Both probes confirmed fail-fast schema validation without runtime migration or
schema repair.

## 21. Independent review findings

The independent review checked the implementation/evidence against the frozen
POC-01 specification and approved implementation plan.

| Finding | Initial state | Resolution |
| --- | --- | --- |
| Safe unexpected internal `5xx` contract | GAP | Resolved by Gate 8A |
| Structured logging/request-ID context | GAP / exit blocker | Resolved by Gate 8A |
| Invalid/malformed database URL validation | PARTIAL | Resolved by Gate 8B |
| Minimal CI-compatible backend workflow | PLAN GAP | Resolved by Gate 8C |
| Evidence document completeness/current counts | DOC GAP | Reconciled by Gate 8D |

The review also reconfirmed that the implementation does not require runtime
owner/superuser privilege, runtime auto-migration, H2/SQLite substitution,
read-then-unconditional critical writes, or silently skipped PostgreSQL tests.

The final post-remediation independent review found no unresolved critical
contradiction in the POC-01 technical exit gate. Material foundation decisions
are recorded in `docs/adr/ADR-002-backend-production-foundation.md`.

## 22. Decisions supported by evidence

The required POC-01 decision outputs are:

```text
Spring Boot primary framework:
ACCEPT

Exact Spring baseline:
Spring Boot 4.1.1

Gradle layout:
ACCEPT small two-module backend build:
backend/runtime + backend/migration

Package/module rules:
ACCEPT package-level domain/application/adapter boundaries inside runtime,
enforced by ArchUnit; keep migration as a separate Gradle module because it is
a real security/operations boundary.

Persistence strategy:
ACCEPT hybrid baseline:
JPA for suitable aggregate-shaped persistence +
explicit SQL/JdbcClient for correctness-critical state transitions.
Do not introduce jOOQ without a demonstrated need.

Flyway lifecycle:
ACCEPT explicit migration artifact/process before runtime.
Runtime performs schema validation only and never owns migration execution.

DB ownership/grants:
ACCEPT vra_owner NOLOGIN ownership +
vra_migrator controlled SET ROLE ownership path +
vra_runtime least-privilege DML only.
Do not broaden runtime privileges to repair deployment/configuration failures.

Health/readiness:
ACCEPT process-oriented liveness and DB-dependent readiness.

Error contract:
ACCEPT stable code/message/request_id responses with safe generic INTERNAL_ERROR
for unexpected failures and no internal exception text exposed to clients.

Logging baseline:
ACCEPT ECS structured console logs with server-controlled request_id correlation,
HTTP completion fields, and structured error_code classification.

Artifact baseline:
ACCEPT separate deployable runtime and migration JVM artifacts; migration is
executed explicitly before runtime.
```

These are POC-01 evidence-backed baseline decisions. Any later revision must
follow canonical change/ADR governance rather than silently changing behavior.

## 23. Known limitations and deferred questions

POC-01 deliberately leaves the following for later evidence:

- high-contention stock reservation and retry/idempotency behavior — POC-02,
- transactional outbox/worker semantics — POC-03,
- authentication/session/MFA/security-hardening implementation — POC-04,
- broader observability, fault injection and performance validation — POC-05,
- full reservation release/expiry/allocation lifecycle,
- production deployment/reverse proxy/TLS,
- production backup/restore and HA,
- horizontal/multi-region scale,
- specialized derived/data planes.

Operational follow-up still not executed at this checkpoint:

```text
GitHub-hosted backend CI run        NOT EXECUTED
branch-protection required check    NOT EXECUTED
```

Those items must not be reported as completed until they are actually observed.

## 24. Final review readiness

At the end of Gate 8D evidence reconciliation:

```text
Evidence HEAD                     d39e532
runtime regular tests             26 / 26 PASS
runtime PostgreSQL tests          16 / 16 PASS
migration PostgreSQL tests         1 /  1 PASS
automated total                   43 / 43 PASS
skipped                            0
failures                           0
errors                             0
Testcontainers                     2.0.5
runtime Flyway/jOOQ                absent
runtime artifact                   built
migration artifact                 built
local CI rehearsal                 PASS
GitHub-hosted CI                   NOT EXECUTED
```

All technical gaps identified by the first independent review have remediation
evidence.

## 25. Final post-remediation exit review

The final independent review was performed against:

- the frozen `validation/poc-01/SHARED_SPEC.md`,
- the approved `validation/poc-01/IMPLEMENTATION_PLAN.md`,
- the committed implementation through `d39e532`,
- the automated/manual evidence summarized in this document,
- and VRA ADR governance.

Final review result:

```text
implementation matches frozen POC-01 scope          PASS
canonical requirements bypassed                    NO
runtime privilege broadened                        NO
runtime auto-migration                             NO
hidden skipped PostgreSQL tests                    NO
PostgreSQL replaced by H2/SQLite                   NO
rollback proof                                     PASS
database constraint proof                          PASS
optimistic-version proof                           PASS
stable/safe HTTP error contract                    PASS
request correlation / structured logging           PASS
configuration/startup validation                   PASS
architecture dependency enforcement                PASS
persistence decision supported by evidence         PASS
deferred POC-02/03/04/05 scope leakage             NO
evidence reproducibility                           PASS
material decision ADR                              ADR-002
unresolved critical contradiction                  0
```

Technical exit review is therefore complete.

The branch remains in `REVIEW`, not `CLOSED`, until the reviewed evidence and
ADR decision are committed as the required Git checkpoint. GitHub-hosted CI and
branch-protection configuration remain `NOT EXECUTED` and are not claimed as
completed production operations.

## 26. Closure record

POC-01 is closed after the final review/ADR checkpoint:

```text
fd6e97a docs: record POC-01 final review and backend foundation
```

The executable/CI evidence remains anchored to:

```text
d39e532 ci: add backend verification workflow
```

This distinction is intentional: `d39e532` is the implementation commit against
which the final 43-test closure run and dependency/artifact evidence were
collected, while `fd6e97a` records the reviewed evidence and accepted ADR.

Closure state:

```text
POC-01 technical exit gate                 PASS
independent post-remediation review        PASS
ADR governance                             PASS (ADR-002)
unresolved critical contradiction          0
GitHub-hosted backend CI                   NOT EXECUTED
branch-protection required check           NOT EXECUTED
```

GitHub-hosted CI and branch-protection configuration are operational follow-up
items, not claims made by POC-01. If later remote execution exposes a defect,
that defect must be recorded and corrected in a new checkpoint rather than by
rewriting this historical evidence.

The next planned validation branch is:

```text
02 — poc/02-concurrency-idempotency
Status: PLANNED
```

POC-02 implementation must not begin until its branch-specific scope/spec is
reviewed and frozen according to `docs/BRANCH_PLAN.md`.
