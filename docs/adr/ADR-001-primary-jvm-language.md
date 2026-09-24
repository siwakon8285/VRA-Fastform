# ADR-001 — Primary JVM Language

- **Status:** ACCEPTED
- **Date:** 2026-09-24
- **Decision:** Java is the primary JVM language for VRA backend systems.
- **Validated baseline:** Java 21, Spring Boot 3.5.16, Gradle 8.14.3, PostgreSQL 17.11

## Context

VRA requires a primary JVM language for its transactional core and related backend
workloads.

This decision is important because the language will be used across correctness-critical
domains including orders, inventory, fulfillment, payments, settlement, reconciliation,
workers, and future JVM services extracted from the initial modular core.

The decision was therefore not made from language preference, syntax examples, or
framework popularity alone.

POC-00 implemented and independently reviewed equivalent Java and Kotlin candidates on
the same JVM/Spring/PostgreSQL baseline, followed by a controlled requirement-evolution
experiment.

The validation focused on behavior relevant to VRA:

- domain modeling
- explicit state transitions
- typed failures
- strong identifiers
- nullability
- transaction boundaries
- JDBC and PostgreSQL integration
- Flyway migrations
- Spring integration
- architecture boundaries
- compiler assistance during model evolution
- testability
- HTTP error handling
- maintenance/change safety
- implementation and review friction

## Options Evaluated

### Java

The Java candidate used Java 21 language capabilities including:

- records where appropriate
- enums
- sealed failure hierarchies
- exhaustive switch expressions
- `Optional` for explicit optional domain values

### Kotlin

The Kotlin candidate used Kotlin 2.4.20 capabilities including:

- value classes
- data classes
- enums
- sealed failure hierarchies
- exhaustive `when`
- native nullable types

Both candidates used the same general architecture, PostgreSQL engine/version, shared
database migrations, HTTP contract, representative business behavior, and equivalent
verification scenarios.

## Evidence

POC-00 preserved three Git checkpoints:

- Java candidate: `febaaa0`
- Kotlin candidate: `6d562d8`
- Controlled evolution: `f530827`

Detailed evidence is preserved in:

- [`validation/poc-00/evidence/java.md`](../../validation/poc-00/evidence/java.md)
- [`validation/poc-00/evidence/kotlin.md`](../../validation/poc-00/evidence/kotlin.md)
- [`validation/poc-00/evidence/evolution.md`](../../validation/poc-00/evidence/evolution.md)
- [`validation/poc-00/EVOLUTION_SPEC.md`](../../validation/poc-00/EVOLUTION_SPEC.md)

## Findings

### Correctness and Runtime Behavior

Both candidates successfully demonstrated the required Spring Boot, PostgreSQL, JDBC,
Flyway, transaction, HTTP, and architectural behavior.

Both candidates passed their final automated and PostgreSQL integration suites.

Both candidates also passed the controlled POC-00-C evolution that introduced:

- a new `EXPIRED` order state
- typed order reason codes
- new state-specific invariants
- a typed invalid-SKU failure
- an additive PostgreSQL V3 migration
- equivalent HTTP behavior

There was no evidence that either language was incapable of implementing the required
VRA transactional architecture.

### Compiler Assistance

During controlled evolution, both Java and Kotlin detected stale exhaustive state
handling when `EXPIRED` was introduced.

Both also detected incomplete handling after a new sealed `InvalidSkuId` failure was
added.

Adding the new order reason-code constructor component exposed affected construction
sites in both candidates.

For the tested VRA scenarios, modern Java provided substantially more compile-time
assistance for exhaustive state and failure evolution than would be expected from older
Java versions.

### Null Safety

Kotlin has the stronger language-level null-safety model.

Its native nullable type:

`OrderReasonCode?`

provides stronger source-level nullability guarantees than Java reference types.

Java represented optional domain values explicitly with:

`Optional<OrderReasonCode>`

This is more verbose and does not provide all of Kotlin's language-level null guarantees.

Kotlin's null-safety advantage is real and should not be hidden by this decision.

### Spring, JDBC, and Toolchain Integration

Java is the native language boundary for the selected Spring/JVM stack and required less
language-interoperability machinery.

The Kotlin candidate required additional Kotlin compiler/plugin integration, reflection
and Jackson support, and interaction with Java platform types and Java-oriented APIs.

The Kotlin implementation remained fully viable, but the validation exposed additional
interop and tooling surfaces that required review and correction.

The Java candidate achieved the required correctness and state-evolution behavior without
that additional language boundary.

### Change Surface

Both candidates evolved successfully.

Observed source-line differences are retained as descriptive evidence only. Fewer lines
of code are not treated as an architectural quality metric, and the baseline test suites
were not identical enough for raw line counts to determine the decision.

The experiment did not demonstrate a maintenance/change-surface advantage large enough
to outweigh the additional Kotlin integration surface for this architecture.

## Decision

VRA will use **Java as its primary JVM language**.

Java is the default language for:

- the modular transactional core
- application and domain modules
- PostgreSQL-backed backend components
- workers and schedulers
- reconciliation processes
- projection workers
- future JVM services extracted from the core

This decision is specific to VRA's architecture, technology stack, operational goals,
and validation evidence.

It is not a claim that Java is universally superior to Kotlin.

## Rationale

VRA prioritizes:

1. security
2. correctness
3. architectural clarity
4. operations and recovery
5. evidence-driven complexity

Kotlin demonstrated meaningful advantages, particularly language-level null safety and
concise domain modeling.

However, POC-00 demonstrated that Java 21 provides the compile-time state/failure
evolution support required by the tested VRA model while remaining on the most direct
integration path for the selected Spring/JVM ecosystem.

The additional Kotlin language/tooling/interoperability surface therefore did not provide
enough demonstrated VRA-specific benefit to justify making it the primary language.

This follows VRA's principle of not adopting additional complexity without evidence that
the additional complexity is necessary or materially beneficial.

## Consequences

New VRA JVM backend production code should use Java by default.

The production repository must not arbitrarily mix Java and Kotlin by domain or module.

Kotlin is not prohibited. Introducing Kotlin into production VRA backend systems requires
a concrete use case and new evidence showing that its benefit outweighs the operational,
tooling, interoperability, and mixed-language maintenance cost.

The Kotlin POC remains in the repository as validation evidence and must not be rewritten
to make the selected language appear stronger.

Java nullability must be handled deliberately through domain design, explicit optional
values where appropriate, validation, database constraints, tests, and narrow boundary
handling.

## Version Policy

This ADR chooses the primary JVM **language**.

It does not permanently freeze:

- Java 21
- Spring Boot 3.5.16
- Gradle 8.14.3
- PostgreSQL 17.11

Those versions are the validated POC-00 baseline.

Future upgrades may be adopted through normal compatibility, testing, operational, and
architecture review without revisiting the language decision unless the upgrade changes
the assumptions behind this ADR.

## Non-Decisions

POC-00 and this ADR do not decide:

- the final persistence abstraction strategy
- the final JPA versus SQL/jOOQ split
- production authentication or identity provider
- production secrets management
- high-contention inventory strategy
- messaging infrastructure
- search infrastructure
- Kubernetes adoption
- multi-region architecture
- sharding

Those decisions remain governed by later validation and architecture work.

## Revisit Conditions

Revisit this ADR only when material new evidence changes the trade-off, for example:

- a future VRA requirement cannot be modeled safely or maintainably with the selected Java baseline;
- Kotlin provides a demonstrated capability that materially improves VRA correctness or operations;
- the backend technology stack changes enough that the current Java integration advantage no longer applies;
- maintaining Java creates measurable engineering risk that exceeds the cost of changing the primary language.

A preference for different syntax alone is not sufficient reason to revise this ADR.

Any revision must preserve this record and create a new ADR describing the new evidence
and migration consequences.
EOFcat > docs/adr/ADR-001-primary-jvm-language.md <<'EOF'
