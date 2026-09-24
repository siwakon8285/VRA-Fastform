# Roadmap การพัฒนา VRA

**เอกสาร:** `docs/ROADMAP.md`
**สถานะ:** ACTIVE — canonical baseline v1
**ผลิตภัณฑ์:** VRA (วีล่า)
**ขอบเขต:** ลำดับการ validate, สร้าง, harden, ทดสอบ และนำ VRA จาก architecture baseline ไปสู่ production-ready system โดยใช้ evidence-based gates

> เอกสารนี้กำหนดว่า “เราจะทำอะไรตามลำดับไหน”
> Product intent อยู่ใน `PRODUCT.md`
> Architecture อยู่ใน `DESIGN.md`
> Security baseline อยู่ใน `SECURITY.md`
> Verification strategy อยู่ใน `TESTING.md`
> Operational baseline อยู่ใน `OPERATIONS.md`
> Brand/UI baseline อยู่ใน `BRAND.md`
> Architectural decisions อยู่ใน `docs/adr/`

---

## 1. เป้าหมายของ Roadmap

Roadmap ของ VRA มีหน้าที่:

1. ป้องกันการเริ่ม feature จำนวนมากก่อน foundation พร้อม
2. บังคับให้ decision ใหญ่มี evidence ก่อน lock
3. แยก validation code ออกจาก production code
4. ทำให้ correctness/security/operations มาก่อน premature scale
5. ทำให้แต่ละ phase มี entry/exit gate ที่ตรวจได้
6. ป้องกัน scope leakage ระหว่าง POC
7. ลด architecture drift
8. ทำให้ Git history และ evidence audit ได้
9. ทำให้ Codex/automation ทำงานภายใต้ canonical docs เดียวกัน
10. ทำให้ production readiness เป็นผลจากการพิสูจน์ ไม่ใช่ความรู้สึก

---

## 2. Roadmap Principle

ลำดับการตัดสินของ VRA:

```text
Business Requirement
        ↓
Invariant
        ↓
Risk / Threat
        ↓
Consistency
        ↓
Availability
        ↓
Performance
        ↓
Operations
        ↓
Architecture
        ↓
Technology
        ↓
Implementation
```

Roadmap ห้ามย้อนเป็น:

```text
เลือก technology ก่อน
→ พยายามหา use case มารองรับ
```

---

## 3. Phase Model

ภาพรวม:

```text
Phase 0
Technology Validation
POC-00
✅ CLOSED

        ↓

Phase 1
Canonical Documentation Baseline
← ตอนนี้

        ↓

Phase 2
Production Foundation
POC-01

        ↓

Phase 3
Correctness Under Concurrency
POC-02

        ↓

Phase 4
Asynchronous Work / Recovery
POC-03

        ↓

Phase 5
Security / Identity / Least Privilege
POC-04

        ↓

Phase 6
Observability / Performance / Fault
POC-05

        ↓

Phase 7
Core Product Implementation

        ↓

Phase 8
Derived Systems + Experience

        ↓

Phase 9
Production Readiness / Staging

        ↓

Phase 10
Initial Production Deployment

        ↓

Phase 11
Evidence-Driven Scale / Extraction
```

---

## 4. Phase 0 — POC-00 JVM Language Validation

**Status:** CLOSED

เป้าหมาย:

> เลือก primary JVM language จาก evidence จริง

Candidates:

```text
Java
Kotlin
```

Validation:

- same JDK/Spring/PostgreSQL direction
- domain modeling
- JDBC
- migrations
- HTTP
- tests
- state evolution
- nullability
- compiler exhaustiveness
- reviewability

Result:

```text
Primary JVM Language = Java
```

Decision record:

```text
docs/adr/ADR-001-primary-jvm-language.md
```

Historical evidence:

```text
validation/poc-00/
```

POC-00 code เป็น evidence ไม่ใช่ production code

---

## 5. Phase 1 — Canonical Documentation Baseline

**Status:** CLOSED

ต้องสร้างและ review:

```text
docs/
├─ PRODUCT.md
├─ BRAND.md
├─ DESIGN.md
├─ SECURITY.md
├─ TESTING.md
├─ OPERATIONS.md
├─ ROADMAP.md
└─ adr/
```

เป้าหมาย:

> สร้าง source of truth ก่อนเริ่ม production foundation

Exit gate:

- product boundaries ชัด
- architecture direction ชัด
- security invariants ชัด
- testing strategy ชัด
- operations/recovery ชัด
- brand/UI direction ชัด
- roadmap dependency ชัด
- canonical docs cross-review แล้ว
- unresolved contradiction = 0
- `git diff --check` clean
- docs baseline committed

หลัง phase นี้ Codex ต้องอ่าน canonical docs ก่อน implementation ที่มีผลต่อ architecture

---

## 6. Documentation Governance Gate

ก่อน production code:

```text
docs define intent
ADR preserves decisions
tests prove behavior
code implements intent
evidence records experiments
```

ถ้า code กับ docs ขัดกัน:

```text
STOP
→ classify:
   defect?
   intentional architecture change?
→ review
→ ADR/update docs if needed
```

ห้าม automation เลือกเองว่า code หรือ docs “ถูกกว่า”

---

## 7. Phase 2 — POC-01 Transactional Core / Production Foundation

เป้าหมาย:

> พิสูจน์ production-oriented Java/Spring/PostgreSQL foundation ที่จะใช้สร้าง VRA จริง

POC-01 ไม่ใช่การสร้าง marketplace feature จำนวนมาก

ตั้งแต่ POC-01 เป็นต้นไป เราสามารถพัฒนา **production-candidate source**
ใน production tree จริงได้ ขณะที่ `validation/` ใช้เก็บ spec, harness และ evidence
ของการพิสูจน์ วิธีนี้ป้องกันการสร้าง throwaway foundation แล้วต้องเขียนระบบเดิมซ้ำ

production-candidate source จะถูกยอมรับเป็น production baseline
เมื่อผ่าน exit gate และ review ของ phase นั้นแล้วเท่านั้น

ต้อง validate foundation ก่อน

---

### 7.1 POC-01 Scope

ต้องพิสูจน์:

- Java/Spring Boot production baseline
- Gradle project/module layout
- package/module dependency rules
- PostgreSQL connectivity
- Flyway lifecycle
- runtime DB identity
- migrator DB identity
- transaction boundaries
- representative persistence approach
- configuration validation
- health
- readiness
- standardized error contract
- request correlation
- structured logging baseline
- Testcontainers
- architecture tests
- CI-compatible build
- artifact build

---

### 7.2 POC-01 Architecture Questions

ต้องตอบ:

1. production package/module structure แบบไหนเหมาะกับ VRA
2. Gradle single/multi-module แค่ไหนพอดี
3. JPA ใช้ตรงไหน
4. explicit SQL/JDBC/jOOQ ใช้ตรงไหน
5. Flyway migration รันขั้นตอนไหน
6. runtime role ได้สิทธิ์อะไร
7. migrator role own อะไร
8. configuration validate อย่างไร
9. health/readiness semantics คืออะไร
10. error contract รูปแบบไหน
11. architecture rules enforce อย่างไร

---

### 7.3 POC-01 Representative Domain

ใช้ domain slice เล็กแต่มีความหมาย เช่น:

```text
Order
Inventory
Reservation
```

หรือ capability subset ที่พอพิสูจน์ transaction/persistence boundary

ห้ามเริ่มทุก domain พร้อมกัน

---

### 7.4 POC-01 Persistence Validation

ต้องมี representative examples:

```text
simple aggregate persistence
critical conditional SQL
transaction rollback
constraint failure
optimistic version
```

ผลลัพธ์อาจนำไปสู่ decision:

```text
JPA for suitable aggregate CRUD
+
explicit SQL/jOOQ/JDBC for critical paths
```

แต่ต้อง validate ก่อน lock

---

### 7.5 POC-01 DB Least Privilege

ต้องพิสูจน์จริง:

```text
migrator
→ schema change succeeds

runtime
→ application DML succeeds

runtime
→ ALTER/DROP/CREATE TABLE denied
```

ห้ามใช้ superuser/runtime owner เพื่อทำให้ POC ผ่าน

ขอบเขตของ POC-01 ในเรื่องนี้คือการสร้างและพิสูจน์ **structural role separation**
กับ functional grants ที่ application ต้องใช้

POC-04 จะนำ model นี้ไปทำ security validation และ hardening เพิ่มเติม เช่น
forbidden privilege, privilege escalation, workload identity และ security boundary
โดยไม่เริ่ม privilege model ใหม่จากศูนย์

---

### 7.6 POC-01 Exit Gate

POC-01 ผ่านเมื่อ:

- production-oriented project builds
- module boundaries enforced
- migration works
- runtime/migrator separation proven
- representative transaction rollback proven
- PostgreSQL integration green
- health/readiness correct
- standardized errors correct
- architecture tests green
- clean build green
- no hidden skips
- evidence documented
- independent review complete

---

## 8. Phase 3 — POC-02 Concurrency and Idempotency

เป้าหมาย:

> พิสูจน์ correctness เมื่อหลาย actor/process ทำงานพร้อมกัน

ไม่ใช่ performance test

---

### 8.1 POC-02 Core Scenarios

### Inventory

```text
stock = 1
500 concurrent reservation attempts
exactly 1 success
available >= 0
```

### Dispatch

```text
1 exclusive delivery responsibility
1000 assignment attempts
exactly 1 active assignment
```

### Refund

```text
captured amount = X
parallel refund requests
sum(committed refund obligations) <= X
```

### Settlement / Payout

- duplicate command safe
- one payout operation per business identity
- UNKNOWN does not cause blind duplicate transfer

---

### 8.2 Idempotency

ต้อง validate:

- same key + same payload
- same key + conflicting payload
- retry after timeout
- retry after response loss
- concurrent same key
- key scope by actor/operation

---

### 8.3 Optimistic Concurrency

Representative flow:

```text
read version 10
two writers
first commits 11
second expected 10
→ conflict
```

---

### 8.4 POC-02 Metrics

แม้เป้าหมายหลักคือ correctness ต้องเก็บ:

- success count
- rejection count
- latency
- lock wait
- DB connections
- retries
- deadlocks
- CPU/RAM

เพื่อแยก correctness issue กับ capacity issue

---

### 8.5 POC-02 Exit Gate

ต้องมี deterministic evidence ว่า critical invariant ไม่พังภายใต้ contention

ห้ามใช้:

- global JVM mutex
- Redis lock
- distributed lock

ก่อนพิสูจน์ว่า PostgreSQL strategy ไม่พอ

---

## 9. Phase 4 — POC-03 Outbox / Workers / Recovery

เป้าหมาย:

> พิสูจน์ asynchronous side effects และ crash recovery โดยไม่สูญเสีย business intent

---

### 9.1 POC-03 Scope

- transactional outbox
- PostgreSQL worker
- claim/lease
- `SKIP LOCKED` หรือ selected pattern
- at-least-once delivery
- inbox/dedup
- bounded retry
- dead-letter
- worker crash/restart
- reconciliation
- projection rebuild
- event version/order semantics

---

### 9.2 Outbox Atomicity

ต้องพิสูจน์:

```text
business mutation
+
outbox insert
=
one DB transaction
```

---

### 9.3 Worker Crash

Scenario:

```text
claim job
perform work partially
process crashes
restart
reclaim/retry safely
```

ต้องไม่มี lost job

---

### 9.4 Duplicate Delivery

Consumer ต้อง safe ภายใต้:

```text
same event delivered N times
```

---

### 9.5 Dead-Letter

ต้องมี explicit failed state และ replay flow

ไม่ใช่ endless retry

---

### 9.6 Reconciliation

Representative external workflow เช่น payment simulation:

```text
internal = UNKNOWN
external = SUCCEEDED
→ reconcile safely
```

---

### 9.7 POC-03 Exit Gate

- no lost durable work
- duplicate-safe
- retry bounded
- failed work visible
- worker crash recoverable
- reconciliation proven
- projection rebuild proven
- metrics available
- architecture review complete

---

## 10. Phase 5 — POC-04 Security / Identity / Least Privilege

เป้าหมาย:

> พิสูจน์ trust boundaries จริง ไม่ใช่เพิ่ม auth ทีหลัง

---

### 10.1 POC-04 Scope

- external IdP candidate
- OIDC/OAuth
- business identity mapping
- account lifecycle
- authorization
- browser session/BFF
- CSRF
- MFA
- step-up
- staff role
- maker-checker representative flow
- security validation/hardening ของ DB least-privilege model ที่สร้างใน POC-01
- workload identity
- secrets workflow
- webhook verification
- audit
- safe error behavior

---

### 10.2 Browser Session

ต้อง validate:

```text
opaque server-side session
Secure
HttpOnly
SameSite
CSRF
rotation
revocation
```

---

### 10.3 Staff Security

Representative high-risk operation ต้องพิสูจน์:

- staff authentication
- role authorization
- step-up
- reason
- maker-checker หาก required
- audit

---

### 10.4 Object-Level Authorization

ต้องมี cross-user/cross-seller negative tests

ตัวอย่าง:

```text
Seller A → Seller B resource = denied
Customer A → Customer B order = denied
```

---

### 10.5 Secrets

ต้อง validate practical local/deployment secret flow

SOPS เป็น candidate

Managed KMS/secret manager ยัง future option

ห้าม custom crypto

---

### 10.6 POC-04 Exit Gate

- authn proven
- authz proven
- session/CSRF proven
- least privilege proven
- privileged workflow proven
- secret handling proven
- webhook authenticity proven
- audit proven
- known critical blocker = 0

---

## 11. Phase 6 — POC-05 Observability / Performance / Fault

เป้าหมาย:

> พิสูจน์ว่าระบบวัดได้ เข้าใจได้ และรับ failure/load ได้ตาม baseline

---

### 11.1 Observability

ต้องมี:

```text
logs
metrics
traces
```

OTel-compatible direction

---

### 11.2 Metrics

อย่างน้อย:

```text
RED
USE
business correctness metrics
```

เช่น:

- outbox lag
- UNKNOWN payment
- reconciliation backlog
- inventory rejection
- DB pool wait

---

### 11.3 k6

Baseline load stages:

```text
100 VUs
500 VUs
1,000 VUs
```

เมื่อ environment รองรับ

วัด:

- RPS
- p50
- p95
- p99
- error
- timeout

---

### 11.4 Resource Correlation

ต้อง correlate:

- Nginx
- JVM
- Spring
- DB pool
- PostgreSQL
- Docker
- CPU
- RAM
- disk
- network

---

### 11.5 Fault Injection

Representative failures:

- DB unavailable
- provider timeout
- worker crash
- delayed event
- duplicate event
- restart
- outbox backlog
- pool saturation

---

### 11.6 POC-05 Exit Gate

ต้องรู้:

- current capacity baseline
- current bottleneck
- recovery behavior
- alerting baseline
- observability gaps
- fault handling
- no production data used in destructive load

---

## 12. Production Foundation Freeze

หลัง POC-01 ถึง POC-05 ต้องทำ architecture review รวม

Output:

```text
Production Foundation Baseline
```

ต้อง lock/review:

- project structure
- DB privilege model
- migration lifecycle
- concurrency strategy
- outbox/worker model
- auth/session boundary
- observability baseline
- deployment pattern
- test gates

Major decisions ต้อง ADR

---

## 13. Phase 7 — Core Product Implementation

เมื่อ foundation ผ่านแล้ว จึงเริ่ม product implementation จริง

ห้ามเริ่มทุก capability พร้อมกัน

ใช้ vertical slices ที่ preserve domain ownership

---

## 14. Suggested Core Domain Sequence

ลำดับโดยประมาณ:

```text
Identity / Account
        ↓
Seller / Merchant
        ↓
Catalog / Product / Variant / SKU
        ↓
Offer / Pricing
        ↓
Inventory
        ↓
Cart / Checkout
        ↓
Order
        ↓
Payment Coordination
        ↓
Refund
        ↓
Fulfillment
        ↓
Shipment
        ↓
Logistics / Delivery
        ↓
Settlement / Payout
        ↓
Support / Reconciliation
```

ลำดับจริงอาจปรับจาก dependency/evidence แต่ต้อง review ก่อน

---

## 15. Identity / Account Phase

ต้องสร้าง:

- account lifecycle
- customer identity
- seller membership
- staff identity
- authorization foundation
- ownership model
- recovery lifecycle

ต้องไม่ hard-code role logic กระจายทั่ว controller

---

## 16. Seller / Merchant Phase

ต้องมี:

- seller profile/state
- membership
- seller authorization
- ownership
- onboarding state
- compliance hooks
- staff-assisted operations

---

## 17. Catalog Phase

Model:

```text
Product
→ Variant
→ SKU
→ Offer
```

ต้องแยก:

- identity
- publication
- compliance
- buyability
- seller offer
- inventory

---

## 18. Offer / Pricing Phase

ต้องรองรับ:

- seller
- SKU
- exact money
- lifecycle
- pricing validity
- availability policy

ห้ามฝัง current stock ลง offer เป็น authority

---

## 19. Inventory Phase

ต้องสร้าง production inventory model ตาม:

```text
SKU
+ Owner
+ Location
+ Status
```

ต้องมี:

- movement history
- reservation
- allocation
- damaged/quarantine
- ATP invariant
- reconciliation

---

## 20. Cart Phase

Cart:

- mutable
- customer intent
- non-authoritative price/stock guarantee

ต้องไม่กลายเป็น order table ก่อน checkout

---

## 21. Checkout Phase

CheckoutSession ต้อง snapshot:

- selected offer
- seller
- price
- currency
- fulfillment selection
- customer intent

ก่อนสร้าง durable order ต้อง revalidate critical facts

---

## 22. Order Phase

Order:

- durable
- explicit lifecycle
- separate from payment/inventory/fulfillment
- state transition controlled
- reason typed where operationally important

---

## 23. Multi-Merchant Order Grouping

Initial direction:

```text
selected checkout
= all-or-nothing
```

หากหลาย seller ต้องมี grouping model ที่ชัด

ห้าม partial acceptance โดยไม่บอก customer

---

## 24. Payment Coordination Phase

ต้องมี:

- durable payment operation
- provider adapter
- idempotency
- `UNKNOWN`
- webhook
- reconciliation
- audit

ห้าม blind retry provider timeout

---

## 25. Refund Phase

ต้องมี:

- refund operation
- cumulative cap
- concurrency safety
- idempotency
- unknown provider outcome
- reconciliation

---

## 26. Financial Ledger / Settlement Phase

เมื่อ business scopeต้องใช้:

- immutable entries
- balanced journal
- exact money
- settlement lifecycle
- payout operation
- reconciliation

ไม่ใช้ order status เป็น ledger

---

## 27. Fulfillment Phase

Model:

```text
Order
→ Fulfillment
→ Shipment
→ Package
```

ต้องรองรับ multiple shipments

---

## 28. Logistics Phase

Model direction:

```text
Plan
→ Legs
→ Jobs / Trips
```

ห้าม:

```text
order.driver_id
```

เป็น logistics model ทั้งหมด

---

## 29. Custody Phase

ต้องมี explicit custody transitions

tracking location ไม่เท่ากับ custody

---

## 30. Tracking Phase

แยก:

```text
raw observations
current operational state
customer-facing projection
```

ต้องรับ delayed/out-of-order/duplicate data ได้

---

## 31. Support / Operations Product Phase

สร้าง staff workflow สำหรับ:

- customer support
- seller support
- fulfillment support
- finance support
- reconciliation

ห้ามใช้ direct SQL เป็น routine support method

---

## 32. Audit Phase

Audit ต้อง:

- protected
- append-oriented
- actor-attributed
- reason-aware
- searchable สำหรับ investigation
- retention-aware

---

## 33. Phase 8 — Derived Systems and Experience

หลัง authoritative path stable จึงเพิ่ม derived planes

---

## 34. Search

เริ่มจาก PostgreSQL/read model ได้ถ้าเพียงพอ

OpenSearch ยัง deferred

เพิ่ม dedicated search engine เมื่อมี evidence:

- query complexity
- scale
- ranking
- latency
- text features

---

## 35. Tracking Projection

สร้าง customer-friendly tracking projection จาก operational/raw data

ต้อง rebuild ได้

---

## 36. Notification

Email/push/SMS:

```text
outbox
→ worker
→ provider
```

notification failure ไม่ rollback order

---

## 37. Analytics

Analytics ห้ามรบกวน OLTP

ClickHouse/warehouse เฉพาะทางเพิ่มเมื่อ workload justify

---

## 38. Object Storage

ใช้ S3-compatible abstraction สำหรับ:

- product media
- documents
- reports
- exports

private by default

---

## 39. Customer Web Experience

Next.js + TypeScript

ต้องเริ่มจาก:

- design tokens
- accessible primitives
- session integration
- product/catalog
- cart
- checkout
- order status

ใช้ `BRAND.md` เป็น authority

---

## 40. Seller Experience

Seller UI:

- offer management
- inventory views
- order workflow
- fulfillment
- finance view ตาม scope

role/seller isolation ต้อง test

---

## 41. Staff / Operations Experience

Staff UI:

- high-density but clear
- explicit privilege
- audit
- maker-checker
- reason capture
- no silent impersonation

---

## 42. VRA Drive / Food / Fulfill Naming

ชื่อเหล่านี้ยังเป็น family naming architecture

การสร้างเป็น standalone product ต้องมี:

- product requirement
- ownership
- roadmap
- architecture review

ห้ามสร้างเพียงเพราะชื่อมีอยู่ใน brand docs

---

## 43. VRA Pay

`VRA Pay` ยัง DEFERRED

ห้ามตีความ payment coordination ภายใน marketplace ว่าเป็น standalone VRA Pay product

---

## 44. Phase 9 — Production Readiness / Staging

ก่อน production ต้องมี staging/release candidate ที่พิสูจน์ end-to-end

---

### 44.1 Production Readiness Areas

- security
- migrations
- deployment
- backup
- restore
- DR
- payment reconciliation
- inventory concurrency
- logs/metrics/traces
- alerts
- runbooks
- load
- E2E
- access review
- secrets
- data retention

---

### 44.2 Release Candidate Gate

ต้อง freeze candidate commit/artifact

ห้าม deploy “latest branch” แบบคลุมเครือ

ต้องระบุ:

```text
commit SHA
artifact digest
migration version
config version
```

---

### 44.3 Staging Validation

Staging ต้อง validate:

- reverse proxy
- TLS
- session
- DB roles
- migrations
- workers
- webhooks
- object storage
- monitoring
- backups
- restore path
- E2E

---

## 45. Phase 10 — Initial Production Deployment

Initial production ยังเป็น single-host ได้ หาก risk ถูกยอมรับอย่าง explicit

แต่ต้องมี:

- firewall
- Nginx
- secure tunnel/edge
- private DB
- immutable artifacts
- least privilege
- off-host backups
- tested restore
- monitoring
- alerting
- reconciliation

---

## 46. Production Deployment Order

Baseline:

```text
preflight
→ backup/recovery confidence
→ DB bootstrap
→ migration
→ runtime grants
→ backend
→ health/readiness
→ worker
→ frontend
→ reverse proxy
→ HTTPS/tunnel
→ provider webhooks
→ smoke
→ observation
```

---

## 47. Safe-Host Rule

ก่อนแก้ production host:

```text
read-only inventory first
```

ต้องรู้:

- existing services
- ports
- Docker resources
- Nginx
- PostgreSQL
- storage
- firewall
- backups

ห้าม destructive shortcut

---

## 48. Initial Production Limitations

Single-host production มี limitations:

- host SPOF
- no HA
- maintenance can cause downtime
- resource contention
- backup/restore importanceสูง

ต้อง document ตรง ๆ

---

## 49. Phase 11 — Evidence-Driven Scale

หลังมี production workload จริง ค่อยพิจารณา specialized infrastructure

---

## 50. Redis Revisit Conditions

เพิ่ม Redis เมื่อมี evidence เช่น:

- cache hit benefit ชัด
- DB load ลดอย่างวัดได้
- distributed ephemeral coordination ที่ไม่ใช่ authoritative correctness
- rate-limit/state requirement ที่เหมาะสม

ไม่เพิ่มเพื่อ “ระบบใหญ่ต้องมี Redis”

---

## 51. Broker Revisit Conditions

เพิ่ม Kafka/RabbitMQ/NATS เมื่อ:

- fan-out สูง
- consumer groups หลายชุด
- PostgreSQL worker throughput ไม่พอ
- replay/retention requirement
- independent service boundary
- operational ownership พร้อม

---

## 52. Search Engine Revisit

OpenSearch เมื่อ PostgreSQL/read model ไม่พอด้วย evidence

---

## 53. Analytics DB Revisit

ClickHouse/warehouse เมื่อ:

- query volume
- scan cost
- retention
- aggregation workload

justify

---

## 54. Kubernetes Revisit

Kubernetes เมื่อ:

- multiple services
- independent scaling
- scheduling complexity
- rollout need
- host count
- operational team maturity

justify

ไม่ใช่ milestone ที่ต้องทำเพื่อเรียก production-ready

---

## 55. Service Extraction

Extract module เป็น service เมื่อมี evidence:

```text
scale
isolation
security
ownership
deployment cadence
specialized runtime
compliance
```

ต้อง ADR

---

## 56. Multi-Region

ยัง deferred

ก่อน multi-region write ต้องตอบ:

- conflict
- consistency
- failover
- payment state
- inventory authority
- reconciliation
- data sovereignty

---

## 57. Sharding

ยัง deferred

เพิ่มเมื่อ single PostgreSQL architecture มี measured limit ที่แก้ด้วย indexing/query/schema/vertical scale ไม่พอ

---

## 58. CDC

CDC platform ยัง deferred

Transactional outbox เป็น integration baseline

---

## 59. GraphQL

ยัง deferred

REST/OpenAPI เป็น external baseline

GraphQL เพิ่มเมื่อ client/query shape มี evidence

---

## 60. Internal gRPC

ยัง deferred

In-process module call ใช้ตรง ๆ ใน modular core

gRPC ใช้เมื่อมี real network boundary และ benefit

---

## 61. Service Mesh

ยัง deferred จน service count/traffic/security model justify

---

## 62. Roadmap Gates

ทุก phase ต้องมี:

```text
Entry Criteria
Scope
Forbidden Scope
Verification
Evidence
Independent Review
Exit Criteria
Git Checkpoint
```

---

## 63. Branch Strategy

Validation work ใช้ branch ที่สื่อความหมาย เช่น:

```text
poc/01-transactional-core
poc/02-concurrency-idempotency
poc/03-outbox-recovery
poc/04-security-auth
poc/05-observability-performance
```

Git mutation ทำโดย user หลัง review

Automation/Codex ห้าม mutate Git; Git-mutating commands ทำโดย user เองหลัง review

---

## 64. Commit Strategy

Commit ควรเป็น meaningful checkpoints

ตัวอย่าง:

```text
poc: add transactional core baseline
poc: verify database privilege separation
poc: verify concurrency invariants
docs: record persistence decision
```

หลีกเลี่ยง giant commit ที่รวมหลาย decision

---

## 65. ADR Strategy

สร้าง ADR เมื่อ decision:

- ยากย้อนกลับ
- กระทบหลาย module
- เปลี่ยน architecture boundary
- เพิ่ม operational complexity
- เพิ่ม infrastructure
- เปลี่ยน security model
- เปลี่ยน data ownership

---

## 66. POC Evidence

แต่ละ POC ต้องมี:

```text
spec
evidence
commands
results
limitations
known risks
Git checkpoint
```

POC evidence เป็น historical truth

ห้าม rewrite ให้เข้ากับ decision ภายหลัง

---

## 67. Production Code Rule

Code ใน `validation/`:

```text
evidence only
```

Production source ต้องอยู่ production tree ที่สร้างหลัง foundation review

POC code copy ไป production ต้อง review line-by-line หรือ reimplement deliberate

---

## 68. Dependency Rule

ก่อนเพิ่ม dependency ใหม่ ถาม:

1. requirement อะไร
2. standard library/framework ทำไม่ได้หรือ
3. security risk
4. maintenance
5. license
6. transitive dependencies
7. operational impact

---

## 69. Complexity Budget

ทุก phase มี complexity budget

ถ้า solution เพิ่ม:

- broker
- cache
- new DB
- new service
- new runtime
- new deployment stack

ต้องมี evidence และ ADR ตาม impact

---

## 70. Product Expansion Gate

Feature ใหม่ต้องตอบ:

- customer/business value
- authoritative owner
- state model
- permissions
- failure behavior
- audit
- recovery
- tests
- operational ownership

---

## 71. Security Gate

Feature ที่มี sensitive operation ต้องไม่ merge จน:

- authz negative path test
- safe errors
- audit requirement
- secret handling
- ownership checks

ครบตาม risk

---

## 72. Financial Gate

Financial feature ต้องมี:

- exact money
- idempotency
- caps/invariants
- UNKNOWN semantics
- reconciliation
- audit
- concurrency
- provider retry policy

---

## 73. Inventory Gate

Inventory feature ต้องมี:

- owner/location/status
- movement history
- ATP invariant
- concurrency test
- idempotent release
- allocation/reservation distinction

---

## 74. Migration Gate

Schema change ต้อง:

- additive เมื่อ practical
- migration test
- legacy-data preservation
- lock assessment
- compatibility
- runtime grant impact

---

## 75. API Gate

Public/client API:

- OpenAPI
- stable errors
- authz
- idempotency
- pagination
- version compatibility
- request correlation

---

## 76. UI Gate

Customer/staff UI ต้องผ่าน:

- accessibility
- responsive
- loading/error/empty
- keyboard
- reduced motion
- correct authoritative messaging
- brand consistency

---

## 77. Operations Gate

ก่อน deploy component ใหม่:

- health
- metrics
- logs
- runbook
- backup dependency
- recovery
- alert
- owner

---

## 78. Definition of Ready for Production

VRA ไม่ถือว่า production-ready เพราะ:

```text
features complete
```

อย่างเดียว

ต้องมี:

```text
correct
secure
observable
recoverable
reconcilable
tested
operable
documented
```

---

## 79. What We Do Not Optimize For Early

ระยะแรกไม่ optimize สำหรับ:

- maximum service count
- extreme horizontal scale
- global active-active
- theoretical throughput ที่ไม่มี workload
- architecture diagram complexity
- tool diversity

---

## 80. What We Optimize For Early

เรา optimize สำหรับ:

- correctness
- security
- clear ownership
- safe migrations
- operational recovery
- testability
- observability
- simple failure model
- reversible decisions

---

## 81. Short-Term Next Steps

หลัง canonical docs baseline ถูก review และ commit:

```text
1. Create POC-01 branch
2. Freeze POC-01 spec
3. Audit current repository structure
4. Create production-oriented Java/Spring foundation
5. Establish Gradle/module boundaries
6. Provision local PostgreSQL roles
7. Prove migration/runtime separation
8. Implement representative transaction
9. Add Testcontainers + architecture tests
10. Capture evidence
11. Independent review
12. ADR for any major decision
13. Merge POC-01
```

---

## 82. Medium-Term Sequence

```text
POC-01
Transactional Core

→ POC-02
Concurrency / Idempotency

→ POC-03
Outbox / Workers / Recovery

→ POC-04
Security / Auth / Least Privilege

→ POC-05
Observability / Performance / Fault

→ Core Product Slices

→ Derived Systems / Web UX

→ Staging

→ Production
```

---

## 83. Long-Term Direction

VRA สามารถเติบโตไปสู่:

- multiple deployables
- specialized databases
- dedicated search
- event broker
- autoscaling
- multiple hosts/regions
- stronger DR
- domain-team ownership

แต่แต่ละขั้นต้องเกิดจาก evidence

---

## 84. Roadmap Change Governance

Roadmap เปลี่ยนได้เมื่อ:

- business priority เปลี่ยน
- POC evidence เปลี่ยน assumption
- security risk ใหม่
- operational bottleneck
- external dependency
- regulatory requirement

แต่ต้องไม่เปลี่ยนเพื่อข้าม gate ที่ยาก

---

## 85. Forbidden Roadmap Shortcuts

ห้าม:

- เริ่ม microservices ก่อน module ownership ชัด
- เพิ่ม Kafka ก่อน outbox/worker baseline
- เพิ่ม Redis ก่อนมี cache requirement
- ใช้ production DB เป็น test DB
- deploy ก่อน restore test
- bypass least privilege เพื่อให้ release ง่าย
- skip concurrency test ใน inventory/finance
- hide `UNKNOWN` provider state
- commit secret
- rewrite historical POC evidence
- ให้ Codexตัดสิน architecture โดยไม่มี review
- เริ่มหลาย POC พร้อมกันจน evidence ปนกัน

---

## 86. Roadmap Review Questions

ก่อนเริ่ม phase ใหม่ถาม:

1. phase ก่อนหน้าปิดจริงหรือยัง
2. evidence ครบหรือยัง
3. blocker เหลือไหม
4. docs/ADR update แล้วไหม
5. scope phase ใหม่ชัดหรือยัง
6. forbidden scope ชัดหรือยัง
7. test gate คืออะไร
8. recovery/operations impact คืออะไร
9. Git baseline clean หรือยัง
10. เรากำลังแก้ requirement จริงหรือเพิ่ม complexity เอง

---

## 87. Canonical Roadmap Rule

หลักของ VRA คือ:

> **เราจะไม่รีบสร้างระบบให้ “ดูใหญ่” แต่จะสร้างทีละชั้น โดยทุกชั้นต้องพิสูจน์ correctness, security และ recovery ก่อนขยายความซับซ้อน**

ลำดับสำคัญคือ:

```text
Understand
→ Specify
→ Validate
→ Implement
→ Test
→ Review
→ Record
→ Operate
→ Measure
→ Scale only when justified
```

นี่คือ roadmap หลักที่ใช้พา VRA จาก technology validation ไปสู่ production system ที่เชื่อถือได้
