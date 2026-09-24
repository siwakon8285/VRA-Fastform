# POC-00-C Controlled Evolution Specification

Status: frozen for this controlled evolution experiment. This document applies only to POC-00-C; it does not replace or amend `SHARED_SPEC.md` and does not rewrite Candidate A/B historical evidence. After this file is created, do not change its requirements during the experiment to make either implementation pass.

## Purpose and controls

Apply the same realistic requirement evolution to the already runtime-verified Java and Kotlin candidates. Collect factual evidence about compiler assistance, exhaustiveness, nullability/Optional behavior, domain-model evolution, typed failures, changed files, test impact, Spring/JDBC friction, migration impact, reviewability, and maintenance/change safety. This experiment does not select a language, rank candidates, or establish production readiness.

Keep JDK 21, Spring Boot 3.5.16, Gradle 8.14.3, PostgreSQL 17.11, Testcontainers 1.21.4, ArchUnit 1.4.1, Kotlin 2.4.20, SQL/JDBC persistence, existing API contract, and architecture constraints unchanged. Use the same source migration files for both candidates. Do not modify the frozen shared POC-00 specification, historical Java/Kotlin evidence, V1, or V2.

## E1 — Expired order state

Add `EXPIRED` to `OrderState` in both candidates. Add exactly one new transition: `PENDING_PAYMENT -> EXPIRED`. Preserve `CREATED -> PENDING_PAYMENT`, `PENDING_PAYMENT -> CONFIRMED`, and `PENDING_PAYMENT -> CANCELLED`. `CONFIRMED`, `CANCELLED`, and `EXPIRED` are terminal. No transition is allowed out of a terminal state. `EXPIRED` must not have `confirmedAt`.

For the E1 diagnostic, add only the enum entry first, leave exhaustive transition handling unchanged, and compile Java and Kotlin independently. Record exact commands, affected locations, and compiler diagnostics. Then repair the transition handling correctly; do not add a default/catch-all branch solely to suppress exhaustiveness diagnostics.

## E2 — Typed order reason code

Add `OrderReasonCode` with exactly these values:

- `CUSTOMER_CANCELLED`
- `PAYMENT_FAILED`
- `PAYMENT_TIMEOUT`

Add a required constructor/component parameter for the new optional domain value: Java uses `Optional<OrderReasonCode>`; Kotlin uses `OrderReasonCode?`. Do not give Kotlin a default argument. Before updating all call sites, compile each candidate separately and record success/failure, compiler-reported call sites, and diagnostics. Repair legitimate call sites only after recording the diagnostic.

The domain invariants are:

| State | `reasonCode` rule |
| --- | --- |
| `CREATED` | absent/null |
| `PENDING_PAYMENT` | absent/null |
| `CONFIRMED` | absent/null |
| `CANCELLED` | absent/null for legacy data, or `CUSTOMER_CANCELLED` / `PAYMENT_FAILED` |
| `EXPIRED` | required and exactly `PAYMENT_TIMEOUT` |

Preserve `confirmedAt` iff state is `CONFIRMED`; it cannot precede `createdAt`. `EXPIRED` has no `confirmedAt`. Free-text `cancellationReason` remains optional for legacy `CANCELLED`, and when present must be nonblank, no more than 500 characters, and only valid for `CANCELLED`. It is never valid for `EXPIRED`.

`create` yields `CREATED` with no optional state. `pendingPayment` yields `PENDING_PAYMENT` with no optional state. `confirm` yields `CONFIRMED`, sets `confirmedAt`, and has no cancellation/reason code. Change new cancellation operations to require both a free-text reason and typed reason code: Java `cancel(String reason, OrderReasonCode reasonCode)`; Kotlin `cancel(reason: String, reasonCode: OrderReasonCode)`. Legacy domain construction may still represent a cancelled order with no reason code. Add `expire()` for `PENDING_PAYMENT -> EXPIRED`, with `PAYMENT_TIMEOUT`, no confirmation time, and no cancellation reason. Do not read the current clock from a domain object.

## E3 — Invalid nil SKU identity

`SkuId` remains UUID-backed and retains its UUID database/wire representation. The all-zero UUID `00000000-0000-0000-0000-000000000000` is invalid. Constructing this value throws typed expected domain failure `InvalidSkuId`, added to the existing sealed `DomainFailure` hierarchy. Its safe message is `Invalid SKU identifier.` A normal UUID remains valid.

For the E3 diagnostic, add `DomainFailure.InvalidSkuId` before changing HTTP `ApiErrors`, compile each candidate independently, and record whether exhaustive sealed-type handling fails, with exact commands, locations, and diagnostics. Then add explicit HTTP mapping; do not use a default/catch-all branch to bypass exhaustiveness.

The request shape is unchanged. `POST /poc/reservations` with the nil UUID returns HTTP 400, code `request.invalid_sku_id`, safe message `Invalid SKU identifier.`, and a request ID in the body matching `X-Request-Id`. It causes no inventory mutation. Malformed non-UUID input keeps the existing safe `request.invalid` behavior.

## Shared additive PostgreSQL V3 migration

Add exactly one migration, `shared/db/migration/V3__add_order_expiry_and_reason_code.sql`. Do not edit V1 or V2. V3 is additive with respect to existing rows and adds nullable `orders.reason_code varchar(32)` for legacy compatibility. Extend the state constraint to allow `EXPIRED`. Add explicit named constraints for newly introduced/replaced checks and preserve the existing `cancellation_reason` and `confirmed_at` invariants.

Database reason-code validation must enforce the same state matrix above. In particular, `EXPIRED` requires a non-NULL value exactly equal to `PAYMENT_TIMEOUT`; account for PostgreSQL CHECK three-valued logic so NULL cannot satisfy this requirement. `CANCELLED` permits NULL, `CUSTOMER_CANCELLED`, or `PAYMENT_FAILED`, but not `PAYMENT_TIMEOUT`. The other states permit only NULL. Existing cancellation-reason rules continue to make a cancellation reason invalid for `EXPIRED`; the existing confirmed-at rule continues to reject an `EXPIRED` timestamp. Do not drop/recreate tables or destroy existing rows.

## Migration verification

Each candidate uses real disposable PostgreSQL 17.11 Testcontainers and the same shared migration files. For V3, apply V1+V2, populate legacy `CREATED`, `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED` with no reason code (column absent), and legacy `CANCELLED` with a cancellation reason. Apply V3 and verify:

- prior data remains, schema is version 3, and `reason_code` exists;
- legacy cancellation rows with NULL reason code and legacy free-text cancellation reason remain valid;
- `EXPIRED` + `PAYMENT_TIMEOUT` is accepted;
- `EXPIRED` + NULL reason, `EXPIRED` + `CUSTOMER_CANCELLED`, `CANCELLED` + `PAYMENT_TIMEOUT`, and non-terminal state + non-NULL reason are rejected;
- `EXPIRED` + `confirmed_at` and `EXPIRED` + `cancellation_reason` are rejected.

Do not use an in-memory DB, mocks, or the persistent Compose database for automated integration tests. Automated cleanup may only target disposable Testcontainers.

## Required domain and HTTP coverage

Keep all original tests and add equivalent coverage in both languages for:

- `PENDING_PAYMENT -> EXPIRED`; rejection of `EXPIRED -> CONFIRMED`, `CANCELLED`, and `PENDING_PAYMENT`;
- expiration sets `PAYMENT_TIMEOUT` and has neither `confirmedAt` nor `cancellationReason`;
- `CANCELLED` accepts `CUSTOMER_CANCELLED` and `PAYMENT_FAILED`, rejects `PAYMENT_TIMEOUT`, and legacy cancellation without a reason code remains constructible;
- non-terminal states and `CONFIRMED` reject reason codes; `EXPIRED` requires the exact `PAYMENT_TIMEOUT` code;
- nil `SkuId` throws `InvalidSkuId`; normal UUID-backed SKU remains valid.

Both candidates test nil UUID HTTP rejection: status 400, code `request.invalid_sku_id`, safe message, body/header request-ID match, no SQL/stack/path/secret leak, and no inventory mutation. Preserve all existing success, invalid quantity/JSON, unknown SKU, insufficient stock, health, and safe fallback HTTP scenarios.

## Bruno

Keep the existing four Bruno requests unchanged. Add only `invalid-sku-id.bru` using the nil UUID and asserting HTTP 400, `request.invalid_sku_id`, and request-ID correlation. No secrets. The collection has five requests.

## Architecture and transaction controls

Preserve both architecture tests and inward dependencies: no domain-to-interface/infrastructure/JDBC/Web edge, no application-to-infrastructure edge, and no interface-to-infrastructure edge. Do not change reservation SQL except the necessary typed SKU validation at the application boundary. It remains the conditional atomic PostgreSQL `UPDATE ... RETURNING`; add no retries, locks, or concurrency architecture. POC-02 retains contention evaluation.

## Verification and evidence

Run Java and Kotlin production/test compilation, each candidate's regular and PostgreSQL integration suites, a clean multi-project build, and Java regression after changes. No tests may be weakened, skipped, or disabled. When available, validate Compose, explicitly migrate the dedicated persistent POC DB (never clean/reset it), manually smoke each backend one at a time, run all five Bruno requests against both, and inspect authoritative DB invariants. Record unavailable work as NOT EXECUTED.

After final implementation passes, measure Java-specific and Kotlin-specific `git diff --stat` and `git diff --numstat` separately from shared files. Line counts describe the change surface only; fewer lines do not imply a better result. Record diagnostics and observations factually. Do not declare a winner, rank languages, or create a language ADR.
