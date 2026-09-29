Status: ACCEPTED — independent ADR review PASSED; manual acceptance CONFIRMED
POC-04 verification: NOT VERIFIED; S0-S15 NOT EXECUTED
Implementation: NOT AUTHORIZED
Independent ADR Review: PASS
Blocking findings: 0
Material authority conflicts: 0
Frozen-spec contradictions: 0
Security-boundary regressions: 0
Manual acceptance: CONFIRMED

# ADR-005 — Privileged Security Operations and Protected Audit

## Context

VRA staff authority is scoped by operation, data, state, and context; a staff
identity is never an implicit superuser. A privileged access change needs a
human decision that remains valid at execution time and a protected record of
who proposed, approved, and executed it. The bounded POC-04 representative
action is a grant of a narrowly scoped staff security role to another staff
account. It does not create a public staff-signup path or a general staff
administration product. [PRODUCT](../PRODUCT.md), [SECURITY](../SECURITY.md),
and frozen [POC-04 SHARED_SPEC](../../validation/poc-04/SHARED_SPEC.md) §12.

The canonical architecture distinguishes business authorization, security
events, protected audit, application logs, domain events, and integration
events. POC-04 must establish the privileged-action and protected-audit
boundary before later sensitive domains rely on it; investigation tooling and
production retention remain later work. [DESIGN](../DESIGN.md),
[BRANCH_PLAN](../BRANCH_PLAN.md) §5.2, and frozen spec §§18–19.

The final independent review froze U1–U13 for the bounded POC, including U6
and U11. Independent ADR review PASSED and manual acceptance confirmed this
ADR. **Spec acceptance and ADR acceptance are governance results, not
implementation evidence; ADR acceptance != POC-04 PASS.**
All S0–S15 remain NOT VERIFIED until the frozen spec's required evidence is
executed and independently reviewed.

## Evidence-first decision chain

| Stage | Bounded ADR-005 conclusion |
| --- | --- |
| Business Requirement | Permit one authorized, narrowly scoped staff security-role grant with a reviewable reason and attributable history. |
| Invariant | No grant without an eligible scoped maker, a distinct eligible scoped checker, fresh independent assurance for the checker, exact unexpired approval, and protected audit. |
| Risk / Threat | Staff-title escalation, self-approval, stale or transferred MFA assertion, changed proposals, revoked actors, forged attribution, and mutable or suppressed history. |
| Consistency | Proposal, approval, current account/session/role/scope, assurance, and target are checked close to mutation; the permitted role change and required protected audit append succeed or fail together. |
| Availability | Missing or uncertain session, account, policy, approval, step-up, or required audit authority denies the privileged mutation. No availability fallback lowers assurance. |
| Performance | Keep the representative decision bounded and measure actual database/assurance cost later; this ADR claims no latency or capacity target. |
| Operations | Monitor privileged changes and tamper attempts, protect audit access, preserve evidence, and define production retention and provider operations in later reviewed work. |
| Architecture | VRA owns business authorization and the authoritative proposal; separate maker/checker assurance and protected audit form one privileged correctness boundary. |
| Technology | Use the accepted backend/PostgreSQL foundation for authoritative state; no production MFA provider or retention system is selected here. |
| Implementation | A later authorized plan must specify the factor fixture, numeric lifetimes, data shape, database grants, and S0–S15 proof without weakening these decisions. |

## Decision

1. **No implicit staff superuser.** A staff role permits only explicitly
   granted operations and data scope. Authentication, staff status, possession
   of an identifier, and a successful MFA challenge do not alone authorize the
   role grant. VRA, not an external identity provider, owns the current role,
   scope, account, proposal, approval, and target state.
2. **Exact representative operation.** The bounded privileged action grants
   one narrowly scoped staff security role to an eligible staff target. Its
   proposal records the exact target account, role, operation/data scope,
   maker, reason, version or digest, creation, expiry, and state. A proposal
   cannot confer its requested role by being created.
3. **Maker.** An active staff actor with the correct current operation/data
   scope and an MFA-authenticated, current staff session may propose the grant
   with a reason. This bounded proposal does not require a second fresh
   step-up from the maker. An application-set `mfa=true` flag is not assurance.
4. **Checker.** A different active staff actor with the correct current
   operation/data scope must provide a fresh, independently validated step-up
   before execution. Assurance is bound to that checker actor and session and
   to the exact approval context; it has finite validity and cannot be
   transferred to another actor, target, operation, or proposal.
5. **Exact approval.** Approval binds the target, role, scope, proposal
   version/digest, expiry, and maker/checker separation. Editing the proposed
   change invalidates prior approval. Expired or rejected approval cannot
   authorize execution.
6. **Near-mutation recheck.** Execution rechecks maker/checker identity and
   separation, both actors' current account/session and role/scope state, the
   target's eligibility, the proposal version and exact change, approval,
   step-up freshness/context, and expiry close to the authoritative role
   mutation. A revoked maker/checker privilege or session denies execution.
   A successful operation applies at most the one approved change and appends
   the required protected audit in the same correctness boundary. A role
   mutation without that audit is invalid for this architecture.
7. **Attribution and protection.** Audit actor, business subject, and action
   come from authenticated, server-established actor context plus the
   authoritative proposal and target state. Caller-supplied actor, subject,
   or action fields are never audit authority. The protected record includes
   actor ID/type, subject if distinct, action, target, reason, result,
   authoritative timestamp, request/correlation ID, and applicable proposal
   and approval linkage, with only a safe before/after reference or delta.
   Append protected audit for proposal, approval/rejection, privilege
   execution/revocation, and relevant denial/tamper attempts. Staff-assisted
   attribution retains the staff actor separately from a business subject.
   Protected audit is append-oriented and has a separately testable read and
   permission boundary. Routine runtime must not arbitrarily `UPDATE`,
   `DELETE`, or `TRUNCATE` protected history, or directly forge an audit row.
   This architecture does not treat ordinary application logs, domain events,
   integration events, or the security-event stream as protected audit.
8. **Distinct security events.** Emit safe events for authentication,
   authorization denial/probing, step-up challenge/success/failure, privilege
   proposal/grant/revoke, session rotation/revocation, webhook verification
   failure/replay, and audit-tamper attempt where applicable. A denial event
   may be the expected state delta after a rejected mutation. Events carry no
   bearer, password, CSRF token, signature, raw webhook body, unnecessary PII,
   or private foreign-resource content. Telemetry delivery cannot grant
   authority. If a critical decision requires durable event capture, its
   failure behavior must be reviewed and proved rather than assumed.

## Security and correctness invariants

- Every privileged decision uses current VRA account, role, scope, session,
  proposal, approval, and target authority; a stale UI or controller check is
  insufficient.
- Maker and checker are distinct human actors; the checker must prove fresh,
  independently validated, context-bound assurance before execution.
- Approval authorizes only its exact unexpired proposal. Proposal edits,
  replayed/foreign/expired assurance, self-approval, and revoked actors deny.
- A successful role mutation and required protected audit append are coupled;
  an audit failure prevents a committed role grant.
- Protected history is not an ordinary mutable business row. Existing audit
  rows remain unchanged after forbidden direct SQL or application attempts.
- A denied privileged mutation preserves authoritative proposal, approval,
  assurance, role, target, and audit state except for explicitly expected
  denial/tamper evidence. An HTTP status alone is not proof.

## Trust and authority boundary

| Boundary | Trusted authority and required check |
| --- | --- |
| Browser/HTTP → VRA | Authenticate the current VRA staff session and enforce CSRF for cookie-authenticated unsafe requests; treat all request actor, subject, action, target, and scope fields as untrusted selectors. |
| External authentication → VRA | External authentication and factor results provide validated identity/assurance inputs. VRA resolves the actor and owns business roles, data scope, and approval policy; no production provider is selected. |
| Maker → proposal → checker | Persist the exact proposal; independently validate the checker's fresh factor, actor/session/context, scope, and maker separation before approval/execution. |
| VRA runtime → PostgreSQL | Authoritative proposal, role, and protected audit state require narrow permitted operations. Shared `vra_runtime` is a workload credential, not a per-human database identity; server-established context and proposal/target linkage provide bounded human attribution. |
| Protected audit → security telemetry | Audit is the protected record of privileged correctness. Security events support detection and investigation but cannot replace audit or act as approval. |

The implementation must demonstrate that direct SQL using ordinary runtime or
worker credentials cannot insert an arbitrary actor/subject/action audit row
or rewrite/delete/truncate protected history. It must not claim that a shared
runtime database username alone identifies a human or that this bounded
boundary contains every effect of fully compromised higher-trust authority.

## Alternatives considered and why not selected

| Alternative | Why it is not selected for this bounded foundation |
| --- | --- |
| Staff role alone | Role membership does not establish exact target/scope, maker/checker separation, fresh assurance, or approval at execution time. |
| MFA alone | A valid factor establishes limited assurance, not current operation/data scope or an exact approved change. |
| Same-person approval | It removes the independent checker and permits self-approval of a high-impact privilege grant. |
| Application boolean `mfa=true` | A mutable application assertion is not independently validated factor/challenge evidence bound to the actor, session, and operation. |
| Ordinary mutable logging as audit | Logs can rotate, be omitted, or be rewritten under different access and retention rules; they do not satisfy the protected audit correctness boundary. |
| Client-provided audit attribution | The caller could name another actor, subject, or action and produce a misleading privileged history. |

## Consequences and trade-offs

The role grant requires more authoritative state and a second actor. This
increases approval latency, operational handling for expired proposals, and
database/assurance dependencies. It also gives reviewers an exact change to
approve and makes stale or revoked authority deny before mutation. The
protected audit append adds write and access-control obligations. Those costs
are accepted for this representative high-impact action; measured operational
and capacity behavior remains future evidence.

## Operational implications

Privileged grants, approval denials, step-up failures, unusual staff access,
and audit-tamper attempts need observable safe events and review paths. Audit
read/export authority must be narrow and auditable. Operators must preserve
history during incidents, recovery, storage pressure, and access review; an
ad hoc audit deletion is not a recovery procedure. An unavailable session,
MFA, account/policy, approval, or required audit boundary denies the grant.
The production MFA provider and production audit-retention duration remain
deferred; their selection needs separate authority and operational review.
[OPERATIONS](../OPERATIONS.md), [SECURITY](../SECURITY.md).

## Validation obligations

These are future obligations, not completed tests. Frozen-spec S6 and S11
must prove an active scoped MFA maker's reasoned proposal, a distinct scoped
checker with independently validated fresh context-bound step-up, one exact
grant, and an attributable protected audit append. Negative cases include
maker without MFA, self-approval, wrong scope, fake/replayed/wrong-actor or
wrong-session factor, missing/expired/wrong-context step-up, changed or
expired proposal, revoked maker/checker/session, ineligible target, and direct
execution without approval. Test denial of forged actor/subject/action
through the application and direct SQL, and unauthorized audit
`UPDATE`/`DELETE`/`TRUNCATE` under real PostgreSQL credentials.

For each relevant positive and denied operation, record actual HTTP/policy
outcome and authoritative before/after proposal, approval, assurance, role,
target, and audit state. Count expected denial/tamper security events
separately and inspect their safe content. A committed role change without
required audit fails S11. Use the frozen spec's policy, PostgreSQL,
HTTP/filter, Bruno, adversarial/fault, and final source-bound evidence layers;
real-browser step-up proof is additionally required if the privileged flow is
browser-exposed. Section 29's final evidence and independent review are
required before any POC-04 PASS claim. [TESTING](../TESTING.md), frozen spec
§§27–29. **All S0–S15 remain NOT VERIFIED; none has been executed here.**

## Explicit nonclaims

- This ADR selects no production MFA factor/provider, commercial IdP,
  production KMS/secret authority, webhook provider, or production retention
  duration. The later plan may choose a bounded test factor and finite numeric
  lifetimes without turning them into production selections.
- It does not implement or verify the role grant, protected audit, security
  events, database permissions, or incident operations. It is not production
  ready and does not authorize implementation or branch activation.
- It does not provide a general staff administration product, public staff
  signup, all future high-risk action policies, or blanket authorization for
  customer, seller, finance, support, or recovery workflows.
- Shared `vra_runtime` is not per-human database authorization. This ADR does
  not claim complete containment of a fully compromised runtime or privileged
  database operator, or immutable history against every higher-trust actor.
- Security events, application logs, domain events, and integration events are
  not substitutes for protected audit; no exactly-once delivery is claimed.

## Relationship to existing ADRs and frozen POC-04 spec

This ADR extends the accepted [ADR-002](ADR-002-backend-production-foundation.md)
backend/PostgreSQL foundation and the canonical [SECURITY](../SECURITY.md)
privileged-operation and audit architecture. It does not revise ADR-002 or
[ADR-003](ADR-003-durable-async-coordination-and-database-capability-boundary.md),
which governs the bounded asynchronous capability boundary; neither ADR
creates human staff authority. The frozen [POC-04 SHARED_SPEC](../../validation/poc-04/SHARED_SPEC.md)
§§12, 18–19, 23, 26–29 remains the binding scope and evidence gate. If a later
implementation or review finds a contradiction, resolve it through the
project's authority process before affected work. Acceptance of this ADR does
not revise any existing accepted record.
