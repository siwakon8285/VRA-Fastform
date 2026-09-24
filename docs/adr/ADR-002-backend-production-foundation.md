# ADR-002 — Backend Production Foundation

- **Status:** ACCEPTED
- **Date:** 2026-09-24
- **Decision:** VRA accepts the POC-01 Java/Spring/PostgreSQL backend production foundation described below.
- **Validated baseline:** Java 21, Spring Boot 4.1.1, Gradle 9.7.1, PostgreSQL 17.11, Testcontainers 2.0.5

## Context

After ADR-001 selected Java as the primary JVM language, VRA still needed to
validate the production-oriented backend foundation that later domain work will
build on.

The required foundation had to preserve VRA's priority order:

1. security,
2. correctness,
3. architecture clarity,
4. operations and recovery,
5. availability,
6. performance,
7. scalability,
8. developer convenience.

POC-01 therefore used Inventory Reservation as a representative transactional
slice rather than implementing broad marketplace features.

The validation had to answer material architecture questions about:

- Spring Boot suitability and the exact baseline,
- build/module boundaries,
- package dependency enforcement,
- persistence strategy,
- migration lifecycle,
- database ownership and least privilege,
- configuration/startup failure behavior,
- liveness/readiness,
- stable HTTP errors and request correlation,
- structured logging,
- CI-compatible verification,
- and deployable JVM artifacts.

The frozen specification is:

- [`validation/poc-01/SHARED_SPEC.md`](../../validation/poc-01/SHARED_SPEC.md)

The approved implementation plan is:

- [`validation/poc-01/IMPLEMENTATION_PLAN.md`](../../validation/poc-01/IMPLEMENTATION_PLAN.md)

Final POC evidence is preserved in:

- [`validation/poc-01/evidence/RESULTS.md`](../../validation/poc-01/evidence/RESULTS.md)

## Evidence

The final executable/CI evidence HEAD before this ADR was:

```text
d39e532 ci: add backend verification workflow
```

The final automated result was:

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

Additional evidence included:

- explicit migration from an empty PostgreSQL database,
- migration rerun and validation behavior,
- Flyway checksum-drift detection,
- runtime/migrator/owner privilege separation,
- runtime DDL denial,
- runtime inability to run migrations,
- conditional inventory SQL at the database write boundary,
- JDBC + JPA participation in one Spring transaction,
- rollback after forced reservation-persistence failure,
- database constraint failures,
- optimistic version conflict behavior,
- real HTTP-to-PostgreSQL execution,
- database-dependent readiness with process-only liveness,
- missing-schema and incompatible-schema fail-fast behavior,
- safe generic unexpected-500 handling,
- server-controlled request IDs,
- ECS structured logging with request correlation,
- runtime database configuration validation,
- built runtime and migration JARs,
- and a local rehearsal of the CI workflow with explicit no-skip guards for
  PostgreSQL integration tests.

GitHub-hosted execution of the new workflow was not executed before this ADR and
is not claimed as evidence.

## Options Evaluated

### Backend framework

POC-01 evaluated whether the Spring direction should become the production
backend framework baseline or be revised.

Spring Boot 4.1.1 successfully supported the required:

- transaction semantics,
- JDBC and JPA integration,
- HTTP boundary,
- validation,
- health probes,
- configuration binding,
- structured logging,
- and testing model.

No POC-01 evidence required rejecting or replacing Spring Boot.

### Gradle/module layout

The implementation considered the smallest module split that represented a real
security or operational boundary.

A single module would make runtime and migration responsibilities easier to
couple accidentally.

Splitting every domain/layer into separate Gradle modules would add structural
complexity before evidence showed that it was necessary.

The validated middle ground is:

```text
backend/
├── runtime/
└── migration/
```

Domain/application/adapter boundaries inside `runtime` are package-level and
enforced by ArchUnit.

### Persistence strategy

POC-01 did not validate an "all JPA" or "all JDBC" rule.

The representative path demonstrated that:

```text
aggregate-shaped persistence
→ JPA where suitable

correctness-critical state transition
→ explicit SQL / Spring JdbcClient
```

can participate in one predictable Spring transaction against PostgreSQL.

jOOQ was not required by the POC and remains unselected.

### Migration lifecycle

The alternatives were effectively:

```text
runtime startup auto-migration
```

or:

```text
explicit migration artifact/process
→ compatible schema
→ runtime startup/validation
```

The explicit lifecycle preserved the required runtime/migrator security
boundary and produced clear failure behavior for missing or incompatible
schemas.

### Database privilege model

The validated model separates:

```text
vra_owner
vra_migrator
vra_runtime
```

rather than making the runtime identity an owner or superuser.

The POC demonstrated that the application path works without broadening runtime
privileges.

## Decision

VRA accepts **Spring Boot as the primary backend framework** for the production
foundation validated by POC-01.

The accepted baseline is:

```text
Java                    21
Spring Boot             4.1.1
Gradle Wrapper          9.7.1
PostgreSQL              17.11
Testcontainers          2.0.5
Flyway                  12.4.0
PostgreSQL JDBC         42.7.13
Hibernate ORM           7.4.5.Final
ArchUnit                1.4.1
Build DSL               Kotlin DSL
```

The accepted backend build layout is:

```text
backend/
├── runtime/
└── migration/
```

This module split is retained because runtime execution and schema migration are
different security and operational responsibilities.

Inside `runtime`, the accepted dependency direction is:

```text
Domain
← Application
← Adapters / Infrastructure
← Bootstrap / Composition
```

Package boundaries are enforced with ArchUnit. Additional Gradle modules are not
created merely to mirror layers.

The accepted persistence baseline is:

```text
JPA
→ suitable aggregate-shaped persistence

explicit SQL / Spring JdbcClient
→ correctness-critical state transitions
```

This is not a mandate that every future aggregate use JPA or every critical path
use raw JDBC. A future persistence mechanism must still be selected from the
requirements and evidence of that path.

The accepted migration lifecycle is:

```text
migration artifact/process
→ connect as vra_migrator
→ controlled SET ROLE vra_owner
→ apply/validate Flyway migrations
→ runtime starts separately as vra_runtime
→ runtime validates compatible schema
```

Runtime must not gain Flyway migration responsibility.

The accepted database identity model is:

```text
vra_owner
→ NOLOGIN
→ owns schema/database objects

vra_migrator
→ LOGIN / NOINHERIT
→ controlled SET ROLE vra_owner for migrations

vra_runtime
→ LOGIN
→ least-privilege application DML only
→ not schema owner
→ no migration responsibility
```

The accepted operational/API baseline also includes:

- fail-fast runtime database configuration validation,
- process-oriented liveness,
- database-dependent readiness,
- stable public API error codes/messages/request IDs,
- safe generic `INTERNAL_ERROR` for unexpected failures,
- server-controlled request correlation,
- ECS structured console logging,
- separate deployable runtime and migration JVM artifacts,
- and a CI-compatible backend workflow that requires PostgreSQL integration
  suites to execute without skips.

## Rationale

This foundation satisfies the representative POC-01 requirements without
requiring broader privilege, distributed infrastructure, an in-memory database,
runtime auto-migration, or a larger module topology.

The two-module layout follows a real security/operations boundary.

The hybrid persistence approach keeps correctness-critical SQL explicit while
allowing JPA where aggregate-shaped persistence is a good fit.

The owner/migrator/runtime split preserves least privilege while still allowing
repeatable migration and application execution.

The selected baseline therefore provides sufficient evidence to continue VRA
development without introducing complexity that POC-01 did not justify.

## Consequences

Future VRA backend branches should treat this ADR as the production-foundation
default.

In particular:

- new runtime code must not add Flyway migration responsibility,
- deployment must preserve separate migration and runtime identities,
- runtime privilege must not be broadened merely to fix deployment errors,
- correctness-critical transitions should remain auditable at their database
  write boundary,
- package/module dependency rules must remain enforceable,
- PostgreSQL-specific correctness tests must continue using real PostgreSQL,
- integration tests must not silently skip when Docker/PostgreSQL is required,
- and changes to the accepted foundation require deliberate compatibility and
  architecture review.

This ADR does not make every POC dependency version permanent forever.

## Version Policy

Spring Boot 4.1.1 and the dependency versions listed above are the accepted
POC-01 production-foundation baseline.

A routine compatible upgrade may be adopted through normal dependency,
security, test, and operational review.

A new ADR is required when an upgrade materially changes assumptions behind this
decision, such as:

- the framework boundary,
- transaction model,
- persistence strategy,
- migration responsibility,
- module topology,
- database privilege model,
- or operational behavior relied on by later domains.

## Non-Decisions

POC-01 and this ADR do not decide:

- high-contention reservation correctness or retry/idempotency policy,
- the final reservation release/expiry/allocation lifecycle,
- transactional outbox worker implementation,
- broker adoption,
- authentication/session/MFA implementation,
- full security hardening,
- production secrets-management implementation,
- production reverse-proxy/TLS deployment,
- backup/restore and high availability,
- performance/capacity limits,
- horizontal or multi-region scaling,
- Redis,
- OpenSearch,
- ClickHouse,
- Kubernetes,
- GraphQL,
- internal gRPC,
- sharding,
- or specialized derived data planes.

Those remain governed by later POCs/branches and evidence.

## Revisit Conditions

Revisit this ADR when material new evidence shows, for example:

- Spring Boot cannot satisfy a required VRA backend invariant or operational
  constraint,
- the runtime/migration two-module split no longer matches security or
  deployment responsibilities,
- the hybrid JPA/JdbcClient strategy creates measurable correctness or
  maintainability risk,
- Flyway lifecycle requirements materially change,
- the database privilege model must change for a demonstrated requirement,
- or production evidence shows that this foundation blocks required
  availability, performance, or scaling behavior.

Do not revise this accepted ADR silently. Preserve this record and create a new
ADR when a material decision changes.
