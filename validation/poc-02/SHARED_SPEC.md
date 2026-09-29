# POC-02 — Concurrency and Idempotency — Shared Specification

**สถานะ:** FROZEN — POC-02 execution baseline
**Target branch:** `poc/02-concurrency-idempotency`
**Planning baseline:** `8ff0810`
**Representative domain:** Inventory Reservation

---

## 1. Purpose

POC-02 มีเป้าหมายเพื่อพิสูจน์ว่า VRA รักษา business correctness ได้เมื่อ
หลาย caller/process ส่งคำสั่งพร้อมกัน, retry คำสั่งเดิม หรือไม่ทราบว่า response
ก่อนหน้าสำเร็จหรือสูญหาย

POC-02 เป็น **concurrency correctness / idempotency validation**

POC-02 ไม่ใช่ performance/load POC และไม่ใช้ throughput สูงเป็นหลักฐานแทน
correctness

Executable representative slice ของ POC-02 คือ:

```text
Inventory Reservation
```

POC-02 ต้องต่อยอด production-candidate transactional foundation จาก POC-01
โดยไม่สร้าง Dispatch, Refund หรือ Settlement/Payout domain แบบ placeholder

---

## 2. Decision Chain

การตัดสินใจต้องรักษาลำดับ:

```text
Business Requirement
→ Invariant
→ Risk / Threat
→ Consistency
→ Availability
→ Performance
→ Operations
→ Architecture
→ Technology
→ Implementation
```

ห้ามเลือก lock, retry, cache, Redis หรือ isolation level ที่แรงขึ้นเพียงเพื่อทำให้
concurrency test ผ่าน

---

## 3. Canonical Authority

POC-02 ต้องสอดคล้องกับ:

- `docs/PRODUCT.md`
- `docs/DESIGN.md`
- `docs/SECURITY.md`
- `docs/TESTING.md`
- `docs/OPERATIONS.md`
- `docs/ROADMAP.md`
- `docs/BRANCH_PLAN.md`
- `docs/adr/ADR-001-primary-jvm-language.md`
- `docs/adr/ADR-002-backend-production-foundation.md`
- historical evidence จาก `validation/poc-01/`

บทบาทของเอกสาร:

```text
ROADMAP.md
= strategic phase / direction

BRANCH_PLAN.md
= execution scope / dependency / exit gate

validation/poc-02/SHARED_SPEC.md
= executable branch-specific requirements หลัง freeze
```

ถ้า implementation ขัดกับ canonical requirement ต้องหยุดและ review ความขัดแย้ง
อย่าง explicit

---

## 4. Decisions Already Accepted

POC-02 ไม่เปิดตัดสินใหม่โดยไม่มี evidence สำหรับเรื่องต่อไปนี้:

- Java เป็น primary JVM language
- Spring Boot เป็น primary backend framework
- PostgreSQL เป็น authoritative OLTP datastore
- `backend/runtime` และ `backend/migration` เป็น accepted module baseline
- runtime ไม่มี Flyway
- migration เป็น explicit process แยกจาก runtime startup
- `vra_owner` / `vra_migrator` / `vra_runtime` ต้องรักษา least-privilege separation
- runtime DB identity ต้องไม่เป็น object owner
- critical DB semantics ต้องพิสูจน์กับ PostgreSQL จริง
- application transaction ห้ามครอบ external network call
- JPA ใช้กับ persistence ที่เหมาะสม และ explicit SQL/JdbcClient ใช้ได้กับ
  correctness-critical transitions
- Inventory identity ประกอบด้วย SKU + Owner + Location + Status
- `available = on_hand - reserved`
- `available >= 0`
- Reservation ไม่เท่ากับ Allocation
- request ID เป็น server-controlled correlation identifier
- request ID ไม่ใช่ idempotency key
- POC-03 เป็นเจ้าของ outbox/worker/crash-recovery semantics
- POC-04 เป็นเจ้าของ real authenticated actor/session binding
- POC-05 เป็นเจ้าของ load/performance/capacity validation

---

## 5. Scope

### 5.1 In Scope

POC-02 ต้องพิสูจน์:

1. Inventory stock contention
2. Idempotent reservation command semantics
3. Duplicate command handling
4. Concurrent same-idempotency-key handling
5. Same key + conflicting payload handling
6. Retry หลัง committed response สูญหาย
7. Rollback/failure behavior ของ business mutation + idempotency state
8. Optimistic version conflict เป็น proof แยกจาก stock contention
9. Authoritative PostgreSQL final-state assertions
10. Diagnostic concurrency evidence ที่พอแยก correctness issue ออกจาก capacity issue

### 5.2 Out of Scope

POC-02 ไม่สร้างหรือ finalize:

- Dispatch domain implementation
- Refund domain implementation
- Settlement/Payout domain implementation
- external payment/provider calls
- transactional outbox
- worker claiming / `SKIP LOCKED`
- inbox/event deduplication
- message broker
- Redis
- distributed lock
- authentication / OIDC / session / MFA
- production actor identity binding
- k6 load test
- production capacity target
- production DB-pool sizing
- Kubernetes / service mesh / service extraction

Dispatch/Refund/Settlement concurrency requirements ยังคงเป็น canonical
downstream requirements และต้อง revalidate กับ authoritative state ของ domain จริง
เมื่อ domain เหล่านั้นถูก implement

Inventory proof ห้ามถูกอ้างว่าเป็น direct proof ของ domain เหล่านั้น

---

## 6. Representative Model

POC-02 ใช้ model จาก POC-01:

```text
InventoryKey(
  skuId,
  ownerId,
  locationId,
  stockStatus
)

InventoryBalance(
  inventoryKey,
  onHand,
  reserved,
  version
)

Reservation(
  reservationId,
  inventoryKey,
  quantity,
  createdAt
)
```

POC-02 เพิ่ม logical idempotency model:

```text
IdempotencyIdentity(
  actorScope,
  operation,
  idempotencyKey
)

IdempotencyRecord(
  identity,
  fingerprintVersion,
  requestFingerprint,
  terminalOutcome
)
```

`terminalOutcome` ต้องแทน authoritative business outcome ที่ replay ได้ เช่น:

```text
SUCCEEDED + committed reservation result
REJECTED + stable business rejection code/result
```

POC-02 baseline **ไม่ใช้ committed `IN_PROGRESS` row** เป็น recovery protocol

ชื่อ class/table/column จริงยังไม่ freeze ใน draft นี้ แต่ implementation ต้องมี
authoritative durable representation ที่ enforce semantics ใน section ต่อไปได้

---

## 7. Inventory Invariants

ต้องรักษา invariant จาก POC-01 ทุกข้อ:

```text
on_hand >= 0
reserved >= 0
reserved <= on_hand
quantity > 0
available = on_hand - reserved
available >= 0
```

และ:

- reserve ได้เฉพาะ reservable stock status
- reservation inventory key ต้องตรงกับ authoritative balance
- committed reservation quantity ต้องสอดคล้องกับ committed increase ของ `reserved`
- business success ห้ามมี reservation โดยไม่มี inventory mutation ที่สอดคล้องกัน
- inventory mutation ห้าม commit ถ้า reservation persistence ที่เกี่ยวข้อง fail

---

## 8. Stock-Contention Proof

Required representative scenario:

```text
initial on_hand = 1
initial reserved = 0

500 logical concurrent reservation attempts
quantity = 1
same InventoryKey
distinct business operations / distinct idempotency keys
```

Required final result:

```text
exactly 1 committed business reservation
reserved = 1
available = 0
available never becomes negative
exactly 1 inventory version advancement attributable to the successful reservation
499 attempts produce controlled non-success outcomes
no duplicate committed reservation
no partial state
```

`500 concurrent attempts` หมายถึง 500 caller attempts ที่ถูก release ให้แข่งขันกัน
ในช่วงเดียวกัน

ไม่ได้หมายความว่าต้องเปิด 500 PostgreSQL connections

test harness สามารถใช้ bounded DB connection pool ได้ แต่ต้องมี actual database
concurrency มากกว่า 1 connection และต้องบันทึก test pool configuration ใน evidence

### 8.1 No Version-Guard Shortcut

Stock-contention proof ต้องถูกตัดสินด้วย authoritative stock predicate เช่น:

```text
on_hand - reserved >= quantity
```

ห้ามนับ test ที่ 499 requests แพ้เพียงเพราะทุก request ใช้ stale
`expectedVersion` เดียวกันว่าเป็น stock-contention proof

production reservation path ที่ใช้ใน proof ต้องไม่พึ่ง caller-supplied version
predicate เป็นตัว arbitration หลักของ final-unit reservation

---

## 9. Optimistic Concurrency Proof

Optimistic concurrency เป็น proof แยกจาก stock contention

Representative scenario:

```text
authoritative version = 10

writer A expects 10
writer B expects 10

both attempt concurrently

exactly one compare-and-set succeeds
new authoritative version = 11
the stale writer gets deterministic version conflict
```

Required properties:

- stale writer ไม่ overwrite committed state
- no lost update
- no partial state
- conflict ถูก classify แบบ deterministic
- test ใช้ PostgreSQL จริง

POC-02 ห้ามสร้าง public business endpoint ใหม่เพียงเพื่อให้ optimistic-concurrency
checklist ผ่าน

การพิสูจน์อาจอยู่ที่ persistence/integration boundary ถ้าไม่บิดเบือนว่าเป็น
production capability

---

## 10. Idempotency Identity

Idempotency identity ต้องแยกจาก request correlation

Logical key:

```text
(actorScope, operation, idempotencyKey)
```

สำหรับ representative POC operation:

```text
operation = ReserveInventory
```

Rules:

- `idempotencyKey` เป็น opaque operation identity
- `idempotencyKey` ต้อง non-blank และ bounded ก่อนเข้า authoritative persistence
- exact maximum length/encoding ต้องกำหนดใน implementation plan
- `actorScope` และ `operation` ต้องเป็น bounded/stable application values
- `request_id` ห้ามถูก reuse เป็น idempotency key
- key ต้องถูก scope ด้วย operation
- actor scope ต้องเป็น trusted application input
- caller ห้ามสามารถ spoof trusted actor scope ผ่าน arbitrary header แล้วระบบเชื่อทันที
- POC-02 test harness ใช้ explicit synthetic trusted actor scopes ได้
- binding กับ authenticated actor จริงอยู่ POC-04

POC-02 ไม่ต้องสร้าง fake authentication system เพียงเพื่อให้ actor scoping ผ่าน

### 10.1 Key Binding and Retention

POC-02 ใช้ semantics ต่อไปนี้:

- request ที่ fail syntactic/domain pre-validation ก่อน authoritative claim
  **ไม่ consume idempotency key**
- เมื่อ authoritative idempotency transaction commit terminal outcome แล้ว
  `(actorScope, operation, idempotencyKey)` ต้อง bind กับ fingerprint นั้น
- key ที่ bind แล้วห้าม reuse กับ payload อื่น
- POC-02 ไม่มี TTL/expiry/reuse/delete ของ idempotency identity
- production retention/archival/deletion policy เป็นเรื่องภายหลังและห้ามเปลี่ยน
  semantics จนทำให้ operation เดิมถูก execute ซ้ำอย่างเงียบ ๆ

การไม่มี cleanup ใน POC-02 เป็น deliberate scope boundary ไม่ใช่ production
retention decision

---

## 11. Request Fingerprint

idempotency record ต้องผูกกับ deterministic request fingerprint ของ business
payload

fingerprint ต้อง represent business-relevant request fields อย่าง deterministic

สำหรับ representative `ReserveInventory` operation fingerprint ต้องครอบคลุมอย่างน้อย:

```text
InventoryKey
quantity
```

และ caller-controlled business field อื่นที่ภายหลังถูกเพิ่มเข้ามาแล้วมีผลต่อ
operation semantics

ต้อง exclude:

- `actorScope`, `operation`, `idempotencyKey` เพราะเป็นส่วนของ identity อยู่แล้ว
- server-generated `reservationId`
- request ID
- timestamps ที่ generate ต่อ HTTP attempt
- logging metadata
- network metadata

`reservationId` เป็น server-generated **business result identity** ไม่ใช่ request
identity และต้อง generate เฉพาะ transaction ที่ได้สิทธิ์ execute operation ครั้งแรก

ดังนั้น POC-02 freeze decision คือ `reservationId` ต้องไม่เป็น caller-provided field
ของ normal `ReserveInventoryCommand`

current POC-01 controller-side `UUID.randomUUID()` ก่อน application transaction
ต้องถูก revise; authoritative first execution เป็นผู้สร้าง reservation identity

successful replay ต้องใช้ reservation identity เดิมจาก authoritative terminal outcome

Required semantics:

```text
same identity + same business payload
→ same fingerprint

same identity + materially different business payload
→ different fingerprint
```

authoritative record ต้องเก็บ `fingerprintVersion` หรือ equivalent schema/version
marker เพื่อไม่ให้การเปลี่ยน canonicalization ในอนาคตทำให้ key เก่าถูกตีความใหม่
อย่างเงียบ ๆ

Exact canonical encoding/hash algorithm ต้องถูกกำหนดใน implementation plan ก่อน
เขียน production implementation

ถ้า implementation เก็บ digest แทน canonical bytes ต้องใช้ collision-resistant
cryptographic digest เช่น SHA-256 หรือ stronger project-approved equivalent

ห้ามใช้:

- Java `hashCode()`
- unstable object `toString()`
- unordered map serialization
- process-specific representation

เป็น authoritative fingerprint

---

## 12. Same Key + Same Payload

Scenario:

```text
same actorScope
same operation
same idempotencyKey
same requestFingerprint
```

เมื่อ operation identity มี committed terminal outcome แล้ว การ retry ต้อง replay
terminal outcome เดิมโดยไม่ execute business operation ซ้ำ

### Terminal success

```text
not mutate inventory again
not create another reservation
return the original committed business result
return the original reservation identity
return the authoritative committed inventory version/result metadata
```

### Terminal business rejection

ถ้า request ผ่าน pre-validation และ authoritative operation ถูก claim แล้ว แต่
business rule ให้ผล controlled rejection เช่น insufficient stock:

```text
persist terminal REJECTED outcome
commit idempotency identity + rejection outcome
do not mutate inventory
do not create reservation
same-key same-payload retry
→ replay the same business rejection
```

การเปลี่ยน authoritative state ภายหลัง เช่น stock ถูกเติม ไม่ทำให้ key เดิมกลายเป็น
operation ใหม่

caller ที่ต้องการสร้าง **business attempt ใหม่** ต้องใช้ idempotency key ใหม่

transport metadata เช่น current HTTP `request_id` ไม่ใช่ persisted business result
และต้องสร้างใหม่ต่อ inbound attempt

การ replay เป็น resolution ของ business operation เดิม ไม่ใช่ business operation ใหม่

---

## 13. Same Key + Conflicting Payload

Scenario:

```text
same actorScope
same operation
same idempotencyKey
different requestFingerprint
```

Required behavior:

```text
deterministic conflict
zero second business effect
zero second inventory mutation
zero second reservation
```

rule นี้ใช้เมื่อ key ถูก authoritative claim/bind แล้ว

request ที่ fail pre-validation ก่อน claim ไม่ได้ bind key และสามารถแก้ payload แล้ว
ใช้ key เดิมได้ เพราะยังไม่มี authoritative operation identity ถูกสร้าง

สำหรับ concurrent conflicting payloads transaction ที่ได้ authoritative claim ก่อน
เป็นผู้ bind key; contender อีก payload ต้อง resolve เป็น deterministic conflict
หลัง PostgreSQL ตัดสิน claim order

ต้องมี stable application-level failure classification:

```text
IDEMPOTENCY_KEY_REUSED
```

POC-02 ไม่ freeze public HTTP status/error mapping สำหรับ failure นี้ เพราะ public
`Idempotency-Key` binding ถูก defer ไป POC-04 ตาม section 21

application/integration tests ต้องพิสูจน์ failure code/semantic นี้โดยตรง

---

## 14. Concurrent Same Key

Scenario:

```text
N concurrent callers
same actorScope
same operation
same idempotencyKey
same requestFingerprint
```

Required business result:

```text
exactly 1 authoritative execution
at most 1 business mutation
at most 1 committed reservation
all terminal replays resolve to the same terminal outcome
successful replays resolve to the same reservation identity
no oversell
no duplicate business effect
```

POC-02 baseline ใช้ PostgreSQL-backed unique operation identity เป็น serialization
authority

concurrent duplicate สามารถรอ PostgreSQL uniqueness/transaction resolution ได้
ภายใต้ bounded database/application timeout

หลัง owner transaction resolve:

```text
owner commits terminal outcome
→ duplicate reads and replays that outcome

owner rolls back
→ no committed claim exists
→ one contender may acquire the identity and execute
```

POC-02 ไม่ต้องมี committed `IN_PROGRESS` lease/state หรือ process-local waiting
registry สำหรับ normal synchronous path

Authoritative serialization/deduplication ต้องเกิดจาก PostgreSQL-backed state

ห้ามใช้:

- JVM-global mutex
- static synchronized map
- process-local dedup cache
- Redis lock
- external distributed lock

เป็น correctness authority

PostgreSQL unique constraint / unique-index waiting / row-level concurrency behavior /
atomic DML สามารถใช้ได้เมื่อ implementation plan อธิบาย failure semantics ชัดเจน

---

## 15. Transactional Idempotency Boundary

สำหรับ synchronous reservation:

```text
idempotency claim/binding
+
business decision
+
inventory mutation when successful
+
reservation persistence when successful
+
terminal replay outcome
=
one PostgreSQL transaction
```

Required guarantees:

### Successful business outcome

```text
inventory state commits
+
reservation commits
+
idempotency terminal SUCCEEDED outcome commits
```

พร้อมกัน

### Controlled business rejection after claim

เช่น insufficient stock / not reservable / not found หลัง request ผ่าน pre-validation:

```text
no inventory mutation
no reservation
idempotency terminal REJECTED outcome commits
```

การ map rejection เป็น HTTP/application error ต้องเกิดหลัง transaction สามารถ commit
authoritative terminal rejection ได้ ไม่ใช่ throw จน rollback record ที่ต้องใช้ replay

### Infrastructure / forced failure before commit

```text
business state rolls back
+
idempotency claim/outcome rolls back
```

retry จึงสามารถ acquire operation identity ใหม่อย่างปลอดภัย

### No Externally Visible Partial Success

ห้ามมี:

```text
inventory committed
but reservation absent
```

หรือ:

```text
reservation committed
but replay authority absent for a successful idempotent operation
```

หรือ:

```text
replay authority says success
but business mutation rolled back
```

หรือ:

```text
terminal business rejection committed
but its idempotency identity/fingerprint absent
```

---

## 16. Claim and Terminal-State Policy

POC-02 baseline ใช้ **transaction-local claim + committed terminal outcome**

required lifecycle:

```text
BEGIN
→ attempt authoritative unique claim
→ execute business decision
→ persist terminal SUCCEEDED or REJECTED outcome
→ COMMIT
```

ระหว่าง transaction claim สามารถยังไม่ visible ต่อ transaction อื่น และ PostgreSQL
สามารถทำให้ contender รอ unique-key resolution ได้

POC-02 **ห้าม commit `IN_PROGRESS` claim แยก transaction** แล้วพึ่ง lease/expiry
เพื่อ recovery เพราะจะขยาย scope ไป worker/recovery protocol ของ POC-03

ดังนั้น normal states หลัง commit ต้องเป็น terminal เท่านั้น:

```text
SUCCEEDED
or
REJECTED
```

ถ้า process/connection สูญเสียผลของ `COMMIT` และไม่ทราบ outcome:

```text
retry same identity
→ committed record exists: replay terminal outcome
→ no committed record: operation may be claimed/executed safely
```

ถ้าพบ durable non-terminal state จาก implementation จริง ต้องถือเป็น design finding
และหยุด review; ห้าม blind re-execute business mutation

---

## 17. Retry Semantics

POC-02 ต้องแยก retry อย่างน้อย:

### 17.1 Caller Retry After Response Loss

```text
server commits successful reservation
response is lost / caller does not observe it
caller retries same idempotency identity + same payload
→ original committed business result
→ fresh per-attempt request/correlation metadata
→ no duplicate effect
```

นี่เป็น required exit-gate proof

### 17.2 Retry After Infrastructure / Forced Rollback

```text
first attempt fails before commit
business + idempotency claim/outcome rollback
same logical operation retries
→ may acquire identity and execute normally
```

ต้องไม่มี orphan claim ที่ทำให้ operation ถูกหลอกว่า complete

### 17.3 Retry After Terminal Business Rejection

```text
first attempt commits terminal REJECTED outcome
same key + same payload retries
→ same rejection
→ no business re-evaluation
```

ถ้าผู้ใช้ต้องการ business attempt ใหม่หลัง state เปลี่ยน ต้องใช้ key ใหม่

### 17.4 Automatic Infrastructure Retry

POC-02 ห้ามเพิ่ม automatic retry สำหรับ deadlock/serialization/connection error
เพียงเพื่อทำให้ test ผ่าน

ถ้าจำเป็นต้องเพิ่ม retry ต้องมี:

- exact retryable error classification
- bounded attempts
- backoff policy
- idempotency proof
- evidence ว่า retry ไม่ซ่อน correctness defect

ไม่เช่นนั้น error ต้อง surface ตามจริง

---

## 18. Expected Version on Reservation Path

POC-01 reservation path ปัจจุบันมี caller-provided `expectedVersion`

POC-02 freeze decision:

```text
normal ReserveInventory command
DOES NOT require caller-provided expectedVersion
```

เหตุผลคือการ reserve final available stock เป็น business command ที่ต้องให้
authoritative PostgreSQL stock predicate ตัดสิน current availability

required normal path:

```text
ReserveInventory
→ atomic authoritative stock predicate
→ reserve when current available stock is sufficient
```

`inventory_balance.version` ยังคงเป็น authoritative version และสามารถ increment
เมื่อ state เปลี่ยน รวมทั้งถูก return เป็น result metadata ได้

แต่ caller ไม่ต้อง read version ก่อนเพื่อแข่งขัน reserve stock

POC-01 `expectedVersion` request field เป็น validation-era contract ที่ POC-02
สามารถ revise ได้โดยไม่ rewrite historical POC-01 evidence

ดังนั้น implementation ของ normal reservation path ใน POC-02 ต้องนำ
`expectedVersion` ออกจาก:

```text
CreateReservationRequest
ReserveInventoryCommand
normal reservation repository method contract
```

และต้องนำ caller/controller-generated `reservationId` ออกจาก normal
`ReserveInventoryCommand` ด้วย

normal reservation command จึงเป็น business intent ไม่ใช่ persistence/result identity

exact application service/facade shape ระหว่าง:

```text
non-idempotent HTTP regression path
and
idempotent application proof path
```

ต้อง finalize ใน implementation plan โดยห้าม duplicate authoritative business rule
หรือเปิด insecure public idempotency boundary

และปรับ tests/error mapping ที่อ้าง normal reservation version conflict ตาม semantics
ใหม่

`inventory_balance.version` ยังคงอยู่ใน authoritative schema/result metadata

Optimistic stale-write proof ใน section 9 ยังคง required แต่เป็น separate
persistence/integration proof และไม่ต้องสร้าง public business endpoint ใหม่

canonical `ETag / expected_version` direction ยังคงใช้ได้สำหรับ update semantics
ที่ business requirement ต้องการ stale-write detection; decision นี้ไม่ได้ห้าม
optimistic concurrency ทั้งระบบ

ห้ามคง semantics ที่ทำให้ stock=1/500 pass จาก version conflict แล้วเรียกว่า
inventory contention proof

---

## 19. Isolation and Locking Policy

Baseline validation ต้องเริ่มจาก PostgreSQL / Spring transaction behavior ที่
production foundation ใช้อยู่

ห้ามเพิ่ม global `SERIALIZABLE` isolation เพียงเพื่อทำให้ test ผ่าน

ถ้า default transaction isolation ไม่พอ ต้อง:

1. แสดง failing invariant/evidence
2. ระบุ exact transaction/statement ที่ต้องเปลี่ยน
3. ประเมิน lock/wait/deadlock implications
4. ใช้ narrowest mechanism ที่พิสูจน์ correctness ได้
5. บันทึก decision ใน evidence/ADR ตาม impact

PostgreSQL row locks หรือ unique-index conflict behavior ที่เกิดจาก authoritative
DML ไม่ถือเป็น prohibited external distributed lock

---

## 20. Database Authority and Constraints

POC-02 ต้องใช้ PostgreSQL จริงสำหรับ correctness proofs

Required authoritative protections:

- `reserved <= on_hand`
- reservation primary identity
- database-backed idempotency uniqueness
- idempotency fingerprint/result consistency ตาม schema strategy ที่เลือก
- runtime privilege เฉพาะที่ application ต้องใช้

Migration ใหม่ต้อง:

- run ผ่าน `backend/migration`
- owner ยังคงเป็น `vra_owner`
- migration ใช้ `vra_migrator` + controlled `SET ROLE`
- runtime ไม่ได้ DDL
- runtime ไม่ได้ owner membership
- runtime ไม่ได้ DELETE privilege ถ้า application ไม่ต้องใช้
- migration rerun/validate semantics ยังถูกต้อง

ห้าม broaden runtime privileges เพื่อแก้ implementation error

---

## 21. HTTP Boundary

POC-02 ต้องไม่แกล้งทำว่า real actor authentication ถูกแก้แล้ว

### 21.1 Freeze Decision — Public HTTP Idempotency Binding Deferred

POC-02 **ไม่เปิด production `Idempotency-Key` HTTP contract** เพราะยังไม่มี trusted
authenticated caller identity ที่เหมาะสำหรับ bind `actorScope`

required POC-02 proof อยู่ที่:

```text
application command boundary
+
trusted synthetic actor scope supplied directly by test/application harness
+
PostgreSQL authoritative idempotency state
```

synthetic actor scope:

- ใช้เพื่อพิสูจน์ key scoping semantics เท่านั้น
- ไม่รับจาก arbitrary public HTTP header
- ไม่ถูกอ้างว่าเป็น authentication/authorization
- ไม่ถูก hard-code เป็น production caller identity abstraction

public HTTP idempotency binding จะถูกเพิ่มเมื่อ POC-04 มี trusted actor/session
boundary แล้ว และต้อง reuse/revalidate semantics ที่ POC-02 พิสูจน์

### 21.2 Existing Reservation HTTP Boundary

normal reservation HTTP request ใน POC-02 ต้องนำ `expectedVersion` ออกตาม section 18

existing request ID behavior ต้องคง:

- server-controlled
- generated ใหม่ต่อ inbound attempt
- ไม่ใช้เป็น idempotency key
- ไม่ถูก persist เป็น business replay result

POC-02 HTTP tests ต้องพิสูจน์อย่างน้อยว่า revised reservation request contract
ทำงานกับ stock-arbitration semantics ใหม่ และ existing safe-error/request-ID
baseline ไม่ regress

existing HTTP endpoint ใน POC-02 เป็น **transport/stock-arbitration regression path**
เท่านั้น และห้ามถูกนับเป็น idempotency exit-gate proof

idempotency exit-gate proof ต้องเข้าผ่าน application boundary ที่มี trusted synthetic
scope ตาม section 21.1

POC-02 ห้าม:

- รับ `actorScope` จาก caller-controlled header แล้ว trust โดยตรง
- expose public `Idempotency-Key` แล้วใช้ global/unscoped namespace
- claim end-to-end authenticated HTTP idempotency ก่อน POC-04

---

## 22. Concurrency Test Harness

required PostgreSQL concurrency test ต้อง:

- ใช้ Testcontainers PostgreSQL ตาม project baseline
- ใช้ production migration path
- ใช้ runtime DB identity สำหรับ application writes
- ใช้ independent Spring transactions ต่อ concurrent attempt
- release attempts ด้วย deterministic start gate/coordination
- ห้ามใช้ 500-party barrier ที่รอ task ทั้งหมดพร้อมกันเมื่อ executor มี worker
  น้อยกว่า 500 เพราะจะ deadlock ที่ test harness เอง
- ต้องพิสูจน์ว่ามี overlapping DB work จริงอย่างน้อยมากกว่า 1 concurrent transaction
  และ record executor/DB-pool configuration
- ใช้ bounded timeout เพื่อไม่ให้ test hang
- assert final authoritative DB state
- fail เมื่อมี unexpected exception/deadlock/timeout
- ห้าม silently retry จน test green
- ห้าม skip เมื่อ Docker/PostgreSQL พร้อมตาม required CI environment

`500 attempts` สามารถ run ผ่าน bounded worker/connection pool

ต้อง record อย่างน้อย:

```text
logical attempts
worker/executor strategy
DB pool maximum
successful business effects
business rejections
unexpected errors
timeouts
deadlocks
total duration
```

ตัวเลข performance เป็น diagnostic evidence ไม่ใช่ production capacity claim

---

## 23. Required Test Scenarios

ขั้นต่ำต้องมี:

### Stock contention

```text
stock=1
500 distinct operations
→ exactly 1 committed reservation
→ reserved=1
→ available=0
```

### Concurrent same idempotency key

```text
many concurrent attempts
same key + same payload
→ exactly 1 authoritative execution
→ at most 1 business effect
→ same terminal outcome
```

### Conflicting payload after binding

```text
first payload binds key
same key + different payload
→ deterministic conflict
→ no second effect
```

### Concurrent conflicting payloads

```text
same identity
payload A and payload B race
→ exactly one fingerprint binds to authoritative identity
→ other payload resolves deterministic conflict
→ at most one business effect
```

### Pre-validation failure does not consume key

```text
invalid request fails before authoritative claim
→ no idempotency record
→ corrected request may use the same key
```

### Terminal business rejection replay

```text
valid request claims key
business outcome = controlled rejection
→ REJECTED terminal outcome commits
state later changes
same key + same payload retries
→ same rejection
→ no re-execution
```

### Result/response loss

application-level required proof:

```text
commit success
caller does not consume/observe returned result
retry same trusted identity + same payload
→ same committed business result
→ no duplicate effect
```

POC-02 ไม่อ้างว่า proof นี้เป็น authenticated public HTTP retry proof

### Rollback

```text
forced failure after authoritative mutation begins but before commit
→ no inventory mutation committed
→ no reservation committed
→ no completed idempotency state committed
→ retry remains safe
```

### Optimistic concurrency

```text
two writers
same expected authoritative version
→ one compare-and-set success
→ one version conflict
```

### Scope isolation

```text
same key
different trusted actor scope
→ independent operation identity
```

และ operation scope ต้องไม่ทำให้ key ของ capability อื่น collide โดย design

---

## 24. Final-State Assertions

response count อย่างเดียวไม่ใช่ proof

หลัง concurrency test ต้องตรวจ authoritative state อย่างน้อย:

- `inventory_balance.on_hand`
- `inventory_balance.reserved`
- `inventory_balance.version`
- reservation row count
- reservation IDs
- reservation quantities
- idempotency authoritative row count
- idempotency fingerprint + fingerprint version
- idempotency terminal outcome
- absence of committed `IN_PROGRESS` state
- absence of partial/incomplete committed state ตาม expected flow

สำหรับ `stock=1 / 500` ที่ทุก request ผ่าน pre-validation และใช้ 500 distinct
idempotency identities:

```text
terminal idempotency records = 500
SUCCEEDED = 1
REJECTED = 499
reservation rows = 1
reserved = 1
available = 0
```

ถ้า response กับ DB state ขัดกัน ให้ DB authoritative state เป็นหลักฐานหลักและ
ถือ test ว่า fail

---

## 25. Metrics and Diagnostics

แม้ POC-02 ไม่ใช่ performance POC ต้องเก็บ diagnostic evidence เท่าที่ practical:

- attempt count
- success count
- rejection count by reason
- unexpected error count
- timeout count
- elapsed duration
- representative latency
- configured DB pool size
- observed DB connection pressure ถ้าวัดได้อย่าง reliable
- lock wait observation ถ้าวัดได้อย่าง reliable
- retry count
- deadlock count
- host CPU/RAM context

ไม่มี throughput/latency SLA ใน POC-02

ถ้าต้อง tune performance เพื่อให้ correctness test run ได้ ต้องบันทึกว่าเป็น test
operability adjustment ไม่ใช่ production capacity decision

---

## 26. Failure Classification

Expected business outcomes ต้องไม่ถูกปนกับ infrastructure failure

อย่างน้อยต้องแยกได้:

```text
INSUFFICIENT_STOCK
VERSION_CONFLICT
IDEMPOTENCY_KEY_REUSED
```

POC-02 baseline ไม่ควรมี committed unresolved/`IN_PROGRESS` state

ถ้าพบ durable non-terminal idempotency state:

```text
treat as design violation/finding
must fail safely
must not blindly repeat business mutation
```

replayed terminal business rejection ต้องคง stable business error code/semantics
แต่ request ID เป็นของ inbound attempt ปัจจุบัน

unexpected exception ยังคงใช้ safe generic 5xx baseline จาก POC-01

stack trace, SQL และ credentials ห้ามหลุดสู่ response

---

## 27. Structured Logging

POC-01 structured logging baseline ต้องคงอยู่

สำหรับ POC-02:

- request ID ใช้ correlation
- error code log เมื่อ relevant
- idempotency key ห้ามถูก treat เป็น request ID
- ห้าม log secret
- ห้าม dump environment
- full raw idempotency key ไม่จำเป็นต้อง log เพื่อพิสูจน์ correctness
- ถ้าต้อง correlate idempotent operation ใน log ให้ใช้ safe derived identifier
  หรือ documented non-sensitive representation

Observability expansion เต็มรูปแบบ defer ไป POC-05

---

## 28. Architecture Rules

ต้องรักษา:

```text
Domain
  ← Application
  ← Adapters / Infrastructure
  ← Bootstrap / Composition
```

เพิ่มเติม:

- domain ห้าม depend on Spring/JDBC/JPA/HTTP
- application ห้าม depend on concrete persistence adapter
- idempotency business semantics ต้องไม่อยู่ใน controller เพียงอย่างเดียว
- PostgreSQL-specific concurrency implementation ต้องอยู่ adapter/infrastructure
- web adapter ห้ามเป็น authoritative dedup store
- singleton Spring bean ห้ามเก็บ request mutable state เพื่อทำ dedup

ถ้าเพิ่ม cross-cutting idempotency package ต้องมี ownership/dependency rule ชัด
และห้ามทำ `platform` เป็น dumping ground

---

## 29. Explicitly Forbidden Shortcuts

POC-02 ห้าม:

- global JVM mutex
- `synchronized` map เป็น authoritative concurrency mechanism
- process-local idempotency cache เป็น source of truth
- Redis/distributed lock เพื่อหลบ PostgreSQL proof
- H2/SQLite แทน PostgreSQL concurrency semantics
- fake sleep แล้วสรุปว่าพิสูจน์ race condition แล้ว
- 500 sequential calls แล้วเรียกว่า concurrent
- hidden automatic retries
- weakening DB constraints
- broadening runtime DB privilege
- using request ID as idempotency key
- trusting arbitrary caller-provided actor scope
- committing an `IN_PROGRESS` idempotency lease in a separate transaction for the
  normal synchronous path
- expiring/reusing idempotency keys in POC-02
- creating fake Dispatch/Refund/Payout implementation
- calling POC-02 result a production load/capacity benchmark

---

## 30. Migration / Compatibility Requirement

POC-02 schema change ต้องเป็น new migration

ห้ามแก้ historical POC-01 migration เพื่อ rewrite history

ต้องพิสูจน์:

```text
POC-01 schema
→ apply POC-02 migration
→ compatible runtime schema
```

และ:

- fresh database migration ยังผ่าน
- existing migration history validate
- runtime identity migrate ไม่ได้
- runtime startup ไม่ auto-migrate
- grants เท่าที่ต้องใช้
- rollback/recovery strategy ของ migration ถูกบันทึก

---

## 31. Evidence Requirements

Evidence target:

```text
validation/poc-02/evidence/RESULTS.md
```

ต้องบันทึกอย่างน้อย:

- date/environment
- repository commit tested
- Java version
- Spring Boot version
- Gradle version
- PostgreSQL version
- Testcontainers version
- Flyway version
- relevant JDBC/Hibernate/ArchUnit versions
- exact commands executed
- regular test count / skipped / failures / errors
- PostgreSQL integration test count / skipped / failures / errors
- concurrency harness configuration
- stock=1/500 result
- same-key concurrency result
- conflicting-payload result
- application-level result-loss retry result
- rollback result
- optimistic-concurrency result
- authoritative final DB state
- migration/grant proof
- deadlock/timeout/unexpected-error observation
- decisions supported
- limitations
- deferred questions
- independent review findings
- NOT EXECUTED items

Evidence ต้อง factual และห้ามอ้าง hosted CI ว่าผ่านถ้ายังไม่ได้รันจริง

---

## 32. Decision Outputs

ก่อนปิด POC-02 evidence/decision record ต้องยืนยันอย่างน้อย:

1. reservation stock arbitration ใช้ authoritative stock predicate
2. normal `ReserveInventory` ไม่มี caller-provided `expectedVersion`
3. normal `ReserveInventory` ไม่มี caller/controller-provided `reservationId`
4. reservation identity ถูก generate โดย authoritative first execution
5. optimistic concurrency ยังถูกพิสูจน์เป็น separate CAS/stale-write behavior
6. authoritative idempotency identity
7. fingerprint fields/version/canonicalization strategy
8. PostgreSQL unique transaction-local claim strategy
9. terminal `SUCCEEDED` / `REJECTED` persistence semantics
10. committed terminal-outcome replay strategy
11. concurrent same-key behavior
12. conflicting-payload behavior
13. pre-validation key-consumption rule
14. rollback/retry/unknown-commit resolution semantics
15. actor-scope boundary ก่อน POC-04
16. transaction isolation/locking choice
17. required runtime DB grants
18. public HTTP idempotency binding deferred to POC-04
19. revised normal reservation HTTP contract without `expectedVersion`
20. idempotency key bounds/encoding
21. idempotency retention เป็น deferred production decision
22. reusable pattern ส่วนใดที่ downstream domains ต้อง revalidate

ถ้า decision ใด material ต่อ architecture baseline ให้สร้าง ADR หรือ revise ผ่าน
canonical ADR governance

---

## 33. Exit Gate

POC-02 ผ่านเมื่อทุกข้อด้านล่างมี evidence:

- branch-specific spec frozen ก่อน implementation
- stock=1 / 500 concurrent proof ผ่าน
- exactly one committed reservation สำหรับ final unit
- `available >= 0` preserved
- stock-contention proof ไม่ได้ผ่านเพราะ stale expectedVersion shortcut
- same key + same payload replay safe
- concurrent same key → one authoritative execution / at most one business effect
- same key + conflicting payload → deterministic conflict
- concurrent conflicting payload → one fingerprint binds / other conflicts
- pre-validation failure ไม่ consume key
- terminal business rejection replay stable
- application-level result-loss retry → original committed business result
- no public HTTP idempotency contract is falsely claimed before trusted actor binding
- revised reservation HTTP request no longer requires `expectedVersion`
- normal `ReserveInventoryCommand` no longer accepts caller/controller-generated
  `reservationId`
- authoritative first execution generates reservation identity and replay preserves it
- existing request-ID/safe-error HTTP baseline does not regress
- rollback → no partial business/idempotency state
- no committed `IN_PROGRESS` idempotency state
- optimistic concurrency proof แยกและผ่าน
- PostgreSQL authoritative constraints/uniqueness proven
- migration/runtime privilege separation preserved
- no JVM/Redis/distributed-lock correctness shortcut
- regular/integration/architecture tests green
- zero hidden skips
- no unexpected deadlock/timeouts in required proof
- clean build/artifact baseline ยังคง green
- evidence documented
- independent review complete
- zero unresolved critical contradiction

POC-02 ไม่ถือว่า PASS เพียงเพราะ 500 requests จบโดยไม่มี exception

---

## 34. Independent Review Checklist

final reviewer ต้องตรวจอย่างน้อย:

- implementation ตรง frozen spec
- no scope creep เข้า Dispatch/Refund/Payout
- no hidden global/process-local lock
- no Redis/distributed-lock shortcut
- no fake sequential contention test
- stock proof แยกจาก optimistic-version proof จริง
- same-key semantics backed by PostgreSQL authority
- transaction-local claim + terminal-outcome lifecycle implemented as specified
- no committed `IN_PROGRESS` lease/state in normal synchronous path
- conflicting payload cannot silently replay another payload
- concurrent conflicting payload binds exactly one fingerprint
- controlled business rejection is replayable and does not re-evaluate with same key
- pre-validation failure does not consume key
- application-level result-loss retry cannot duplicate reservation
- no insecure public `Idempotency-Key` contract was introduced before POC-04
- revised HTTP reservation request no longer accepts normal caller `expectedVersion`
- normal reservation command no longer receives controller-generated `reservationId`
- replay preserves the first committed reservation identity
- existing HTTP request ID remains server-controlled
- forced rollback leaves no partial idempotency/business state
- request ID ไม่ถูกใช้เป็น idempotency identity
- actor scope ไม่ได้ trust arbitrary caller input
- runtime privileges ไม่ broaden
- historical migration ไม่ถูก rewrite
- real PostgreSQL used
- no skipped required suite
- final DB state assertions ครบ
- evidence claims match commands/results
- hosted CI/remote checks marked NOT EXECUTED when not actually executed
- deferred POC-03/04/05 concerns ไม่ถูก silently implemented หรือ claimed complete

---

## 35. Freeze Rule

เอกสารนี้ยังเป็น DRAFT จนกว่าจะผ่าน independent spec review

ก่อน freeze ต้อง:

```text
review semantics
→ resolve contradictions
→ confirm normal ReserveInventory has no caller-provided expectedVersion
→ confirm transaction-local PostgreSQL claim + terminal outcome semantics
→ confirm terminal business-rejection replay policy
→ confirm no-expiry POC retention boundary
→ confirm actor-scope boundary
→ confirm public HTTP idempotency binding is deferred to POC-04
→ confirm revised reservation HTTP request removes expectedVersion
→ confirm reservationId is generated by authoritative first execution, not command caller
→ confirm conflicting-payload failure remains application-level until POC-04
→ confirm idempotency key bounds/encoding requirement
→ confirm exact required tests
→ confirm migration/grant implications
→ mark spec FROZEN
→ checkpoint docs
```

หลัง freeze:

- implementation plan ต้องอ้างเอกสารนี้
- ห้ามลด invariant เพื่อให้ implementation ง่ายขึ้น
- ถ้าพบ requirement ขัดกัน ต้องกลับมา revise spec อย่าง explicit
- Git mutation ยังคงเป็น user-controlled workflow
