Status: ACCEPTED — independent ADR review PASSED; manual acceptance CONFIRMED

POC-04 verification: NOT VERIFIED; S0-S15 NOT EXECUTED

Implementation: NOT AUTHORIZED

Independent ADR Review: PASS

Blocking findings: 0

Material authority conflicts: 0

Frozen-spec contradictions: 0

Security-boundary regressions: 0

Manual acceptance: CONFIRMED

# ADR-004 — Human Identity and Browser Session Authority

## Context

VRA needs to authenticate a human through an external identity provider without
delegating account lifecycle or business authorization to that provider. A
browser also needs a revocable authentication boundary that does not expose a
long-lived privileged bearer to JavaScript. The frozen [POC-04 shared
specification](../../validation/poc-04/SHARED_SPEC.md) accepts bounded choices
U1–U4 for this foundation; its SHA-256 is
`c035e627c087b41cb4533b00f0bdaf8fa208a5b55881358cf1f85b6809b2ed2d`.
The specification is frozen, while all S0–S15 gates remain NOT VERIFIED.

The [product account model](../PRODUCT.md) separates authentication, recovery,
authorization, and business eligibility. The canonical [design](../DESIGN.md)
puts VRA business authority behind an external IdP boundary and directs
browser traffic through an opaque server session/BFF. The canonical
[security architecture](../SECURITY.md) requires explicit account states,
session rotation and revocation, and CSRF protection for cookie-authenticated
unsafe operations. [Testing](../TESTING.md), [operations](../OPERATIONS.md),
[roadmap](../ROADMAP.md), and [branch plan](../BRANCH_PLAN.md) require executed
security evidence before a POC-04 or production-foundation claim.

### Evidence-first decision chain

| Step | Bounded POC-04 reasoning |
| --- | --- |
| Business Requirement | Eligible people must reach only the VRA accounts and resources for which VRA currently grants authority. |
| Invariant | A validated external `(issuer, subject)` resolves to at most one intended account binding; current VRA state, membership, ownership, role, and context decide business access. A stale browser credential cannot regain authority. |
| Risk / Threat | Email takeover or reassignment, forged OIDC callbacks or keys, login CSRF, open redirect, session fixation/theft, persistent-store disclosure, stale privilege, and cross-site mutation. |
| Consistency | Binding uniqueness and collision denial, single-use authentication transactions, and atomic session generation change/revocation preserve the authority decision. |
| Availability | IdP failure denies new login and re-authentication; unavailable PostgreSQL session authority denies protected operations without a local success fallback. |
| Performance | Session reads, expiry, and revocation add PostgreSQL load; record latency and load before any later scaling choice. |
| Operations | Trusted issuer configuration, clock/expiry controls, key rotation through trusted authority, revocation, restart recovery, and safe secret/log handling need reviewable procedures. |
| Architecture | External authentication feeds VRA-owned identity mapping; a server/BFF validates an opaque cookie against durable server-side session authority before business policy. |
| Technology | Standard OIDC Authorization Code with PKCE and PostgreSQL-backed session state for this bounded POC; no commercial IdP or Redis selection. |
| Implementation | A later authorized plan must choose exact schema, durations, configuration, and test commands, then satisfy the frozen specification's evidence gates. No implementation is authorized by this accepted ADR. |

## Decision

### External authentication and VRA business authority

An external IdP authenticates an external subject. VRA validates the issuer and
immutable subject, and uses their tuple as the stable external lookup key.
Email, display name, and phone are mutable profile attributes, never identity
authority or automatic account-linking proof. A tuple has at most one current
intended VRA-account binding; uniqueness and an atomic linking operation must
deny conflicting or concurrent bindings without moving either account. The
bounded POC may establish reviewed initial fixture bindings. It creates no
self-service link, unlink, merge, or recovery authority and does not forbid a
future reviewed multiple-identity model.

VRA owns its stable account identity; `ACTIVE`, `LOCKED`, `DISABLED`, and
`CLOSED` lifecycle; organization/seller memberships; roles and permissions;
resource ownership; and privileged business authorization. Only an eligible
`ACTIVE` account can create or use an ordinary session. IdP claims, including
roles, do not grant a VRA operation. VRA derives actor and seller scope from
current server authority and checks the resource and state for each protected
action. Moving away from `ACTIVE` invalidates usable sessions and privileged
assurance before another protected decision.

### Browser authentication profile

The bounded browser profile is standard OIDC Authorization Code at the
server/BFF boundary with PKCE, transaction-bound `state`, and `nonce` for an
ID token. The redirect target is exactly registered. VRA configures a trusted
issuer before login; an untrusted request, callback, or token cannot select
issuer authority. Discovery, JWKS, and verification keys come only from that
trusted profile. Allowed signing algorithms and key types are explicit.
Attacker-selected `jku`, `x5u`, embedded JWK, or equivalent key source cannot
establish verification authority. Unsigned, confused, unknown-key,
wrong-issuer/client/audience, expired, wrong-use, or invalidly signed material
denies.

Each finite authentication transaction binds the expected issuer, client,
registered redirect, `state`, `nonce`, and PKCE context; it is consumed once.
Callback and token results must match before a VRA session is issued. A
post-login continuation is a reviewed local target; absolute, protocol-relative,
and encoded external redirect bypasses deny. A successful IdP login establishes
only a session for a mapped eligible account, not business authorization. A
test-only independent OIDC issuer supplies protocol evidence. No custom
authentication protocol or commercial IdP is selected.

### Browser session and CSRF authority

For this bounded POC, PostgreSQL holds authoritative server-side session
state. The browser holds only a high-entropy opaque bearer in a cookie with
`Secure`, `HttpOnly`, narrow path/domain, and bounded absolute and idle
lifetimes. `SameSite=Lax` is the selected first-party, top-level redirect POC
policy. It is not universal production tuning; a cross-site or embedded flow
needs separate threat review and browser evidence.

Persistent authority stores a non-reversible, non-reusable verifier or
equivalent design, never a directly reusable copy of the browser bearer.
Reading session-table fields alone must not yield a cookie credential that
authenticates. Required security metadata comprises account, current
generation, issue/use/expiry and revocation state, and assurance context;
raw bearer material must not enter
logs, errors, evidence, or artifacts. Both lifetime bounds are finite and
controlled-clock testable. Numeric POC values belong in a later implementation
plan and are not production recommendations.

Login replaces any pre-authentication identifier. A change in authority through
step-up/elevation or a later authorized recovery atomically invalidates the old
verifier and issues a new bearer/verifier generation; the old CSRF token and
assurance do not carry over. Logout revokes server authority and clears the
cookie. Account disable, credential-security change, suspicious-login
response, and staff-privilege revocation invalidate affected session or
elevated capability before the next protected action under a tested policy.
Unknown, expired, revoked, and pre-rotation credentials deny. Restart cannot
resurrect expired or revoked state. A valid session survives a POC restart
only while durable authority still permits it. Inability to consult session
authority denies protected actions.

Every cookie-authenticated state-changing API operation must use an unsafe
method and a session-generation-bound unguessable synchronizer token carried
in an explicit request header. The token comes from a same-origin safe
bootstrap response and expires on session rotation or revocation. Same-origin
is the bounded default; any added origin needs narrow independent review.
Origin/method policy and all accepted content types must preserve the CSRF
check. `SameSite` and CORS do not replace it. The OIDC top-level callback is
bound by its single-use `state`/`nonce` transaction rather than treated as an
ordinary resource mutation.

## Security and correctness invariants

- External authentication never grants a VRA role, membership, owner scope, or
  privileged action without a current VRA decision.
- `(validated issuer, immutable subject)` is the only external identity key;
  collision, missing mapping, and ineligible account deny without accidental
  linking or usable session creation.
- Only current durable session state can authorize a cookie. Stored verifier
  disclosure, fixation, old generations, expiry, revocation, and session-store
  outage cannot authenticate.
- Browser session changes preserve an exact generation boundary, including
  anti-CSRF and assurance invalidation.
- Cookie-authenticated unsafe operations require CSRF proof and VRA business
  authorization. Neither a UI check nor a successful OIDC callback substitutes
  for those checks.

## Trust and authority boundary

```text
Configured, trusted IdP
  → verified immutable external subject
  → VRA-owned account binding and current lifecycle
  → PostgreSQL-backed VRA session authority
  → server/BFF account, membership, resource, and context policy

Browser
  → opaque cookie + session-bound CSRF proof for unsafe requests
  → server/BFF verification against current VRA authority
```

The IdP controls its authentication result, not VRA business policy. The
browser can present an opaque credential and request data but cannot assert
its account, seller scope, role, or ownership. PostgreSQL is session authority
for this bounded POC; the browser and a process-local cache are not. The
shared runtime database credential is a workload identity, not per-human
database authorization. [ADR-006](ADR-006-synchronous-guarded-postgresql-capability-boundary.md)
is the separate accepted synchronous capability boundary.

## Alternatives considered and why they were not selected

| Alternative | Why it is not selected for this bounded foundation |
| --- | --- |
| Client-held long-lived bearer/JWT as browser authority | It would place durable privileged authentication material within a browser attack surface and make immediate server-side revocation/rotation harder to prove. |
| Process-local session authority | It cannot prove durable revocation and restart behavior, and cannot support a multi-instance authority claim without further design. |
| Redis immediately | No measured need justifies another authoritative store or its recovery and operations boundary. PostgreSQL is already operated; later capacity evidence may change the choice. |
| Email-based account matching | Email is mutable and may be reassigned or verified under a different authority; equality cannot safely establish the immutable external-account binding. |
| IdP roles as business authorization | VRA must decide current memberships, ownership, resource state, scope, risk, and approvals; provider claims cannot supply that complete authoritative policy. |
| Custom authentication protocol | Standard OIDC with the bounded trust profile supplies reviewed protocol semantics; a new protocol would add avoidable key, callback, and replay risk. |

## Consequences and trade-offs

VRA must maintain account binding, lifecycle, membership, session, and CSRF
state as separate authority from the IdP. PostgreSQL session authority adds
database read/write load and availability coupling, but permits durable
revocation and restart evidence within the existing operational boundary.
The IdP remains necessary for new login and re-authentication; outage cannot
be treated as local login success. A later Redis decision requires its own
measured performance, failure, consistency, and operations review.

The first-party `SameSite=Lax` policy restricts the bounded browser topology.
Any future embedded or cross-site topology requires a separate cookie/CSRF
decision and real-browser proof. Future self-service identity linking and
recovery require independently reviewed authority, not email matching.

## Operational implications

Operations must manage trusted issuer/profile configuration, key rotation
through that profile, finite transaction/session expiry and synchronized time,
session revocation, database availability, and secret-safe diagnostics. A
misconfigured or unavailable trusted profile denies new login; a missing
session-authority read denies protected operations. Record session-store
latency/load, restart behavior, and denied-path evidence without printing a
bearer or test secret. This accepted ADR chooses no production IdP, session
capacity target, KMS, or deployment availability guarantee.

## Validation obligations

Later implementation must satisfy the frozen shared specification, including
S1 authentication, S2 mapping, S3 server-derived authorization, S4 browser
session, S5 CSRF, S12 safe disclosure, S14 black-box/real-browser proof, and
S15 source-bound final evidence. The independent issuer fixture must exercise
actual browser redirect and callback navigation, trusted discovery/JWKS and
key-source/algorithm denials, transaction replay/expiry, PKCE/state/nonce,
client/issuer/redirect binding, local continuation, mapping, and account-state
negatives. A mocked principal alone is insufficient.

Real-browser evidence must verify `Secure`, `HttpOnly`, and `SameSite` cookie
behavior, login establishment, rotation, logout/revocation, and CSRF on a
minimal genuine auth/BFF surface. PostgreSQL and HTTP evidence must cover
binding uniqueness/concurrency, persistent verifier non-reuse, restart, idle
and absolute expiry, stale credential and token replay, store outage, account
disable, staff privilege revocation, and all accepted unsafe methods/content
types. Denials require authoritative before/after state and only their
explicitly permitted security telemetry delta. Numeric lifetimes and exact
commands remain for a later authorized implementation plan. No S gate has run.

## Explicit nonclaims and governance

- Spec acceptance != implementation evidence. ADR acceptance is an
  architecture-governance result, not implementation evidence or POC-04 PASS.
- Independent ADR review PASSED and manual acceptance was CONFIRMED. Neither
  is implementation verification, branch activation, or a production-readiness
  claim. S0–S15 remain NOT VERIFIED until executed, source-bound evidence exists.
- No commercial IdP, production session scaling solution, Redis deployment,
  production KMS/secret authority, or other production vendor/provider is
  selected here. The frozen spec also selects none of those providers.
- Future self-service linking, unlinking, merging, and recovery are deferred.
  This POC does not prove a production recovery policy or real mobile client.
- This bounded PostgreSQL choice does not prove multi-instance behavior unless
  two actual instances are tested; it does not prove HA, capacity, or a
  permanent production storage choice.
- Representative POC identity and resource checks do not prove authorization
  for all future marketplace domains or per-human database isolation.

## Relationship to existing ADRs and frozen specification

This accepted ADR extends the accepted [ADR-002 backend production
foundation](ADR-002-backend-production-foundation.md): it uses that foundation's
server runtime, PostgreSQL authority, and explicit runtime/migration separation
without changing their accepted responsibilities. It does not revise ADR-002
or the accepted [ADR-003 asynchronous capability
boundary](ADR-003-durable-async-coordination-and-database-capability-boundary.md).
ADR-003 continues to govern async workload roles; this record governs human
identity and browser-session authority. The frozen POC-04 shared specification
controls bounded semantics and all later implementation evidence gates. ADR-004
received independent review and manual acceptance. Its acceptance leaves
POC-04 NOT VERIFIED until S0–S15 execute and their evidence is reviewed.
