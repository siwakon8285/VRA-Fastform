# กลยุทธ์การทดสอบ VRA

**เอกสาร:** `docs/TESTING.md`
**สถานะ:** ACTIVE — canonical baseline v1
**ผลิตภัณฑ์:** VRA (วีล่า)
**ขอบเขต:** กลยุทธ์การพิสูจน์ correctness, security, concurrency, migration, API contract, failure behavior, recovery, performance และ architecture boundaries ของ VRA

> เอกสารนี้กำหนดว่า VRA จะพิสูจน์พฤติกรรมสำคัญอย่างไร
> Product intent และ business invariants อยู่ใน `PRODUCT.md`
> Architecture อยู่ใน `DESIGN.md`
> Security invariants อยู่ใน `SECURITY.md`
> Deployment/recovery procedure อยู่ใน `OPERATIONS.md`

---

## 1. เป้าหมายของการทดสอบ

Testing ของ VRA มีเป้าหมายมากกว่า “ให้ CI เป็นสีเขียว”

เราต้องพิสูจน์ว่า:

1. business invariant ยังถูกต้อง
2. transaction rollback ทำงานตามที่ออกแบบ
3. concurrent requests ไม่ทำลาย authoritative state
4. duplicate/retry ไม่สร้าง side effect ซ้ำ
5. migration รักษาข้อมูลเดิม
6. authorization ปฏิเสธ actor ที่ไม่ควรเข้าถึง
7. external uncertainty เข้าสู่ state ที่ recover/reconcile ได้
8. worker crash และ retry ไม่ทำให้งานสูญหายหรือซ้ำแบบอันตราย
9. API contract stable ตามที่ประกาศ
10. derived system สามารถ stale/rebuild ได้โดยไม่เปลี่ยน transactional truth
11. backup/restore และ reconciliation ใช้งานได้จริง
12. performance อยู่ภายใน threshold ที่กำหนด
13. architecture boundaries ไม่ค่อย ๆ พังจาก implementation drift

---

## 2. หลักการทดสอบ

### 2.1 Invariant First

เริ่มจาก invariant ไม่ใช่เริ่มจาก controller method

ตัวอย่าง:

```text
Invariant:
available-to-promise >= 0

แล้วค่อยถาม:
- unit test อะไร
- DB constraint อะไร
- integration test อะไร
- concurrency test อะไร
```

### 2.2 Real Dependency Where Correctness Depends on It

ถ้า behavior ขึ้นกับ PostgreSQL semantics ต้องใช้ PostgreSQL จริงในการทดสอบ

ห้ามใช้ H2/mock แล้วถือว่าได้พิสูจน์:

- locking
- isolation
- CHECK constraint
- index behavior
- SQL syntax
- `RETURNING`
- `SKIP LOCKED`
- transaction semantics
- Flyway migration

### 2.3 Test the Failure Path

Happy path อย่างเดียวไม่พอ ต้องทดสอบ invalid input, unauthorized access, duplicate, timeout, retry, stale version, unknown outcome, partial failure, worker restart, migration failure และ recovery

### 2.4 No Silent Skip

```text
Not Executed ≠ Passed
```

ถ้า environment ไม่พร้อม ต้องรายงาน `NOT EXECUTED` พร้อมเหตุผล ห้าม auto-skip critical test แล้วนับว่า green

### 2.5 Flaky Test = Defect

Flaky test ต้องถูก treat เป็น defect เช่นเดียวกับ code defect ต้องหาสาเหตุ ไม่ใช่ rerun จนผ่านแล้ว ignore

---

## 3. Testing Portfolio

VRA ใช้หลายชั้นร่วมกัน:

```text
Domain / Unit
State-machine
Property / Invariant
Application
Database Integration
Migration
Adapter / Provider Contract
HTTP/API Contract
Security
Architecture
Concurrency
Worker / Outbox
E2E
Failure Injection
Performance / Load
Backup / Restore / DR
Production Monitoring / Reconciliation
```

ไม่มี test layer เดียวแทนทั้งหมดได้

---

## 4. Domain และ State Tests

Domain test ต้อง deterministic และไม่พึ่ง network หรือ clock จริงโดยตรง

ต้องครอบคลุมอย่างน้อย:

- valid/invalid state transition
- terminal state
- Money/currency invariant
- reservation invariant
- account lifecycle
- strong identifier
- reason-code invariant
- refund limit
- seller/customer ownership rule

เมื่อเพิ่ม state ใหม่ compiler และ tests ต้องช่วย expose affected logic

---

## 5. Property / Invariant Tests

สำหรับ rule ที่มี input space กว้าง ให้คิดเป็น property เช่น:

```text
available = on_hand - reserved
available >= 0
```

หรือ:

```text
cumulative_refund <= captured_amount
```

ไม่จำเป็นต้องใช้ property-testing library ทุกกรณี แต่ test design ต้องยึด invariant มากกว่า example เดียว

---

## 6. Money Tests

ต้องตรวจ:

- exact arithmetic
- currency mismatch
- rounding policy
- zero/negative policy
- boundary values
- aggregation
- serialization

ห้ามใช้ floating point เป็น authoritative financial assertion

---

## 7. Strong Identifier Tests

ต้องทดสอบ:

- valid identifier
- nil/zero identifier ถ้า forbidden
- malformed wire value
- serialization
- domain construction bypass attempt

Strong type มีไว้ลด semantic mix-up ไม่ใช่แค่ห่อ UUID/String เพื่อความสวยงาม

---

## 8. Application Service Tests

Application test เน้น orchestration:

- transaction boundary
- authorization precondition
- domain operation sequencing
- idempotency
- outbox creation
- provider adapter boundary

Mock ใช้ได้เมื่อเราต้อง isolate orchestration แต่ห้ามใช้แทน PostgreSQL behavior ที่ correctness พึ่งพา

---

## 9. PostgreSQL Integration Tests

ทุก behavior ที่ขึ้นกับ PostgreSQL ต้อง test กับ PostgreSQL จริง

Baseline:

```text
Testcontainers
```

ใช้ disposable isolated database ต่อ suite/test scope ตามความเหมาะสม

ห้าม automated test ใช้ persistent developer Compose DB

---

## 10. DEV ≠ TEST

```text
DEV database
≠
automated TEST database
```

ห้าม:

- truncate developer DB เพื่อให้ test ผ่าน
- reset local persistent volume โดย automation
- reuse demo/prod data
- destructive test บน shared environment

---

## 11. Migration Tests

ต้องพิสูจน์:

- fresh database `0 → latest`
- populated previous version → latest
- legacy rows survive
- constraints ใหม่ถูกต้อง
- invalid state ถูก reject
- migration history/checksum valid
- rerun ไม่มี pending migration

Migration ที่ซับซ้อนต้องมี representative legacy data

---

## 12. Expand → Backfill → Switch → Contract Tests

ถ้า migration ใช้ phased evolution ต้องมี compatibility tests ตามช่วง:

```text
old app + expanded schema
new app + expanded schema
backfill complete
new read path
contract release
```

ห้าม assume rolling deployment compatibility โดยไม่ทดสอบ

---

## 13. API Contract Tests

HTTP/API tests ต้องตรวจ:

- method/path
- request/response shape
- required/optional fields
- status
- stable error code
- request ID
- malformed input
- unknown field policy
- boundary values
- authorization

---

## 14. Bruno

Bruno เป็น baseline black-box API tool สำหรับ:

- local smoke
- API contract
- representative regression
- manual verification

Bruno collection ต้องไม่มี secret และแต่ละ critical request ควรมี assertion ที่ชัดเจน

---

## 15. OpenAPI Verification

OpenAPI ต้องไม่ drift จาก implementation

ควรมี gate ตรวจ:

- field shape
- required/optional
- enum
- response status
- error schema

---

## 16. Stable Error Tests

Error test ต้อง assert:

```text
status
code
message safety
requestId
```

และตรวจว่าไม่ leak SQL, stack trace, filesystem path, secret หรือ provider credential

---

## 17. Authorization Tests

Protected resource ทุกประเภทต้องมี positive + negative cases

ตัวอย่าง:

```text
Customer A → own order = allowed
Customer A → Customer B order = denied

Seller A → own offer = allowed
Seller A → Seller B offer = denied
```

Identifier randomness ไม่ใช่ authorization

---

## 18. Staff / Privileged Tests

ต้องมี:

- correct role
- insufficient role
- wrong scope
- state/context mismatch
- step-up required
- maker-checker
- self-approval denied
- audit attribution

---

## 19. Authentication / Session Tests

ต้องครอบคลุม:

- valid session
- invalid credential
- expired session
- revoked session
- locked/disabled/closed account
- session rotation
- logout/revocation
- malformed auth material

---

## 20. CSRF / CORS / Browser Security Tests

เมื่อใช้ cookie session ต้องทดสอบ CSRF positive/negative path

CORS test ต้องตรวจ disallowed origins ด้วย ไม่ใช่แค่ allowed origin

Browser security headers และ session cookie attributes ต้องมี verification ตาม environment

---

## 21. Webhook Tests

Inbound webhook ต้อง test:

- valid signature
- invalid signature
- wrong key
- stale timestamp
- duplicate
- replay
- malformed payload
- delayed event
- out-of-order event

Business side effect ต้อง idempotent

---

## 22. File Upload / Object Security Tests

ต้องตรวจ:

- type/size
- malformed file
- extension/MIME mismatch
- path traversal filename
- unauthorized access
- private-by-default behavior
- signed URL expiry
- duplicate upload semantics

---

## 23. SSRF Tests

ถ้ามี URL fetch capability ต้องทดสอบ:

- localhost
- loopback variants
- private ranges
- metadata endpoints
- redirect to private network
- unsupported scheme
- timeout/size limit

---

## 24. SQL Injection และ Mass Assignment Tests

SQL input ต้อง parameterized

Mass-assignment tests ต้องส่ง forbidden field เช่น:

```json
{
  "role": "ADMIN",
  "ownerId": "...",
  "status": "CONFIRMED"
}
```

แล้วพิสูจน์ว่า authoritative state ไม่เปลี่ยน

---

## 25. Architecture Tests

Architecture rules ต้อง executable เช่น:

```text
domain !-> interfaces
domain !-> infrastructure
domain !-> JDBC/Web
application !-> infrastructure implementation
```

ใช้ ArchUnit หรือ equivalent

ถ้าใช้ multi-module Gradle ต้อง enforce dependency direction เพิ่มด้วย

---

## 26. Transaction Tests

ต้องพิสูจน์:

- success commits
- failure rolls back
- no partial authoritative state
- outbox + mutation atomic
- external network call ไม่อยู่ใน critical DB transaction

---

## 27. Outbox Tests

ต้องทดสอบ:

```text
business mutation succeeds
outbox insert fails
→ rollback all
```

และ:

```text
business mutation fails
→ no outbox
```

Worker tests ต้องครอบคลุม claim, success, retry, duplicate, crash, lease expiry, dead-letter และ replay

---

## 28. Inbox / Deduplication Tests

Event เดิมหลาย delivery:

```text
delivery #1
delivery #2
delivery #3
```

ต้อง produce business effect ตาม semantics เพียงครั้งเดียว

---

## 29. Saga / Process Manager Tests

ต้อง test:

- happy path
- failure ทุก step
- compensation
- timeout
- duplicate
- stale event
- unknown provider result
- restart/resume

---

## 30. Reconciliation Tests

ตัวอย่าง:

```text
internal payment = UNKNOWN
provider = SUCCEEDED
→ reconcile to internal success
```

และ:

```text
internal payout = UNKNOWN
provider remains unknown
→ remain unresolved
→ no blind retry
```

---

## 31. Concurrency Tests

Concurrency test พิสูจน์ correctness ไม่ใช่ throughput อย่างเดียว

ใช้ real PostgreSQL และ simultaneous operations

---

## 32. Inventory Contention Gate

Critical scenario:

```text
stock = 1
buyers = 500
successful reservation = exactly 1
available >= 0
```

ต้อง assert final DB state

---

## 33. Dispatch Concurrency Gate

ตัวอย่าง:

```text
one exclusive job
1000 assignment attempts
exactly one active assignment
```

---

## 34. Refund Concurrency Gate

```text
captured = 1000
parallel refunds
sum(committed/pending refund obligations) <= 1000
```

---

## 35. Settlement / Payout Concurrency

ต้องพิสูจน์ว่า duplicate/concurrent command ไม่สร้าง financial transfer ซ้ำ และ `UNKNOWN` ไม่ trigger blind replacement

---

## 36. Optimistic Concurrency Tests

ถ้าใช้ version:

```text
A reads version 10
B reads version 10
A writes → 11
B writes expected 10 → conflict
```

ต้องไม่ silent overwrite

---

## 37. Time Tests

ใช้ injected `Clock`

ทดสอบ expiry, lease, timeout และ boundary โดยไม่ `sleep()` โดยไม่จำเป็น

เวลา occurrence/receive/process ต้องแยกเมื่อ semantics ต่างกัน

---

## 38. Retry / Dead-Letter Tests

ต้องตรวจ:

- retryable vs permanent
- bounded attempts
- backoff
- exhaustion
- failed/dead-letter state
- controlled replay
- audit/observability

Infinite retry ไม่ยอมรับ

---

## 39. Derived Projection Tests

Search/tracking/read model ต้อง test:

- source mutation
- update
- duplicate event
- out-of-order event
- stale state
- rebuild

Derived projection ห้ามใช้เป็น proof ของ authoritative write

---

## 40. E2E Tests

E2E มีไว้พิสูจน์ critical journey ไม่ใช่ทุก permutation

ตัวอย่าง:

```text
login
→ browse
→ checkout
→ durable order
→ payment coordination
→ fulfillment
→ shipment
```

Playwright เป็น baseline web E2E tool

---

## 41. E2E Data Isolation

แต่ละ test run ต้องมี unique data scope

ห้ามพึ่ง test order หรือ shared mutable account โดยไม่จำเป็น

---

## 42. Failure Injection

ต้องมี scenario:

- PostgreSQL unavailable
- provider timeout
- provider 5xx
- worker crash
- duplicate event
- delayed event
- process restart
- outbox backlog
- temporary network failure
- migration failure

---

## 43. External Provider Testing

ใช้หลายระดับร่วมกัน:

```text
unit fake
sanitized fixture
contract test
provider sandbox
controlled failure simulation
```

CI หลักไม่ควรขึ้นกับ unstable external sandbox ทุก run แต่ก่อน production ต้องมี real provider validation

---

## 44. Compatibility Tests

API/event/schema evolution ต้องทดสอบ backward compatibility ตาม policy

ตัวอย่าง:

- old reader/new writer
- new reader/old writer
- added optional field
- enum expansion
- deprecated field

---

## 45. Database Constraint Tests

Critical constraint ต้องมี explicit integration tests เช่น:

- reserved <= on_hand
- state/reason matrix
- unique active assignment
- unique idempotency operation
- foreign key ownership
- finance integrity constraints ที่เลือกใช้

---

## 46. Final-State Assertions

Response status อย่างเดียวไม่พอ

หลัง critical request ต้องตรวจ authoritative state เช่น:

- inventory
- payment
- order
- outbox
- audit
- version

---

## 47. Test Evidence

Validation/POC ต้องบันทึก:

- exact commands
- versions
- environment
- executed suites
- pass/fail/skip
- not executed
- limitations
- Git status

Evidence ต้อง factual

---

## 48. Test Counts ไม่ใช่ Quality Score

จำนวน test เป็นข้อมูล ไม่ใช่คะแนน

Coverage ก็เป็น signal ไม่ใช่ proof ของ correctness

สิ่งสำคัญคือ coverage ของ invariant, failure mode, concurrency และ recovery

---

## 49. Static Analysis / Supply Chain Checks

CI ควรมีตามความเหมาะสม:

- compiler warnings
- formatting/lint
- dependency vulnerability scan
- secret scan
- static analysis
- architecture rules

ไม่เพิ่ม tool เพื่อ badge อย่างเดียว

---

## 50. Performance Test Categories

แยก:

```text
micro benchmark
component benchmark
load
stress
spike
soak
capacity
concurrency correctness
```

ห้ามเรียกทุกอย่างรวมว่า load test

---

## 51. k6 Baseline

k6 เป็น baseline performance/load tool

Metric ขั้นต่ำ:

- VUs
- RPS
- p50
- p95
- p99
- error rate
- timeout rate
- duration

---

## 52. Load-Test Data

ใช้ dedicated TEST/load data

ห้าม destructive load test กับ:

- DEV data
- demo data
- production data
- real customer data

---

## 53. Separate Load Generator

เมื่อโหลดสูงพอ ให้ใช้เครื่องแยกสำหรับ k6 เพื่อไม่ให้ load generator แย่ง CPU/RAM จาก system under test

---

## 54. Performance Correlation

ต้อง correlate k6 กับ:

- Nginx
- JVM/Spring
- DB pool
- PostgreSQL
- Docker
- CPU
- RAM
- disk
- network

---

## 55. Connection Pool Tests

ต้องวัด:

- active
- idle
- waiters
- acquisition latency
- timeout

ห้ามแก้ performance ด้วยการเพิ่ม pool size อย่างเดียวโดยไม่ดู DB saturation

---

## 56. Soak / Spike / Capacity

Soak หา leak/degradation

Spike หา backpressure/recovery

Capacity ตอบว่า workload สูงสุดเท่าไรที่ยังอยู่ใน SLO/resource budget

ทุกผลต้องระบุ hardware/environment context

---

## 57. Backup Tests

Backup test ต้องตรวจ:

- job success
- artifact exists
- encryption
- retention
- off-host copy
- integrity

Backup success ≠ restore success

---

## 58. Restore Tests

ต้อง restore จริงเป็นระยะ

ตรวจ:

- schema
- data
- migration history
- app startup
- critical queries
- financial/audit integrity
- isolation from production providers

---

## 59. PITR / DR Tests

ถ้าใช้ PITR ต้องทดสอบ target time และ post-restore reconciliation

DR drill ต้องวัด:

- RTO
- RPO
- operator steps
- credential availability
- documentation correctness
- reconciliation backlog

---

## 60. Production Smoke

หลัง deploy ใช้ non-destructive checks เช่น:

- liveness
- readiness
- DB connectivity
- migration version
- safe representative read
- worker health
- outbox/reconciliation lag

---

## 61. Business Health Verification

Monitoring ต้องมี correctness signals เช่น:

- payment `UNKNOWN` backlog
- stale reservation
- outbox lag
- reconciliation age
- failed payout operation
- invariant violation

---

## 62. Release / Rollback Verification

ต้องรู้ว่า:

- code rollback compatible กับ schema หรือไม่
- event compatibility เป็นอย่างไร
- migration ควร rollback หรือ forward-fix
- release abort threshold คืออะไร

---

## 63. Test Environment Matrix

Baseline:

```text
DEV
Disposable Integration TEST
CI TEST
LOAD
STAGING
PROD smoke
```

แต่ละ environment ต้องมี purpose และ data policy ชัดเจน

---

## 64. Unicode / ภาษาไทย

VRA ต้องทดสอบ UTF-8 และภาษาไทยอย่างจริงจัง

เช่น:

- normalization
- whitespace
- length
- search
- display
- serialization
- filename/metadata

---

## 65. Boundary Values

ต้องมี test สำหรับ:

- zero
- one
- max
- max+1
- empty
- null
- long text
- Unicode
- extreme money
- timestamp boundary

---

## 66. CI Required Gates

ก่อน merge production code baseline ควรมี:

```text
compile
domain/unit
architecture
PostgreSQL integration
migration
API contract
security/static checks
artifact build
```

POC สามารถมี gate ต่างกัน แต่ต้องบันทึกตามจริง

---

## 67. CI Failure Policy

Critical gate fail = merge blocked

ห้าม:

- rerun จน pass โดยไม่หาสาเหตุ
- disable test
- weaken assertion
- เพิ่ม timeout แบบเดาสุ่ม
- mark skip เพื่อให้ pipeline green

---

## 68. Quarantine Policy

ถ้าต้อง quarantine flaky non-critical test ต้องมี:

- owner
- reason
- issue
- deadline
- visible status

Critical correctness/security tests ไม่ควร quarantine ง่าย ๆ

---

## 69. POC-01 Testing Scope

POC-01 ต้องพิสูจน์:

- Java/Spring production baseline
- module boundaries
- PostgreSQL
- Flyway
- transaction behavior
- persistence baseline
- runtime vs migrator DB identity
- health/readiness
- standardized errors
- Testcontainers
- architecture tests
- CI-compatible build

---

## 70. POC-02 Testing Scope

POC-02:

- stock=1 / 500 buyers
- idempotency
- one active dispatch
- refund limit under concurrency
- settlement/payout uniqueness
- expected-version conflict
- retry/duplicate behavior

---

## 71. POC-03 Testing Scope

POC-03:

- transactional outbox
- worker claim
- duplicate delivery
- worker crash/restart
- bounded retry
- dead-letter
- reconciliation
- projection rebuild

---

## 72. POC-04 Testing Scope

POC-04:

- authentication
- authorization
- BFF/session
- CSRF
- MFA/step-up
- adversarial/security validation ของ DB least-privilege model จาก POC-01
- secrets
- workload identity
- webhook verification
- audit

---

## 73. POC-05 Testing Scope

POC-05:

- logs
- metrics
- traces
- k6
- p50/p95/p99
- RPS
- CPU/RAM
- PostgreSQL
- pool
- Nginx
- Docker
- fault injection
- soak/spike
- recovery observation

---

## 74. Production Readiness Gate

ก่อน production ต้องมี evidence อย่างน้อยสำหรับ:

- checkout/order correctness
- inventory concurrency
- payment uncertainty
- refund cap
- seller/customer isolation
- staff authorization
- migration
- backup/restore
- reconciliation
- load baseline
- deployment recovery
- alerting

---

## 75. Definition of PASS

```text
required tests executed
expected assertions passed
zero unexpected failures
zero hidden skips
environment matched required scope
required evidence captured
```

---

## 76. Definition of NOT VERIFIED

Feature/POC ต้องเป็น `NOT VERIFIED` เมื่อ:

- required test ไม่รัน
- dependency unavailable
- test skipped
- blocker unresolved
- environment ไม่ representative
- result ambiguous

---

## 77. Test Review Questions

ก่อน merge critical change ให้ถาม:

1. invariant ไหนเปลี่ยน
2. happy path มี test ไหม
3. failure path มีไหม
4. authorization negative path มีไหม
5. DB constraint มี integration test ไหม
6. concurrency มีผลไหม
7. duplicate/retry มีผลไหม
8. migration มี legacy-data test ไหม
9. unknown outcome มี reconciliation test ไหม
10. final authoritative state ถูก assert ไหม
11. architecture boundary ยังผ่านไหม
12. test flaky ได้ไหม
13. มีอะไร `NOT EXECUTED` หรือไม่

---

## 78. Canonical Testing Rule

หลักของ VRA คือ:

> **สิ่งสำคัญต้องพิสูจน์ด้วย test ที่เหมาะกับ failure mode จริง ไม่ใช่เพียง mock ให้ผ่านหรือเชื่อว่า implementation ดูถูกต้อง**

และ:

```text
Business invariant
        ↓
Executable test
        ↓
Realistic dependency
        ↓
Failure / concurrency evidence
        ↓
Operational confidence
```

Testing เป็นส่วนหนึ่งของ architecture และ product correctness ตั้งแต่ต้น ไม่ใช่งานปิดท้ายหลังเขียน code เสร็จ
