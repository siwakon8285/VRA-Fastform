# POC-03 — Outbox and Recovery — Shared Specification

**Status:** FROZEN — independent spec review PASSED
**Target future branch:** `poc/03-outbox-recovery`
**Repository baseline:** Closed POC-02; branch activation and implementation have not begun
**Representative authoritative domain:** Inventory Reservation
**Verification status:** NOT VERIFIED — this document specifies future proof obligations

---

## 1. Purpose

POC-03 is a bounded validation branch proving that VRA can durably coordinate
asynchronous work from a committed Inventory Reservation mutation through
delivery, retry, duplicate handling, worker crash recovery, reconciliation, and
derived projection recovery without losing durable business intent or silently
duplicating critical effects.

The authoritative occurrence is the first successful `ReserveInventory`
execution. The selected representative integration contract is:

```text
eventType = inventory.reservation.created
schemaVersion = 1
```

This contract was selected through independent spec review and manual
architecture review. The specification is FROZEN; its verification gates remain
NOT VERIFIED.

POC-03 proves durable intent and duplicate-safe effects under at-least-once
processing. It does not claim exactly-once delivery or exactly-once execution.
It must extend the accepted transactional foundation without creating fake
Payment, Refund, Dispatch, Order, Fulfillment, or Logistics production domains.

Spec freeze does not authorize implementation or branch activation. The next
required step is the post-freeze read-only repository audit. Implementation
planning follows only after that audit completes.

---

## 2. Decision Chain

Decisions must preserve the canonical order:

```text
Business Requirement
→ Invariant
→ Risk / Threat
→ Consistency
→ Availability
→ Performance
→ Operations
→ Architecture
→ Technology
→ Implementation
```

Durability, explicit uncertainty, and recoverability determine the mechanism.
A passing response count, a convenient queue library, or faster processing
cannot substitute for authoritative state and failure evidence.

---

## 3. Canonical Authority

This specification must preserve:

- `docs/PRODUCT.md`
- `docs/DESIGN.md`
- `docs/SECURITY.md`
- `docs/TESTING.md`
- `docs/OPERATIONS.md`
- `docs/ROADMAP.md`
- `docs/BRANCH_PLAN.md`
- `docs/adr/ADR-001-primary-jvm-language.md`
- `docs/adr/ADR-002-backend-production-foundation.md`

The closed baseline is supported by:

- `validation/poc-02/SHARED_SPEC.md`
- `validation/poc-02/IMPLEMENTATION_PLAN.md`
- `validation/poc-02/evidence/RESULTS.md`

Authority roles remain distinct:

```text
PRODUCT / DESIGN / SECURITY / TESTING / OPERATIONS
= canonical requirements and architecture/operational boundaries

accepted ADRs
= accepted architectural decisions and their rationale

ROADMAP
= strategic validation sequence

BRANCH_PLAN
= branch scope, dependencies, activation, and exit governance

this SHARED_SPEC
= frozen branch-specific requirements after independent spec review

POC-02 plan and evidence
= historical implementation choices and factual proof of the closed baseline
```

Relevant canonical support includes PRODUCT's ownership, recoverability, and
reconciliation requirements; DESIGN §§18–21, 44–48, 53–56, and 90–99; SECURITY's
workload least privilege and protected-audit separation; OPERATIONS §§45–51;
TESTING's real-PostgreSQL and failure-injection requirements; ROADMAP §9; and
BRANCH_PLAN §17's review/freeze/plan/activation sequence.

Historical handoff or audit notes cannot override canonical documents or
accepted ADRs. No accepted POC-01/02 decision is reopened without an actual
contradiction and explicit review. A contradiction requires STOP, not a silent
change of authority.

The manually accepted reconciliation-resume clarification is incorporated in
§§12, 18, and 19. It resolves the earlier missing delivery transition without
weakening the unresolved-UNKNOWN replay prohibition.

---

## 4. Accepted Foundation

Preserve:

- Java 21 and Spring Boot as the production backend direction.
- PostgreSQL as authoritative OLTP and transactional correctness authority.
- `backend/runtime` and `backend/migration` as the accepted module baseline.
- Explicit Flyway migration execution, separate from runtime startup; runtime
  does not own migration and does not acquire Flyway migration responsibility.
- `vra_owner`, `vra_migrator`, and `vra_runtime` separation; runtime is neither
  schema/object owner nor a route to owner privileges.
- JPA where aggregate-shaped persistence fits; explicit SQL/JdbcClient for
  correctness-critical state transitions where appropriate.
- Real PostgreSQL for locks, transactions, constraints, migrations, and ACLs.
- External/network calls outside correctness-critical database transactions.
- Domain → application → adapters/bootstrap dependency boundaries, with
  dependencies pointing inward; domain code remains independent of Spring,
  JDBC/JPA, and HTTP concerns.
- No JVM mutex, process-local dedup store, Redis, or distributed lock as
  correctness authority.

POC-02 semantics remain valid:

```text
InventoryKey = SKU + Owner + Location + Status
available = on_hand - reserved
on_hand >= 0
reserved >= 0
reserved <= on_hand
quantity > 0
```

Normal reservation is arbitrated by PostgreSQL's current stock predicate, not
caller `expectedVersion`. Authoritative first execution generates the
reservation identity. Optimistic compare-and-set remains a separate proof.

The idempotent path retains trusted actor scope plus operation ownership plus
opaque idempotency key, deterministic fingerprint semantics, transaction-local
claim, terminal success/rejection, same-result replay, conflicting-payload
rejection, and full rollback on infrastructure failure. Its accepted
`READ COMMITTED` model is not replaced by a blanket stronger isolation level.
POC-03 asynchronous claims do not convert synchronous idempotency into a
committed nonterminal lease protocol.

Request ID remains server-controlled correlation, not an idempotency key.
Public trusted `actorScope`/authenticated idempotency binding remains POC-04.
POC-03 introduces no fake authentication or trusted caller-supplied scope.

The POC-02 table-level idempotency UPDATE hardening finding remains its recorded
nonblocking POC-04 follow-up. It is not justification for broad UPDATE authority
on new POC-03 surfaces, nor proof of adversarial immutability of old rows.

---

## 5. Scope

### 5.1 In Scope

1. Atomic business mutation, reservation, applicable terminal idempotency
   outcome, immutable outbox intent, and initial delivery creation.
2. Exactly one logical delivery target per event instance in separate validation
   scenarios: Scenario A targets the reservation projection consumer; Scenario B
   targets a validation-only external-effect adapter. Both use the selected
   `inventory.reservation.created`, schemaVersion 1 contract, durable claims/leases,
   bounded batches, stale-claim fencing, and real process crash/restart recovery.
3. Durable bounded retry, explicit FAILED work, controlled replay and closure.
4. Atomic inbox deduplication and representative reservation projection effects.
5. Validation-only external uncertainty, independent durable simulator state,
   observation-based reconciliation, controlled resume, and explicit closure.
6. Projection rebuild from authoritative reservations.
7. Event contract/version/order semantics, separated physical authorities,
   least-privilege proof, and minimum operational visibility.
8. Fresh migration and actual V2 upgrade proof, regression, review, factual
   evidence, and explicit ADR materiality assessment before closure.

### 5.2 Out of Scope

POC-03 does not add or finalize:

- Production Payment, Refund/Payout, Order, Dispatch, Fulfillment, or Logistics
  domains; real payment-provider correctness certification.
- Kafka, RabbitMQ, NATS, Redis, distributed locks, a generic event-streaming
  platform, event bus framework, async-job/workflow engine, or subscription /
  multi-subscriber / fan-out infrastructure.
- Production routing infrastructure, subscription tables, consumer groups,
  multiple delivery rows per event, or composite production handlers combining
  projection mutation and an external effect for these proofs.
- Microservice extraction, localhost HTTP between internal modules, Kubernetes,
  service mesh, multi-region, or HA.
- Browser authentication/session/BFF, CSRF, MFA/passkeys, staff maker-checker,
  public trusted actor binding, or a production replay UI.
- A full protected audit platform or production secret-management/KMS selection.
- Production RPS/capacity certification, p95/p99/SLO values, k6/soak/spike capacity
  proof, or full POC-05 tracing/alert maturity.
- A permanent event store, event sourcing, infinite outbox retention, generalized
  global ordering, or generalized aggregate sequence machinery.
- Persistent `vra_dev`/pgAdmin rollout or production deployment.

Workload identities do not require service extraction or new Gradle modules.
One codebase/artifact family with distinct process modes/configuration and
credentials is allowed, provided authority boundaries remain explicit.

---

## 6. Representative Model and Source Support

The existing authoritative model is:

```text
InventoryKey(skuId, ownerId, locationId, stockStatus)
Reservation(reservationId, inventoryKey, quantity, createdAt)
```

The logical Reservation is implemented by `InventoryReservation`. Current
source support, inspected against the closed POC-02 baseline, is:

| Fact | Source support |
| --- | --- |
| Reservation identity, key, quantity, and occurrence-related timestamp exist | `backend/runtime/src/main/java/dev/vra/inventory/domain/InventoryReservation.java` |
| SKU, owner, location, stock status exist in the key | `backend/runtime/src/main/java/dev/vra/inventory/domain/InventoryKey.java` |
| All six payload business fields are persisted | `backend/runtime/src/main/java/dev/vra/inventory/adapter/out/persistence/ReservationEntity.java`, `JpaReservationRepository.java`, and V1's `vra.inventory_reservation` definition |
| Successful first execution generates identity and uses an injected Clock | `backend/runtime/src/main/java/dev/vra/inventory/application/ReservationExecution.java` |
| Application clock uses timezone-neutral instant semantics | `backend/runtime/src/main/java/dev/vra/platform/configuration/ApplicationConfiguration.java` supplies `Clock.systemUTC()` |
| First execution and replay have distinct paths | `ReservationApplicationService.java` and `IdempotentReservationApplicationService.java` under `backend/runtime/src/main/java/dev/vra/inventory/application/` |
| Authoritative stock predicate is existing behavior | `backend/runtime/src/main/java/dev/vra/inventory/adapter/out/persistence/JdbcInventoryBalanceRepository.java` |
| No conflicting production integration-event convention was found | Inspection/search of `backend/runtime/src/main` and `backend/migration/src/main`; no existing production outbox/event contract was found |

The V1 reservation record carries the business data needed to rebuild the
representative projection without reconstructing it from outbox history. This
is support for feasibility, not a claim that a rebuild implementation already
exists. Optional event provenance must not turn outbox retention into a
prerequisite for rebuilding those business fields.

No separate consumer requirement was found for duplicating `createdAt` in the
event payload. The payload therefore uses the six fields below, with occurrence
time in the envelope only.

---

## 7. Core Invariants

1. One successful authoritative reservation occurrence creates one durable
   immutable integration event and one initial logical delivery. Every event
   instance has exactly one logical target; projection and external-effect
   validation use separate scenarios, not fan-out of the same instance.
2. Business state, applicable terminal idempotency success, event, and initial
   delivery commit together or all roll back.
3. Rejections, malformed requests, conflicting idempotency reuse, and committed
   successful idempotent replay create no new event or delivery.
4. Committed event identity/content never changes across attempts or replay.
5. Durable work survives worker death. Expiry enables reclaim; only a committed
   new claim transfers ownership.
6. Current claim tokens guard all claim-owned transitions; worker ID alone is
   never ownership authority.
7. Duplicate effects are prevented by durable deduplication/operation identity,
   not by an assumption of exactly-once execution.
8. Unknown external outcome never becomes guessed failure or blind retry.
9. Current operational state and append-only processing history are separate.
10. At most one active unresolved reconciliation case exists per delivery.
11. Projection is derived; rebuild uses authoritative Reservation state.
12. Runtime, ordinary worker, reconciler, and migration authority remain separate.
13. SUCCEEDED and CLOSED delivery states never reactivate. FAILED stops automatic
    delivery; any continuation is an explicit guarded controlled operation.
14. Cross-state changes and their required history commit atomically.

---

## 8. Producer / Outbox Atomicity

Successful authoritative first execution must commit in one PostgreSQL
transaction:

```text
inventory mutation
+ reservation persistence
+ terminal idempotency SUCCEEDED outcome, when using the idempotent path
+ immutable outbox_event
+ initial outbox_delivery in READY
= one atomic commit
```

The successful first-execution behavior applies to both the existing ordinary
reservation path and the idempotent application path. The ordinary HTTP path
does not acquire an authenticated idempotency contract by implication.

An event or initial-delivery creation failure must roll back inventory,
reservation, and applicable idempotency state. A post-commit callback that
attempts to create durable intent is not equivalent to this guarantee.

No new event/delivery is emitted for:

- Malformed input or prevalidation failure.
- Controlled business rejection, including persisted terminal rejection on the
  idempotent path.
- Conflicting reuse of a bound idempotency identity.
- Successful replay of an already committed idempotent reservation.

Concurrent same-key success and lost-result replay must still resolve to the
original reservation and exactly one event/delivery. A rolled-back attempt
leaves no committed partial intent; a later valid first execution can succeed.

The command transaction must not synchronously write the derived projection
to keep it current, and must not invoke an external/network effect.

---

## 9. Event Contract, Version, and Ordering

### 9.1 Immutable Envelope

```text
eventId
eventType
schemaVersion
occurredAt
payload
```

`eventId` is a system-generated opaque identity, distinct from reservation ID,
request ID, idempotency key, delivery attempt identity, and claim token.
UUID v4 versus v7 is an IMPLEMENTATION-PLAN DECISION, not frozen here.

`eventType = inventory.reservation.created` is a stable integration contract
identifier, not a Java class or package name.

`schemaVersion = 1` versions the event contract. It is not aggregate version,
delivery attempt, reconciliation cycle, or ordering sequence.

`occurredAt` records when the authoritative business fact occurred, using the
existing injected application Clock and timezone-neutral instant semantics.
It is not claim time, retry time, publish time, or a timestamp regenerated by
each delivery attempt.

### 9.2 Payload v1

```text
reservationId
skuId
ownerId
locationId
stockStatus
quantity
```

Do not duplicate `createdAt` in the payload. Do not add `inventoryVersion`
solely to introduce ordering.

Exclude secrets, credentials, authorization/HTTP headers, session data,
`actorScope`, idempotency keys, arbitrary request metadata, and raw exception
content. Observability needs do not justify copying raw requests into events.

The same committed envelope and semantic payload remain unchanged on every
retry and controlled replay. A repair cannot silently rewrite old event
content to make a consumer accept it.

### 9.3 Consumer Compatibility and Order

Unsupported event type/schema version must fail visibly and safely. It must
not be silently acknowledged as processed, registered as a successful inbox
effect, or marked SUCCEEDED. Poison/non-retryable handling must stop endless
retry and retain the immutable intent and diagnostic reason.

No global event ordering is assumed. The representative reservation-created
event has no correctness dependency on global ordering. Reordered and duplicate
deliveries must not corrupt its projection. `inventory_balance.version` is not
automatically an event-stream sequence.

Future event evolution must review compatibility explicitly; this POC does not
introduce a generic ordering or schema-registry platform.

---

## 10. Physical Data Boundaries

These semantic/physical boundary names are required. Exact SQL columns,
types, indexes, and constraint mechanisms remain implementation-plan work.

| Boundary | Authority and invariants |
| --- | --- |
| `outbox_event` | Append-once immutable integration intent. Ordinary workers cannot rewrite committed content or DELETE it. Not a permanent event store. |
| `outbox_delivery` | Authoritative current delivery/work lifecycle. Exactly one delivery row and one logical delivery target per event instance in each separate Scenario A or B. No routing/subscription/fan-out machinery. |
| `outbox_delivery_history` | Append-only operational claims, attempts, transitions, failures, and controlled replays. Preserves prior evidence; not protected security/business audit. |
| `consumer_inbox` | Durable consumer identity plus event/message identity deduplication. Registration and consumer effect share one transaction. No universal FK to local `outbox_event` is frozen, because future messages may originate elsewhere. |
| `reservation_projection` | Derived representative read model. Never Inventory/Reservation truth. Written only by its consumer or controlled rebuild. |
| `reconciliation_case` | Current uncertainty/reconciliation lifecycle and durable per-item reconciliation checkpoint (§18.1). A delivery may have zero to many historical cases, but at most one active unresolved case at once. |
| `reconciliation_history` | Append-only claim, observation, transition, resume, and close evidence for reconciliation. Not a substitute for protected audit. |

Do not collapse these into one broadly mutable outbox table. Current rows are
not history stores. Histories must retain earlier attempts even when current
state advances or work is replayed.

An OPERATOR_REQUIRED case with unresolved UNKNOWN remains the matching active
unresolved case until controlled resume or explicit close. RESOLVED and CLOSED
cases are historical terminal cases. Later uncertainty creates a new case;
it does not reopen an old terminal case.

Validation-only simulator state must stay outside VRA production migrations
and schema. It must not appear as a fake production provider/payment domain.

Historical migrations remain immutable:

```text
V1__inventory_reservation_foundation.sql
V2__inventory_reservation_idempotency.sql
```

POC-03 adds forward migration(s) through the accepted migration process.

---

## 11. Delivery Claim, Lease, and Crash Recovery

The selected POC mechanism to validate is PostgreSQL durable claim/lease with bounded
batches, row locking, and `FOR UPDATE SKIP LOCKED`:

```text
short claim transaction
→ commit ownership token, lease, attempt increment, and claim history
→ consumer/external processing outside the claim transaction
→ short guarded finalize transaction
```

SKIP LOCKED is selected for this POC proof. It is not declared a permanent
architecture mandate without later evidence.

Each committed claim or reclaim receives a unique per-claim token. `workerId`
is diagnostic attribution only. Each committed claim/reclaim increments
`deliveryAttemptCount` exactly once. A rolled-back claim increments no durable
attempt count and grants no ownership.

Eligibility and ownership are different:

```text
C1 expires
→ delivery becomes eligible for reclaim
→ ownership has not yet transferred

C2 claim transaction commits a NEW token
→ C2 owns the current claim
→ C1 can no longer finalize
```

Every claim-owned success, retry, failure, or unknown transition must guard
the current claim token, its continuing authority, and valid source state.
Successful guarded relinquishment (§24.1) revokes that claim's processing and
finalization authority even before a new owner exists; token equality alone
cannot authorize a relinquished claim. After C2 reclaims, C1 cannot
mark success, retry, failure, or UNKNOWN, even if its late operation returns.
A stale transition must not append misleading successful-transition history
or partially change related state.

Duplicate execution can overlap around expiry. Claim fencing protects VRA
state; inbox deduplication and stable external operation identity protect
effects. Lease duration alone is not duplicate safety.

No heartbeat/lease renewal is added in the baseline. A finite processing
timeout is required, with `processingTimeout < leaseDuration`. A timed-out
external operation must not be assumed cancelled or known to have had no effect.

Crash scenarios must include committed claim before processing, consumer
commit before delivery completion, and external effect before local outcome
confirmation. An expired claim or absent local success record cannot prove
external non-execution. Recovery must preserve uncertainty and reconcile when
outcome is unknown; it must not use reclaim as permission for a blind effect.
The implementation plan must specify the recovery protocol and deterministic
failure boundaries that demonstrate this behavior.

### 11.1 Scenario-Specific Reclaim Behavior

For Scenario A, an expired PROCESSING claim may be reclaimed normally. The new
owner may redeliver to the projection consumer: inbox registration and projection
effect commit in one PostgreSQL transaction, and durable inbox deduplication
makes repeated processing safe.

For Scenario B, an interrupted PROCESSING attempt whose execution may have begun
requires reconciliation-first recovery. After successful reclaim, the new owner
MUST NOT invoke external execute first. It must conservatively treat the prior
attempt as potentially UNKNOWN and enter the existing atomic reconciliation path
(§19.1). The external authority must be observed through query-only reconciliation
before another effect can become eligible.

The baseline introduces no durable phase/sub-state marker proving that external
invocation definitely never began. Ambiguity immediately before/while issuing
the request, after send, after external commit with lost response, or before
recording UNKNOWN therefore requires reconciliation-first recovery. An
unnecessary observation query is preferable to a blind possible duplicate effect.
Lease expiry or relinquishment is never evidence that no external effect occurred.

Convergence follows the existing guarded reconciliation transactions:

- CONFIRMED_SUCCEEDED → delivery SUCCEEDED.
- CONFIRMED_NO_EFFECT → RETRY_WAIT only if retry is safe and budget remains;
  otherwise FAILED. Later normal processing may execute the same stable E1.
- INDETERMINATE → bounded reconciliation with UNKNOWN preserved.

`externalOperationId = eventId` remains unchanged. No replacement event or new
operation identity is created. No new delivery state is introduced. The exact
mechanism routing reclaimed Scenario B work into reconciliation is an
IMPLEMENTATION-PLAN DECISION; reconciliation-first semantics are selected by
this specification and cannot be replaced with execute-again-on-expiry behavior.

At least one critical proof must terminate an actual worker process and start
a fresh process against the durable state. An exception in the same process
does not prove crash/restart recovery.

---

## 12. Delivery State Machine

States:

```text
READY
PROCESSING
RETRY_WAIT
RECONCILIATION_REQUIRED
SUCCEEDED
FAILED
CLOSED
```

The following list is exhaustive for semantic state transitions:

| From | To | Required meaning/guard |
| --- | --- | --- |
| creation | READY | Producer creates initial delivery atomically with event/business success. |
| READY | PROCESSING | Eligible durable claim, new token, attempt increment. |
| RETRY_WAIT | PROCESSING | Durable retry time reached and automatic budget permits claim. |
| PROCESSING | PROCESSING | Expired or safely relinquished claim is durably reclaimed with a NEW token; increment attempt once. Scenario-specific recovery follows §11.1; Scenario B must reconcile first when prior execution may have begun. |
| PROCESSING | SUCCEEDED | Current delivery token; required processing is durably successful. |
| PROCESSING | RETRY_WAIT | Current token; classified safe retry, remaining budget, durable next eligibility. |
| PROCESSING | RECONCILIATION_REQUIRED | Current token; UNKNOWN entry and active PENDING case commit together. |
| PROCESSING | FAILED | Current token; durable failure classification; automatic processing stops. |
| RECONCILIATION_REQUIRED | SUCCEEDED | Guarded reconciliation confirms success; case resolution is atomic. |
| RECONCILIATION_REQUIRED | RETRY_WAIT | Reconciliation confirms no effect, retry is safe, and budget remains. |
| RECONCILIATION_REQUIRED | FAILED | No safe/budgeted retry, reconciliation exhaustion, or operator-required outcome, with matching case disposition. |
| FAILED | READY | Controlled replay only; no unresolved external UNKNOWN/reconciliation. |
| FAILED | RECONCILIATION_REQUIRED | Controlled reconciliation resume only, under §19.5. Never ordinary replay. |
| FAILED | CLOSED | Controlled explicit closure; unresolved case closure is atomic under §19.6. |

Forbidden examples include:

```text
READY → SUCCEEDED
PROCESSING → READY (generic shutdown/unclaim shortcut)
FAILED → PROCESSING
RECONCILIATION_REQUIRED → ordinary PROCESSING
SUCCEEDED → READY
CLOSED → any active state
FAILED + unresolved UNKNOWN → READY
```

Guarded lease relinquishment (§24.1) leaves delivery PROCESSING, revokes the
old claim's authority, and only advances eligibility for normal reclaim. It is
not a delivery-state transition, outcome classification, or ownership transfer.

There is no generic `setState()` or arbitrary-state PATCH mechanism. Permission
to perform one guarded operation does not authorize other transitions.

SUCCEEDED and CLOSED are absolute terminal delivery states. FAILED is terminal
for automatic delivery but permits the guarded controlled operations above.
RECONCILIATION_REQUIRED is unavailable to ordinary delivery workers; only the
reconciliation path may resolve it.

---

## 13. Time Model

Business/domain time and distributed coordination time are separate:

| Meaning | Authority |
| --- | --- |
| Event `occurredAt` | Existing injected application Clock; timezone-neutral instant |
| Claim and reclaim eligibility | PostgreSQL time |
| Lease expiry | PostgreSQL time |
| Retry eligibility | PostgreSQL time |
| Reconciliation eligibility and leases | PostgreSQL time |
| Operational processing/history timestamps | PostgreSQL time unless a different timestamp semantic is explicitly defined |

Workers must not compare persisted lease/eligibility timestamps to independent
JVM wall clocks as the correctness authority. Application clock skew must not
transfer ownership or make durable retry/reconciliation eligible early.

Retry and reconciliation policy compute a Duration. PostgreSQL time anchors
that duration into durable next-eligibility timestamps. Exact PostgreSQL time
function and numeric production timings are IMPLEMENTATION-PLAN DECISIONS.

The required no-heartbeat relation is:

```text
finite processingTimeout < leaseDuration
```

Deterministic tests must control eligibility/failure boundaries without long
real sleeps or using a JVM test Clock as a substitute for PostgreSQL lease
authority. Test-only coordination must not become production correctness state.

---

## 14. Retry, Failed Work, and Controlled Replay

### 14.1 Failure Semantics

| Classification | Required behavior |
| --- | --- |
| RETRYABLE_TRANSIENT | Bounded automatic retry only when repeating execution is known safe; durable backoff, jitter, and finite budget. |
| NON_RETRYABLE | No uncontrolled automatic retry; explicit durable reason and stopped work. |
| UNKNOWN_OUTCOME | No ordinary retry; enter RECONCILIATION_REQUIRED atomically with a case. |
| POISON | Explicit FAILED state and stable reason; no endless retry. |
| OPERATOR_REQUIRED | No autonomous continuation; controlled intervention needed. |

An exception is not automatically retryable. An external request timeout may
mean UNKNOWN_OUTCOME. Stable classifications/reasons must not embed raw
exceptions, SQL, credentials, or arbitrary provider responses.

Retry waiting is durable PostgreSQL state and time, not a correctness-critical
`Thread.sleep`, in-memory timer, or process-local scheduled task. Automatic
retry exhaustion transitions to FAILED. Polling can awaken work but cannot
replace durable eligibility or budget checks.

FAILED means automatic processing stopped and the work is visible for
investigation. Dead-letter is this durable semantic state; no broker is added
merely to obtain a DLQ.

### 14.2 Controlled Replay

```text
FAILED → READY
```

Replay requires explicit inspection, reason, safe-execution assessment, and a
guarded controlled operation. No blind batch replay is allowed.

Replay preserves the same immutable `eventId` and payload. It creates neither
a new integration event nor a new business occurrence. All previous claims,
attempts, failures, and replay history remain; lifetime counters/history never
reset. Replay authorization must not create an unbounded automatic retry loop.
Exact budget accounting for authorized replay remains implementation-plan work.

A FAILED item with unresolved external UNKNOWN/reconciliation cannot use replay
to bypass reconciliation. It may only resume reconciliation through §19.5 or
be explicitly closed through §19.6. SUCCEEDED and CLOSED cannot be replayed.

Controlled replay, controlled reconciliation resume, and projection rebuild
are different operations with different authority and effects.

---

## 15. Consumer Inbox and Duplicate Safety

### 15.1 Scenario A — Projection Proof

Scenario A uses the selected event contract with one target:

```text
inventory.reservation.created (schemaVersion = 1)
→ one logical delivery target
→ reservation projection consumer
```

This scenario proves inbox/dedup, duplicate delivery, the consumer crash window,
projection correctness, and rebuild behavior. Scenario B (§17) separately
proves external uncertainty using the same contract. A single event instance
is not required to fan out to both targets, and a single production handler is
not required to perform both projection mutation and an external effect.

For each individual scenario/event instance:

```text
one event → exactly one logical delivery target
```

There are no multiple delivery rows per event, production fan-out/routing,
subscription tables, consumer groups, or composite production multi-effect
handlers introduced by these scenarios. The exact test/bootstrap mechanism
selecting/running Scenario A versus Scenario B is an IMPLEMENTATION-PLAN DECISION.

### 15.2 Inbox Transaction Semantics

Scenario A's representative logical consumer:

```text
reservation projection consumer
```

Durable deduplication identity:

```text
consumerIdentity + eventId
```

The consumer transaction must atomically register inbox/dedup and apply the
projection mutation. Concurrent duplicates must converge through PostgreSQL
authority, not a process-local cache or an unguarded check-then-write sequence.

```text
consumer effect fails
→ inbox registration and projection effect both roll back

consumer transaction commits
→ worker crashes before delivery completion
→ event may be delivered again
→ inbox prevents another projection effect
→ delivery can converge safely
```

Validate supported type/version before accepting successful processing.
Unsupported content is not a successful inbox record. A duplicate must resolve
the durable prior processing result, not fabricate a new business effect.

Local inbox/effect atomicity does not extend a PostgreSQL transaction around
an external call. External duplicate safety uses the separate stable operation
identity and reconciliation contract in §17.

No universal foreign key from inbox identity to a local outbox event is frozen.
The logical contract must remain usable for future externally originated
messages without inventing those integrations in this POC.

---

## 16. Projection and Rebuild

Projection and rebuild are Scenario A proof obligations. They are not additional
effects that Scenario B's external-effect handler must perform.

`reservation_projection` is a derived, non-authoritative representative read
model. Its business content may include:

```text
reservationId
skuId
ownerId
locationId
stockStatus
quantity
sourceEventId (optional provenance)
```

Inventory/Reservation decisions must never depend on the projection being
current. Only the projection consumer or a controlled rebuild writes it.
The authoritative Reservation command path does not synchronously maintain it.

### 16.1 Freshness and Representative v1 Ordering

`reservation_projection` is eventually consistent with authoritative Reservation
state; it is not read-after-write authoritative state. Business decisions never
require it to be fresh. POC-03 selects no numeric production freshness SLO.
For this bounded POC, freshness is observable through durable delivery/backlog
state and oldest outstanding-work age. Pending, retrying, failed, or reconciling
delivery can leave derived state lagging; that lag must remain visible. Scenario B
itself does not populate the projection or promise projection freshness.

Representative v1 contains only `inventory.reservation.created` for an immutable
reservation creation occurrence. There is no reservation-update integration event.
Therefore:

- `sourceEventId` is dedup/provenance identity, not an ordering sequence.
- `occurredAt` is occurrence evidence from the injected application Clock, not
  last-write-wins authority.
- `inventory_balance.version` is not projection ordering.
- No generic aggregate sequence or cross-reservation ordering is required.
- Out-of-order delivery across independent reservations must not corrupt the
  projection; same-event duplicates remain governed by inbox deduplication.

Future streams with multiple mutations of the same logical entity must explicitly
define narrow version/sequence/ordering semantics before adoption. Timestamp-based
last-write-wins behavior must not be generalized from this creation-only contract.

### 16.2 Rebuild and Projection Proof Obligations

Rebuild must derive the representative business state from authoritative
reservation records, not from permanent outbox history. The existing records
contain the six required business fields. Optional `sourceEventId` provenance
must not be a required input for business-state reconstruction when it is
unavailable from authoritative reservations.

Rebuild and delivery replay are distinct: rebuild repairs derived data, while
replay continues delivery of an existing immutable event. Neither creates new
inventory reservations or re-executes business mutation.

The implementation plan must define how rebuild and resumed consumer processing
converge without stale inbox state suppressing necessary repair or duplicate
delivery corrupting the rebuilt view. Exact physical rebuild algorithm,
coordination, and provenance representation are IMPLEMENTATION-PLAN DECISIONS.
They must not force ordinary worker DELETE, TRUNCATE, or broad maintenance
authority. General online rebuild/zero-downtime behavior is not claimed here.

Outbox is durable delivery infrastructure, not the permanent event store.
No infinite-retention assumption is allowed to make recovery work.

G5/G8 must cover normal source reservation creation, delayed and duplicate delivery,
out-of-order delivery across independent reservations, stale/missing projection
state, authoritative rebuild, and rebuild followed by resumed/redelivered consumer
processing. Final derived business state must match authoritative Reservations.
The canonical generic projection-update test is NOT APPLICABLE to representative
v1: no reservation-update event exists. Do not add an update event to satisfy that
generic test category.

---

## 17. External Uncertainty and Validation Simulator

### 17.1 Scenario B — External Uncertainty Proof

Scenario B uses the same selected event contract as Scenario A with its own
single logical target:

```text
inventory.reservation.created (schemaVersion = 1)
→ one logical delivery target
→ validation-only external-effect adapter
```

This separate scenario proves stable external operation identity, response
loss, UNKNOWN, reconciliation, and the defined at-most-one/exactly-one simulator
effect semantics. It does not require projection mutation by that handler or
delivery of the same event instance to both targets. Every event instance still
has exactly one delivery row and one logical target. Scenario selection is a
test/bootstrap implementation-plan decision, not production routing,
subscriptions, consumer groups, fan-out, or composite multi-effect handling.

### 17.2 Simulator and Uncertainty Contract

External validation uses a validation-only simulator outside VRA PostgreSQL
transaction authority. It must survive independently of the worker process
being terminated and durably prove external effect state. Its state is not
stored as a fake provider domain in VRA production schema/migrations.

The external proof exercises the required delivery/reconciliation semantics in
Scenario B. The implementation plan defines its bounded validation harness
without inventing a production external business domain.

For this simulator:

```text
externalOperationId = eventId

same operation identity + same semantic payload
→ at most one durable external effect
→ existing result is returned/revealed

same operation identity + conflicting payload
→ explicit conflict
→ no second effect
```

Claim token, worker identity, retry attempt, or reconciliation cycle must not
replace this stable operation identity. Reclaimed Scenario B attempts follow
§11.1: possible prior execution requires reconciliation-first observation, even
when the simulator would deduplicate another execute. Stable operation identity
does not authorize blind external re-execution after expiry or relinquishment.

Required critical scenario:

```text
worker sends E1
→ simulator durably succeeds
→ response deliberately lost
→ VRA cannot prove outcome
→ UNKNOWN / RECONCILIATION_REQUIRED
→ ordinary delivery does not blindly send a replacement effect
→ reconciler queries external authority
→ authority confirms SUCCEEDED
→ VRA converges to SUCCEEDED
→ authoritative simulator effect count for E1 is exactly one
```

Processing workflow and external outcome knowledge are different dimensions.
External knowledge distinguishes at least:

```text
UNKNOWN
CONFIRMED_SUCCEEDED
CONFIRMED_NO_EFFECT
```

Reconciliation observation results distinguish:

```text
CONFIRMED_SUCCEEDED
CONFIRMED_NO_EFFECT
INDETERMINATE
```

Reconciliation queries are read/observation operations and must not create an
external effect. A raw provider NOT_FOUND must not automatically mean confirmed
no effect. The simulator contract may explicitly provide authoritative strongly
consistent absence as CONFIRMED_NO_EFFECT to test a safe retry path; that
guarantee must be stated and proved, not generalized to real providers.

INDETERMINATE preserves UNKNOWN and permits only bounded reconciliation
scheduling. Exhaustion preserves UNKNOWN and requires operator intervention;
lookup failure/budget exhaustion does not prove external failure.

CONFIRMED_NO_EFFECT can permit RETRY_WAIT only when retry is proven safe and
delivery budget remains. The same `eventId`/external operation identity is used.

---

## 18. Reconciliation State Machine and Budget

Case states:

```text
PENDING
CHECKING
WAITING
RESOLVED
OPERATOR_REQUIRED
CLOSED
```

Allowed semantic transitions are exhaustive:

| From | To | Required meaning/guard |
| --- | --- | --- |
| creation | PENDING | Atomic UNKNOWN entry with matching delivery in RECONCILIATION_REQUIRED. |
| PENDING | CHECKING | Eligible reconciliation claim with new reconciliation token. |
| WAITING | CHECKING | PostgreSQL eligibility reached and cycle budget permits query. |
| CHECKING | CHECKING | Expired reconciliation claim reclaimed with NEW token. |
| CHECKING | WAITING | Current token; INDETERMINATE/UNKNOWN, bounded schedule and remaining cycle budget. |
| CHECKING | RESOLVED | Current token; confirmed observation and atomic matching delivery transition. |
| CHECKING | OPERATOR_REQUIRED | Current token; automatic continuation stops, matching delivery becomes FAILED. |
| OPERATOR_REQUIRED | PENDING | Controlled reconciliation resume, atomic with FAILED → RECONCILIATION_REQUIRED. |
| OPERATOR_REQUIRED | CLOSED | Controlled explicit close, atomic with FAILED → CLOSED for unresolved UNKNOWN. |

RESOLVED and CLOSED are absolute terminal states for that case. A later
uncertain execution episode creates a new case. It must not reopen a RESOLVED
case or coexist with another active unresolved case for the delivery.

Forbidden examples include PENDING → RESOLVED without a claim, WAITING → RESOLVED
without a claim, reopening RESOLVED/CLOSED, and stale CHECKING-token completion
after another claim has taken ownership.

Reconciliation has its own claim token, distinct from the delivery token, and
its own monotonically increasing attempt count. Each committed claim/reclaim
increments reconciliation attempts once. Querying occurs outside the critical
claim transaction; finalization guards current case ownership and matching
delivery/case state. A stale reconciler cannot overwrite a newer observation
or its delivery result.

Separate two accounting concepts:

```text
lifetime reconciliation attempt count/history
≠ bounded automatic reconciliation budget for one cycle
```

Lifetime count/history never resets. Controlled OPERATOR_REQUIRED → PENDING
resume begins a new bounded automatic reconciliation cycle on the same case.
It preserves prior attempts and observations. Exact cycle/generation physical
representation and numeric budget are IMPLEMENTATION-PLAN DECISIONS; no exact
schema fields are selected here.

Boundedness includes crash/reclaim behavior; repeated worker failures must not
silently grant infinite automatic query attempts. Reconciliation timing follows
the PostgreSQL coordination authority in §13.

---

### 18.1 Durable Per-Item Reconciliation Checkpoint

The durable `reconciliation_case` is the per-item reconciliation checkpoint.
Its durable semantics include current case state, next eligibility when
applicable, current ownership while CHECKING, lifetime reconciliation
attempt/history, and the current bounded automatic reconciliation cycle.
Related append-only history remains in `reconciliation_history`; checkpoint
semantics do not collapse that history into the current-state row.

A reconciler restart must rediscover eligible durable cases from PostgreSQL,
including normal lease-expiry/reclaim handling for interrupted CHECKING work.
A process-local cursor, scheduler offset, in-memory queue position, or local
timer is not the correctness checkpoint. OPERATOR_REQUIRED unresolved cases
are the durable failed-item/operator backlog for this bounded POC.

A scan cursor or pagination checkpoint may later optimize discovery. Its loss
or reset must not lose cases, and it must never be required to avoid losing
reconciliation work. Exact scan/pagination implementation is an
IMPLEMENTATION-PLAN DECISION. Processing remains idempotent and observable
through guarded durable transitions, outcome observations, and retained history.

---

## 19. Atomic Cross-State Operations

All operations below are guarded PostgreSQL transactions. Required current-state
changes and delivery/reconciliation history append together or all roll back.
No external/network call occurs inside these transactions. A failed ownership
guard or failed required history write cannot leave a partial transition.

### 19.1 UNKNOWN Entry

```text
guard current delivery claim and PROCESSING
delivery: PROCESSING → RECONCILIATION_REQUIRED
create matching active case: PENDING / UNKNOWN
append required delivery and reconciliation history
COMMIT together
```

No committed RECONCILIATION_REQUIRED delivery may lack its exactly one matching
active unresolved case. Ordinary delivery ownership ends; reconciliation
claims use their own token.

### 19.2 Confirmed Success

```text
guard current reconciliation claim and matching delivery/case
case: CHECKING → RESOLVED
externalKnowledge: UNKNOWN → CONFIRMED_SUCCEEDED
delivery: RECONCILIATION_REQUIRED → SUCCEEDED
append observation evidence and required histories
COMMIT together
```

The resolved case remains historical evidence; delivery has no active
reconciliation case afterward.

### 19.3 Confirmed No Effect

```text
guard current reconciliation claim and matching delivery/case
case: CHECKING → RESOLVED
externalKnowledge: UNKNOWN → CONFIRMED_NO_EFFECT

if retry is proven safe and delivery budget remains:
    delivery: RECONCILIATION_REQUIRED → RETRY_WAIT
    persist PostgreSQL-anchored next eligibility and retry reason
otherwise:
    delivery: RECONCILIATION_REQUIRED → FAILED
    persist stable stopped-work reason

append observation evidence and required histories
COMMIT together
```

A later retry that becomes uncertain creates a new case, retaining the prior
confirmed-no-effect observation as history of the earlier execution episode.

### 19.4 Automatic Reconciliation Exhaustion

```text
guard current reconciliation claim and matching delivery/case
case: CHECKING → OPERATOR_REQUIRED
delivery: RECONCILIATION_REQUIRED → FAILED
externalKnowledge: UNKNOWN remains UNKNOWN
clear active claims and automatic eligibility
append exhaustion reason and required histories
COMMIT together
```

The matching case remains unresolved. Delivery FAILED describes VRA processing,
not authoritative external failure. No ordinary replay is permitted.

### 19.5 Controlled Reconciliation Resume

Preconditions must all hold:

- Delivery is FAILED due to unresolved reconciliation/operator intervention.
- Its matching active reconciliation case is OPERATOR_REQUIRED.
- External knowledge is UNKNOWN.
- No ordinary delivery claim is active.
- The controlled operation is authorized and records an explicit reason.

Atomic operation:

```text
delivery: FAILED → RECONCILIATION_REQUIRED
case: OPERATOR_REQUIRED → PENDING
externalKnowledge: UNKNOWN → UNKNOWN (unchanged)
begin a new bounded automatic reconciliation cycle
preserve lifetime reconciliation attempts and all history
append required delivery/reconciliation resume history
COMMIT together
```

This is not replay. It creates no integration event and executes no external
effect. It makes only reconciliation eligible; ordinary outbox processing
remains prohibited while delivery is RECONCILIATION_REQUIRED.

### 19.6 Controlled Close of Unresolved Reconciliation

Given delivery FAILED, matching active case OPERATOR_REQUIRED, and knowledge
UNKNOWN, a guarded controlled close must atomically perform:

```text
delivery: FAILED → CLOSED
case: OPERATOR_REQUIRED → CLOSED
externalKnowledge: UNKNOWN remains UNKNOWN
record explicit closure reason
append required delivery/reconciliation history
COMMIT together
```

CLOSED means VRA deliberately stops further processing. It does not mean the
external operation is known to have failed. No effect is executed and no new
event is created. Closing does not erase prior uncertainty or observations.

For FAILED work without an unresolved case, controlled delivery closure still
requires a reason and delivery history; it does not invent a reconciliation
case merely for closure.

Concurrent resume, replay, close, or late finalization must be serialized and
guarded so only a valid operation commits. Controls are operation-specific,
never arbitrary state editing or manual SQL as the normal recovery interface.

---

## 20. History and State/Reason Invariants

`outbox_delivery` is current operational truth; `outbox_delivery_history` is
historical processing evidence. `reconciliation_case` is current case truth;
`reconciliation_history` is historical claim/observation/transition evidence.

Histories must be sufficient to reconstruct which claim/attempt/case performed
an operation, its prior/resulting state, safe stable reason or observation,
operational time, and controlled-action context. Exact physical fields are
implementation-plan decisions. Do not overwrite old attempts during replay,
resume, or closure, or use raw exception dumps as reason semantics.

| Current state | Required semantic shape |
| --- | --- |
| READY | Valid initial or controlled-replay work; no active claim. |
| PROCESSING | Claim/attempt evidence and finite lease exist. Only a current non-relinquished claim authorizes processing/finalization; safe relinquishment preserves PROCESSING as reclaimable without an authorized old owner (§24.1). |
| RETRY_WAIT | Retry classification/reason and durable next eligibility; no active delivery claim. |
| RECONCILIATION_REQUIRED | Unknown-outcome reason, exactly one matching active unresolved case; no ordinary delivery claim/eligibility. |
| SUCCEEDED | No active claim, retry eligibility, or active reconciliation case; cannot be replayed. |
| FAILED | Stable failure classification/reason; no active delivery claim or automatic delivery eligibility; operator action required. An unresolved case is OPERATOR_REQUIRED. |
| CLOSED | Explicit closure reason; no active claim or automatic eligibility; any matching unresolved case is atomically CLOSED, knowledge preserved. |

Reconciliation CHECKING requires current reconciliation ownership. WAITING
requires a reason and durable next eligibility. OPERATOR_REQUIRED has no active
claim or automatic query eligibility. RESOLVED/CLOSED cases have no active claim
or automatic eligibility and never reactivate.

Terminal states carry no active claims or automatic retry eligibility.
Operational history is not protected security/business audit. POC-03 must not
claim that append-only processing history implements the full audit platform.

---

## 21. Workload Identity and Database Capability Matrix

The semantic capability matrix is a required frozen specification contract. Exact GRANT syntax,
table/column privileges, guarded-operation mechanism, and role provisioning
details are IMPLEMENTATION-PLAN DECISIONS. The implementation must prove the
effective capability boundary with real PostgreSQL allow/deny tests.

| Identity | Permitted authority | Prohibited authority |
| --- | --- | --- |
| `vra_owner` | Accepted schema/object ownership responsibility. | Ordinary application/worker credential use. |
| `vra_migrator` | Explicit migration process and controlled owner-role assumption under accepted POC-01 boundaries. | Ordinary runtime/worker operation using migrator privileges. |
| `vra_runtime` | Existing synchronous Inventory/Reservation/idempotency authority; append event and create initial READY delivery in producer transaction. | Claim/retry/complete/fail/replay/close/delete async work; fabricate arbitrary PROCESSING/SUCCEEDED/FAILED rows; schema ownership/DDL or owner escalation. |
| `vra_outbox_worker` | Read required immutable events; guarded normal delivery claims/transitions; append delivery history; required inbox/projection writes; atomic UNKNOWN handoff through a narrow operation. | Authoritative inventory/reservation/idempotency mutation; event rewrite; arbitrary reconciliation results; schema/DDL/GRANT/TRUNCATE; ordinary DELETE. |
| `vra_reconciliation_worker` | Read required event/case data; guarded reconciliation claims/observations/transitions and the narrowly coupled delivery updates required by §19. | Arbitrary normal-delivery claiming; unrestricted authoritative Inventory/Reservation writes; event rewrite; schema/DDL/GRANT/TRUNCATE; broad maintenance authority. |

The ordinary worker's UNKNOWN handoff is not unrestricted reconciliation-table
administration. The reconciler's ability to resolve a matching delivery is not
unrestricted UPDATE of delivery state or permission to perform a normal effect.

Producer INSERT authority must permit only the initial lifecycle shape; it
must not provide a route to fabricate claimed, successful, or failed work.
New write surfaces must use the narrowest practical PostgreSQL privilege
boundary. Do not repeat broad table-level UPDATE merely for convenience when
immutable and mutable authority can be separated.

Controlled replay/resume/close and rebuild require explicit, narrow authority
separate from automatic processing behavior. They do not add these powers to
`vra_runtime`, authorize arbitrary SQL, or justify ordinary worker maintenance
privileges. The implementation plan must specify how the bounded validation
operations are invoked and authorized within this capability matrix; it must
not claim production staff authentication/replay UI or introduce broad grants.

Privilege tests must prove effective denial, not merely that application code
usually refrains from a write. Include attempts to rewrite immutable event
content, mutate business state as a worker, bypass allowed initial state,
claim ordinary delivery as reconciler, and acquire DDL/owner privileges.
Required legitimate compound operations must also succeed under the intended
workload credentials, without silently switching to migrator/superuser access.

---

## 22. Threat and Failure-Mode Analysis

| Threat/failure | Required protection and observable proof |
| --- | --- |
| Business commits without work intent | One producer transaction; inject event and delivery creation failures and prove total rollback. |
| Duplicate caller or lost command result | Preserve POC-02 replay authority; one reservation/event/delivery for the occurrence. |
| Concurrent worker claim or stale finalizer | PostgreSQL locks/claims and token guards; only current owner changes state. |
| Worker killed after durable claim | Fresh process reclaims committed work without in-memory ownership. |
| Worker killed after consumer commit | Inbox suppresses duplicate projection effect on redelivery. |
| Timeout after durable external success | UNKNOWN and reconciliation; no guessed failure or blind replacement effect. |
| Worker death before recording external uncertainty | Recovery cannot infer no effect from missing local completion; stable identity and observation preserve safety. |
| Stale reconciliation observation | Separate reconciliation token guards atomic case/delivery finalization. |
| Poison content or unsupported version | Visible stopped work, stable reason, no silent acknowledgement/endless retry. |
| Retry storm or repeated indeterminate query | Durable backoff/jitter and finite automatic budgets; visible FAILED/operator-required state. |
| Reconciliation resume erases evidence | New bounded cycle, monotonic lifetime attempt/history; atomic paired resume. |
| Closure falsely asserts external failure | Preserve UNKNOWN in CLOSED case/delivery evidence. |
| Replay bypasses unresolved UNKNOWN | Guard FAILED → READY; permit only controlled reconciliation resume or close. |
| Clock skew or worker restart loses schedule | PostgreSQL time/state controls eligibility; process timers are not authority. |
| Misused/compromised worker credentials | Effective narrow DB capabilities; deny business writes, event rewrite, DDL, and escalation. |
| Secret leakage through event/history/metrics | Minimal business payload; stable safe classifications and sanitized operational context. |
| Projection loss or stale inbox after rebuild | Rebuild from reservations; prove consumer/rebuild convergence without outbox history dependence. |
| History overwritten by current-state update | Separate append-only histories; atomic append with transitions. |

These protections do not certify a real provider, full protected audit system,
production authentication, or all downstream business-domain invariants.

---

## 23. Persistence and Consistency Implications

PostgreSQL commits authoritative business state and outbox intent atomically.
Delivery state, inbox/projection effects, and reconciliation use separate short
transactions with explicit guards. Projection may be temporarily stale; that
does not change Reservation truth.

There is no distributed atomic transaction between VRA and the simulator.
Durable operation identity, external outcome evidence, and reconciliation bridge
that boundary. An HTTP response alone is insufficient evidence of final state.

Reconciliation persistence is also the recovery checkpoint: `reconciliation_case`
and its associated durable history/cycle semantics preserve per-item progress
across restarts (§18.1). PostgreSQL rediscovery must recover eligible work without
a process-local scan position or timer. Lost/reset optimization cursors cannot
discard durable cases; unresolved OPERATOR_REQUIRED items remain visible backlog.

The implementation plan must map semantic uniqueness, valid state/reason shapes,
one delivery per event, inbox uniqueness, immutable content, at-most-one active
case, and cross-state ownership guards to enforceable PostgreSQL behavior.
Exact CHECK/UNIQUE/FK/index and guarded-write implementations are not frozen.
They cannot rely only on well-behaved callers where the capability contract
requires effective DB denial.

No stronger global transaction isolation, distributed lock, or new service
boundary is selected to hide an unproven invariant. Any necessity for such a
change triggers review/STOP rather than silent scope expansion.

Forward migration proof must cover fresh installation and an actual database
at V2 with representative existing data, preserving V1/V2 checksums, ownership,
business/idempotency semantics, explicit migration execution, validation, and
safe rerun behavior. Existing reservations remain available as rebuild sources;
schema upgrade itself is not a new reservation occurrence.

Outbox/inbox/history retention policy must eventually preserve duplicate safety
and investigation/recovery requirements. Production durations and purge
mechanisms are deferred; this POC grants no ordinary deletion authority and
does not promise infinite retention.

---

## 24. Operational Implications and Minimum Visibility

Workload credentials/configuration must keep runtime, worker, reconciler, and
migrator boundaries explicit even if they share artifact code. Startup must
not migrate, acquire owner privilege, or silently accept invalid timeout/budget
configuration.

### 24.1 Graceful Shutdown, Drain, and Backpressure

Shutdown/drain must stop claiming new work first. Once drain begins, a worker
must not intentionally begin a new external effect for work that was not already
in-flight. Already in-flight bounded work may finish and finalize normally when
safe, using the existing claim/state guards.

The baseline has no generic PROCESSING → READY unclaim transition. Once the
worker has STOPPED processing an attempt and safe relinquishment can be established,
it should attempt guarded lease relinquishment. This operation must:

- Guard the current claim token and its continuing authority.
- Leave delivery PROCESSING; never mark SUCCEEDED/FAILED or assert whether an
  external effect happened.
- Only make existing PROCESSING work immediately/earlier eligible for normal
  reclaim, using PostgreSQL coordination time.
- Revoke the old worker's right to continue processing/finalizing that claim
  after successful relinquishment, including before another worker claims it.
- Transfer no ownership directly. A new owner requires the normal committed
  claim/reclaim operation with a NEW token and its normal attempt increment.

Relinquishment is an ownership/recovery optimization, not outcome classification.
It does not make UNKNOWN retry-safe. Scenario B remains reconciliation-first
under §11.1 after relinquishment/reclaim; no blind external execute is permitted.
No new delivery state is introduced. Exact SQL/update mechanism is an
IMPLEMENTATION-PLAN DECISION within narrow guarded worker authority, without
broad UPDATE/DELETE/TRUNCATE/DDL privileges.

If safe relinquishment cannot be established, or shutdown/crash occurs before
it commits, preserve durable current state and do not fabricate certainty.
Normal lease expiry/reclaim remains authoritative fallback. Expiry enables
reclaim but does not itself transfer ownership. Work that cannot safely finalize
must not be assigned fabricated success/failure or made immediately retry-safe.

Graceful shutdown is an operational optimization, not a correctness requirement.
Abrupt process death must remain safe. Shutdown must close DB connections/pools
and other resources appropriately and flush telemetry when appropriate, but no
shutdown hook may silently rewrite
uncertain external work into a retry-safe state. No new delivery transition is
introduced for shutdown convenience.

Claim batches, concurrency, and in-flight work must be bounded. No unbounded
in-memory work queue is permitted. Backpressure must use bounded concurrency,
queues, and limits; process memory must not become durable work storage.
Finite processing timeouts, PostgreSQL eligibility, and bounded automatic
retry/reconciliation remain required. Exact numeric limits, drain mechanics,
and bootstrap details are IMPLEMENTATION-PLAN DECISIONS within these semantics.

### 24.2 Reconciliation Restart and Backlog

The per-item checkpoint is the durable reconciliation case, including its
state, eligibility, CHECKING ownership, lifetime attempt/history, and bounded
cycle semantics (§18.1). On restart, the reconciler must rediscover eligible
cases from PostgreSQL. Local timers/queues/cursors cannot own recovery progress.
Loss/reset of an optional pagination cursor cannot lose durable work.
OPERATOR_REQUIRED unresolved cases remain the durable failed-item/operator
backlog. Reconciliation must remain idempotent and operationally observable.

### 24.3 Minimum Visibility

At minimum expose or make directly observable:

- Ready/pending work count.
- Processing count.
- Retry-wait count and retry activity.
- Failed work count.
- Reconciliation-required count and reconciliation backlog, including
  operator-required unresolved work.
- Oldest outstanding work age, with its included states/time basis documented.
- Processing success/failure activity.
- Attempt/retry/claim and reconciliation history sufficient for investigation.

Operational state must make stopped work and unknown outcomes visible rather
than hiding them behind a successful poll/health response. Safe stable reasons
and non-sensitive identifiers must support investigation without dumping events,
credentials, raw requests, or exceptions into public errors/metrics.

This is minimum POC visibility, not POC-05 completion. Production SLO, p95/p99,
capacity, complete tracing, alert maturity, and production retention remain
unproven/deferred. Exact metric exposition technology is not selected here.

---

## 25. Required Verification Gates

All gates below are mandatory before POC-03 closure. They describe future work;
none is marked executed or passed by spec freeze.

| Gate | Required proof and authoritative assertions |
| --- | --- |
| G0 — Migration / fresh DB / V2 upgrade / ownership & grants | Execute production migration path on fresh PostgreSQL and actual V2 baseline; preserve existing data and historical migration checksums; validate/rerun; prove ownership and runtime/worker inability to migrate or assume owner. |
| G1 — Producer atomicity | First success commits inventory, reservation, applicable idempotency terminal success, immutable event, and initial READY delivery together. Independently force event creation and delivery creation failures; prove all rollback with no orphan intent or partial business state. Cover ordinary and idempotent producer paths. |
| G2 — Event and POC-02 replay semantics | Same-key sequential/concurrent replay and lost-result retry preserve original reservation/event/delivery; conflict/rejection/prevalidation produce none. Verify exact type/version/payload, identity separation, stable occurredAt/content across replay, unsupported type/version visible failure, and no global-order dependency. Preserve POC-02 stock/idempotency behavior. |
| G3 — Claim concurrency / lease / stale ownership | Prove Scenario A safe redelivery and Scenario B reconciliation-first behavior after reclaim under §11.1. Independent PostgreSQL transactions/processes contend using SKIP LOCKED/bounded claims. Prove committed-token ownership, rollback behavior, exactly one count increment per claim/reclaim, PostgreSQL eligibility, expiry without automatic ownership transfer, and denial of all late C1 finalizations after C2 commits. Include stale reconciliation guards and invalid timeout configuration. |
| G4 — REAL worker process crash and fresh restart | Prove safe bounded work finalizes normally; guarded relinquishment after processing stops preserves PROCESSING and makes it reclaimable, revokes old-token finalization before and after a new claim, and grants ownership only via committed reclaim with a new token. Prove inability/failure to relinquish falls back to ordinary expiry. For Scenario B, C1 dies or relinquishes after execute may have begun; C2 reclaims, performs no execute first, and enters query-only reconciliation. Inspect authoritative simulator effect counts and durable convergence. Verify drain stops new claims and does not intentionally start new external effects for work not already in-flight; safe in-flight finalization remains guarded, with no generic unclaim or uncertainty rewrite. Prove bounded claims/concurrency/in-flight work and no unbounded memory queue under backpressure. Abrupt death remains recoverable independently of graceful shutdown. Terminate an actual worker PROCESS at a deterministic critical boundary after durable claim, start a fresh process, reclaim/converge from PostgreSQL state, and prove no lost work. Record old/new process identities, kill boundary, durable state before/after, and bounded completion. Same-process exception injection alone fails this gate. |
| G5 — Inbox duplicate safety | Cover normal source creation, delayed delivery with visible freshness lag, and out-of-order delivery across independent reservations as well as duplicates; verify final derived business state against authoritative Reservations and §16.1 identity/timestamp/order semantics. In Scenario A, prove one event instance has exactly one delivery row/target, the reservation projection consumer. Concurrent/sequential redelivery yields one projection effect; failed consumer effect rolls back inbox and projection; crash after committed consumer effect and before delivery completion leads to safe redelivery. Inspect durable inbox/projection/delivery state. |
| G6 — Bounded retry / FAILED / controlled replay | Exercise failure classes, durable backoff/jitter, restart during retry wait, finite exhaustion, visible failure reason/history, and guarded replay using same event without history/count reset. Deny replay of SUCCEEDED/CLOSED and unresolved UNKNOWN. No hidden retry or blind batch replay. |
| G7 — External uncertainty and reconciliation | Prove C1 PROCESSING execution may have begun, C1 dies/lease expires, and C2 reclaims without invoking execute first; query-only reconciliation precedes any further effect eligibility. Cover ambiguous pre-send/send, lost response after external commit, and crash before UNKNOWN persistence. Assert simulator effect counts and convergence to SUCCEEDED, safe budgeted RETRY_WAIT using the same E1 after confirmed no effect, or bounded UNKNOWN reconciliation for INDETERMINATE. Relinquishment/reclaim must follow the same rule. In separate Scenario B, prove one event instance has exactly one delivery row/target, the validation-only external-effect adapter, without a composite projection/external handler or fan-out. Restart the reconciler and rediscover eligible per-item checkpoints from PostgreSQL, including interrupted CHECKING claims; lose/reset any optional scan cursor and prove no cases are lost. Verify durable eligibility, current ownership, lifetime history, bounded cycle semantics, and visible OPERATOR_REQUIRED backlog; processing remains idempotent and observable. Independently durable simulator succeeds but response is lost; enter UNKNOWN, query only, converge, and prove exactly one effect for stable operation. Prove same-ID conflicting payload rejection, safe confirmed-no-effect path, indeterminate bounded exhaustion retaining UNKNOWN, stale reconciler denial, active-case uniqueness, and new case after later uncertainty. Prove controlled resume atomically restores RECONCILIATION_REQUIRED/PENDING, starts a new bounded cycle without resetting lifetime history, and can finish through normal reconciliation. Prove unresolved close atomically closes delivery/case while retaining UNKNOWN. Inject failures in compound transitions and history appends to prove rollback; race resume/close/replay safely. |
| G8 — Projection rebuild | Cover stale/missing projection, authoritative rebuild followed by resumed/redelivered consumer processing, and final derived business state matching authoritative Reservations. Generic projection-update testing is NOT APPLICABLE to creation-only v1; no update event is added (§16.2). In Scenario A, damage/remove derived state through controlled test/rebuild authority, reconstruct business fields from authoritative reservations without requiring outbox history, then prove correct consumer/dedup convergence. No business mutation and no ordinary worker maintenance privilege. |
| G9 — State machines / constraints | Exercise every allowed transition with its required guards and meaningful forbidden transitions/invalid row shapes. Prove terminal irreversibility, stable reasons, one active case, no RECONCILIATION_REQUIRED orphan, no arbitrary state API, and required cross-state atomicity. |
| G10 — Least-privilege allow/deny matrix | Execute legitimate operations under each POC workload identity and forbidden SQL/capability attempts against real PostgreSQL. Prove narrow producer initialization, immutable event/history protection, worker business-write denial, reconciler normal-claim denial, no ordinary DELETE/TRUNCATE/DDL/GRANT/escalation, and accepted owner/migrator separation. Metadata-only assertions are insufficient. |
| G11 — Operational visibility | Observe every §24 minimum signal across ready/processing/retry/success/failure/unknown/operator-required scenarios. Cross-check reported counts/age/activity against durable state/history and verify safe reasons without secret exposure. |
| G12 — Full regression / architecture / security / evidence | Architecture verification must cover new async boundaries under §25.3, not merely rerun old Inventory-only rules. Execute required regular, PostgreSQL integration, migration, architecture, and build/artifact regression checks for the accepted baseline and new scope. Complete independent architecture/security review, factual evidence, limitations, blocker resolution, and explicit ADR materiality assessment. |

### 25.1 Test Authority and Determinism

Real PostgreSQL is mandatory whenever proof depends on locking, SKIP LOCKED,
transaction behavior, constraints, grants/ACLs, or migrations. H2/mocks cannot
be called equivalent proof. Pure policy tests may complement, not replace,
database/process/external-boundary evidence.

Concurrent tests must demonstrate actual overlapping work with independent
transactions/connections and intended workload credentials. Record bounded
executor/process/pool configuration and deadlines. No sequential execution
disguised as contention, unsafe barriers, hidden retry-until-green, or long
real sleeps as the synchronization mechanism.

Failure injection must identify durable boundaries: before/after claim commit,
consumer commit, external effect, and local finalization. The simulator must
survive the killed worker. At least one critical real process termination and
fresh start is non-negotiable; fault-injected exceptions may only supplement it.

Final assertions inspect authoritative inventory/reservation/idempotency state,
events/deliveries/history, inbox/projection, reconciliation state/history, and
external simulator operation/effect state as relevant. Response counts and
logs alone do not prove correctness. Record unexpected errors, deadlocks, and
timeouts; do not conceal them with retries.

### 25.2 Future Evidence Requirements

When execution is later authorized, evidence must include:

- Tested repository revision, date, environment, relevant dependency/runtime
  versions, PostgreSQL version, and actual workload identities.
- Exact executed commands, test/build results and counts, failures/errors/skips,
  and explicit NOT EXECUTED / NOT APPLICABLE items.
- Claim/retry/reconciliation configuration, time authority, concurrency setup,
  process crash/restart details, and simulator persistence/absence guarantee.
- Authoritative final-state assertions for each gate, permission allow/deny
  results, migration upgrade/fresh results, and safe operational visibility.
- Decisions supported, unsupported claims, known limitations, open findings,
  independent review result, and ADR materiality decision.

No POC-03 execution evidence accompanies spec freeze. Historical POC-02 evidence
does not prove POC-03. Hosted CI or remote checks may be called passed only if
actually executed and observed.

---

### 25.3 Architecture Regression Coverage

When POC-03 code exists, architecture tests and review must cover the new async
boundaries as applicable, not merely rerun old Inventory-only rules. Required
coverage includes domain independence, application/adapter dependency direction,
worker/bootstrap placement, reconciliation placement, and persistence adapter
boundaries. Verify no inappropriate web/infrastructure dependency enters domain
or application, no generic platform dumping-ground pattern emerges, and no
service/network boundary is introduced solely for internal communication.

Existing Inventory architecture regression remains required. Exact package/class
structure and rule implementation are IMPLEMENTATION-PLAN DECISIONS; this specification
selects no package names and introduces no code or tests.

---

## 26. Exit Criteria

POC-03 cannot close until every required gate is executed and passes, with:

- No lost durable business intent/work and no partial producer commit.
- Duplicate delivery proven safe and one occurrence producing one event.
- Bounded retry, explicit visible failed work, and controlled replay proven.
- Real worker process crash/fresh restart and reclaim recovery proven.
- Drain stops new claims/new external-effect starts as specified, bounded
  backpressure and safe guarded relinquishment/fallback are proven, and abrupt
  death remains safe without drain or relinquishment.
- Scenario B reclaim after possible execution is reconciliation-first with
  authoritative simulator effect-count proof; no blind execute on lease expiry.
- Projection eventual consistency, visible freshness lag, and creation-only v1
  identity/timestamp/order semantics are proven through §16.2 scenarios.
- Reconciler restart recovers per-item checkpoints from PostgreSQL without
  dependence on local cursors/timers; operator backlog remains durable/visible.
- External UNKNOWN/reconciliation proven without blind retry; same stable
  simulator operation produces at most one effect, with exactly one in the
  required successful lost-response scenario.
- Reconciliation resume and close proven with atomic paired states, preserved
  knowledge, bounded per-cycle budget, and monotonic lifetime history.
- Projection rebuild proven from authoritative Reservation state.
- Event identity/type/version/content/order semantics proven.
- POC workload least-privilege positive/negative matrix proven.
- Fresh migration and actual V2 upgrade proven with unchanged V1/V2 history.
- Required operational signals available and verified against durable state.
- Full regression green and independent architecture/security review complete.
- No unresolved blocking contradiction; evidence factual and complete.
- ADR materiality explicitly reviewed and handled through canonical governance
  if a material decision requires an ADR in later authorized work.
- Known limitations and nonclaims preserved.

If a required test is skipped, not executed, dependency unavailable, environment
nonrepresentative, blocker unresolved, or result ambiguous:

```text
POC-03 = NOT VERIFIED
```

Mostly-passed work is not PASS. Spec drafting, spec freeze, or an implementation
plan is not executed proof or permission to close the POC.

---

## 27. Explicit Nonclaims

This frozen specification and the eventual bounded POC do not establish:

- Exactly-once delivery/execution or atomic transactions with external providers.
- Fan-out of a single event to projection and external-effect targets, production
  routing/subscriptions/consumer groups, multiple delivery rows per event, or
  composite production multi-effect handlers. Scenario A and Scenario B are
  separate validation scenarios over the same selected contract.
- Real Payment/Refund/Payout/Dispatch/Order/Fulfillment/Logistics correctness.
- Real payment-provider certification or universal NOT_FOUND/no-effect semantics.
- Production public authenticated idempotency, trusted actor binding, browser
  security, staff approval UI, or full protected audit.
- Production throughput/capacity, latency/SLO targets, full observability maturity,
  deployment readiness, HA, multi-region, or disaster-recovery certification.
- Global event order, aggregate sequencing, permanent event history, or infinite
  retention.
- Existing executable outbox/rebuild/reconciliation implementation in POC-02.

The existing POC-02 nonclaims remain intact. Future domains/providers must
validate their own authoritative invariants and uncertainty contracts.

---

## 28. Implementation-Plan Decisions and Deferred Items

The following remain implementation-plan decisions or deferred items; spec
freeze does not select their physical mechanisms:

| Item | Status and required constraint |
| --- | --- |
| Exact SQL columns/types, indexes, CHECK/UNIQUE/FK forms, partial unique index | IMPLEMENTATION-PLAN DECISION; enforce the specified semantic boundaries/invariants. |
| Exact table-versus-column grants and guarded write mechanism | IMPLEMENTATION-PLAN DECISION; satisfy effective capability matrix and compound operations without broadening authority. |
| JSONB or other payload representation | IMPLEMENTATION-PLAN DECISION; committed event semantics remain immutable. |
| UUID v4 versus v7 | IMPLEMENTATION-PLAN DECISION; opaque event identity remains distinct from other identities. |
| Exact PostgreSQL time function and claim SQL shape | IMPLEMENTATION-PLAN DECISION; DB time, short claims, SKIP LOCKED selected for this POC proof, fencing, and atomicity remain required. |
| Migration count/file names and role provisioning mechanics | IMPLEMENTATION-PLAN DECISION; forward only, explicit migrator, unchanged V1/V2. |
| Numeric lease duration, processing timeout, retry/reconciliation budgets, delays, poll interval, batch size | IMPLEMENTATION-PLAN DECISION; finite/bounded and processingTimeout < leaseDuration. |
| Retry base/max delay and jitter algorithm | IMPLEMENTATION-PLAN DECISION; durable backoff/jitter and safe bounded repetition required. |
| Reconciliation cycle/generation physical representation | IMPLEMENTATION-PLAN DECISION; each controlled resume starts a new bounded cycle without resetting lifetime attempts/history. |
| Controlled replay budget accounting and invocation/authorization of controlled operations | IMPLEMENTATION-PLAN DECISION; no infinite automatic loop, no UNKNOWN bypass, no runtime privilege expansion. |
| Java packages/classes and same-JAR/process bootstrap | IMPLEMENTATION-PLAN DECISION; preserve accepted dependency/module/identity boundaries. |
| Simulator storage/process technology and Scenario A versus Scenario B test/bootstrap selection | IMPLEMENTATION-PLAN DECISION; separate scenarios, one target per event instance, independent durable simulator authority, stable operation identity, no production routing/fan-out/composite handler. |
| Scan/pagination implementation or optional optimization checkpoint | IMPLEMENTATION-PLAN DECISION; durable case is the per-item checkpoint; cursor loss/reset cannot lose reconciliation work. |
| Drain/resource-close mechanics and bounded backpressure limits | IMPLEMENTATION-PLAN DECISION; stop new claims/effect starts as specified, preserve uncertain durable state, no unclaim transition or unbounded queue. |
| Projection rebuild physical algorithm, coordination, and optional provenance | IMPLEMENTATION-PLAN DECISION; authoritative reservation source and consumer convergence without ordinary maintenance privileges. |
| Routing reclaimed Scenario B attempts into reconciliation | IMPLEMENTATION-PLAN DECISION; observation first after possible prior execution, no phase marker/new state or blind external re-execution. |
| Guarded safe lease relinquishment SQL/update mechanism | IMPLEMENTATION-PLAN DECISION; revoke old claim authority, preserve PROCESSING/outcome uncertainty, advance reclaim eligibility only, no broader DB privileges. |
| Exact metrics exposition technology | IMPLEMENTATION-PLAN DECISION; all required signals observable. |
| Production retention durations/cleanup, production replay UI, full protected audit | DEFERRED; no infinite-retention or unrestricted-delete assumption. |
| Heartbeat/lease renewal | DEFERRED; absent from baseline unless later evidence requires explicit reconsideration. |

Implementation planning must not use a deferred mechanism as permission to
weaken an invariant. If a choice exposes materially different security or
correctness semantics not determined here, STOP for explicit review.

---

## 29. STOP Conditions

Stop and report the exact source/path and reason without choosing a design if:

- Canonical documents conflict with required semantics.
- Repository source contradicts a required invariant or A/B/C source assumptions.
- Implementation requires broader DB privileges than the capability matrix.
- An external/network call appears to require remaining inside a critical DB
  transaction.
- Duplicate safety depends on exactly-once delivery.
- UNKNOWN external state requires blind retry.
- Committed event content requires mutation.
- Worker/reconciler requires unrestricted authoritative business-table writes.
- Kafka, Redis, distributed lock, or a new service boundary appears necessary.
- V1/V2 historical migrations would need rewriting.
- Required crash/recovery proof cannot be representative and deterministic.
- Two materially different security/correctness designs remain possible because
  required semantics are ambiguous.

Do not silently resolve a STOP, broaden permissions, weaken invariants, or
continue drafting/implementation as though the issue were resolved. Deferred
physical choices are not preapproval for semantic changes.

The earlier resume-path STOP is resolved only by the accepted clarification:
guarded atomic FAILED → RECONCILIATION_REQUIRED and OPERATOR_REQUIRED → PENDING,
UNKNOWN preserved, with a new bounded cycle and unchanged lifetime history.
It does not authorize any other missing transition or arbitrary replay.

---

## 30. Open Decision Register

| ID | Question/finding | Disposition |
| --- | --- | --- |
| A | Are reservationId, skuId, ownerId, locationId, stockStatus, quantity supported? | SUPPORTED by inspected domain, persistence mapping, and V1 reservation columns listed in §6. Supported in the frozen spec after manual and independent review. |
| B | Can representative business projection be rebuilt from authoritative reservations without permanent outbox history? | SUPPORTED by stored reservation business fields. Supported in the frozen spec. Executable rebuild and consumer convergence remain G8 proof obligations; optional event provenance cannot defeat this requirement. |
| C | Does production naming conflict with inventory.reservation.created? | No conflicting production integration-event convention found in inspected production source/migrations. Supported in the frozen spec; selected representative integration contract is retained. |
| R1 | Exhausted reconciliation could resume case but not delivery under the original transition list. | RESOLVED by manual clarification incorporated in §§12, 18, 19.5: atomic controlled FAILED → RECONCILIATION_REQUIRED / OPERATOR_REQUIRED → PENDING; UNKNOWN unchanged. |
| R2 | Budget semantics after controlled reconciliation resume. | RESOLVED semantically: new bounded automatic cycle; lifetime attempts/history never reset. Physical cycle representation/numeric budget remain implementation-plan decisions. |
| R3 | Closure of failed delivery with unresolved case. | RESOLVED semantically: atomic delivery/case CLOSED with UNKNOWN retained, explicit reason/history, no claim of external failure. |
| P1 | Physical persistence, privilege enforcement, scheduling/configuration, process/harness, rebuild, and metric choices. | OPEN — IMPLEMENTATION-PLAN DECISIONS enumerated in §28; no mechanism frozen here. |
| P2 | Production retention, heartbeat reconsideration, full audit/auth/operational maturity. | DEFERRED under §28 and later POC boundaries; no new scope authorized. |
| V1 | Have required gates and independent architecture/security/ADR reviews passed? | NOT EXECUTED for POC-03; required before closure. |
| R4 | Independent-review round 1 findings. | RESOLVED 6/6 per independent review: prerequisites/audit sequence, drain/backpressure, per-item checkpoints, separate scenarios, selected-contract wording, and async architecture coverage. Round 1 resolution alone did not freeze the specification. |
| R5 | Independent-review round 2 blocking findings. | RESOLVED 3/3 blocking findings by the independent Round 3 PASS: Scenario B reconciliation-first reclaim, projection freshness/version/timestamp semantics and proof coverage, and safe guarded lease relinquishment. |
| R6 | Independent Spec Review Round 3 | PASS; blocking findings: 0; unresolved critical contradictions: 0; STOP findings: 0; freeze recommendation: APPROVED. Round 1: 6/6 resolved. Round 2: 3/3 blocking findings resolved. G0–G12 and final implementation architecture/security review remain NOT EXECUTED. |
| S1 | Is the branch specification frozen? | YES — FROZEN after independent spec review. POC-03 verification remains NOT VERIFIED. |

No unresolved source contradiction was identified in the accepted A/B/C
inspection. That factual support and independent spec approval are not executed POC-03
evidence.

---

## 31. Implementation Prerequisites, Spec Review, and Freeze Rule

### 31.1 Implementation Prerequisites

The spec-review/freeze prerequisite is complete. The remaining checks and
approvals below are not claimed complete. Before implementation may begin:

1. Confirm POC-02 remains CLOSED and canonical scope, canonical documents, and
   accepted ADRs remain consistent.
2. This branch-specific SHARED_SPEC has passed independent spec review with no
   blocking findings and is explicitly FROZEN. Freeze does not authorize
   implementation planning before the post-freeze audit.
3. Complete a post-freeze read-only repository audit with no blocking source
   drift or contradiction. Earlier A/B/C inspection does not substitute for it.
4. Create the exact IMPLEMENTATION_PLAN only after that audit completes.
5. The IMPLEMENTATION_PLAN must pass independent review. ADR materiality must be
   explicitly reviewed before the approval checkpoint.
6. Confirm the known base commit and working tree cleanliness before the approval
   checkpoint and branch activation; do not assume the current workspace
   is clean or that a base commit is already confirmed.
7. Complete the approval checkpoint. The user must then explicitly
   create/switch/activate the POC-03 branch; specification approval alone does
   not activate it.
8. Only after all prerequisites and user branch activation may implementation
   begin. Neither implementation nor implementation planning has begun.

### 31.2 Independent Spec Review and Exact Sequence

This specification is **FROZEN — independent spec review PASSED**.
**Verification status remains NOT VERIFIED.** Spec freeze does not mean G0–G12
passed and does not authorize implementation or branch activation. The
post-freeze read-only repository audit is the NEXT step; the exact
IMPLEMENTATION_PLAN must not be created before that audit completes.

The independent spec review checklist covers at least:

- Canonical/ADR alignment and preservation of the closed POC-02 baseline.
- Producer atomicity and exact selected event contract/payload/version semantics.
- Immutable event versus mutable lifecycle/history boundaries.
- Exhaustive delivery and reconciliation transitions, reasons, fencing, and
  atomic cross-state behavior, including resume/close of unresolved UNKNOWN.
- Separate business/coordination time and lifetime/per-cycle attempt semantics.
- Inbox/effect atomicity, external operation deduplication, uncertainty recovery,
  real-process crash proof, and authoritative projection rebuild feasibility.
- Effective least-privilege capability matrix, controlled-operation boundaries,
  and positive/negative PostgreSQL proof requirements.
- Separate Scenario A/Scenario B target semantics, drain/backpressure safety,
  durable per-item reconciliation checkpoints, and new async architecture coverage.
- Complete G0–G12 obligations, factual evidence rules, nonclaims, deferred items,
  STOP handling, and ADR materiality governance.

The future sequence remains:

```text
canonical-scope review
→ branch-specific spec
→ independent spec review
→ freeze
→ post-freeze read-only repository audit
→ exact implementation plan
→ independent plan review
→ approval checkpoint
→ user branch activation
→ implementation
```

Findings/contradictions must be resolved before advancing the applicable review
stage. The post-freeze read-only repository audit cannot be omitted or replaced
by pre-freeze source inspection. Execution and eventual independent final
review/closure remain subject to the required evidence gates.

This specification is FROZEN after independent spec review. Freeze does not
authorize implementation, branch activation, or imply that verification gates
passed. The post-freeze read-only repository audit is next. The
IMPLEMENTATION_PLAN must not be created before that audit completes. Any
future material change must preserve review and canonical ADR governance. Git
mutation remains user-controlled.
