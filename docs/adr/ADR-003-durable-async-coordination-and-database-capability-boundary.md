# ADR-003 — Durable Async Coordination and Database Capability Boundary

- **Status:** ACCEPTED — independent ADR review PASSED
- **Date:** 2026-09-27
- **Decision:** Accepted bounded POC-03 PostgreSQL-backed asynchronous coordination and database capability architecture described below.
- **POC-03 verification:** NOT VERIFIED; G0–G12 NOT EXECUTED

This record extends the accepted backend foundation in ADR-002. It does not
revise or supersede that ADR. Independent ADR Review Round 1 passed, and this
ADR has been manually accepted. Acceptance is an architecture-governance result;
it does not authorize implementation or branch activation.

## Independent ADR Review — Round 1

- **Result:** PASS
- **Blocking findings:** 0
- **Critical contradictions:** 0
- **Semantic drift:** 0
- **STOP findings:** 0
- **Decision 14 fidelity:** PASS
- **Decision 15 fidelity:** PASS
- **Decision 16 fidelity:** PASS
- **Decision 17 fidelity:** PASS
- **ADR-002 compatibility:** PASS
- **Frozen-spec compatibility:** PASS
- **Implementation-plan alignment:** PASS
- **Evidence-status honesty:** PASS
- **Manual acceptance:** CONFIRMED

This is architecture/document review evidence only. It is not automated or
implementation evidence.

## Context

VRA needs to preserve durable intent when an Inventory Reservation mutation
requires asynchronous work. The authoritative business mutation and the intent
to process it must not become separated by a process crash. Workers must also
recover safely when they stop between claiming work, applying a consumer effect,
calling an external boundary, and recording the observed outcome.

The canonical architecture already establishes PostgreSQL as the authoritative
transactional store, recommends a transactional outbox and inbox deduplication,
expects at-least-once processing, and requires bounded retries and explicit
reconciliation for unknown outcomes. POC-03 applies those principles to one
representative business slice: the first successful Inventory Reservation.
It does not create production Payment, Refund, Dispatch, Order, Fulfillment, or
Logistics domains.

These concepts have separate meanings:

```text
Command != Integration Event != Delivery Attempt
```

`ReserveInventory` is a command. A successful first authoritative execution
records the selected integration event `inventory.reservation.created`,
schema version `1`. A delivery attempt is a later processing episode for that
immutable event. Replaying delivery does not create a new business occurrence
or event.

The architecture must represent failures truthfully. A worker can lose a
response after an external system has committed an effect. Lease expiry cannot
prove that the effect did not happen, and exactly-once execution cannot be
assumed across PostgreSQL, a process, and a network boundary.

## Decision

VRA will use a PostgreSQL-backed transactional outbox and guarded asynchronous
lifecycle operations for the bounded POC-03 proof. The business mutation,
reservation, applicable terminal idempotency outcome, immutable event, and
initial READY delivery are committed in one PostgreSQL transaction. Event
creation or initial delivery failure rolls back that transaction.

The durable model separates immutable intent, current lifecycle, and historical
evidence. It uses `outbox_event`, `outbox_delivery`,
`outbox_delivery_history`, `consumer_inbox`, `reservation_projection`,
`reconciliation_case`, and `reconciliation_history`. The outbox is a durable
delivery mechanism, not an event store. Current rows describe current state;
append-only histories retain processing and reconciliation evidence. The
projection is derived and is never authoritative Inventory or Reservation
state.

Each event instance has exactly one logical delivery target in a given POC
scenario. POC-03 uses separate validation scenarios over the same event
contract:

- Scenario A targets the reservation projection consumer and proves inbox
  deduplication, duplicate delivery safety, and projection rebuild.
- Scenario B targets a validation-only external-effect adapter and proves
  stable operation identity, UNKNOWN handling, and observation-based
  reconciliation.

One event instance does not fan out to both scenarios. The design adds no
production routing, subscription, consumer-group, multi-delivery, or
fan-out framework.

Workers claim bounded batches in short PostgreSQL transactions using row
locking and `FOR UPDATE SKIP LOCKED`. They commit a unique claim token and lease
before processing, then complete work in a separate guarded transaction.
Claim tokens fence every claim-owned completion. Lease expiry makes a row
eligible for reclaim; it does not itself transfer ownership. Ownership changes
when a new claim transaction commits a new token. Safe relinquishment revokes
the old claim's authority while retaining PROCESSING state and making the work
eligible for normal reclaim. `SKIP LOCKED` is selected for the POC proof, not
established as a permanent rule for all future asynchronous workloads.

Delivery and reconciliation work use durable, bounded automatic cycles. The
POC policy permits five execution-bearing delivery claims and four
query-bearing reconciliation claims per cycle. The current limit is persisted
when the cycle begins. Ordinary workers cannot replace or increase it. A
bounded recovery-only claim may fence an exhausted crashed item and immediately
move it to a non-dispatching outcome; it never returns work for another effect
or observation and cannot form an automatic loop.

Processing is at-least-once. Exactly-once delivery and exactly-once execution
are not claimed. Scenario A commits the consumer inbox identity
(`consumerIdentity + eventId`) and projection mutation in one PostgreSQL
transaction, so redelivery cannot apply the projection effect twice. The
projection is eventually consistent, not a read-after-write authority, and can
be rebuilt from authoritative reservation records without permanent outbox
history.

External calls occur outside VRA correctness-critical database transactions.
For Scenario B, `eventId` is the stable external operation identity. A timeout,
lost response, or interrupted attempt whose execution may have begun is
UNKNOWN, not failure. A reclaimed Scenario B delivery enters reconciliation
before any new execute call. Reconciliation performs observation only:

- confirmed success resolves the delivery as SUCCEEDED;
- confirmed no effect may permit a bounded retry using the same event and
  operation identity when retry is safe;
- an indeterminate observation preserves UNKNOWN and schedules bounded
  reconciliation;
- exhaustion leaves visible operator-required work without asserting that the
  external effect failed.

The POC simulator is validation infrastructure only. Its state is independent
of the worker process and outside VRA production migrations and schema.

## Accepted implementation-plan decisions

Decisions 14–17 were accepted as planning inputs and confirmed by independent
ADR Review Round 1. This ADR records those decisions without changing them.

### Decision 14 — Hybrid PostgreSQL capability enforcement

Application code owns orchestration, retry and reconciliation policy, and
failure classification. PostgreSQL enforces correctness-critical lifecycle
capabilities through narrow, guarded operations. Async workloads do not receive
broad lifecycle-table UPDATE merely for convenience. Operation-specific
`SECURITY DEFINER` functions expose only the required transitions; there is no
generic `set_state` operation. Static row-shape and referential invariants use
constraints and indexes as defense in depth. RLS is not added without a
separate, demonstrated requirement.

### Decision 15 — Material architecture decision

POC-03 materially extends the database privilege and asynchronous coordination
architecture, so a new ADR is required. ADR-003 is that record and is ACCEPTED
after independent review.

### Decision 16 — Durable automatic-cycle budget

The current automatic-cycle limit is durable database state. The selected POC
limits are five execution-bearing delivery claims and four query-bearing
reconciliation claims. A new cycle persists its limit when it begins, and
ordinary worker or reconciler callers cannot change the current cycle's bound.
The final recovery-only claim, when needed after a crash at exhaustion, is
non-dispatching and transitions the item atomically out of the claimable state.

### Decision 17 — Stable administrative ownership-transfer edge

The administrative membership edge is:

```text
vra_owner -> vra_async_executor
SET TRUE
INHERIT FALSE
ADMIN FALSE
```

Both roles are NOLOGIN. This edge exists only to let the already higher-trust
migration ownership path manage ownership of the narrowly selected guarded
functions. The expected administrative path is `vra_migrator → vra_owner →
vra_async_executor`; PostgreSQL may report the corresponding effective
transitive SET path from migrator to executor. Ordinary workload identities
must have no direct or transitive SET path to either role. PostgreSQL's actual
membership and ownership behavior remains an executable G0/G10 proof
obligation; this ADR does not claim that proof has passed.

## Security architecture

The architecture separates process credentials according to the work they may
perform:

- `vra_runtime` remains the synchronous Inventory/Reservation authority. It
  may append the immutable event and initial READY delivery as part of the
  producer transaction, but it cannot claim or later transition asynchronous
  work.
- `vra_outbox_worker` claims and completes ordinary delivery work, reads the
  immutable event, appends processing history, and performs its permitted
  inbox/projection writes. It cannot mutate authoritative Inventory,
  Reservation, or idempotency state.
- `vra_reconciliation_worker` claims reconciliation cases and performs guarded
  observation-driven reconciliation transitions. It cannot claim arbitrary
  ordinary delivery work or make unrestricted authoritative business writes.
- `vra_async_operator`, `vra_projection_rebuilder`, and `vra_async_observer`
  are bounded POC-only identities for controlled replay/resume/close,
  projection rebuild, and read-only visibility. They do not define a production
  staff authentication or authorization model.
- `vra_async_executor` is a constrained NOLOGIN function owner, not a process
  identity. It owns only the selected guarded functions and required narrow
  trigger helpers; it owns no schema or table and has no steady-state schema
  CREATE, DDL, DELETE, TRUNCATE, or GRANT capability. It receives only the
  table and sequence access those functions need.

No ordinary async workload receives broad lifecycle UPDATE. This keeps a
credential from bypassing source-state checks, claim-token fencing, immutable
event boundaries, controlled replay, or paired delivery/reconciliation
transitions. Controlled operator operations remain distinct from ordinary
worker functions and do not claim a production operator-auth model.

Every `SECURITY DEFINER` function must use a fixed safe `search_path`, fully
qualified object references, validated arguments, no unsafe dynamic SQL, and
revoked PUBLIC EXECUTE. EXECUTE is granted only to the intended caller.
Functions guard exact source states and claim tokens where applicable; failure
to satisfy a stale guard produces no mutation. PostgreSQL constraints remain
defense in depth. Function ownership, membership paths, grants, and real
positive and negative SQL behavior must be demonstrated in PostgreSQL before
the architecture can be considered verified.

The stable owner-to-executor edge deliberately allows the existing higher-
trust migration administration path to manage the narrower function-owner
role. It is not workload delegation. The edge's options are fixed as SET TRUE,
INHERIT FALSE, and ADMIN FALSE. No runtime, worker, reconciler, POC operator,
rebuilder, or observer identity may gain a direct or transitive SET path to
`vra_owner` or `vra_async_executor`. The role bootstrap and V3 ownership
transfer must prove this exact boundary; the plan's G0 and G10 remain
unexecuted.

## Failure and recovery model

The producer transaction never calls an external system. Keeping external
calls outside the transaction avoids holding correctness-critical database
locks over a network operation and avoids making database commit depend on an
external response. Durable event and delivery intent survive independently of
the worker process.

For Scenario A, the inbox record and projection effect commit together. A crash
after that commit but before delivery completion can cause redelivery; inbox
deduplication makes the repeated processing safe. Projection state can lag
while delivery is pending, retrying, failed, or under reconciliation. Its
freshness is observable through durable backlog and oldest outstanding age.

For Scenario B, the simulator uses `eventId` as `externalOperationId`. The same
identity and semantic payload return or reveal the existing effect; a
conflicting payload is an explicit conflict. A response loss after an external
effect becomes UNKNOWN. Reclaim after a possibly begun execution routes to
reconciliation first. No durable marker claims that execution definitely did
not begin, so an observation query is preferred over a possible duplicate
effect. Confirmed no effect can permit a bounded retry with the same identity;
indeterminate results remain UNKNOWN. Unknown is not silently converted to
failure just because the observation budget is exhausted.

This model prefers explicit uncertainty and a visible operator backlog over a
guessed failure classification. Closing unresolved UNKNOWN work means VRA
deliberately stops further processing; it does not mean the external operation
is known to have failed.

## Alternatives considered

1. **Direct broad table UPDATE by workers — rejected.** A broad UPDATE grants
   more capability than each workload needs and can bypass lifecycle, token,
   immutability, and paired-state invariants.
2. **RLS as the primary lifecycle mechanism — not selected.** RLS can provide
   row-level defense in depth where a concrete requirement calls for it, but
   it does not by itself express the selected operation-specific state-machine
   capabilities. Adding it here would increase policy and operational
   complexity without a demonstrated need.
3. **One large trigger-driven state machine — rejected.** It would hide
   application orchestration and failure policy in database triggers and make
   migrations and recovery behavior harder to review. Narrow constraint
   triggers may still enforce cross-table invariants; they do not own lifecycle
   orchestration.
4. **Kafka, RabbitMQ, or NATS — deferred, not selected.** The bounded proof
   does not require broker infrastructure. Adoption remains evidence-driven if
   fan-out, independent consumer groups, retention/replay, throughput, or
   workload-isolation needs emerge.
5. **Redis or distributed locks as correctness authority — rejected.**
   PostgreSQL already owns authoritative work and reservation state. A separate
   lock authority is unnecessary for this proof and would split correctness
   state.
6. **Exactly-once execution — rejected as an assumption.** A process or
   network failure can occur after an external effect commits and before VRA
   records its result. At-least-once processing with deduplication and
   reconciliation is the supported model.
7. **Blind retry after timeout or lease expiry — rejected.** Either signal can
   occur while an external effect may already have happened. Scenario B must
   observe external truth before another effect becomes eligible.
8. **Event sourcing or permanent event store — not selected.** The outbox
   provides durable delivery intent, while authoritative reservation records
   support projection rebuild. POC-03 does not require permanent event history.
9. **Service extraction or localhost HTTP between internal components — not
   selected.** Separate process modes and credentials can preserve workload
   boundaries within the accepted codebase and artifact family.
10. **Runtime-owned migrations — rejected.** This conflicts with ADR-002's
    accepted separation between the migration process and runtime identity.

## Relationship to ADR-002

ADR-002 remains the accepted backend foundation: Java and Spring, PostgreSQL as
the authoritative OLTP store, separate `backend/runtime` and
`backend/migration` modules, explicit migration execution, the
`vra_owner`/`vra_migrator`/`vra_runtime` baseline, and JPA plus explicit SQL
where each fits. ADR-003 extends that foundation with additional asynchronous
workload identities and guarded PostgreSQL lifecycle capabilities.

ADR-003 does not supersede ADR-002, rewrite its history, or retroactively modify
its evidence. ADR-002 remains historically valid and accepted.

## Consequences

### Benefits

- A successful business mutation commits with durable intent to process it.
- Delivery and reconciliation lifecycle state survives worker crashes and can
  be rediscovered after restart.
- Claim tokens fence stale workers, and bounded durable cycles prevent endless
  automatic execution or observation.
- Narrow workload identities and guarded database operations limit what a
  compromised or misconfigured process can change.
- Failed and uncertain work remains visible with attempt and transition
  history.
- External uncertainty is represented explicitly and resolved through
  observation where possible.
- The representative projection can be repaired from authoritative
  Reservation state.
- Broker adoption remains deferred until evidence justifies its operational
  cost.

### Costs and risks

- PostgreSQL gains more tables, constraints, roles, functions, and migration
  ordering requirements.
- Role membership, function ownership, SECURITY DEFINER attributes, and ACLs
  require ongoing review and executable positive/negative tests.
- Separate runtime, worker, reconciler, and bounded POC credentials add
  configuration and operational work.
- Retry, failure, reconciliation, and append-only histories add lifecycle and
  support complexity.
- Real process crash tests require separate worker and simulator processes,
  deterministic failure boundaries, and cleanup of disposable resources.
- Transactional claims and lifecycle enforcement deliberately couple this
  core to PostgreSQL behavior.
- POC proof does not establish production throughput, availability, retention,
  or provider correctness.

## Boundaries and non-decisions

This ADR does not select a permanent broker, Redis, a generic workflow engine,
microservices, Kubernetes, global event ordering, event sourcing, real payment
or provider behavior, production staff replay UI or authentication, production
retention values, production HA/SLO/capacity values, heartbeat or lease renewal,
or service extraction.

`FOR UPDATE SKIP LOCKED` is selected for the bounded POC proof; it is not a
permanent architecture mandate for every future asynchronous workload. Numeric
lease/retry settings in the POC plan are validation settings, not production
tuning. No production performance or operational maturity claim follows from
this decision.

## Evidence and proof status

The accepted architecture decision is supported at the governance stage by:

- the frozen POC-03 shared specification;
- the post-freeze read-only repository audit, which found no blocking source
  contradiction and returned READY FOR IMPLEMENTATION-PLAN DRAFT;
- the independently reviewed POC-03 implementation plan, whose Round 3 result
  was PASS with zero blocking findings, zero critical contradictions, and zero
  STOP findings.

These reviews do not prove the implementation. The implementation plan remains
NOT APPROVED for implementation. POC-03 remains NOT VERIFIED; G0–G12 are all
NOT EXECUTED; implementation has not started; and branch activation has not
been performed.

Future proof obligations include:

- **G0:** migration, ownership transfer, role paths, and ACLs on PostgreSQL;
- **G1:** producer and outbox atomicity;
- **G2:** event contract, idempotent replay, and no duplicate event creation;
- **G3:** claim concurrency, lease eligibility, fencing, and bounded recovery;
- **G4:** actual worker process termination and fresh-process recovery;
- **G5:** inbox deduplication, projection transaction, and duplicate safety;
- **G6:** bounded retry, FAILED work, and controlled replay;
- **G7:** UNKNOWN, reconciliation, and simulator effect identity;
- **G8:** projection rebuild from authoritative Reservation state;
- **G9:** valid and invalid lifecycle transitions and database constraints;
- **G10:** positive and negative least-privilege behavior for every workload;
- **G11:** operational counts, age, activity, and history visibility;
- **G12:** regression, architecture and security review, and complete evidence.

ADR acceptance must not be represented as proof that PostgreSQL ownership or
grant behavior, crash recovery, retry bounds, simulator behavior, or permission
enforcement has passed. Results remain NOT VERIFIED until the required evidence
is actually executed and reviewed.

The implementation plan requires a narrow governance-only consistency re-review
after this sequence correction; that review does not reopen ADR-003 acceptance or
the architecture decisions. After it passes, the next governance sequence is:

```text
ADR-003 ACCEPTED
→ known-base / literal working-tree verification before approval
→ approval checkpoint
→ repeat known-base / literal working-tree verification immediately before branch activation
→ user-controlled branch activation
→ authorized implementation
→ G0–G12 execution and evidence
→ independent closure review
```

The read-only readiness audit must report exact Git status, including untracked
files; untracked governance documents are not treated as clean by exception. If
literal working-tree cleanliness requires a user Git decision, report that fact
and leave the approval checkpoint blocked. This sequence does not assert that
any later step has occurred. The implementation plan remains NOT APPROVED for
implementation, POC-03 remains NOT VERIFIED, G0–G12 remain NOT EXECUTED,
implementation remains NOT STARTED, and branch activation remains NOT
PERFORMED.

## Revisit conditions

Revisit this ADR only when new evidence demonstrates that an assumption or
trade-off no longer fits VRA. Relevant evidence could include:

- PostgreSQL claim contention or measured capacity cannot meet a required
  workload;
- required fan-out, independent subscriptions, or retention/replay semantics
  emerge;
- independently operated services need broker-mediated integration;
- external provider contracts require a materially different reconciliation
  model;
- maintaining the database function and privilege surface becomes
  operationally unsafe;
- measured throughput or availability requirements cannot be met by this
  architecture;
- new domains establish a concrete need for ordering or workflow
  orchestration.

Revisit is evidence-driven, not speculative. Any material change must preserve
this record and receive a new architecture review; POC-03 limits or proof
results alone do not silently generalize to later workloads.

## References

- [POC-03 Shared Specification](../../validation/poc-03/SHARED_SPEC.md)
- [POC-03 Implementation Plan](../../validation/poc-03/IMPLEMENTATION_PLAN.md)
- [ADR-001 — Primary JVM Language](ADR-001-primary-jvm-language.md)
- [ADR-002 — Backend Production Foundation](ADR-002-backend-production-foundation.md)
- [Design](../DESIGN.md)
- [Security](../SECURITY.md)
- [Roadmap](../ROADMAP.md)
