# VRA Branch Plan

**สถานะ:** ACTIVE — planning baseline v1
**Authority:** `docs/ROADMAP.md` กำหนดทิศทางและ phase; เอกสารนี้กำหนด execution branch-by-branch
**Current branch:** `main`

---

## 1. Purpose

`BRANCH_PLAN.md` เป็น execution map ของ VRA ระดับ Git branch

ความสัมพันธ์กับ `ROADMAP.md`:

```text
ROADMAP.md
= ไปทางไหน
= phase ใหญ่คืออะไร
= dependency ระดับระบบ
= technology/architecture gates

BRANCH_PLAN.md
= แต่ละ branch ทำอะไร
= scope อะไรเข้าได้
= อะไรห้ามทำ
= ต้องพึ่ง branch ไหน
= exit gate คืออะไร
= branch ถัดไปคืออะไร
```

`BRANCH_PLAN.md` ไม่แทน `ROADMAP.md`, ADR, POC specification หรือ canonical domain documents

รายละเอียด implementation ของ branch ปัจจุบันต้องอยู่ใน branch-specific spec เช่น:

```text
validation/poc-01/SHARED_SPEC.md
```

---

## 2. Planning Rule

หมายเลข branch `01–100` เป็น **planning namespace** ไม่ใช่ข้อบังคับว่าจะต้องสร้างครบ 100 branch จริง

แต่ละ slot สามารถ:

- `PLANNED` — อยู่ใน canonical sequence แล้ว
- `READY` — prerequisite ครบและพร้อมสร้าง branch
- `IN PROGRESS` — branch ปัจจุบัน
- `REVIEW` — implementation เสร็จและกำลัง review/evidence
- `CLOSED` — merge/decision เสร็จ
- `PROPOSED` — มีเหตุผลจาก roadmap แต่ exact scope ยังไม่ freeze
- `CONDITIONAL` — ทำเมื่อมี evidence/requirement จริง
- `SUPERSEDED` — ถูกแทนด้วย branch/decision ใหม่
- `SKIPPED` — review แล้วไม่จำเป็น

Branch ที่ยังเป็น `PROPOSED` หรือ `CONDITIONAL` ห้ามตีความว่า technology/implementation ถูก lock แล้ว

การ split, merge, reorder หรือ skip branch หลัง `05` ทำได้เมื่อมี evidence แต่ต้อง review และแก้เอกสารนี้แบบ explicit

### 2.1 Commitment Level

การมี slot อยู่ในไฟล์นี้ไม่ได้แปลว่า branch นั้นได้รับอนุมัติให้สร้างแล้ว

```text
01–03
= CLOSED validation branches

04–05
= planned validation sequence ตาม ROADMAP

06
= proposed foundation-freeze checkpoint หลัง POC-01..05

07–100
= forecast execution map เท่านั้น
```

สำหรับ `07–100`:

- branch name เป็น working name
- scope เป็น planning hypothesis
- dependency เป็น provisional dependency
- ห้ามสร้าง branch จน status ถูก review เป็น `READY`
- ห้ามตีความหนึ่ง row ว่าต้องเท่ากับหนึ่ง implementation branch เสมอ
- สามารถ merge/split/reorder/skip ได้เมื่อ dependency และ evidence justify

เป้าหมายของเลข `01–100` คือ traceability ไม่ใช่การสร้างงานให้ครบจำนวน

### 2.2 Dependency Rule

ตาราง branch ต้องระบุ `Depends on`

dependency ของ `PROPOSED`/`CONDITIONAL` เป็น provisional และต้อง revalidate ก่อนเปลี่ยนเป็น `READY`

ถ้า dependency ที่ระบุถูก `SKIPPED` หรือ `SUPERSEDED` ห้ามเดินต่อโดยอัตโนมัติ ต้อง review replacement dependency ก่อน

---

## 3. Git Governance

Git mutation ทำโดย user หลัง review

Automation/Codex ห้ามทำ Git-mutating commands

ก่อนเริ่ม branch ใหม่ต้องยืนยัน:

```text
previous branch gate passed
canonical docs consistent
required ADRs accepted
working tree clean
base commit known
branch scope frozen
forbidden scope explicit
```

ก่อน commit/merge ต้องมี:

```text
verification
evidence
independent review
git diff --check
staged diff review
```

---

## 4. Branch Naming

Baseline:

```text
poc/NN-name
foundation/NN-name
domain/NN-name
experience/NN-name
ops/NN-name
scale/NN-name
milestone/NN-name
```

หมายเลขสองหลักใช้ `01–99`; milestone 100 ใช้ `100`

ชื่อ branch จริงสามารถปรับก่อนสร้าง หาก scope ที่ review แล้วชัดกว่า แต่หมายเลข planning slot ต้อง trace กลับมาได้

---

## 5. Validation / Production Foundation — 01–06

ช่วงนี้พิสูจน์ foundation ก่อนเริ่ม core product implementation

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 01 | `poc/01-transactional-core` | **CLOSED** | Java/Spring/PostgreSQL production-candidate foundation, Gradle/module boundary, Flyway, runtime/migrator roles, transaction/persistence, health/readiness, error contract, Testcontainers/architecture tests | Canonical docs baseline + ADR-001 | POC-01 evidence + independent review ผ่าน |
| 02 | `poc/02-concurrency-idempotency` | **CLOSED** | inventory concurrency, idempotency semantics, duplicate/concurrent commands, optimistic concurrency; stock=1/500 proof | 01 | correctness under concurrency proven |
| 03 | `poc/03-outbox-recovery` | CLOSED | transactional outbox, worker claiming, inbox/dedupe, retry/backoff, dead-letter/reconciliation, crash recovery | 02 | async work/recovery semantics proven |
| 04 | `poc/04-security-auth` | PLANNED | OIDC/OAuth boundary, account identity mapping, browser session/BFF, CSRF, privileged MFA/step-up, authorization, workload identity, DB least-privilege hardening, secrets, webhook security, audit | 03 | trust boundaries/security gate proven |
| 05 | `poc/05-observability-performance` | PLANNED | logs/metrics/traces, RED/USE, correctness/freshness signals, fault tests, k6 baseline, resource/latency evidence | 04 | observability/performance/fault baseline proven |
| 06 | `foundation/06-production-foundation-freeze` | PROPOSED | review POC-01..05 together, resolve contradictions, finalize production foundation decisions/ADRs | 01–05 | Production Foundation Baseline accepted |

### 5.1 Forbidden Scope for 01–06

ช่วง validation ห้ามสร้าง marketplace domains เต็มระบบ, specialized infrastructure หรือ UX จำนวนมากเพียงเพื่อให้ POC ดูสมบูรณ์

### 5.2 Cross-Cutting Audit Rule

POC-04 ต้อง validate audit/security-event baseline และหลัง foundation freeze ทุก sensitive production branch ต้องสร้าง audit evidence ตาม risk ของ operation นั้น

ดังนั้น audit capture **ห้ามรอถึง branch 70**

branch 70 มีหน้าที่ harden ความสามารถด้าน investigation, query, retention และ operational audit tooling หลัง event capture baseline มีอยู่แล้ว ไม่ใช่ branch แรกที่ระบบเริ่ม audit

---

## 6. Identity / Seller Foundation — 07–14

อิงลำดับ core domain: Identity/Account → Seller/Merchant

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 07 | `domain/07-account-core` | PROPOSED | account aggregate/identity mapping, strong IDs, lifecycle skeleton | 06 | account source-of-truth ชัด |
| 08 | `domain/08-account-lifecycle` | PROPOSED | `ACTIVE/LOCKED/DISABLED/CLOSED`, controlled transitions/reasons | 07 | lifecycle invariants tested |
| 09 | `domain/09-account-recovery` | PROPOSED | recovery lifecycle, revocation hooks, security-sensitive state changes | 08 | recovery state machine proven |
| 10 | `domain/10-authorization-foundation` | PROPOSED | RBAC + ownership + state/context policy foundation | 06, 07–09 | object-level authorization testable |
| 11 | `domain/11-staff-identity` | PROPOSED | staff identity, privileged role boundary, no public staff signup | 10 | privileged actor boundary proven |
| 12 | `domain/12-seller-core` | PROPOSED | seller/merchant profile and lifecycle | 10 | seller authority/lifecycle explicit |
| 13 | `domain/13-seller-membership` | PROPOSED | memberships, roles, seller ownership/isolation | 12 | cross-seller isolation tested |
| 14 | `domain/14-seller-onboarding-compliance` | PROPOSED | onboarding state, compliance hooks, staff-assisted operations boundary | 12–13 | onboarding/compliance states explicit |

---

## 7. Catalog / Offer — 15–22

อิง model:

```text
Product → Variant → SKU → Offer
```

publication/compliance/buyability ต้องไม่ถูกยุบเป็น boolean เดียว

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 15 | `domain/15-product-core` | PROPOSED | Product identity/content ownership | 06 | product aggregate contract stable |
| 16 | `domain/16-product-variant` | PROPOSED | Variant model and relationship to Product | 15 | variant invariants tested |
| 17 | `domain/17-sku-core` | PROPOSED | SKU identity/sellable unit semantics | 16 | SKU authority explicit |
| 18 | `domain/18-catalog-lifecycle` | PROPOSED | catalog lifecycle distinct from publication/compliance | 15–17 | lifecycle state machine tested |
| 19 | `domain/19-publication-compliance` | PROPOSED | publication and compliance state separation | 18 | `PUBLISHED != BUYABLE` preserved |
| 20 | `domain/20-offer-core` | PROPOSED | seller + SKU offer identity/lifecycle | 13, 17, 19 | offer ownership isolated |
| 21 | `domain/21-pricing-money` | PROPOSED | exact money, currency, pricing validity | 20 | no floating-money ambiguity |
| 22 | `domain/22-buyability-policy` | PROPOSED | combine lifecycle/compliance/offer policy without making inventory secondary truth | 19–21 | buyability decision reproducible |

---

## 8. Inventory — 23–31

Authoritative dimensions:

```text
SKU + Owner + Location + Status
```

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 23 | `domain/23-inventory-balance` | PROPOSED | production inventory balance model | 13, 17 | ATP invariant protected |
| 24 | `domain/24-inventory-movement` | PROPOSED | movement history and reason semantics | 23 | quantity mutations traceable |
| 25 | `domain/25-inventory-reservation` | PROPOSED | productionize reservation capability from validated foundation where reusable | 23–24; evidence 01–02 | reservation/release invariants proven |
| 26 | `domain/26-inventory-allocation` | PROPOSED | allocation distinct from reservation | 25 | allocation ownership/state explicit |
| 27 | `domain/27-inventory-release-expiry` | PROPOSED | release/expiry idempotency and lifecycle | 25 | no leaked reservations |
| 28 | `domain/28-inventory-status` | PROPOSED | available/damaged/quarantine/other status semantics | 23 | unsellable stock excluded correctly |
| 29 | `domain/29-inventory-location-owner` | PROPOSED | owner/location dimension and isolation | 13, 23, 28 | cross-owner/location correctness tested |
| 30 | `domain/30-inventory-reconciliation` | PROPOSED | reconciliation commands/reports and discrepancy handling | 23–29 | drift detectable/recoverable |
| 31 | `domain/31-inventory-operations` | PROPOSED | staff-safe corrections through explicit commands; no generic quantity PATCH | 10–11, 30 | operational mutation audit complete |

---

## 9. Cart / Checkout / Order — 32–42

ต้องรักษา:

```text
Cart != CheckoutSession != Order != Payment != Reservation
```

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 32 | `domain/32-cart-core` | PROPOSED | mutable customer intent/cart ownership | 10, 20–22 | cart semantics separate from order |
| 33 | `domain/33-cart-validation` | PROPOSED | offer references, quantity/input constraints, stale-data behavior | 32 | cart cannot masquerade as guarantee |
| 34 | `domain/34-checkout-session` | PROPOSED | CheckoutSession snapshot | 32–33 | snapshot contract stable |
| 35 | `domain/35-checkout-revalidation` | PROPOSED | critical fact revalidation before durable order | 22, 25, 34 | stale offer/stock handled explicitly |
| 36 | `domain/36-checkout-atomicity` | PROPOSED | selected-checkout all-or-nothing v1 semantics | 35 | partial acceptance forbidden |
| 37 | `domain/37-order-core` | PROPOSED | durable Order aggregate and lifecycle | 36 | order source-of-truth explicit |
| 38 | `domain/38-order-state-reasons` | PROPOSED | controlled transitions, typed operational reason codes | 37 | illegal transitions rejected |
| 39 | `domain/39-order-lines-snapshots` | PROPOSED | durable commercial snapshots needed by order | 37 | historical order meaning preserved |
| 40 | `domain/40-multi-seller-grouping` | PROPOSED | order grouping across sellers | 13, 37–39 | seller grouping semantics explicit |
| 41 | `domain/41-order-cancellation` | PROPOSED | cancellation policy/compensation hooks | 37–40 | cancellation state machine tested |
| 42 | `domain/42-order-reconciliation` | PROPOSED | detect/repair order-side cross-boundary drift through explicit workflow | 03, 37–41 | discrepancies observable |

---

## 10. Payment / Refund / Finance — 43–54

External provider ambiguity ต้องรองรับ `UNKNOWN`; ห้าม blind retry

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 43 | `domain/43-payment-operation` | PROPOSED | durable payment operation before provider outcome | 03, 37 | payment operation lifecycle explicit |
| 44 | `domain/44-payment-provider-adapter` | PROPOSED | provider boundary, request/response mapping | 43 | provider contract isolated |
| 45 | `domain/45-payment-unknown` | PROPOSED | timeout/ambiguous outcome → `UNKNOWN` | 44 | no blind duplicate charge |
| 46 | `domain/46-payment-webhook` | PROPOSED | authenticated webhook ingestion, dedupe, ordering-safe processing | 04, 43–45 | duplicate webhook safe |
| 47 | `domain/47-payment-reconciliation` | PROPOSED | provider vs internal truth reconciliation | 43–46 | ambiguity recoverable |
| 48 | `domain/48-refund-core` | PROPOSED | durable refund operation | 43, 47 | refund lifecycle explicit |
| 49 | `domain/49-refund-cap-concurrency` | PROPOSED | cumulative cap/concurrency correctness | 02, 48 | refund cannot exceed captured amount |
| 50 | `domain/50-financial-ledger` | PROPOSED | immutable double-entry journal when business scope requires | 43–49 | journal balanced/exact |
| 51 | `domain/51-settlement-core` | PROPOSED | settlement lifecycle | 50 | settlement not inferred from order status |
| 52 | `domain/52-payout-operation` | PROPOSED | payout command/operation/idempotency | 51 | one intended transfer per identity |
| 53 | `domain/53-payout-unknown-reconcile` | PROPOSED | payout ambiguity/reconciliation | 52 | no blind duplicate payout |
| 54 | `domain/54-finance-operations` | PROPOSED | finance support/reconciliation workflow | 47–53 | manual SQL not routine workflow |

---

## 11. Fulfillment / Shipment / Logistics — 55–68

Model direction:

```text
Order → Fulfillment → Shipment → Package
Plan → Legs → Jobs / Trips
```

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 55 | `domain/55-fulfillment-core` | PROPOSED | fulfillment aggregate/lifecycle | 37 | fulfillment separate from order/payment |
| 56 | `domain/56-fulfillment-split` | PROPOSED | multiple fulfillment/shipment semantics | 55 | split lifecycle tested |
| 57 | `domain/57-shipment-core` | PROPOSED | shipment identity/state | 55–56 | shipment authority explicit |
| 58 | `domain/58-package-core` | PROPOSED | package model and shipment relationship | 57 | package lifecycle explicit |
| 59 | `domain/59-logistics-plan` | PROPOSED | delivery/logistics Plan + Legs | 57 | routing plan not flattened into order |
| 60 | `domain/60-driver-job-trip` | PROPOSED | Job/Trip model | 59 | executable work explicit |
| 61 | `domain/61-driver-assignment` | PROPOSED | assignment lifecycle and one-active-assignment invariant | 02, 60 | competing assignment safe |
| 62 | `domain/62-custody` | PROPOSED | explicit custody transitions | 57, 61 | custody independent from GPS |
| 63 | `domain/63-delivery-events` | PROPOSED | pickup/in-transit/delivered/failure operational events | 62 | transitions typed/auditable |
| 64 | `domain/64-offline-driver-commands` | PROPOSED | durable offline mutation/retry/idempotency model | 03, 60–63 | duplicate offline commands safe |
| 65 | `domain/65-tracking-observations` | PROPOSED | raw tracking observations ingestion | 03, 59–64 | duplicate/out-of-order tolerated |
| 66 | `domain/66-tracking-operational-state` | PROPOSED | current operational projection from raw observations | 65 | rebuildable operational view |
| 67 | `domain/67-carrier-ambiguity` | PROPOSED | external carrier ambiguity/`UNKNOWN` handling | 57, 66 | uncertainty explicit |
| 68 | `domain/68-logistics-reconciliation` | PROPOSED | custody/tracking/job discrepancy detection | 61–67 | operational drift recoverable |

---

## 12. Support / Audit / Derived Planes — 69–78

Derived systems ห้ามกลายเป็น authoritative truth โดยไม่ review

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 69 | `domain/69-support-workflows` | PROPOSED | customer/seller/fulfillment/finance support commands | 10–14; applicable domain operations | direct SQL not routine support |
| 70 | `domain/70-audit-investigation` | PROPOSED | investigation/query/retention hardening for protected audit records; audit capture is cross-cutting from security foundation onward | 04; audit-producing branches | investigation trail complete and operationally searchable |
| 71 | `domain/71-object-storage` | PROPOSED | S3-compatible private-by-default media/document boundary; may be pulled forward when the first product slice has a real object-storage dependency | 04, 06 | object access policy tested |
| 72 | `domain/72-notifications` | PROPOSED | outbox-driven email/push/SMS provider workflow | 03; relevant domain events | notification failure independent from order tx |
| 73 | `domain/73-search-read-model` | PROPOSED | PostgreSQL/read-model search baseline | 15–22 | authoritative/derived boundary explicit |
| 74 | `scale/74-search-engine-decision` | CONDITIONAL | measure whether dedicated search engine is justified | 73 + workload evidence | adopt/defer decision with evidence |
| 75 | `domain/75-tracking-customer-projection` | PROPOSED | customer-friendly tracking projection | 65–66 | rebuildable derived view |
| 76 | `domain/76-analytics-events` | PROPOSED | analytics event/export boundary that does not burden OLTP | 03; core domain events | analytics isolated from OLTP correctness |
| 77 | `scale/77-analytics-store-decision` | CONDITIONAL | assess ClickHouse/warehouse need from workload evidence | 76 + workload evidence | adopt/defer decision |
| 78 | `domain/78-exports-reports` | PROPOSED | asynchronous reports/exports using object storage | 71–72 | large exports bounded/auditable |

---

## 13. Web / Product Experience — 79–88

`docs/BRAND.md` เป็น authority ด้าน brand/UI

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 79 | `experience/79-web-foundation` | PROPOSED | Next.js/TypeScript app foundation, tokens, accessible primitives, session integration boundary | 04, 06 | frontend foundation passes accessibility/contract gate |
| 80 | `experience/80-customer-catalog` | PROPOSED | product/search/browse experience | 73, 79 | catalog UX uses canonical read models |
| 81 | `experience/81-customer-cart` | PROPOSED | cart experience | 32–33, 79 | cart semantics preserved |
| 82 | `experience/82-customer-checkout` | PROPOSED | checkout flow and failure states | 34–36, 79 | checkout uncertainty/errors explicit |
| 83 | `experience/83-customer-orders` | PROPOSED | order history/detail/status | 37–42, 79 | durable order state rendered correctly |
| 84 | `experience/84-customer-tracking` | PROPOSED | customer tracking experience | 75, 79 | derived tracking semantics clear |
| 85 | `experience/85-seller-portal` | PROPOSED | seller shell/navigation/identity isolation | 12–14, 79 | seller isolation tested |
| 86 | `experience/86-seller-operations` | PROPOSED | offer/inventory/order/fulfillment workflows | 20–31, 37–58, 79 | write paths use explicit commands |
| 87 | `experience/87-staff-console` | PROPOSED | high-density staff operations UI | 11, 69–70, 79 | privileges/reasons/audit visible |
| 88 | `experience/88-privileged-ux` | PROPOSED | maker-checker, step-up, sensitive action confirmations | 04, 87 | privileged UX aligned with security policy |

---

## 14. Production Readiness / Staging / Deployment — 89–95

ก่อน production ต้อง freeze release candidate ด้วย commit/artifact/migration/config identity

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 89 | `ops/89-production-readiness` | PROPOSED | cross-system readiness gap closure: security, migrations, reconciliation, retention, runbooks | 06; applicable 07–88 | readiness checklist green |
| 90 | `ops/90-staging-environment` | PROPOSED | staging topology, DB roles, workers, object storage, providers, monitoring | 89 | staging representative of production boundaries |
| 91 | `ops/91-e2e-release-candidate` | PROPOSED | freeze candidate SHA/artifact/config/migration and execute E2E | 90 | RC identified and E2E green |
| 92 | `ops/92-backup-restore-dr` | PROPOSED | off-host backup, restore drill, PITR/DR evidence, post-restore reconciliation | 90 | restore tested with evidence |
| 93 | `ops/93-load-fault-security-gates` | PROPOSED | k6/load, concurrency regression, fault injection, adversarial/security regression | 02, 04, 05, 91–92 | production test gates green |
| 94 | `ops/94-safe-host-deployment` | PROPOSED | read-only host preflight then DB bootstrap → migration → grants → backend/worker/frontend/proxy/TLS | 91–93 | deployment completed without destructive shortcut |
| 95 | `ops/95-production-verification` | PROPOSED | smoke, monitoring, alerts, reconciliation, backup verification, initial observation window | 94 | production baseline verified |

---

## 15. Evidence-Driven Scale — 96–99

ห้ามเพิ่ม specialized infrastructure เพียงเพราะ “ระบบใหญ่ควรมี”

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 96 | `scale/96-redis-decision` | CONDITIONAL | measure cache/ephemeral coordination/rate-limit need | 95 + workload evidence | adopt/defer Redis with evidence |
| 97 | `scale/97-broker-decision` | CONDITIONAL | measure PostgreSQL worker limits/fan-out/replay/consumer requirements | 95 + workload evidence | adopt/defer Kafka/RabbitMQ/NATS |
| 98 | `scale/98-data-plane-decision` | CONDITIONAL | revisit OpenSearch/analytics/special tracking DB from real workload | 95 + workload evidence | adopt/defer specialized data planes |
| 99 | `scale/99-service-extraction` | CONDITIONAL | identify service extraction candidates from scaling/failure/team/deployment evidence | 95; decisions 96–98 | keep modular core or extract specific boundary with ADR |

---

## 16. Milestone 100

| ID | Branch | Status | Scope | Depends on | Exit gate |
|---:|---|---|---|---|---|
| 100 | `milestone/100-production-baseline-review` | PROPOSED | full architecture/operations/security/product review against actual production evidence; reconcile docs/ADRs/roadmap/branch plan | 95; applicable 96–99 | VRA production baseline version recorded; next roadmap explicitly approved |

Branch 100 ไม่ได้หมายความว่า “โปรเจกต์จบ”

มันเป็น checkpoint ที่ถามว่า:

```text
architecture ปัจจุบันยังเหมาะหรือไม่
อะไรถูกพิสูจน์แล้ว
อะไรควรถูกลด/เลิก
อะไรต้อง scale
อะไรต้องแยก service
อะไรยังควรอยู่ modular core
production risks ที่เหลือคืออะไร
roadmap รุ่นถัดไปควรเป็นอะไร
```

---

## 17. Current Execution Pointer

Current closure:

```text
03 — poc/03-outbox-recovery
Status: CLOSED
Final review checkpoint / closure commit: 073f63e378bb7b2ef1547b8bae9d700543c0eb40
Evidence: validation/poc-03/evidence/RESULTS.md
ADR decision: no new/revised ADR required
PR #1: merged; POC-03 branch head 5a88d4d9a562652fc856a32d266ddc055e8e6c37 reachable from main — VERIFIED
Merge commit: fb8d0034be9d57aebcf07c5bb8702d3654ee5733
Remote synchronization: VERIFIED / CLOSED
Hosted CI: PASS — PR Backend CI 36586572864 and post-merge main Backend CI 36588526956
```

Prior closures: `02 — poc/02-concurrency-idempotency` remains CLOSED; its final
review checkpoint was `a7a908c`, with evidence in
`validation/poc-02/evidence/RESULTS.md` and no new/revised ADR required.
`01 — poc/01-transactional-core` also remains CLOSED; its final review checkpoint
was `fd6e97a`, and its material foundation decision is recorded in
`docs/adr/ADR-002-backend-production-foundation.md`.

Next planned:

```text
04 — poc/04-security-auth
Status: PLANNED
Depends on: POC-03 CLOSED
```

POC-04 has not started. Before its implementation, follow the normal
governance sequence:

```text
read-only repository / canonical-scope audit
→ branch-specific spec draft
→ independent spec review
→ freeze
→ exact implementation plan
→ independent plan review
→ approval checkpoint
→ user branch activation
→ implementation
```

---

## 18. Branch Scope Rule

ทุก branch ต้องมีอย่างน้อย:

```text
Purpose
Prerequisites
In scope
Out of scope
Invariants
Threat/risk considerations
Persistence/consistency implications
Operational implications
Tests
Evidence
Exit criteria
```

branch-specific spec อาจเล็กหรือใหญ่ตาม risk แต่ห้ามไม่มี scope boundary

---

## 19. No Rewrite Rule

เมื่อ branch ปิดแล้ว:

- evidence เป็น historical truth
- ห้าม rewrite result เพื่อให้ตรงกับ decision ภายหลัง
- ถ้า decision เปลี่ยน ให้ ADR/branch ใหม่อธิบายการเปลี่ยน
- bug fix ที่พบภายหลังต้อง trace ไป branch/commit ใหม่
- `SUPERSEDED` ไม่เท่ากับ “ของเก่าผิด”; หมายถึงมี decision ใหม่ที่แทนที่

---

## 20. Change Governance

การเปลี่ยน branch plan ต้องตอบ:

1. requirement อะไรเปลี่ยน
2. invariant/risk อะไรทำให้ต้องเปลี่ยน
3. branch ไหนได้รับผล
4. dependency เปลี่ยนหรือไม่
5. canonical docs/ADR ต้องเปลี่ยนหรือไม่
6. evidence เดิมยัง valid หรือไม่
7. branch ที่ถูก split/merge/reorder มี migration path อย่างไร

ห้าม reorder เพื่อความสะดวกโดยไม่ตรวจ dependency

---

## 21. Authority Boundary

ถ้าเอกสารขัดกัน ให้ใช้บทบาทดังนี้:

```text
PRODUCT.md
→ product truth / business invariant

DESIGN.md
→ architecture and system boundary

SECURITY.md
→ security invariant / trust boundary

TESTING.md
→ verification policy

OPERATIONS.md
→ runtime/deployment/recovery policy

ROADMAP.md
→ strategic development sequence

BRANCH_PLAN.md
→ executable branch sequence and branch scope

ADR
→ accepted material technical decision

branch-specific SHARED_SPEC.md
→ exact frozen scope for branch currently being executed
```

`BRANCH_PLAN.md` ห้าม override accepted ADR หรือ canonical invariant

---

## 22. Status Update Rule

เมื่อ branch เปลี่ยนสถานะ ให้แก้เฉพาะสถานะที่พิสูจน์ได้

ตัวอย่าง:

```text
PROPOSED
→ READY
→ IN PROGRESS
→ REVIEW
→ CLOSED
```

ห้าม mark `CLOSED` ก่อน:

- exit gate ผ่าน
- evidence complete
- independent review complete
- Git checkpoint/merge ตาม workflow เสร็จ

สำหรับ conditional branch:

```text
CONDITIONAL
→ READY
```

ได้เมื่อ evidence trigger ที่กำหนดเกิดขึ้นจริงเท่านั้น
