# POC-02 Evidence Results

Status: GATE 7 FINAL REVIEW PASS — pending Gate 8 closure / ADR decision
Date: 2026-09-25
Branch: `poc/02-concurrency-idempotency`
Evidence HEAD: `48988256b2e5c0be20868b8a32e225206ea97245`

## Objective and authority

POC-02 uses Inventory Reservation as the representative executable slice to
prove PostgreSQL stock arbitration, transactional idempotency, replay, rollback,
and concurrent behavior. The approved authorities are
`validation/poc-02/SHARED_SPEC.md` and
`validation/poc-02/IMPLEMENTATION_PLAN.md`. This document records the fresh
local Gate 6 run against the HEAD above. It is evidence for Gate 7 review, not
a closure decision.

## Validated local environment and selected dependencies

| Item | Freshly observed value | Source |
| --- | --- | --- |
| Date and host | 2026-09-25; macOS 27.0, aarch64 | `date +%F`; Gradle `--version` |
| Java | OpenJDK 21.0.12.1 | `java -version` |
| Gradle | 9.7.1 | `./backend/gradlew --version` |
| Spring Boot | 4.1.1 | Current Gradle build configuration |
| PostgreSQL | `postgres:17.11` Testcontainers image | PostgreSQL integration test source and fresh execution |
| Testcontainers PostgreSQL and JUnit Jupiter | 2.0.5 | Selected `testRuntimeClasspath` dependencyInsight results |
| Flyway Core | 12.4.0 | Selected migration `runtimeClasspath` dependencyInsight result |
| PostgreSQL JDBC | 42.7.13 | Selected runtime `runtimeClasspath` dependencyInsight result |
| Hibernate ORM Core | 7.4.5.Final | Selected runtime `runtimeClasspath` dependencyInsight result |
| ArchUnit JUnit 5 | 1.4.1 | Selected runtime `testRuntimeClasspath` dependencyInsight result |
| Docker client / server | 29.8.0 / 29.8.0 | `docker version --format` |
| Docker Compose | v5.5.1 | `docker compose version` |

The six dependency versions above were resolved from the current Gradle graph
using `dependencyInsight`, with the runtime and migration configurations named
in the Gate 6 instructions. No Docker Desktop application version was queried.

## Reservation contract and stock arbitration

The normal public request has `skuId`, `ownerId`, `locationId`, `stockStatus`,
and `quantity`. `ReserveInventoryCommand` has only `InventoryKey` and
`quantity`: no caller `expectedVersion` or reservation ID. The shared
`ReservationExecution` creates the reservation UUID on authoritative first
success and persists the reservation. Replay returns that original ID.

The decisive PostgreSQL update increments `reserved` and `version` only when
the full inventory key matches, `stock_status = 'AVAILABLE'`, and
`on_hand - reserved >= :quantity`. It has no caller version predicate. A missed
write is classified from authoritative state as not found, not reservable, or
insufficient stock; an unclassifiable miss fails as an internal invariant.
Optimistic version compare-and-swap remains a separate direct-SQL persistence
proof and is not a normal reservation outcome.

## Idempotency identity, fingerprint, and transaction lifecycle

The application identity is the exact trusted synthetic `actorScope` plus the
opaque idempotency key, within the Inventory Reservation operation/table. A
request ID is correlation data, not idempotency identity. Fingerprint v1 is
SHA-256 over UTF-8 canonical lines, in fixed order, for version, SKU UUID,
owner UUID, location UUID, exact stock-status enum name, and base-10 quantity;
its stored value is 64 lowercase hexadecimal characters. The three literal
golden-vector tests passed in `runtime:test`.

PostgreSQL's `(actor_scope, idempotency_key)` primary key is the identity and
serialization authority. The claim uses `INSERT ... ON CONFLICT DO NOTHING
RETURNING`, followed on conflict by a new `SELECT` under `READ COMMITTED`.
The claimed first execution, stock update, reservation insert, and guarded
terminal `SUCCEEDED` completion share one transaction. A controlled business
rejection is completed as terminal `REJECTED` and returned normally so it
commits. Terminal updates require a matching identity and fingerprint and an
uncompleted row; replay does not execute stock mutation or replace the
reservation ID. Infrastructure failures propagate and roll back the claim and
business writes. The application integration test observed actual isolation
`read committed` and database user `vra_runtime` through a real service call.

No automatic retry, JVM mutex, Redis, or distributed lock is a correctness
authority. Idempotency retention and expiry are deferred.

## Fresh local full regression

The authoritative local command, matching the core build command in
`.github/workflows/backend-ci.yml`, completed with exit status **0**:

```sh
./backend/gradlew -p backend --no-daemon --warning-mode all \
  :runtime:clean :migration:clean \
  :runtime:check :migration:check \
  :runtime:bootJar :migration:bootJar
```

The fresh log shows both `clean` tasks, both `check` tasks, both PostgreSQL
`integrationTest` tasks, and both `bootJar` tasks executed. Counts below come
from XML generated after that clean run:

| Suite | Tests | Failures | Errors | Skipped | Status |
| --- | ---: | ---: | ---: | ---: | --- |
| `runtime:test` | 31 | 0 | 0 | 0 | PASS |
| `runtime:integrationTest` | 37 | 0 | 0 | 0 | PASS |
| `migration:integrationTest` | 2 | 0 | 0 | 0 | PASS |
| **Executed automated total** | **70** | **0** | **0** | **0** | **PASS** |

`migration:test` has zero non-PostgreSQL test cases and produced no test XML;
its two authoritative migration/security tests ran in
`migration:integrationTest`. `:migration:check` passed. The five
`InventoryArchitectureTest` cases passed with zero skips, failures, or errors;
they are included in the 31 regular runtime tests, not added to the total.

## Gate 4 application-boundary proofs

The fresh `IdempotentReservationApplicationServiceIntegrationTest` XML reports
9 passed, zero failures, errors, or skips. Its PostgreSQL assertions cover:

- First success: one stock mutation, one reservation, and terminal `SUCCEEDED`.
- Same identity and payload: the original reservation ID/version replay with
  no second mutation. A changed payload returns
  `IDEMPOTENCY_KEY_REUSED` and leaves the bound result unchanged.
- Insufficient stock: terminal `REJECTED(INSUFFICIENT_STOCK)` replays after
  stock is increased; a new key can execute a new successful attempt.
- QUARANTINED and missing inventory: post-claim terminal business rejections.
- Invalid identity, missing inventory key, and nonpositive quantity: rejection
  before claim. Quantity zero does not consume the key; corrected quantity
  with the same key succeeds. Accepted identity strings retain their exact
  values.
- Application-level result loss: discarding the first returned success after
  commit and retrying returns the original persisted result, with one business
  effect. This is not a network or HTTP response-loss test.
- Injected reservation save failure: the in-transaction claim and inventory
  update are observed, then the exception propagates; committed inventory,
  reservation, and idempotency state all remain unchanged. The same identity
  succeeds when retried after rollback.
- An after-each authoritative PostgreSQL scan found zero committed
  `outcome_status IS NULL` rows on every application scenario.

## Gate 5 concurrent PostgreSQL proofs

The fresh `ReservationConcurrencyIntegrationTest` XML reports 5 passed, zero
failures, errors, or skips. Its test-only harness has 16 workers, a primary
Hikari pool with asserted `maximumPoolSize = 8`, explicit per-worker
`REQUIRES_NEW` / `READ_COMMITTED` transactions with 30-second transaction
timeouts, a one-way start gate, a 120-second scenario deadline, and an
in-transaction two-backend overlap latch. Every worker checked effective
`read committed` isolation and `current_user = vra_runtime`; every scenario
recorded at least two distinct PostgreSQL backend PIDs at the overlap point.
The execution counter delegates to the real JDBC inventory repository.

| Scenario | Distinct backend PIDs | Fresh returned result / execution count | Authoritative committed state | Elapsed diagnostic |
| --- | ---: | --- | --- | ---: |
| Stock 1 / 500 distinct identities | 8 | 1 success; 499 `INSUFFICIENT_STOCK`; 0 conflicts; 500 executions | On hand 1, reserved 1, version 1, available 0; one quantity-1 reservation; 500 rows: 1 `SUCCEEDED`, 499 `REJECTED`, 0 incomplete; all 500 fingerprint version 1 and matching payload fingerprint | 198.503 ms |
| Same key and payload, 64 calls | 3 | 64 equivalent successes; 1 execution | On hand 10, reserved 1, version 1; one reservation and one terminal `SUCCEEDED` row with the common ID/version | 31.632 ms |
| Same key, conflicting quantities 1 and 2 | 2 | 1 success; 1 `IDEMPOTENCY_KEY_REUSED`; 1 execution | Quantity **2** happened to bind in this run; its fingerprint alone is stored; reserved 2, version 1; one matching reservation and terminal row | 3.523 ms |
| Same key in two actor scopes | 2 | 2 successes; 2 executions | On hand 2, reserved 2, version 2; two distinct reservations and two terminal rows, one per scope | 12.382 ms |
| Separate direct optimistic CAS | 2 | Affected rows `[1, 0]`; 0 reservation executions | On hand 10, reserved 2, version 11; no reservation and no idempotency effect | 1.703 ms |

The conflicting winner is intentionally nondeterministic. Every scenario
recorded **0 unexpected exceptions, 0 timeouts, 0 deadlocks**, and an
after-each authoritative count of **0 committed NULL outcomes**. The CAS
workers used direct conditional SQL with stale expected version 10, not a
reservation service. Elapsed values are test diagnostics, not throughput or
latency-capacity evidence.

## Migration and least-privilege evidence

The fresh `MigrationSecurityIntegrationTest` XML reports these two PASS cases:

- `provesMigrationOwnershipAndRuntimeLeastPrivilege()` — fresh PostgreSQL
  executes V1 and V2 (2 migrations), validates, and reruns with 0 migrations.
  It proves `inventory_balance`, `inventory_reservation`,
  `inventory_reservation_idempotency`, and Flyway history ownership by
  `vra_owner`; the controlled migrator `SET ROLE` boundary; runtime not owner
  or owner member; and denial of runtime migration and DDL. It also proves
  checksum-drift detection and the runtime's inability to read Flyway history.
- `upgradesActualVersionOneDatabaseToVersionTwo()` — test-only Flyway applies
  V1, confirms the V2 table absent, then production `MigrationRunner` executes
  exactly one V2 migration, validates, confirms owner `vra_owner`, and reruns
  with zero migrations.

The V2 table's primary key, reservation foreign key, fingerprint version and
lowercase-hex checks, outcome check, and explicit NULL-safe transient/success/
rejection row-shape check were exercised against PostgreSQL with valid and
invalid rows. The runtime can `SELECT`, `INSERT`, and `UPDATE` the table,
including a transient claim and terminal rejection. Privilege metadata and
denied statements prove no `DELETE`, `TRUNCATE`, `REFERENCES`, or `TRIGGER`;
runtime is not granted DDL or owner membership. V2 grants exactly
`SELECT, INSERT, UPDATE` to `vra_runtime`. A read-only diff against baseline
`117dabf` returned exit 0 for V1, proving the historical V1 file was not
rewritten. This is POC/Testcontainers migration evidence, not production
deployment history.

## HTTP and architecture boundary

The regular controller regression and real HTTP/PostgreSQL integration tests
passed. A legacy JSON `expectedVersion` field returns HTTP 400 with
`REQUEST_INVALID` and a server-controlled `request_id`, without invoking the
application service or mutating PostgreSQL. Unknown request fields are
rejected locally by `CreateReservationRequest`. Normal HTTP continues to use
the five-field request and server-generated request correlation; the
controller does not create a reservation ID.

There is no public `Idempotency-Key` or `actorScope` binding in the HTTP
adapter. The trusted synthetic scope is exercised only at the application
boundary. Public authenticated idempotency remains deferred to POC-04. A
read-only search of production inventory code found no `synchronized`,
`@Retryable`, `RetryTemplate`, Redis, `ConcurrentHashMap`, `ReentrantLock`,
`StampedLock`, `Thread.sleep`, or production mutex/retry shortcut. The
`CountDownLatch` and worker executor belong solely to the Gate 5 test harness.

## Built artifact and local CI status

Both fresh bootJars exist and are nonempty:

| Artifact | Bytes | SHA-256 | Structural smoke |
| --- | ---: | --- | --- |
| `backend/runtime/build/libs/runtime-0.1.0-SNAPSHOT.jar` | 55,021,720 | `a72c844cc392f84a96589ce6761d323cabc04404f3bdebee170c87626f9dbd13` | PASS: manifest and `Start-Class: dev.vra.VraApplication`; application class present; no packaged Flyway library |
| `backend/migration/build/libs/migration-0.1.0-SNAPSHOT.jar` | 5,167,945 | `8457ff346061fbba67df74359654815e9207f708b6c644869f5abb5e4e4929dd` | PASS: manifest and `Start-Class: dev.vra.migration.MigrationMain`; main class and V1/V2 migration resources present |

Built bootJar artifacts: **PASS**. Structural artifact smoke: **PASS**.
No POC-02 built-artifact live HTTP runtime smoke was executed. No persistent
POC-01 database was used or modified.

| Check | Status |
| --- | --- |
| Local CI-equivalent regression/build | PASS |
| Runtime PostgreSQL integration suite | PASS |
| Migration PostgreSQL integration suite | PASS |
| Architecture rules | PASS |
| GitHub-hosted backend CI | NOT EXECUTED |
| Branch-protection required-check observation | NOT EXECUTED |
| Authenticated public idempotency | NOT EXECUTED — deferred to POC-04 |
| Persistent production database setup | NOT APPLICABLE |
| Production capacity proof | NOT APPLICABLE |

The local command mirrors the current workflow's core build. It does not
establish a hosted CI or branch-protection result.

## Decisions supported and limits

The evidence supports PostgreSQL conditional stock arbitration without
caller-supplied `expectedVersion`; first-execution ownership of reservation
identity; original-identity replay; separate optimistic CAS; deterministic
fingerprint v1; PostgreSQL unique identity and `READ COMMITTED` fresh-statement
resolution; transactional terminal success/rejection; immutable same-key
replay or payload conflict; no key consumption before validation; rollback
without partial state; and runtime least privilege. The idempotency namespace
is the trusted synthetic actor scope plus key within the reservation operation.
Request ID does not participate in that identity. No hidden retry or
process-local/distributed lock is required by these proofs.

This representative Inventory Reservation slice does **not** directly prove
downstream Dispatch, Refund, or Payout correctness; each domain must validate
its own authoritative concurrency semantics. It also does not prove production
throughput, latency SLA, production pool sizing, a persistent production
database, pgAdmin setup, Redis, distributed locking, transactional outbox,
POC-03 recovery/worker semantics, POC-04 authentication/OIDC and trusted public
idempotency binding, POC-05 observability/performance baseline, HA,
backup/restore, or multi-region behavior. Idempotency retention/expiry remains
deferred.

The Gate 7 independent final review result is recorded below. Gate 8 determines
closure and any canonical ADR change; neither is recorded as executed here.

## Gate 7 independent final review

Review HEAD: `1a180dc8b42c18fa23a4a52df75ebe966194c23d`

Verdict: **PASS WITH NON-BLOCKING FINDINGS**

Blocking findings: **0**

Unresolved critical contradictions: **0**

The independent review recorded **PASS** for:

- Compliance with the frozen `SHARED_SPEC.md` and approved
  `IMPLEMENTATION_PLAN.md`, with no Dispatch, Refund, or Payout scope creep.
- The authoritative PostgreSQL atomic stock predicate; no caller
  `expectedVersion` in normal reservation; no caller/controller reservation ID;
  and reservation identity generated by authoritative first execution.
- PostgreSQL-backed same-key serialization and the transaction-local claim and
  terminal-result lifecycle. Terminal repository completion is guarded by
  matching `actor_scope`, `idempotency_key`, `fingerprint_version`, and
  `request_fingerprint`, plus `outcome_status IS NULL`.
- Stable controlled-business-rejection replay; pre-validation failures that
  consume no key; result-loss retry that preserves the original result; forced
  rollback with no partial committed state; and no committed incomplete
  normal-path state.
- Conflicting payloads binding one fingerprint; separate stock-arbitration and
  optimistic-CAS proofs; and actual `READ COMMITTED` evidence.
- Preserved runtime/migration ownership separation and unchanged historical
  V1 migration, with real PostgreSQL test execution.
- No hidden retry, JVM/process-local correctness lock, Redis or distributed
  lock correctness shortcut, or external network call in the critical
  inventory transaction.
- No public `Idempotency-Key` or `actorScope` HTTP binding; required automated
  suites with zero skips; evidence claims matching the fresh results; hosted
  and remote checks still marked **NOT EXECUTED**; and no false claim for
  deferred POC-03, POC-04, or POC-05 concerns.

### Non-blocking defense-in-depth finding

V2 grants table-level `SELECT, INSERT, UPDATE` on
`vra.inventory_reservation_idempotency` to `vra_runtime`. Normal application
correctness remains write-once: `JdbcReservationIdempotencyRepository`
completion SQL requires matching actor scope, key, fingerprint version, and
request fingerprint; requires `outcome_status IS NULL`; and requires exactly
one affected row. Repository integration tests prove that a second terminal
completion cannot rewrite the result. There is no generic terminal-rewrite
repository API, and invalid persisted semantics fail closed on replay.

The table-level `UPDATE` grant does not itself prevent arbitrary SQL executed
under a compromised or misused `vra_runtime` identity from rewriting columns
of an already-terminal row when the new values still satisfy the current
`CHECK` constraints. This is **non-blocking for POC-02**: the frozen plan
explicitly requires runtime `SELECT, INSERT, UPDATE`, and this POC validates
application transaction and idempotency correctness. Adversarial
runtime-identity and database least-privilege hardening belongs to POC-04.

**POC-04 follow-up:** Re-evaluate production hardening using evidence for one
or more of column-level `UPDATE` privileges, stronger database transition
enforcement, a narrower write surface, or another mechanism. POC-02 does not
select a mechanism.

Gate 7 does **not** close POC-02. Gate 8 must record closure decisions, decide
whether POC-02 materially requires a new or revised ADR, reconcile canonical
branch and project status, and mark POC-02 closed only after that closure
review. No ADR is created or revised here.
