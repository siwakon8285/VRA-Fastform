# POC-00 frozen shared specification

Status: Candidate A baseline, frozen for the Java and future Kotlin experiment.
This is architecture validation, not production feature development. Once Candidate A starts,
this specification MUST NOT change merely to make either language look better. Any necessary
correction requires an explicit recorded amendment, rationale, and equivalent revalidation
of both candidates; do not silently change the comparison baseline.

## Question and controls

Is modern Java suitable for VRA's future Spring/JVM Modular Transactional Core?
The only intended major experimental variable is **Java vs Kotlin**.

Both candidates must use the same:

- JDK **21 LTS**, without preview/incubator flags. Java 25 was unavailable at initial inspection;
  Java 21 is the selected fallback requirement, not a claim that it was executed locally.
  Record vendor/patch when execution becomes possible and use that same distribution for comparison.
- Spring Boot **3.5.16**, Gradle **8.14.3** (official wrapper), PostgreSQL **17**, image **postgres:17.11**.
- Shared SQL migrations, seed semantics, JDBC-oriented persistence, API contract and scenarios below.
- Transaction boundaries, architecture constraints, error mapping, and business invariants.
- PostgreSQL Testcontainers **1.21.4**, ArchUnit **1.4.1**, equivalent assertions and test isolation.

Dependency baseline: Spring Boot BOM; no Lombok, ORM, jOOQ, H2, SQLite or substitute database.
Gradle Kotlin DSL is build configuration only; it is not a Kotlin candidate.

Evaluate correctness expression, type safety, null/optional handling, immutability, state modeling,
Spring integration, PostgreSQL interaction, testing ergonomics, build/tooling, debugging clarity,
architecture clarity, AI-generated and human reviewability, and small-change maintainability.
No language winner, production capacity, final persistence policy, production authentication,
payment architecture, or final inventory concurrency strategy is decided here.

## Architecture and ownership

Direction: Modular Transactional Core + Explicit Domain Ownership + PostgreSQL authoritative OLTP
+ transactional outbox at future asynchronous boundaries + derived search/tracking/analytics planes
+ evidence-driven extraction. Domain boundaries are not deployment boundaries. This slice has no
asynchronous boundary and adds no outbox worker or distributed infrastructure.

- Domain depends only on language/JDK types, never HTTP, Spring, application, or persistence.
- Application owns the explicit Spring transaction and repository port.
- JDBC infrastructure implements the port; interfaces map requests, responses and errors inward.
- No inner layer depends on interfaces; interfaces do not depend on persistence implementation.
- No external network calls in a business transaction. PostgreSQL is authoritative.
- No Redis, Kafka, queues, search, Kubernetes, microservices, auth, payment, or frontend.
- Server-authoritative state transitions and multiple integrity defenses take priority over throughput.
- No custom crypto, production secrets, or real customer data. POC code is not automatically production code.

## Representative domain

- Money: required exact decimal amount and currency; non-negative commerce price/value, not signed
  ledger money. Numeric equality ignores decimal scale. Same-currency addition is exact;
  mismatch rejects with CurrencyMismatch. No float/double or implicit rounding.
- OrderId and SkuId: immutable, required UUID identities.
- IdempotencyKey: immutable string, non-null/non-blank, at most 128 characters. No full idempotency subsystem.
- InventoryBalance: immutable skuId, onHand, reserved, version; non-negative counters/version;
  reserved <= onHand; available = onHand - reserved. Positive integer reservation quantity only.
- InventoryReservation: immutable UUID identity, SKU, positive quantity, explicit ACTIVE/RELEASED state.
  Representative model only; this slice updates inventory counters, not a persistent reservation ledger.
- OrderItem: immutable SKU, non-blank product name snapshot, positive quantity, immutable unit Money.
- Order: immutable identity, state, creation instant, optional confirmation instant/reason, copied immutable
  nonempty item list, non-negative version. Confirmation instant exists iff CONFIRMED and cannot precede
  creation. Cancellation reason, when present, is non-blank, <=500 characters and only for CANCELLED.
  Legacy V1 cancelled orders may have no reason. New cancellation command requires a reason.
- States: CREATED -> PENDING_PAYMENT; PENDING_PAYMENT -> CONFIRMED or CANCELLED. All other transitions
  reject, especially CANCELLED -> CONFIRMED. No EXPIRED or later evolution changes yet.
- Typed expected failures: InsufficientStock, InvalidQuantity, InvalidTransition, InvalidIdempotencyKey,
  CurrencyMismatch, UnknownSku. Application propagates these to explicit API translation.

## HTTP contract

Backend binds 127.0.0.1:8080 by default, runs manually from a terminal, no authentication.

`POST /poc/reservations`, JSON: `{"skuId":"<uuid>","quantity":2}`.
Required UUID and positive signed 32-bit integer quantity; reject missing/null/malformed inputs,
unknown fields and fractional quantities. A successful request returns **200**:
`{"skuId":"<uuid>","quantity":2,"onHand":10,"reserved":2,"available":8}`.
These are post-update values from the atomic operation, not separately sampled counters.
Each accepted call reserves again; retries are not idempotent in this slice.

All MVC error responses: `{"code":"...","status":400,"requestId":"<server UUID>","message":"safe text"}`.

| Condition | Status | Code |
| --- | --- | --- |
| Invalid input | 400 | request.invalid |
| Unknown SKU | 404 | inventory.unknown_sku |
| Insufficient stock | 409 | inventory.insufficient_stock |
| Unexpected exception | 500 | internal.error |

Other framework 4xx use request.invalid with the framework status. Messages are safe and static;
no stack traces, SQL, internal exception dumps, paths, or secrets. Every HTTP request gets a generated
X-Request-Id response header; errors carry the same ID; incoming IDs are not trusted.
Log correlation uses MDC and consistent structured logs, without request payloads or sensitive headers.
`GET /actuator/health` exposes basic health only. Micrometer HTTP observations provide instrumentation
sanity; no telemetry exporter, OpenTelemetry end-to-end claim, or observability stack is required.

## Persistence and local topology

Use one conditional PostgreSQL UPDATE with RETURNING, sufficient-available-stock predicate and database
CHECK constraints. This is representative coordination only; POC-02 owns the stock=1/500-buyer decision.
No JVM/global/distributed locks. Failed reservation does not change counters or version.

V1: inventory_balance (SKU PK, bigint counters/version, CHECK invariants), orders (UUID PK, bounded state,
timestamps/version, confirmation-state constraints), order_items (order FK + line PK, snapshot, positive
quantity, exact numeric price, currency). SQL numeric has no imposed scale: no silent decimal rounding.
Timestamps are UTC instants round-tripped at PostgreSQL microsecond precision.
V2: additive nullable cancellation_reason and nullable-compatible validation; preserve populated V1 data.
Use exactly shared/db/migration SQL for local migration and tests.

Flyway runs explicitly via migrateLocal. Application Flyway and SQL auto-init are disabled; no schema
auto-update. Production requires **migration identity != runtime identity**. Shared local login is only
POC convenience, not final privilege design. Test cleanup may clean only disposable Testcontainers.

Compose contains exactly PostgreSQL infrastructure, persistent named volume, healthcheck, image above,
127.0.0.1 binding, host port VRA_PG_PORT default 55432, container port 5432. Database/user vra_poc00.
Password comes from ignored .local/secrets/db_password via Compose file secret. Runtime
VRA_DB_PASSWORD has no default. Runtime/migration URL restricted to loopback and dedicated POC database.
Never touch an existing project/shared/remote database. No backend/frontend/pgAdmin Compose services.

## Required equivalent verification

Unit/domain: valid/negative/missing Money; exact same-currency addition; currency mismatch; zero/negative
quantity; valid/invalid inventory; available calculation; valid/invalid transitions; cancelled-confirm
rejection; invalid idempotency keys; immutable purchase snapshot; optional confirmation/reason semantics.

Real PostgreSQL Testcontainers: migrate latest; apply V1, populate inventory/order/item, apply V2 and verify
schema + preserved data + nullable reason; inventory insert/read; order insert/read; exact money/currency;
enum/state; timestamp; nullable reason; invalid inventory CHECK; invalid item quantity CHECK; transaction
rollback after a prior successful mutation. No mock database or silent Docker-unavailable skipping.

HTTP: success; zero/negative quantity; unknown SKU; insufficient stock status/code; safe internal errors
and matching request IDs. Include full HTTP -> application transaction -> real PostgreSQL integration.
Architecture: enforce inward dependencies, domain independent of web/controller/infrastructure.
Bruno: localhost health, reserve-success, invalid-quantity, insufficient-stock, no secrets.
Attempt clean build, each test category and Compose config; distinguish failures, passes and NOT EXECUTED.

Future evolution tasks (EXPIRED, SKU-boundary refactoring, reasonCode) are deferred. Strong SkuId is
already required by this base spec; record that fact when designing later equivalent evolution work.
Evidence must describe observations and limitations, never infer production readiness or a language winner.
