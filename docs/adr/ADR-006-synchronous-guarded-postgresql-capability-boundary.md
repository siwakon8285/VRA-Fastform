Status: ACCEPTED — independent ADR review PASSED; manual acceptance CONFIRMED

POC-04 verification: NOT VERIFIED; S0-S15 NOT EXECUTED

Implementation: NOT AUTHORIZED

Independent ADR Review: PASS
Blocking findings: 0
Material authority conflicts: 0
Frozen-spec contradictions: 0
Security-boundary regressions: 0
Manual acceptance: CONFIRMED

# ADR-006 — Synchronous Guarded PostgreSQL Capability Boundary

## Context

The accepted backend foundation uses PostgreSQL for authoritative transactional
state, an explicit migration process, and distinct `vra_owner`, `vra_migrator`,
and `vra_runtime` roles. ADR-003 added guarded capabilities for asynchronous
workers; it did not govern the synchronous Inventory Reservation idempotency
path. POC-04's frozen [SHARED_SPEC](../../validation/poc-04/SHARED_SPEC.md),
sections 13–15, accepts bounded decision U7 for that path and requires S7/S8
proof before any implementation claim.

The existing V1 grants give `vra_runtime` `SELECT, UPDATE` on
`vra.inventory_balance` and `SELECT, INSERT` on
`vra.inventory_reservation`. V2 grants table-wide `SELECT, INSERT, UPDATE` on
`vra.inventory_reservation_idempotency`. V2 constrains row shape, and the
application uses conditional SQL, but arbitrary SQL under the shared runtime
credential can bypass application predicates. In particular, a valid terminal
idempotency row can be rewritten and a terminal-shaped row can be inserted
directly under the current broad V2 grants. The later POC must preserve V1–V3
source bytes, existing rows, Flyway history, and POC-03's first-success outbox
transaction while closing that selected V2 bypass.

The bounded decision addresses integrity of terminal idempotency records and
the transient-to-terminal transition. It does not assert that a compromised
shared runtime credential is contained from all reads or writes.

## Evidence-first decision chain

| Step | POC-04 decision basis |
| --- | --- |
| Business Requirement | A retry of a representative Inventory Reservation returns the one intended result without a second authoritative effect, including after the POC-03 outbox extension. |
| Invariant | Under `vra_runtime`, an existing terminal V2 result is immutable; a terminal result cannot be forged by direct table insertion; only a matching transient claim may complete once to the exact `SUCCEEDED` or `REJECTED` shape. |
| Risk / Threat | A holder of the shared runtime credential can issue direct SQL that bypasses application `WHERE` clauses; a poorly owned privileged function can add a role, search-path, or caller escalation path. |
| Consistency | Claim, business decision, terminal result, and, on first success, reservation plus integration event and initial delivery remain within the existing authoritative PostgreSQL transaction. |
| Availability | A denied or unavailable guarded capability prevents completion; it cannot fall back to broad DML. This POC makes no HA or capacity claim. |
| Performance | Narrow operation calls may add database work. Measure their effect in the later POC; do not trade away the invariant based on an assumed bottleneck. |
| Operations | Apply only forward migrations, inspect effective grants and role paths, preserve checksum history and populated data, and keep migration credentials out of runtime. |
| Architecture | Give the synchronous runtime only operation-specific guarded V2 capabilities with a dedicated narrow owner for any privileged object; retain ADR-003's separate async boundary. |
| Technology | PostgreSQL constraints, grants, catalog inspection, transactions, and guarded objects provide the bounded enforcement. RLS is not adopted automatically. |
| Implementation | A later authorized plan selects the exact forward migration and callable signatures, then demonstrates legitimate and adversarial behavior on real PostgreSQL. This ADR authorizes no implementation. |

## Decision

For the bounded POC, the V2 transient claim and completion will pass through
narrow, operation-specific PostgreSQL capabilities. The runtime will not retain
table-wide V2 `UPDATE` power that can rewrite a terminal row, and direct DML
under `vra_runtime` must not insert a terminal-shaped V2 row. No generic
`set_state` capability is permitted. A trigger or other guarded mechanism may
be selected only if its executable PostgreSQL no-bypass proof is equivalent.

The selected guarded transition accepts a stored, matching actor scope and
request fingerprint, including its fingerprint version, and moves exactly one
transient row from NULL outcome to the valid `SUCCEEDED` or `REJECTED` shape.
It rejects a wrong actor scope, key, fingerprint, source state, result shape,
caller, or repeated completion without changing authoritative state. A
column-level `UPDATE` grant alone does not establish terminal immutability:
allowed columns could still be rewritten after completion. Any retained V2
direct `INSERT` must be proved unable to forge a terminal row or bypass the
guarded claim, and any retained direct `UPDATE` must be proved unable to
rewrite a terminal row. The later migration and evidence must inventory the
exact retained, revoked, and replacement rights, including V1 grants.

If the implementation uses `SECURITY DEFINER` or an equivalent privileged
PostgreSQL object, its owner is a dedicated, narrowly empowered identity,
`NOLOGIN` where appropriate, separate from `vra_runtime`, `vra_migrator`, and
`vra_async_executor`. Its function surface has a fixed safe `search_path`,
fully qualified trusted relations where appropriate, no attacker-controlled
dynamic SQL, and validated input. `PUBLIC` execution is revoked; `EXECUTE` is
granted only to each exact intended caller. Untrusted callers have no `CREATE`
in any schema that could shadow referenced objects. Function ownership,
definition, `SECURITY DEFINER` status, ACLs, schema ownership and `CREATE`,
role membership, and direct or transitive `SET ROLE` paths are reviewable
parts of the capability. Actual wrong-caller, shadow-object, function-bypass,
and role-escalation SQL must fail. The chosen equivalent mechanism has the
same no-bypass obligation. It must not reuse `vra_async_executor` for
synchronous work without a separate reviewed role and ownership decision.

This is a synchronous extension of ADR-002's least-privilege model. It does
not weaken `vra_migrator → vra_owner` migration control or the separate
POC-03 async worker capabilities and their ownership path.

## Security and correctness invariants

1. The authenticated HTTP path derives `actorScope` from verified VRA account,
   operation, and business scope. A request ID is correlation data, never
   idempotency identity. Cross-account key reuse must not disclose another
   actor's stored result.
2. A legitimate first claim, success, business rejection, same-key and
   same-fingerprint replay, fingerprint conflict, concurrent claim, rollback,
   and lost-result recovery preserve the frozen POC-02/03 behavior. A first
   success still atomically records Reservation, terminal result, integration
   event, and initial delivery.
3. An existing terminal V2 row cannot be rewritten by direct `vra_runtime`
   SQL or any callable operation granted to that role. Direct table DML cannot
   forge a terminal V2 row.
4. Completion requires stored transient identity and fingerprint match, exact
   NULL-to-terminal transition, valid result shape, and one completion. Wrong
   state, input, or caller has no authoritative effect.
5. The guarded object introduces no schema-shadowing, role-ownership,
   `SET ROLE`, `PUBLIC EXECUTE`, or dynamic-SQL privilege escalation. S8 cannot
   pass if this hardening creates a new escalation path.
6. Existing V1/V2/V3 migration bytes and Flyway checksums are preserved;
   the runtime never acquires migration or schema-owner power.

## Trust and authority boundary

An authenticated human's business scope is established by VRA's application
authorization layer. `vra_runtime` is one shared PostgreSQL workload identity,
not a database role per human or seller. The SQL capability uses the stored
V2 actor scope and fingerprint as transition guards; a holder of the runtime
credential may still choose inputs to a granted operation. The database
therefore protects the selected terminal-row transition, while the application
remains responsible for human identity, resource authorization, and legitimate
first-result semantics.

The migrator applies reviewed forward schema and grant changes through the
accepted ADR-002 owner path. The synchronous guarded object's dedicated owner
has only its required privileges; ordinary runtime and async workload roles
cannot assume that owner or reach owner/migrator capabilities. ADR-003's async
executor retains its separate purpose and is not an implicit synchronous
function owner.

## Alternatives considered and why they were not selected

1. **Keep broad table `UPDATE`.** It permits arbitrary terminal V2 rewrites
   despite conditional SQL in the repository, so it cannot support the
   selected terminal-immutability claim.
2. **Use only column-level `UPDATE`.** It narrows writable fields but still
   allows changing granted terminal fields after completion. It may be defense
   in depth, not the sole transition guard.
3. **Expose generic `SECURITY DEFINER set_state`.** A generic state setter
   would recreate broad mutation through a privileged function and make
   allowed source state, fingerprint, and result shape hard to constrain.
4. **Add RLS automatically.** RLS does not by itself define the operation's
   one-time transition and is not a per-human boundary with a shared runtime
   principal. Its policy and operational costs need a separate evidenced use.
5. **Reuse `vra_async_executor` as owner.** That role owns narrowly selected
   async capabilities under ADR-003. Reuse would merge synchronous and async
   ownership and expand its privilege graph without a reviewed reason.
6. **Rely on application predicates alone.** They do not constrain an
   arbitrary SQL client authenticated as `vra_runtime`, the precise bypass
   this decision is required to address.

## Consequences and trade-offs

The runtime's V2 write power becomes narrower and the terminal result gains
database-enforced integrity within the tested capability boundary. The cost is
additional PostgreSQL objects or guards, grant and ownership maintenance, and
real database tests for every callable path. The existing V1 grants remain a
disclosed residual risk for the bounded POC; any later change to those grants
requires separate review. Closing the V2 terminal bypass does not close every
business-state bypass. The selected design is deliberately PostgreSQL-specific
and may add call overhead, which the later POC must measure if performance is
claimed.

## Operational implications

The implementation must use a new forward migration; it must not rewrite
V1, V2, or V3. It must exercise both fresh migration and a populated V3-to-new
upgrade, preserve existing transient and terminal rows, and verify Flyway
history and rerun behavior. Migration and runtime remain separate artifacts
and credentials. Deployment preflight must inspect role membership, function
owners, ACLs, schema `CREATE`, effective direct grants, and compatibility of
the old and new application paths. A failed migration or missing capability
stops deployment or the affected mutation; broad grant restoration is not an
automatic recovery path. Operational evidence must record exact migration,
catalog, SQLSTATE, and state-delta results without secrets.

## Validation obligations

No POC-04 execution has occurred. Before the selected claim can be verified,
the future implementation must satisfy the frozen spec's S0, S7, S8, and S15
requirements, along with affected idempotency, authorization, and outbox
regression gates. On real PostgreSQL, demonstrate:

- the full post-hardening V1/V2 direct-grant inventory and exact callable
  replacements; dedicated owner, function definition and safe search path,
  ACLs, schema shadow resistance, and direct/transitive role/`SET` graph;
- positive first success and rejection, replay and conflict, concurrent
  same-key arbitration, rollback and lost-result recovery, plus POC-03
  first-success outbox atomicity;
- denied direct terminal `UPDATE`, forged terminal `INSERT`, wrong-shape or
  repeated completion, wrong fingerprint/scope/input, actual wrong caller,
  shadow/search-path/dynamic-SQL/function bypass, DDL, and role escalation;
- SQLSTATE `42501` for revoked direct `UPDATE`, exact designed SQLSTATE for
  other guards, and authoritative before/after Inventory balance, Reservation,
  V2 idempotency, outbox event/delivery, and version/count snapshots for every
  denial, allowing only separately recorded expected telemetry changes.

Source inspection, application-only tests, or an HTTP denial status alone do
not meet these obligations. A bypass of the selected V2 claim or a new
escalation path requires correction and review before S8 can pass.

## Explicit nonclaims and governance

- The shared `vra_runtime` role is not per-human PostgreSQL authorization and
  provides no database-enforced per-seller confidentiality.
- A compromised runtime credential may retain direct-read exposure, including
  cross-scope rows available through retained `SELECT` rights.
- Retained V1 `inventory_balance UPDATE` and `inventory_reservation INSERT`
  may permit fabricated supporting state outside the application path.
- A caller holding the runtime credential may invoke permitted guarded
  operations with chosen inputs. Complete authenticity of a first result
  under that compromise is not proven.
- This ADR makes no general credential-compromise containment, production
  throughput, HA, SLO, provider, vendor, or production-readiness claim. No
  production vendor or provider is selected here.

The frozen spec accepted U7's bounded semantics; **spec acceptance is not
implementation evidence**. This ADR is **ACCEPTED** after independent review
and manual acceptance; **ADR acceptance is not implementation evidence and
does not make POC-04 PASS**. All S0–S15 remain NOT VERIFIED until the frozen
[SHARED_SPEC](../../validation/poc-04/SHARED_SPEC.md), sections 27–30, has
executed and independently reviewed evidence. Implementation and branch
activation remain NOT AUTHORIZED by this record.

## Relationship to existing authority

This ADR extends [ADR-002](ADR-002-backend-production-foundation.md):
PostgreSQL authoritative OLTP, separate migration/runtime identities, explicit
Flyway lifecycle, and explicit SQL for correctness-critical transitions. It
also extends [ADR-003](ADR-003-durable-async-coordination-and-database-capability-boundary.md)
with a **synchronous** guarded capability; ADR-003 remains the accepted
**asynchronous** capability boundary. Neither accepted ADR is revised,
rewritten, or superseded. Canonical [DESIGN](../DESIGN.md),
[SECURITY](../SECURITY.md), [TESTING](../TESTING.md),
[OPERATIONS](../OPERATIONS.md), [ROADMAP](../ROADMAP.md), and
[BRANCH_PLAN](../BRANCH_PLAN.md) govern the wider constraints. The exact
bounds, residuals, and proof obligations are in the frozen POC-04
[SHARED_SPEC](../../validation/poc-04/SHARED_SPEC.md), sections 13–15, 26–30.
