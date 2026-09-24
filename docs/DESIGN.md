# การออกแบบสถาปัตยกรรม VRA

**เอกสาร:** `docs/DESIGN.md`
**สถานะ:** ACTIVE — canonical baseline v1
**ผลิตภัณฑ์:** VRA (วีล่า)
**ขอบเขต:** สถาปัตยกรรมระบบหลักของ VRA ตั้งแต่ transactional core, domain boundaries, data ownership, asynchronous work, API boundaries, derived systems, deployment shape และเกณฑ์การเพิ่มความซับซ้อน

> เอกสารนี้อธิบายว่า VRA ควรถูกออกแบบอย่างไรเพื่อรักษา product intent และ business invariants จาก `PRODUCT.md`
> รายละเอียด threat model, authorization, secrets และ security controls อยู่ใน `SECURITY.md`
> รายละเอียด test strategy อยู่ใน `TESTING.md`
> รายละเอียด deployment, backup, restore และ runbook อยู่ใน `OPERATIONS.md`

---

## 1. เป้าหมายของสถาปัตยกรรม

สถาปัตยกรรม VRA ต้องสนับสนุนระบบ commerce, marketplace, fulfillment และ logistics ที่มี correctness สูง โดยไม่เพิ่ม distributed complexity ก่อนมีหลักฐานว่าจำเป็น

เป้าหมายหลักเรียงตามลำดับคือ:

1. Security
2. Correctness
3. Architectural clarity
4. Operations and recovery
5. Availability
6. Performance
7. Scalability
8. Developer convenience

เมื่อเป้าหมายขัดกัน การตัดสินต้องย้อนกลับไปยัง business requirement และ invariant ที่ต้องรักษา ไม่ใช้ fashion หรือ framework popularity เป็นเหตุผลหลัก

---

## 2. Decision Chain

การตัดสินใจเชิงสถาปัตยกรรมที่สำคัญควรเดินตามลำดับ:

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
```

Technology เป็นผลลัพธ์ของการตัดสิน ไม่ใช่จุดเริ่มต้น

ตัวอย่าง:

```text
Requirement:
ห้ามขาย stock หน่วยสุดท้ายให้หลาย buyer เกิน policy

        ↓

Invariant:
available-to-promise >= 0

        ↓

Consistency:
ต้องมี authoritative atomic decision

        ↓

Architecture:
transactional inventory boundary

        ↓

Technology:
PostgreSQL conditional UPDATE / locking strategy
```

---

## 3. Architecture Summary

VRA ใช้แนวทาง:

> **Modular Transactional Core + Explicit Domain Boundaries + Worker Plane + Specialized Derived/Data Planes + Evidence-Driven Service Extraction**

ความหมายคือ:

- เริ่มจาก transactional core ที่ deploy เป็นระบบเดียวหรือ deployable จำนวนน้อย
- แบ่ง ownership ภายในอย่างชัดเจนด้วย module boundaries
- PostgreSQL เป็น authoritative transactional store
- งาน asynchronous ออกจาก transaction ผ่าน transactional outbox
- worker ทำงานแบบ idempotent และ recoverable
- search, tracking projection, analytics และ read model เป็น derived systems
- service extraction เกิดเมื่อมีเหตุผลเชิง scale, isolation, ownership หรือ operations ที่พิสูจน์ได้
- ไม่เริ่มจาก fine-grained microservices
- ไม่สร้าง localhost HTTP ระหว่าง modules เพื่อเลียนแบบ microservices
- ไม่พยายามสร้าง global distributed ACID transaction

---

## 4. High-Level Logical Architecture

```text
Clients
├─ Web
├─ Mobile (future)
├─ Staff / Operations UI
└─ External Integrations
        │
        ▼
Edge / API Boundary
├─ Reverse Proxy
├─ TLS
├─ Request Limits
├─ Session / Auth Boundary
└─ API Contract
        │
        ▼
VRA Transactional Core
├─ Identity / Account
├─ Seller
├─ Catalog / Product
├─ Offer / Pricing
├─ Cart / Checkout
├─ Order
├─ Inventory
├─ Payment Coordination
├─ Refund
├─ Fulfillment
├─ Shipment
├─ Logistics
├─ Finance / Settlement
├─ Support
└─ Audit / Reconciliation
        │
        ├───────────────┐
        ▼               ▼
PostgreSQL          Transactional Outbox
Authoritative       + Worker Plane
OLTP                    │
                        ├─ notifications
                        ├─ projections
                        ├─ provider callbacks
                        ├─ reconciliation
                        └─ integration publishing
                                │
                                ▼
                    Derived / Specialized Planes
                    ├─ Search
                    ├─ Tracking projection
                    ├─ Analytics
                    ├─ Object storage
                    └─ Future specialized systems
```

---

## 5. Deployment Shape ระยะแรก

Production-oriented initial shape:

```text
Internet
   │
   ▼
Cloudflare / Edge
   │
   ▼
Secure Tunnel
   │
   ▼
Nginx Reverse Proxy
   │
   ▼
VRA Web / API containers
   │
   ▼
PostgreSQL
```

ข้อกำหนดสำคัญ:

- application port ไม่เปิดสู่ Internet โดยตรง
- PostgreSQL ไม่เปิด public port
- reverse proxy เป็น boundary ที่ชัดเจน
- application image เป็น immutable artifact
- migration ไม่รันด้วย runtime DB identity
- deployment ต้องไม่ใช้ `git pull` เป็น production release mechanism
- build artifact หนึ่งชุดควรถูก promote ระหว่าง environment แทน build ใหม่แบบไม่ควบคุม

Docker Compose เป็น initial runtime orchestration ที่ยอมรับได้

Kubernetes ยัง deferred จนกว่าจะมี requirement หรือ operational pressure ที่ชัดเจน

---

## 6. Primary Technology Baseline

### 6.1 Backend

Primary JVM language:

```text
Java
```

Decision record:

```text
docs/adr/ADR-001-primary-jvm-language.md
```

Backend framework candidate direction (ต้อง validate ใน POC-01):

```text
Java
+ Spring Boot
+ PostgreSQL
```

POC-00 พิสูจน์ Java 21 baseline แล้ว แต่ production dependency versions อาจเปลี่ยนผ่าน compatibility review โดยไม่ถือว่า language decision เปลี่ยน

### 6.2 Web

Web direction:

```text
Next.js
+ TypeScript
```

Web client ต้องไม่กลายเป็น authoritative owner ของ business state

### 6.3 Database

```text
PostgreSQL
```

เป็น authoritative OLTP datastore หลักของ transactional system

### 6.4 HTTP API

External/client API ใช้:

```text
HTTP + JSON
REST-style resources / commands
OpenAPI
```

GraphQL ยัง deferred

Internal gRPC ยัง deferred

### 6.5 Validation / Testing Tooling

Baseline direction:

- JUnit / Spring Test
- Testcontainers
- Bruno
- Playwright
- k6
- ArchUnit หรือ architecture rule verification ที่เหมาะสม

---

## 7. Modular Transactional Core

VRA เริ่มจาก modular core แทน fine-grained microservices

เหตุผล:

- critical business workflows ต้องการ transaction และ consistency ที่เข้าใจง่าย
- early system ยังไม่มี evidence ว่า service-level independent scaling จำเป็น
- ลด distributed failure modes
- ลด network boundary ที่ไม่จำเป็น
- ลด deployment และ observability overhead
- ทำให้ invariant enforcement ใกล้ authoritative data

Modular monolith ในที่นี้ไม่ได้หมายถึง code ก้อนเดียวไร้ boundary

ต้องมี explicit ownership และ dependency rules ภายใน

---

## 8. Domain Ownership

แต่ละ domain module ต้องเป็นเจ้าของ:

- domain model
- application use cases
- invariant
- authoritative write semantics
- event emission
- persistence access ที่เกี่ยวข้อง
- public module contract

Module อื่นห้ามแก้ table หรือ aggregate ของ domain โดยตรงเพียงเพราะฐานข้อมูลเดียวกัน

Shared PostgreSQL instance ไม่ได้แปลว่า shared ownership

---

## 9. Suggested Domain Modules

Initial capability map อาจประกอบด้วย:

```text
identity
customer
seller
catalog
offer
cart
checkout
order
inventory
payment
refund
finance
fulfillment
shipment
logistics
tracking
notification
support
risk
audit
reconciliation
```

ชื่อ module production จริงอาจปรับเมื่อ domain model ชัดขึ้น

การสร้าง module ไม่ได้หมายความว่าทุก module ต้องมี deployable service ของตัวเอง

---

## 10. Dependency Direction

เป้าหมาย dependency direction:

```text
interfaces
    ↓
application
    ↓
domain

infrastructure
    ─────► application ports
```

Domain ต้องไม่ขึ้นกับ:

- Spring Web
- JDBC
- HTTP
- Docker
- provider SDK
- infrastructure package

Application layer orchestration สามารถใช้ transaction boundary และ ports ได้ แต่ไม่ควรผูก domain semantics เข้ากับ persistence implementation

Infrastructure เป็น adapter

Interfaces เป็น transport boundary

---

## 11. Package / Module Boundary

ตัวอย่าง conceptual shape:

```text
com.vra.<domain>
├─ domain
├─ application
├─ infrastructure
└─ interfaces
```

สำหรับระบบขนาดใหญ่จริง อาจใช้ Gradle multi-module เพื่อ enforce boundary แข็งขึ้น เช่น:

```text
modules/
├─ order-domain
├─ order-application
├─ order-infrastructure
├─ inventory-domain
├─ inventory-application
└─ ...
```

แต่ POC-01 ต้องพิสูจน์ก่อนว่ารูปแบบใดให้ boundary ที่ชัดโดยไม่สร้าง build complexity เกินจำเป็น

---

## 12. Transaction Boundary

Transaction ต้องครอบเฉพาะ work ที่ต้อง atomic ใน authoritative store

หลักสำคัญ:

- transaction boundary อยู่ใน application use case
- domain logic ไม่เปิด transaction เอง
- ห้ามทำ external network call ภายใน DB transaction โดยไม่มีเหตุผลที่หนักแน่น
- transaction ควรสั้น
- transaction failure ต้อง rollback
- external side effect ใช้ outbox / workflow pattern เมื่อเหมาะสม

ตัวอย่าง:

```text
BEGIN

validate command
load authoritative state
enforce invariants
mutate authoritative rows
append outbox message

COMMIT

        ↓

worker performs external side effect
```

---

## 13. No External Call Inside Critical DB Transaction

VRA หลีกเลี่ยง pattern:

```text
BEGIN
update order
call payment provider
wait network
update payment
COMMIT
```

เพราะ:

- lock ถูกถือระหว่าง network wait
- provider latency กลายเป็น DB latency
- timeout สร้าง ambiguity
- retry ยาก
- transaction duration ไม่แน่นอน

ควรใช้ durable state + external orchestration ที่รองรับ uncertainty และ reconciliation

---

## 14. PostgreSQL เป็น Authoritative OLTP

PostgreSQL ใช้เก็บ transactional truth เช่น:

- account state
- seller state
- product/offer state
- checkout snapshot
- order
- inventory balance/movement
- reservation
- payment coordination state
- refund
- fulfillment
- shipment
- ledger/settlement
- audit metadata
- outbox/inbox

PostgreSQL ไม่จำเป็นต้องใช้ทำทุก workload

Search, analytics, telemetry และ object blob มี data shape ที่ต่างกันและสามารถแยกเมื่อมีเหตุผล

---

## 15. Database Ownership

แม้อยู่ database เดียวกัน ต้องมี logical ownership

ตัวอย่าง:

```text
inventory owns:
- inventory_balance
- inventory_movement
- reservation

order owns:
- orders
- order_items

payment owns:
- payment_attempt
- payment_operation
```

Module อื่นควรเข้าถึงผ่าน application/domain contract หรือ read projection ที่อนุญาต

Direct cross-domain write เป็น architecture violation เว้นแต่ได้รับการออกแบบ explicit

---

## 16. Migration Ownership

Schema migration ต้อง:

- version controlled
- reviewable
- forward-moving
- repeatable ใน environment ใหม่
- validated กับ real PostgreSQL behavior
- แยก runtime identity กับ migration identity

Runtime application identity ต้องไม่เป็น schema owner เพียงเพื่อทำ deployment ให้ง่าย

Production migration ต้องพิจารณา:

- lock impact
- table scan
- backfill
- constraint validation
- rollback strategy
- forward fix
- compatibility window

---

## 17. Expand → Backfill → Switch → Contract

สำหรับ schema evolution ขนาดใหญ่ ใช้ pattern:

```text
Expand
  ↓
Backfill
  ↓
Switch
  ↓
Contract
```

ตัวอย่าง:

1. เพิ่ม column ใหม่แบบ backward-compatible
2. deploy code ที่เขียนทั้ง old/new หรือรองรับทั้งสอง
3. backfill
4. verify
5. switch read path
6. remove old field ใน release ภายหลัง

ห้าม assume ว่า destructive migration ขนาดใหญ่ปลอดภัยเพียงเพราะ SQL syntax valid

---

## 18. Transactional Outbox

เมื่อ transaction ต้องสร้าง asynchronous side effect:

```text
business mutation
+
outbox record
```

ต้อง commit ใน transaction เดียวกัน

หลัง commit worker จึงอ่าน outbox และส่งต่อ

Guarantee ที่คาดหวัง:

```text
authoritative change committed
⇒ intent to publish/process is durable
```

Outbox ไม่ได้ guarantee exactly-once delivery

ระบบต้องออกแบบสำหรับ at-least-once processing

---

## 19. Inbox / Deduplication

Consumer หรือ worker ที่รับ message ซึ่งอาจซ้ำ ต้องมี deduplication strategy

ตัวอย่าง:

```text
message_id
idempotency_key
provider_event_id
business_operation_id
```

Expected model:

```text
at-least-once delivery
+
idempotent consumer
=
safe repeated processing
```

ห้ามสร้าง architecture ที่ correctness ขึ้นกับ assumption ว่า message จะถูกส่งเพียงครั้งเดียว

---

## 20. Event Taxonomy

VRA แยก:

```text
Command
≠ Domain Event
≠ Integration Event
```

### Command

สิ่งที่ actor ขอให้ระบบทำ

ตัวอย่าง:

```text
ReserveInventory
CancelOrder
RequestRefund
```

### Domain Event

ข้อเท็จจริงที่เกิดขึ้นภายใน domain

```text
OrderConfirmed
InventoryReserved
RefundApproved
```

### Integration Event

event ที่ publish ออกไปให้ boundary อื่นบริโภค

Integration event ต้องออกแบบสำหรับ compatibility และ asynchronous delivery

---

## 21. Event Ordering

VRA ไม่ assume global event ordering

Ordering guarantee ควรแคบที่สุดที่ business requirement ต้องการ เช่น:

```text
per aggregate
per order
per shipment
```

หาก consumer ต้องการ detect stale event ควรใช้:

- aggregate version
- sequence
- event timestamp พร้อม semantics ที่ชัด
- idempotency state

---

## 22. Saga / Process Manager

Cross-domain workflow ที่ยาวและไม่สามารถทำใน single transaction อาจใช้ saga/process manager

ตัวอย่าง:

```text
Order created
   ↓
Reserve inventory
   ↓
Initiate payment
   ↓
Confirm order
   ↓
Start fulfillment
```

Saga ต้อง explicit เรื่อง:

- current step
- completed steps
- compensation
- retries
- timeout
- uncertain outcomes
- reconciliation

ห้ามซ่อน saga ไว้ใน chain ของ synchronous HTTP calls แบบไม่มี durable state

---

## 23. Idempotency

Critical mutation ที่ client หรือ integration อาจ retry ต้องรองรับ idempotency ตาม business semantics

ตัวอย่าง:

- create payment operation
- request refund
- create order
- provider webhook processing
- payout initiation

Idempotency key ต้องผูกกับ operation semantics และ caller scope

Duplicate key ที่ payload ขัดกันต้องไม่ถูกตีความว่าเป็น operation เดิมอย่างเงียบ ๆ

---

## 24. Inventory Concurrency

Inventory correctness ต้องอาศัย authoritative database decision

Target invariant:

```text
available-to-promise >= 0
```

แนวทางที่ยอมรับได้ เช่น:

```sql
UPDATE inventory_balance
SET reserved = reserved + :qty
WHERE ...
  AND available >= :qty
RETURNING ...
```

หรือ locking/serialization strategy อื่นที่พิสูจน์ได้

ห้ามใช้ JVM mutex เป็น authoritative concurrency control สำหรับระบบที่อาจมีหลาย process

POC-02 เป็นเจ้าของการพิสูจน์ stock contention เช่น:

```text
stock = 1
buyers = 500
successful reservation = exactly 1
```

---

## 25. Financial Concurrency

Financial invariant ต้อง enforce ทั้ง domain และ authoritative store ตามความเหมาะสม

ตัวอย่าง:

```text
cumulative_refund <= captured_amount
```

และ:

```text
balanced journal
```

Operation ที่เกี่ยวกับ money ต้องรองรับ duplicate request และ provider uncertainty

---

## 26. Payment Orchestration

Payment workflow ต้องแยก:

```text
our durable intent
provider request
provider outcome
our authoritative interpretation
```

ตัวอย่าง flow:

```text
TX1
- create durable payment operation
- persist request identity
- commit

outside transaction
- call provider

TX2
- record provider result
- transition internal state
- commit
```

ถ้า provider response ไม่แน่นอน:

```text
UNKNOWN
```

แล้วเข้าสู่ reconciliation

ห้าม blind retry charge เพราะ timeout

---

## 27. Refund / Payout Orchestration

Refund และ payout ใช้แนวคิดเดียวกัน:

```text
durable operation
→ provider call
→ authoritative result or UNKNOWN
→ reconcile
```

สำหรับ payout ความเสี่ยง duplicate financial transfer สูงมาก

การไม่รู้ผลลัพธ์ต้องไม่แปลว่า “ส่งใหม่ทันที”

---

## 28. Money Model

Money ต้องประกอบด้วย:

```text
amount
currency
```

Rule:

- ห้ามใช้ floating point เป็น authoritative money
- currency mismatch ต้อง fail
- rounding policy ต้อง explicit
- scale/minor-unit semantics ต้องกำหนดต่อ currency
- conversion ต้องเป็น explicit operation

Ledger หรือ settlement records ต้อง immutable ตาม accounting design

---

## 29. API Design

External/client APIs ใช้ REST-style HTTP/JSON

Principles:

- resource และ command semantics ชัดเจน
- stable error code
- request correlation
- explicit idempotency
- version compatibility
- pagination
- optimistic concurrency เมื่อจำเป็น
- DTO แยกจาก persistence model

---

## 30. Explicit Command Endpoints

บาง business operation ไม่ควรถูกย่อเป็น generic PATCH

ตัวอย่างที่ดีกว่า:

```text
POST /orders/{id}/cancel
POST /payments/{id}/capture
POST /refunds
POST /inventory/reservations
```

มากกว่า:

```text
PATCH /orders/{id}
{
  "status": "CANCELLED"
}
```

เพราะ command endpoint ทำให้ authorization, invariant, audit และ idempotency ชัดกว่า

---

## 31. Stable Error Model

API error ควรมีอย่างน้อย:

```json
{
  "code": "inventory.insufficient_stock",
  "message": "Insufficient stock.",
  "requestId": "..."
}
```

`code` ต้อง stable สำหรับ client

`message` ต้องปลอดภัย

ห้าม expose:

- SQL
- stack trace
- secret
- internal filesystem path
- provider credential
- raw exception message ที่ไม่ผ่าน review

---

## 32. Optimistic Concurrency

Resource ที่แก้ไขพร้อมกันและต้อง detect stale write สามารถใช้:

```text
version
expected_version
ETag / If-Match
```

Policy ต้องกำหนดว่า conflict คือ retryable, mergeable หรือ user-visible

ห้ามใช้ last-write-wins โดยไม่รู้ตัวใน domain ที่ข้อมูลหายได้

---

## 33. Pagination

Large collection API ต้องใช้ pagination

Default direction:

```text
cursor-based pagination
```

สำหรับ dataset ที่เปลี่ยนเร็วหรือขนาดใหญ่

Offset pagination ใช้ได้กับ admin/read model ที่เหมาะสม แต่ต้องไม่ assume stable ordering โดยไม่มี tie-breaker

---

## 34. Long-Running Operations

Operation ที่ใช้เวลานานเกิน synchronous request window สามารถตอบ:

```text
202 Accepted
```

พร้อม operation resource เช่น:

```text
/operations/{id}
```

operation state ต้อง durable และตรวจสอบได้

---

## 35. Webhook Design

Webhook inbound ต้อง:

- authenticate/verify sender
- deduplicate
- persist receipt/state
- process idempotently
- tolerate duplicate and delayed delivery
- not assume chronological arrival

Webhook outbound ต้อง:

- signed
- retryable
- bounded
- auditable
- observable

---

## 36. Browser Session Boundary

รายละเอียด security อยู่ใน `SECURITY.md` แต่ architecture ตั้ง baseline ว่า browser client ไม่ควรเก็บ long-lived privileged bearer credential โดยไม่จำเป็น

Direction:

```text
browser
→ opaque server-side session
→ backend authority
```

Cookie ต้องถูกกำหนดด้วย security controls ที่เหมาะสม และ state-changing requests ต้องมี CSRF protection เมื่อ architecture ต้องการ

---

## 37. Mobile Authentication Boundary

สำหรับ future native mobile:

```text
Authorization Code
+ PKCE
```

เป็น baseline direction

Mobile client ไม่ควรได้รับ secret แบบ confidential client

---

## 38. Identity Provider Boundary

VRA อาจใช้ external IdP สำหรับ authentication

แต่ VRA ยังเป็นเจ้าของ:

- business identity mapping
- account lifecycle
- roles
- ownership
- seller/customer relationship
- business authorization
- privileged approval
- domain-specific access rules

IdP ไม่ควรกลายเป็นที่เก็บ business authorization ทั้งระบบ

---

## 39. Derived Search

Search เป็น derived plane

Source of truth ยังคง transactional store

Search index สามารถ:

- stale
- rebuild
- replay
- reindex

ได้โดยไม่ทำให้ authoritative transaction หาย

OpenSearch หรือ search engine เฉพาะทางยัง deferred จนกว่า PostgreSQL search/read model ไม่พอหรือ requirement บังคับ

---

## 40. Tracking Projection

Raw logistics observations และ customer-facing tracking view แยกกัน

Conceptual flow:

```text
raw observations
       ↓
operational interpretation
       ↓
current shipment state
       ↓
customer-facing projection
```

Projection สามารถ rebuild จาก authoritative/raw data ที่เก็บเหมาะสม

Tracking presentation ไม่ใช่ source of custody truth

---

## 41. Analytics Plane

Analytics ไม่ควร query OLTP อย่างหนักจนกระทบ transactional workload

ระยะแรกสามารถใช้:

- controlled replica/read path
- exported data
- scheduled projection
- warehouse-friendly extracts

ระบบ analytics เฉพาะ เช่น ClickHouse ยัง deferred จนมี evidence ด้าน volume/query shape

---

## 42. Object Storage

Binary/object data เช่น:

- product media
- documents
- generated reports
- evidence artifact บางประเภท

ควรผ่าน S3-compatible abstraction

Object storage ไม่ควรใช้แทน transactional database สำหรับ business state

Default object visibility:

```text
private
```

public access ต้อง explicit

---

## 43. Cache

Cache เป็น optimization ไม่ใช่ authority

Redis ยัง deferred

หากเพิ่ม cache ภายหลัง ต้องตอบให้ได้:

- cache อะไร
- TTL เท่าไร
- invalidation อย่างไร
- stale ยอมรับได้แค่ไหน
- fallback เมื่อ cache unavailable คืออะไร
- correctness ยังอยู่ที่ไหน

ระบบไม่ควร fail correctness เพราะ cache หาย

---

## 44. Message Broker

Kafka, RabbitMQ และ NATS ยัง deferred

Transactional outbox + PostgreSQL worker สามารถเป็น initial asynchronous mechanism

Broker ควรเพิ่มเมื่อมี evidence เช่น:

- event fan-out สูง
- independent consumer groups จำนวนมาก
- throughput requirement
- retention/replay requirement
- workload isolation
- operational ownership

ห้ามเพิ่ม broker เพียงเพื่อเรียกระบบว่า event-driven

---

## 45. PostgreSQL Worker Plane

Initial worker สามารถ poll outbox/job tables จาก PostgreSQL โดยใช้:

- bounded batches
- row locking
- `SKIP LOCKED` เมื่อเหมาะสม
- lease/claim state
- retries
- dead-letter/reconciliation state

Worker crash ต้องไม่ทำให้ job สูญหาย

Repeated delivery ต้องปลอดภัย

---

## 46. Retry Policy

Retry ต้อง bounded

ต้องแยก:

- retryable transient failure
- permanent business failure
- unknown outcome
- poison message
- operator intervention required

Exponential backoff และ jitter ใช้ได้เมื่อเหมาะสม

ห้าม infinite retry แบบไม่มี visibility

---

## 47. Dead Letter / Failed Work

งานที่ retry เกิน policy ต้องเข้าสู่ explicit failed/dead-letter state

ต้องมี:

- reason
- attempt count
- timestamps
- operation identity
- safe operator workflow
- replay mechanism ที่ควบคุมได้

Dead-letter queue ไม่ใช่ที่ทิ้งปัญหาโดยไม่มี owner

---

## 48. Reconciliation Architecture

Reconciliation worker/process เป็น first-class component

ตัวอย่าง:

```text
find UNKNOWN payment
        ↓
query authoritative provider
        ↓
compare state
        ↓
repair internal interpretation
        ↓
emit audit/event
```

Reconciliation ต้อง idempotent และ auditable

---

## 49. Audit Architecture

Audit data แยกจาก ordinary application log

Audit ต้องรองรับ:

- actor
- subject
- action
- target
- reason
- result
- timestamp
- correlation
- approval context

Audit record ต้องถูกป้องกันจาก unauthorized modification

---

## 50. Data Classification

VRA แบ่งข้อมูลอย่างน้อยเป็นกลุ่ม:

```text
Authoritative transactional
Immutable financial/audit
Operational telemetry
Derived projection
Cache
Object
Analytics
```

แต่ละกลุ่มมี retention, consistency, access และ recovery requirement ต่างกัน

---

## 51. Source-of-Truth Rule

ทุก field ที่สำคัญต้องตอบได้ว่า:

> authoritative source คืออะไร?

ตัวอย่าง:

```text
Order status
→ Order domain / PostgreSQL

Search result
→ derived from product/order source

Customer tracking page
→ derived view

Payment provider response
→ external evidence interpreted into internal payment state
```

ห้ามมี multiple writers ต่อ fact เดียวโดยไม่มี coordination model

---

## 52. Strong Identifiers

Domain identifiers ควรเป็น explicit types เมื่อช่วยลด semantic mix-up

ตัวอย่าง:

```text
OrderId
SkuId
ShipmentId
PaymentId
```

ไม่ควรส่ง UUID/String ดิบผ่าน domain ทุกที่จน compiler แยกความหมายไม่ได้

Wire format อาจยังเป็น UUID/string ได้

---

## 53. Time Model

เวลาใน domain ต้อง explicit

แนวทาง:

- inject `Clock` เมื่อ code ต้องใช้ current time
- เก็บ authoritative timestamp ใน timezone-neutral form
- แสดง local timezone ที่ presentation layer
- ไม่เรียก `Instant.now()` กระจัดกระจายใน domain object
- distinguish occurred_at / received_at / processed_at เมื่อ semantics ต่างกัน

---

## 54. State Machines

Lifecycle สำคัญควรมี state machine ที่ explicit

ต้องกำหนด:

- states
- allowed transitions
- terminal states
- transition reason
- invariants
- actor permissions
- side effects

ห้ามเปลี่ยน state ด้วย generic setter

---

## 55. Domain Invariants

Invariant ต้อง enforce ใกล้ authoritative mutation ที่สุด

ใช้หลายชั้นร่วมกันเมื่อเหมาะสม:

```text
domain validation
+
application orchestration
+
database constraint
+
concurrency control
+
tests
```

ไม่ควรฝาก correctness ไว้ที่ frontend validation

---

## 56. Read Model

Read model สามารถ denormalize เพื่อ performance ได้

แต่ต้องมี:

- source of truth
- rebuild strategy
- freshness expectation
- version or timestamp semantics
- failure monitoring

Read model ไม่ควรรับ authoritative write โดยตรง

---

## 57. Cross-Domain Reads

Cross-domain query อาจใช้:

- application composition
- dedicated read model
- approved read-only query
- projection

การ join table ข้ามทุก domain แบบอิสระใน application code ทำให้ ownership พัง

POC-01/production design ต้องกำหนด pattern ที่ balance ระหว่าง modularity กับความเรียบง่าย

---

## 58. Cross-Domain Writes

Cross-domain writes ห้าม bypass owner

ตัวอย่างที่ไม่ควร:

```text
OrderService
UPDATE inventory_balance directly
```

ควร:

```text
Order workflow
→ Inventory application contract
```

ภายใน same deployable อาจเป็น in-process call ไม่จำเป็นต้อง HTTP

---

## 59. No Fake Microservices

ห้ามทำ:

```text
localhost HTTP
module A → localhost → module B
```

เพียงเพื่อเลียนแบบ microservices ใน process เดียว

ถ้าอยู่ deployable เดียว ใช้ in-process application boundary

Network boundary ต้องมี operational reason

---

## 60. Service Extraction Criteria

Domain/module อาจถูกแยกเป็น service เมื่อมี evidence เช่น:

- independent scaling requirement
- failure isolation requirement
- security isolation
- data sovereignty/compliance
- separate deployment cadence
- clear ownership by independent team
- specialized runtime
- workload profile ที่รบกวน core
- measurable bottleneck

ก่อนแยกต้องตอบ:

- data ownership ย้ายอย่างไร
- transaction เดิมแตกอย่างไร
- consistency model ใหม่คืออะไร
- failure mode ใหม่คืออะไร
- observability เพิ่มอะไร
- operational cost เพิ่มเท่าไร

---

## 61. Distributed Transaction Policy

VRA ไม่ใช้ global distributed ACID เป็น default

Cross-service workflow ต้องออกแบบด้วย:

- local transaction
- durable message
- idempotency
- saga/process manager
- reconciliation
- compensating action เมื่อ business semantics รองรับ

---

## 62. Availability Philosophy

Availability ไม่ได้หมายความว่าทุก operation ต้อง succeed ตลอดเวลา

เมื่อ dependency สำคัญล้ม:

- บาง workflow อาจ degrade
- บาง workflow ต้อง fail closed
- บาง read อาจใช้ stale derived data
- critical financial mutation อาจหยุดแทน guessing

Business mode ต้อง explicit

---

## 63. Single-Host Reality

Initial self-hosted deployment เป็น single failure domain

แม้ container แยกหลายตัว แต่หากอยู่ host เดียว:

```text
host failure
= all colocated services unavailable
```

จึงห้ามเรียกว่า high availability

สิ่งที่ต้องมีแทน:

- backups off-host
- tested restore
- recovery procedure
- monitoring
- controlled deployment
- reconciliation หลัง restore

---

## 64. Backup and Restore Architecture

Backup เป็นส่วนหนึ่งของ architecture

ต้องรองรับ:

- PostgreSQL backup
- WAL/PITR ตามระดับที่กำหนด
- object metadata/reference consistency
- encryption
- off-host copy
- restore testing

Restore สำเร็จทางเทคนิคยังไม่พอ

หลัง restore อาจต้อง reconcile:

- payment
- refund
- payout
- external shipment
- webhook processing

---

## 65. Observability

VRA ต้องรองรับ:

```text
logs
metrics
traces
```

ผ่าน OTel-compatible instrumentation direction

Observability ต้องใช้ตอบได้ทั้ง:

- request performance
- failure root cause
- business correctness
- queue/outbox lag
- reconciliation backlog
- saturation
- security-relevant anomalies

---

## 66. Log Categories

อย่างน้อยแยก conceptually:

```text
application logs
audit records
integration/event records
security events
```

ไม่ควรโยนทุกอย่างลง log stream เดียวแล้วหวังว่าจะใช้แทนกันได้

---

## 67. Metrics Model

ใช้:

```text
RED
- Rate
- Errors
- Duration

USE
- Utilization
- Saturation
- Errors
```

และเพิ่ม business correctness metrics เช่น:

- reservation conflict
- payment UNKNOWN count
- outbox lag
- reconciliation age
- failed payout operation
- inventory invariant violation
- projection freshness

---

## 68. Health Model

แยก:

```text
liveness
readiness
business health
```

### Liveness

process ยังทำงานหรือไม่

### Readiness

พร้อมรับ traffic หรือไม่

### Business Health

dependency/workflow สำคัญมีปัญหาหรือ backlog ที่กระทบ business หรือไม่

ห้ามทำ liveness depend กับทุก external provider จน process ถูก restart เพราะ provider ภายนอกล่ม

---

## 69. Performance Principles

Performance ต้องวัด:

- p50
- p95
- p99
- throughput
- error rate
- timeout rate
- resource utilization

Average latency อย่างเดียวไม่พอ

Optimization ต้องเริ่มจาก measurement

---

## 70. Load Testing

k6 เป็น baseline tool สำหรับ load/performance test

Load test ต้องแยก test data จาก DEV/demo/production data

Target concurrency จะขึ้นกับ phase แต่ POC performance roadmap รวมตัวอย่าง:

```text
100
500
1,000 concurrent VUs
```

ต้อง correlate result กับ:

- application
- PostgreSQL
- connection pool
- CPU
- RAM
- Docker
- Nginx
- host/network

---

## 71. Concurrency Testing

Concurrency correctness test แยกจาก generic load test

ตัวอย่าง critical scenario:

```text
stock = 1
500 concurrent reservation attempts
exactly one successful reservation
```

และ:

```text
1000 dispatch attempts
one active assignment
```

ผลต้องพิสูจน์ business invariant ไม่ใช่แค่ RPS

---

## 72. Connection Pool

DB connection pool เป็น bounded resource

ต้อง monitor:

- active
- idle
- waiters
- acquisition time
- timeout

เพิ่ม pool size อย่างเดียวไม่ใช่การแก้ performance โดยอัตโนมัติ เพราะ PostgreSQL มี concurrency limit ของตัวเอง

---

## 73. Query Design

Critical query ต้องตรวจ:

- index
- cardinality
- execution plan
- lock behavior
- row count
- transaction duration

N+1 query และ unbounded query ต้องตรวจใน review/test

---

## 74. Persistence Strategy

Final split ระหว่าง:

```text
JPA / Hibernate
SQL / JDBC / jOOQ
```

ยังไม่ lock ทั้งระบบ

Direction:

- aggregate CRUD ที่เหมาะสมอาจใช้ ORM
- correctness-critical/concurrency-heavy query อาจใช้ explicit SQL
- reporting/read model อาจใช้ SQL-oriented approach

POC-01 ต้องช่วยพิสูจน์ persistence baseline

ห้ามเลือก ORM หรือ SQL ideology แบบเหมารวม

---

## 75. Flyway

Flyway เป็น migration direction

Migration execution ต้อง explicit ใน deployment lifecycle

Application startup ไม่ควรมีสิทธิ์ schema-owner โดยอัตโนมัติใน production

Migration identity และ runtime identity ต้องแยกกัน

---

## 76. Runtime Database Identity

Runtime identity ต้องใช้ least privilege

ตัวอย่าง:

```text
vra_migrator
- schema changes
- migration ownership

vra_runtime
- DML/SELECT ที่ application ต้องใช้
- no schema ownership
```

ห้าม broaden privilege เพียงเพื่อแก้ deployment error แบบเร็ว

---

## 77. Secrets Boundary

Secret ไม่ใช่ config ทั่วไป

Code, config, secret และ cryptographic key ต้องแยก concept

Initial deployment อาจใช้ Docker/host secret files

SOPS เป็น candidate สำหรับ validation

Managed secret manager/KMS อาจเพิ่มภายหลังเมื่อ operational model รองรับ

ห้ามสร้าง custom cryptography

---

## 78. Configuration

Configuration ต้อง:

- environment-specific
- non-secret by default
- validated on startup
- immutable ระหว่าง process lifetime เว้นแต่ feature ถูกออกแบบให้ dynamic
- มี safe default เฉพาะที่ไม่สร้าง security/correctness risk

Missing critical config ต้อง fail fast

---

## 79. Environment Separation

Environment อย่างน้อย conceptually:

```text
DEV
TEST
LOAD
STAGING
PROD
```

Data ต้องไม่ปะปนโดยไม่ตั้งใจ

Automated test ใช้ disposable/isolated database

Load test ใช้ dedicated test data

Production/demo data ห้ามใช้เป็น destructive test target

---

## 80. Build and Artifact Policy

Build once, promote artifact

OCI container image เป็น artifact direction

Artifact ต้อง trace กลับไปได้ถึง:

- source commit
- dependency lock/version
- build metadata
- test result

Production deploy ไม่ควร compile source บน server แบบ ad hoc

---

## 81. CI/CD Boundary

CI มีหน้าที่:

- compile
- unit test
- integration test
- architecture test
- migration verification
- security/static checks ที่กำหนด
- build artifact

CD มีหน้าที่:

- select immutable artifact
- apply approved migration
- deploy
- verify health
- observe
- rollback/roll-forward ตาม policy

---

## 82. Frontend Architecture

Next.js + TypeScript เป็น web direction

Frontend มีหน้าที่:

- presentation
- interaction
- client validation
- session interaction
- optimistic UI เมื่อปลอดภัย

Frontend ไม่มีสิทธิ์:

- ตัดสิน authoritative inventory
- ตัดสิน payment success
- bypass authorization
- สร้าง financial truth
- แก้ state machine โดยตรง

---

## 83. BFF / Session Authority

Browser-facing BFF/session architecture ยังต้อง validate ใน security POC

Direction คือ:

```text
Browser
  ↓
BFF / Server Session Boundary
  ↓
Backend APIs
```

เป้าหมาย:

- ลด token exposure ใน browser
- รวม CSRF/session policy
- แยก public client concern จาก internal authorization

รายละเอียดต้องพิสูจน์ใน POC-04

---

## 84. File / Media Upload

Upload architecture ต้อง:

- validate type/size
- avoid trusting filename
- private by default
- use object storage
- issue controlled access
- support malware/content scanning เมื่อ risk ต้องการ
- store metadata transactionally

Binary payload ขนาดใหญ่ไม่ควรผ่าน OLTP table โดยไม่มีเหตุผล

---

## 85. Notification

Notification เป็น side effect/derived communication

ตัวอย่าง:

- email
- push
- SMS
- in-app

Notification failure ไม่ควร rollback authoritative order transaction

ใช้ outbox/worker pattern

Notification content ต้องไม่เป็น sole audit record

---

## 86. Search / Notification / Analytics Failure

Derived plane ล่มไม่ควร corrupt transactional core

ตัวอย่าง:

```text
Search down
→ order creation ยังทำได้ถ้า workflow ไม่ depend search

Analytics down
→ transaction ยัง commit

Notification down
→ queue/retry
```

แต่ UX ต้องแสดง degradation อย่างเหมาะสม

---

## 87. External Provider Adapter

Provider integration ต้องผ่าน adapter boundary

Domain/application ไม่ควร depend กับ vendor SDK โดยตรง

ตัวอย่าง:

```text
PaymentGateway
ShipmentCarrier
IdentityProvider
ObjectStorage
NotificationProvider
```

Adapter แปลง vendor-specific error/state เป็น internal semantics

---

## 88. Provider Error Taxonomy

Provider error ต้อง map อย่างน้อยเป็น:

```text
success
business rejection
transient failure
permanent failure
unknown outcome
```

HTTP 500 จาก provider ไม่ได้มี semantic เดียวกันทุกกรณี

Unknown outcome ต้องมี reconciliation path

---

## 89. Circuit Breaking / Rate Limiting

Circuit breaker, rate limiter และ bulkhead ใช้เมื่อมี evidence/requirement

ไม่ควรเพิ่ม library/pattern ทุกอย่างตั้งแต่วันแรก

แต่ external boundary ต้องสามารถเพิ่ม controls เหล่านี้ภายหลังโดยไม่ rewrite domain core

---

## 90. Security Boundary by Design

แม้รายละเอียดอยู่ `SECURITY.md`, architecture ต้องไม่สร้าง assumption ว่า:

```text
inside network = trusted
```

ทุก workload identity, staff actor, external integration และ user request ต้องผ่าน explicit trust boundary

---

## 91. Least Privilege by Architecture

Least privilege ต้องใช้กับ:

- DB role
- workload
- object storage
- CI credential
- deployment credential
- staff role
- API scope

Architecture ที่ต้องให้ broad privilege เพื่อให้ทำงานได้ถือว่าเป็น design smell

---

## 92. Audit vs Event vs Log

ห้ามใช้แทนกัน:

```text
Domain event
≠ Integration event
≠ Audit record
≠ Application log
```

แต่ละชนิดมี semantics, retention และ trust requirement ต่างกัน

---

## 93. Data Retention

Retention ต้องกำหนดตาม data class และ legal/business requirement

อย่า default ว่าเก็บทุกอย่างตลอดไป

Retention ต้องคำนึง:

- audit
- finance
- customer privacy
- telemetry cost
- recovery
- dispute window
- compliance

---

## 94. Deletion

การลบ account หรือ business record ไม่ได้แปลว่า hard-delete ทุก row

ต้องแยก:

- logical closure
- PII removal/anonymization
- legally required retention
- financial/audit preservation
- derived projection cleanup

รายละเอียดอยู่ใน domain/security policy

---

## 95. Recovery-Oriented Design

ทุก critical component ต้องตอบได้:

- ถ้า process crash กลางทางจะเกิดอะไร
- ถ้า message ซ้ำจะเกิดอะไร
- ถ้า provider timeout จะเกิดอะไร
- ถ้า projection หาย rebuild ได้หรือไม่
- ถ้า DB restore ย้อนเวลา external world จะ reconcile อย่างไร

ระบบที่ตอบ recovery ไม่ได้ถือว่ายังออกแบบไม่เสร็จ

---

## 96. Failure Injection

ก่อน production maturity ต้องมี test ที่จำลอง:

- DB unavailable
- provider timeout
- worker crash
- duplicate event
- delayed event
- process restart
- migration failure
- outbox backlog

รายละเอียด test matrix อยู่ `TESTING.md`

---

## 97. Operational Ownership

Component สำคัญต้องมี owner และ operational responsibility

อย่างน้อยต้องรู้:

- metric ไหนบอกว่าเสีย
- alert ใครรับ
- retry policy คืออะไร
- runbook อยู่ไหน
- recovery path คืออะไร
- data reconciliation ทำอย่างไร

---

## 98. Architecture Enforcement

Boundary ต้อง enforce ด้วย:

- code structure
- build modules เมื่อเหมาะสม
- architecture tests
- code review
- ADR
- database access policy
- API contracts

Documentation อย่างเดียวไม่พอ

---

## 99. Architecture Drift

เมื่อ implementation diverge จาก canonical design:

1. ห้ามแก้ docs ให้ตาม code โดยอัตโนมัติ
2. ตรวจว่าความเปลี่ยนแปลง intentional หรือ defect
3. ถ้า architecture เปลี่ยนจริง ต้อง review
4. major decision ต้อง ADR
5. update docs หลัง decision

Code ที่ merge ไปแล้วไม่ได้แปลว่า architecture ใหม่ถูกต้องโดยอัตโนมัติ

---

## 100. Technology Status Matrix

### LOCKED / ACCEPTED DIRECTION

- Java เป็น primary JVM language
- PostgreSQL authoritative OLTP
- modular transactional core
- explicit domain ownership
- transactional outbox
- derived search/tracking/analytics
- REST-style HTTP/JSON external API
- OpenAPI
- Next.js + TypeScript web
- object storage ผ่าน S3-compatible abstraction
- OTel-compatible observability
- Bruno
- Playwright
- k6
- OCI/container artifacts
- Docker Compose initial runtime
- Nginx reverse proxy / safe-host boundary

### VALIDATE / FINALIZE IN UPCOMING POCs

- Spring Boot เป็น primary backend framework และ exact production baseline/version
- persistence split: JPA vs JDBC/jOOQ
- Flyway production lifecycle
- PostgreSQL worker implementation
- BFF/session authority
- external IdP
- SOPS / secret workflow
- module/build layout
- migration/runtime DB grants

### DEFERRED UNTIL EVIDENCE

- Redis
- Kafka
- RabbitMQ
- NATS
- OpenSearch
- ClickHouse
- specialized tracking database
- Kubernetes
- GraphQL
- internal gRPC
- service mesh
- multi-region active-active
- sharding
- CDC platform
- dedicated distributed workflow engine

---

## 101. POC Roadmap Relationship

Architecture validation sequence:

```text
POC-00
JVM language
✅ CLOSED
Java selected

        ↓

POC-01
Transactional Core
Spring + PostgreSQL
module boundaries
transactions
migrations
persistence baseline

        ↓

POC-02
Concurrency / Idempotency
stock contention
dispatch uniqueness
refund limits
settlement safety

        ↓

POC-03
Outbox / Workers / Recovery

        ↓

POC-04
Security / Auth / Least Privilege / Secrets

        ↓

POC-05
Observability / Performance / Fault Testing
```

POC result ต้อง update architecture evidence แต่ไม่ rewrite historical evidence

---

## 102. POC Code vs Production Code

สิ่งที่อยู่ภายใต้:

```text
validation/
```

ถือเป็น validation spec, harness หรือ historical evidence ไม่ใช่ production source

ตั้งแต่ POC-01 เป็นต้นไป การ validation สามารถทำกับ **production-candidate source**
ที่อยู่ใน production tree จริงได้ เพื่อหลีกเลี่ยงการสร้าง throwaway implementation
แล้วเขียนระบบเดิมซ้ำอีกครั้ง

อย่างไรก็ตาม source ดังกล่าวจะกลายเป็น accepted production baseline ก็ต่อเมื่อผ่าน
POC exit gate, testing และ independent review ที่เกี่ยวข้องแล้วเท่านั้น

การที่ code ถูกใช้ใน POC หรือ POC ผ่าน ไม่ได้อนุญาตให้ experimental shortcut
กลายเป็น production architecture โดยอัตโนมัติ

ห้าม copy POC shortcuts เข้า production โดยไม่มี review

---

## 103. Production Foundation Gate

ก่อนเริ่ม feature implementation จำนวนมาก ต้องผ่านอย่างน้อย:

- Java/Spring production project structure
- module dependency rules
- PostgreSQL connectivity
- migration identity
- runtime least privilege identity
- migration lifecycle
- configuration validation
- standardized error contract
- health/readiness
- logging/observability baseline
- Testcontainers integration
- CI-compatible build
- architecture tests

สิ่งเหล่านี้เป็นเป้าหมายหลักของ POC-01 / production foundation

---

## 104. Architecture Non-Goals ระยะแรก

VRA ไม่พยายามพิสูจน์หรือสร้างสิ่งต่อไปนี้ใน initial architecture:

- hundreds of microservices
- global distributed transaction
- multi-region active-active OLTP
- arbitrary event sourcing ทุก domain
- custom database
- custom cryptography
- custom service mesh
- Kubernetes เพื่อชื่อเสียงด้าน scale
- broker เพียงเพื่อให้ architecture ดู asynchronous
- cache เพียงเพื่อให้ดูเร็ว
- separate database ต่อ module โดยอัตโนมัติ

---

## 105. Evidence Before Extraction

ก่อนแยก service ต้องมีหลักฐานที่ตอบอย่างน้อยหนึ่งข้อ:

```text
Scale?
Isolation?
Security?
Ownership?
Deployment cadence?
Operational risk?
Specialized runtime?
Compliance?
```

ถ้าคำตอบคือเพียง:

> "ระบบใหญ่ควรเป็น microservices"

ถือว่ายังไม่มีเหตุผลเพียงพอ

---

## 106. Architecture Review Questions

ก่อน merge architecture-significant change ให้ถาม:

1. Business requirement คืออะไร
2. Invariant อะไรได้รับผล
3. Source of truth อยู่ที่ไหน
4. Transaction boundary เปลี่ยนหรือไม่
5. Retry duplicate ปลอดภัยหรือไม่
6. Unknown outcome ถูกจัดการอย่างไร
7. Recovery path คืออะไร
8. Security boundary เปลี่ยนหรือไม่
9. Observability เพียงพอหรือไม่
10. Data migration มีผลอย่างไร
11. Complexity ใหม่มี evidence หรือไม่
12. ต้องสร้าง ADR หรือไม่

---

## 107. Canonical Document Relationship

`PRODUCT.md`

กำหนดว่า:

> ระบบต้องทำอะไรและ business truth คืออะไร

`DESIGN.md`

กำหนดว่า:

> architecture จะรักษา product truth อย่างไร

`SECURITY.md`

กำหนดว่า:

> trust และ threat ถูกควบคุมอย่างไร

`TESTING.md`

กำหนดว่า:

> เราจะพิสูจน์ invariant และ failure behavior อย่างไร

`OPERATIONS.md`

กำหนดว่า:

> ระบบจะถูก deploy, run, recover และ reconcile อย่างไร

`ROADMAP.md`

กำหนดว่า:

> เราจะสร้างและ validate ตามลำดับใด

`docs/adr/`

บันทึก:

> ทำไม decision สำคัญจึงถูกเลือกในเวลานั้น

---

## 108. Change Governance

Material architecture change ต้อง review อย่าง deliberate

ตัวอย่าง:

- เปลี่ยน authoritative datastore
- เปลี่ยน transaction ownership
- เพิ่ม broker
- แยก service
- เปลี่ยน authentication boundary
- เพิ่ม distributed cache ที่กระทบ correctness
- เปลี่ยน deployment model
- เปลี่ยน financial/inventory consistency
- เพิ่ม multi-region writes
- เปลี่ยน runtime/migration privilege model

การแก้เพียง implementation detail ที่ไม่เปลี่ยน architecture intent อาจไม่ต้อง ADR

แต่หากไม่แน่ใจ ให้ถือว่าต้อง review ก่อน

---

## 109. Final Architecture Principle

VRA ต้องคงหลักนี้ไว้:

> **เริ่มจากระบบที่ถูกต้อง เข้าใจได้ กู้คืนได้ และตรวจสอบได้ แล้วเพิ่มความซับซ้อนเฉพาะเมื่อ requirement หรือ evidence บังคับ**

เป้าหมายไม่ใช่การสร้าง architecture ที่ดูใหญ่ตั้งแต่วันแรก

เป้าหมายคือสร้าง architecture ที่สามารถเติบโตเป็นระบบใหญ่ได้โดยไม่เสีย business truth ระหว่างทาง
