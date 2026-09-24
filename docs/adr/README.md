# VRA Architecture Decision Records

Architecture Decision Records (ADRs) preserve significant technical and architectural
decisions for VRA together with the context, evidence, trade-offs, and consequences
known at the time of the decision.

ADRs are not substitutes for the current architecture documentation. The canonical
design describes how VRA is intended to work now; ADRs explain why important decisions
were made and preserve their history.

## Statuses

VRA ADRs use the following statuses:

- `PROPOSED` — a decision has been proposed but is not yet accepted.
- `VALIDATING` — evidence is actively being collected before a decision.
- `ACCEPTED` — the decision is the current architectural policy.
- `REVISED` — the decision remains relevant but has been materially updated by a later ADR.
- `SUPERSEDED` — a later ADR replaces this decision.
- `DEPRECATED` — the decision should no longer be used for new work.

Accepted ADRs are historical records. Do not silently rewrite an accepted decision to
match a later implementation. When an architectural decision materially changes, create
a new ADR and link the relationship between the records.

## Decision Process

Significant decisions should follow VRA's evidence-first decision chain where applicable:

Business Requirement
→ Invariant
→ Risk / Threat
→ Consistency
→ Availability
→ Performance
→ Operations
→ Architecture
→ Technology

Experiments and POCs should preserve factual evidence separately from the ADR. The ADR
may summarize that evidence and make the decision, but should link back to the original
validation material.

## Index

| ADR | Decision | Status | Date |
| --- | --- | --- | --- |
| [ADR-001](ADR-001-primary-jvm-language.md) | Primary JVM language | ACCEPTED | 2026-09-24 |
