# POC-01 — Transactional Core / Production Foundation — Shared Specification

**สถานะ:** FROZEN — POC-01 execution baseline
**Branch:** `poc/01-transactional-core`
**Base commit:** `d6ee6acfd2b55d901cea60dda6eafd82e812fc7e`

---

## 1. Purpose

POC-01 มีเป้าหมายเพื่อพิสูจน์ว่า VRA สามารถเริ่ม production-candidate backend foundation บน Java, Spring Boot และ PostgreSQL ได้โดยรักษา security, correctness, transaction boundaries, module boundaries และ operational discipline ที่กำหนดไว้ใน canonical documentation

POC-01 ไม่ใช่ feature sprint และไม่ใช่ marketplace implementation เต็มระบบ

ผลลัพธ์ที่ต้องการคือ foundation ที่สามารถนำไปพัฒนาต่อได้โดย deliberate review หากผ่าน exit gate ทั้งหมด ไม่ใช่ throwaway demo

---

## 2. Decision Chain

การตัดสินใน POC นี้ต้องรักษาลำดับ:

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

ห้ามเลือก framework, library, module layout หรือ persistence style เพราะความคุ้นเคยเพียงอย่างเดียว

---

## 3. Canonical Authority

POC-01 ต้องสอดคล้องกับ:

- `docs/PRODUCT.md`
- `docs/DESIGN.md`
- `docs/SECURITY.md`
- `docs/TESTING.md`
- `docs/OPERATIONS.md`
- `docs/ROADMAP.md`
- `docs/BRANCH_PLAN.md`
- `docs/adr/ADR-001-primary-jvm-language.md`

หาก implementation ขัดกับ canonical documentation ต้องหยุดและ review ความขัดแย้งอย่าง explicit

POC ห้าม rewrite canonical requirement เพื่อให้ implementation ผ่านง่ายขึ้น

---

## 4. Decisions Already Accepted

สิ่งต่อไปนี้ไม่ใช่หัวข้อให้ POC-01 ตัดสินใหม่:

- Java เป็น primary JVM language
- PostgreSQL เป็น authoritative OLTP datastore
- architecture เริ่มจาก modular transactional core
- domain ownership และ dependency direction ต้อง explicit
- runtime DB identity ต้องไม่เป็น schema/database owner
- migration identity ต้องแยกจาก runtime identity
- application transaction ห้ามครอบ external network call
- real PostgreSQL เป็นฐานของ DB integration verification
- migration ต้อง explicit ไม่ผูกกับ application startup โดยอัตโนมัติ
- transactional outbox เป็น architecture direction แต่ implementation ของ outbox อยู่ POC-03
- concurrency/idempotency stress อยู่ POC-02
- auth/session/MFA/security hardening เต็มรูปแบบอยู่ POC-04
- observability/performance/fault validation เต็มรูปแบบอยู่ POC-05

---

## 5. Decisions POC-01 Must Validate

POC-01 ต้องสร้าง evidence เพื่อ finalize หรือ narrow สิ่งต่อไปนี้:

1. Spring Boot เป็น primary backend framework สำหรับ production foundation หรือไม่
2. exact Spring Boot / dependency baseline ที่จะใช้หลัง POC
3. Gradle project/module layout ที่เล็กที่สุดแต่ enforce boundary ได้จริง
4. production package structure
5. persistence split ที่เหมาะสมระหว่าง ORM-style persistence กับ explicit SQL
6. Flyway production lifecycle
7. exact PostgreSQL owner/migrator/runtime privilege model
8. configuration validation และ startup failure semantics
9. liveness/readiness semantics
10. standardized HTTP error contract
11. request ID / correlation baseline
12. structured logging baseline
13. architecture-test enforcement
14. CI-compatible build/artifact baseline

POC ห้ามประกาศสิ่งเหล่านี้ว่า production standard ก่อนมี evidence

---

## 6. Representative Domain Slice

POC-01 ใช้ **Inventory Reservation** เป็น representative slice

เหตุผล:

- เล็กพอที่จะไม่กลายเป็น marketplace feature sprint
- มี business invariant จริง
- ต้องใช้ transaction
- มี database constraints ที่มีความหมาย
- มี critical conditional write
- ใช้ต่อใน POC-02 concurrency/idempotency ได้
- เหมาะสำหรับพิสูจน์ persistence boundary โดยไม่ต้องสร้าง Order/Payment/Logistics ก่อนเวลา

### 6.1 Minimal Concepts

```text
InventoryBalance
Reservation
ReserveInventory command
```

ขั้นต่ำที่ต้องมี:

```text
InventoryKey
- skuId
- ownerId
- locationId
- stockStatus

InventoryBalance
- inventoryKey
- onHand
- reserved
- version

Reservation
- reservationId
- inventoryKey
- quantity
- createdAt
```

POC-01 ยังไม่สร้าง inventory domain เต็มระบบ แต่ representative slice ต้องไม่ทำลาย canonical inventory dimensions `SKU + Owner + Location + Status`

อย่างน้อยต้องมีหนึ่ง reservable status และหนึ่ง non-reservable status เพื่อพิสูจน์ว่า `Status` เป็นส่วนหนึ่งของ authoritative key/availability semantics จริง ไม่ใช่ field ที่ไม่มีผล

release/expiry/allocation lifecycle เป็น scope ของ production inventory branches ภายหลัง ไม่ต้องสร้าง state machine เต็มใน POC-01

POC สามารถปรับชื่อ field/type ได้หลัง repository audit ถ้า semantics ไม่เปลี่ยน

### 6.2 Required Invariants

ต้องรักษาอย่างน้อย:

```text
on_hand >= 0
reserved >= 0
reserved <= on_hand
quantity > 0
available = on_hand - reserved
available >= 0
only reservable stockStatus can be reserved
reservation inventoryKey must match the balance being updated
```

`ReserveInventory` ต้องไม่ทำให้ `reserved > on_hand` และต้องไม่ reserve stock ที่อยู่ใน non-reservable status

DB constraint ต้องเป็น defense-in-depth สำหรับ invariant ที่เหมาะสม ไม่พึ่ง application check เพียงชั้นเดียว

---

## 7. Transaction Requirement

Representative success path ต้องเป็น local database transaction เดียว

Conceptually:

```text
BEGIN

conditional inventory update
+
reservation persistence

COMMIT
```

หากเกิด failure หลัง inventory update แต่ก่อน reservation persistence เสร็จ:

```text
ROLLBACK
```

และ database state ต้องกลับเป็นก่อน transaction

ห้าม:

- external HTTP call ภายใน transaction
- sleep/retry loop ใน transaction
- distributed transaction
- outbox worker ใน POC-01
- hidden auto-commit ที่ทำให้ partial state หลุดออกมา

---

## 8. Critical Conditional Write

ต้องมี critical SQL path ที่ enforce availability ที่ database write boundary

Semantic requirement:

```text
reserve quantity
ONLY IF
on_hand - reserved >= requested quantity
```

implementation อาจใช้ JDBC / Spring `JdbcClient` / jOOQ หรือ explicit SQL mechanism อื่นที่ review แล้ว

ห้ามทำ correctness-critical flow แบบ:

```text
SELECT available
→ application decides
→ unconditional UPDATE
```

แล้วถือว่าเพียงพอสำหรับ production foundation

High-concurrency proof ยังไม่ใช่ exit gate ของ POC-01; stress test อยู่ POC-02

---

## 9. Persistence Validation

POC-01 ต้องมี evidence อย่างน้อยสำหรับ:

```text
simple aggregate persistence
critical conditional SQL
transaction rollback
database constraint failure
optimistic version conflict
```

### 9.1 ORM / JPA Candidate

ถ้าใช้ JPA ใน representative path ต้องพิสูจน์ว่า:

- domain semantics ไม่ถูก ORM entity design บังคับจนผิดรูป
- transaction behavior ชัด
- generated SQL ไม่ซ่อน correctness issue
- optimistic version semantics ถูกต้อง
- mixing กับ explicit SQL อยู่ใน transaction boundary เดียวได้อย่างคาดการณ์ได้

### 9.2 Explicit SQL Candidate

critical conditional write ต้องอ่านและ audit ได้ชัด

SQL ต้องไม่ concatenate untrusted input

### 9.3 POC Outcome

ผลอาจเป็น:

```text
JPA for suitable aggregate-shaped persistence
+
explicit SQL/JDBC/jOOQ for critical state transitions
```

หรือ outcome อื่น

แต่ต้องมาจาก evidence ของ POC-01 ไม่ใช่ lock ล่วงหน้า

---

## 10. PostgreSQL Identity Model

ขั้นต่ำต้องมี logical identities แยกกัน:

```text
owner
migrator
runtime
```

### 10.1 Owner

ควรเป็น non-application ownership identity และไม่ถูกใช้โดย application runtime

เป้าหมายคือทำให้ ownership แยกจาก credentials ที่ application ใช้ประจำวัน

candidate ที่ต้องประเมินหลัง repository audit คือ owner role แบบ `NOLOGIN` เพื่อไม่ให้ application ถือ owner credential โดยตรง

### 10.2 Migrator

ต้องสามารถทำ schema migration ที่ POC ต้องใช้

ไม่ต้องเป็น PostgreSQL superuser

ถ้า PostgreSQL ownership semantics ต้องการให้ migrator เป็น member ของ owner role หรือใช้ controlled `SET ROLE` ให้พิสูจน์และบันทึก exact model แทนการทำ migrator/runtime เป็น owner แบบกว้าง ๆ

exact DDL/role membership ยังไม่ lock ก่อน audit

### 10.3 Runtime

runtime ต้องทำเฉพาะ application DML ที่จำเป็น

ต้องพิสูจน์อย่างน้อย:

```text
CONNECT                 allowed
schema USAGE            allowed
required SELECT         allowed
required INSERT         allowed
required UPDATE         allowed
required DELETE         allowed only if application actually needs it

CREATE TABLE            denied
ALTER TABLE             denied
DROP TABLE              denied
CREATE SCHEMA           denied
```

ห้ามเพิ่ม privilege ให้ runtime เพียงเพื่อแก้ migration/application error

---

## 11. Flyway Lifecycle

Flyway เป็น candidate migration mechanism ที่ต้อง validate ใน POC-01

Required lifecycle:

```text
explicit migration step
        ↓
migration identity
        ↓
schema becomes compatible
        ↓
runtime starts with runtime identity
```

application runtime startup ต้องไม่ทำ production schema migration โดยอัตโนมัติ

POC ต้องพิสูจน์:

- empty database → migration succeeds
- rerun `migrate` หลัง migration สำเร็จแล้วต้องรายงาน schema up-to-date โดยไม่ apply versioned migration ซ้ำ
- checksum/history validation ต้องตรวจ migration drift ตาม Flyway semantics
- versioned migration ไม่ถูกบังคับให้เขียนเป็น idempotent SQL เพียงเพื่อให้ rerun ได้
- runtime credentials ใช้ migrate ไม่ได้
- application starts after compatible migration
- missing/incompatible schema produces explicit failure/readiness behavior ไม่ใช่ silent corruption

---

## 12. Configuration and Secrets

configuration ต้อง externalized

minimum startup validation:

- DB URL required
- runtime DB username required
- runtime DB password required
- blank required value rejected
- invalid URL/config rejected clearly
- secret ไม่มี insecure default
- secret value ไม่ถูก log
- environment dump ไม่ใช่ debugging strategy

migration credentials ต้องไม่ถูก reuse เป็น runtime credentials

---

## 13. HTTP Boundary

POC-01 ต้องมี HTTP boundary เล็กที่สุดที่พิสูจน์ production conventions

ตัวอย่าง capability:

```text
POST /api/v1/inventory/reservations
```

exact route สามารถเปลี่ยนหลัง audit ได้ แต่ semantics ต้อง explicit

### 13.1 Required Request Behavior

- malformed JSON → controlled 4xx
- invalid quantity → controlled 4xx
- unknown resource → controlled 404 เมื่อ semantics แยกได้
- insufficient stock → controlled conflict/business failure
- optimistic version conflict → controlled conflict
- unexpected internal failure → safe generic 5xx
- stack trace / SQL / credentials ห้ามหลุดใน response

---

## 14. Standard Error Contract

ต้องใช้ stable machine-readable error shape

baseline candidate:

```json
{
  "code": "INVENTORY_INSUFFICIENT_STOCK",
  "message": "Insufficient stock",
  "request_id": "server-generated-id"
}
```

`details` เพิ่มได้เฉพาะข้อมูลที่ safe และมี contract ชัด

`code` เป็น stable programmatic contract

`message` ไม่ควรเป็น raw exception text

---

## 15. Request ID

ทุก inbound HTTP request ต้องมี server-controlled request identifier

ขั้นต่ำ:

- generate server-side
- response ส่ง identifier กลับ
- structured log มี identifier
- error response มี identifier
- invalid/untrusted caller input ต้องไม่ถูกใช้โดยตรงเป็น trusted identifier

external correlation propagation สามารถเพิ่มภายหลังเมื่อ boundary ชัด

---

## 16. Health and Readiness

ต้องแยกความหมาย:

### Liveness

ตอบคำถาม:

> process/application ยังทำงานอยู่หรือไม่

ต้องไม่ restart application เพียงเพราะ downstream database ชั่วคราว unavailable

### Readiness

ตอบคำถาม:

> instance พร้อมรับ business traffic หรือไม่

database ที่จำเป็น unavailable ต้องทำให้ readiness ไม่พร้อม

ต้อง document exact endpoint และ semantics หลัง implementation

ห้ามใช้ endpoint เดียวแล้วเรียกทั้ง liveness/readiness โดยไม่มีนิยาม

---

## 17. Structured Logging Baseline

ขั้นต่ำต้องรองรับ machine-readable structured fields สำหรับ:

- timestamp
- level
- logger/component
- request ID เมื่อมี request context
- error classification/code เมื่อเกี่ยวข้อง

ห้าม log:

- DB passwords
- tokens
- secrets
- full credential environment
- unnecessary sensitive business data

POC-01 ยังไม่ต้องสร้าง full observability stack

---

## 18. Module and Dependency Rules

exact Gradle layout จะถูก freeze หลัง read-only repository audit

แต่ dependency direction นี้เป็น requirement:

```text
Domain
  ↑
Application
  ↑
Adapters / Infrastructure
  ↑
Bootstrap / Composition
```

ความหมาย:

- domain ไม่ depend on Spring MVC, JDBC, JPA หรือ HTTP
- application orchestrates use case / transaction intent
- infrastructure implements persistence/adapters
- web/controller layer บาง
- business invariant ไม่อยู่ใน controller
- persistence implementation ไม่กลายเป็น domain API

ArchUnit หรือ equivalent architecture test ต้อง enforce rules ที่สามารถ automate ได้

---

## 19. Gradle Layout Decision Gate

ก่อน production source implementation ต้อง review อย่างน้อยสองทางเลือก:

### Candidate A — Single Gradle application with strong package boundaries

ข้อดีที่ต้องประเมิน:

- simpler build
- lower operational/build complexity
- ArchUnit enforcement อาจเพียงพอ

### Candidate B — Small Gradle multi-module foundation

ข้อดีที่ต้องประเมิน:

- compile-time dependency boundaries
- stronger separation

ต้นทุนที่ต้องประเมิน:

- build complexity
- module ceremony
- duplicated configuration
- developer friction

เกณฑ์ตัดสิน:

> ใช้โครงสร้างที่เล็กที่สุดที่ enforce boundary ที่ VRA ต้องการได้จริง

ห้ามเลือก multi-module เพื่อให้ดู enterprise

---

## 20. Production-Candidate Source Rule

`validation/poc-01/` ใช้เก็บ:

- frozen specification
- evidence
- validation notes
- test/run instructions ที่เฉพาะกับ POC

production-candidate application source ต้องอยู่ใน production tree ที่เลือกหลัง repository audit

source จะถูกถือเป็น accepted production baseline ก็ต่อเมื่อผ่าน POC exit gate และ independent review

ห้าม copy POC shortcut เข้า production โดยอัตโนมัติ

---

## 21. Automated Test Requirements

### 21.1 Unit / Domain

ต้องครอบอย่างน้อย:

- invalid quantity
- invariant-related domain behavior
- typed failures
- state/version behavior ที่มีใน slice

### 21.2 PostgreSQL Integration

ต้องใช้ real PostgreSQL ผ่าน disposable Testcontainers

ห้ามใช้ H2/SQLite แล้วถือว่าแทน PostgreSQL semantics

ต้องครอบอย่างน้อย:

- migration from empty DB
- runtime connectivity
- reservation success
- insufficient stock
- non-reservable stock status rejected
- inventory owner/location/status key isolation
- DB constraint failure
- rollback after forced mid-transaction failure
- optimistic version conflict
- runtime allowed DML
- runtime forbidden DDL

### 21.3 HTTP

ต้องครอบ:

- success contract
- invalid input
- business failure mapping
- unexpected failure safety
- request ID presence
- stable error code

### 21.4 Architecture

ต้องมี automated checks สำหรับ dependency rules ที่ freeze หลัง layout decision

---

## 22. Test Isolation

Automated integration tests ต้องใช้ disposable PostgreSQL

ห้าม:

- reuse local persistent Compose POC database
- clean DEV database เพื่อเตรียม test
- connect tests ไป production/demo
- skip silently เมื่อ database test ควร run

Docker unavailable = test environment failure ไม่ใช่ PASS

---

## 23. Local Development Database

ถ้าต้องมี persistent local PostgreSQL สำหรับ manual smoke:

- ใช้ dedicated POC-01 database
- bind localhost only
- แยกจาก POC-00 database
- แยกจาก DEV/production
- password อยู่ใน ignored local secret location
- destructive commands ต้องระบุชัดว่า destructive

exact Compose layout จะกำหนดหลัง repository audit

---

## 24. Build and Artifact Gate

ต้องมี reproducible clean build command

minimum:

```text
compile
unit tests
integration tests
architecture tests
artifact build
```

artifact ขั้นต่ำของ POC-01 คือ deployable JVM application artifact

OCI image สามารถเพิ่มได้ถ้าตรงกับ repository foundation โดยไม่ขยาย scope; ไม่ใช่เหตุผลให้ POC ล้มเหลวหาก canonical exit gate ยังไม่ได้กำหนดให้ต้องมี image ใน phase นี้

---

## 25. Forbidden Scope

POC-01 ห้ามขยายไปทำ:

- full catalog
- full order lifecycle
- checkout
- payment provider integration
- refund
- payout/settlement
- fulfillment
- shipment/logistics
- driver assignment
- authentication/session implementation
- MFA/passkeys
- full RBAC
- transactional outbox worker
- message broker
- Redis
- Kafka/RabbitMQ/NATS
- OpenSearch
- ClickHouse
- Kubernetes
- service mesh
- GraphQL
- internal gRPC
- multi-region
- sharding
- load test 100/500/1000 VUs
- stock=1 / 500 buyers concurrency proof

สิ่งเหล่านี้มี phase หรือ evidence gate ของตัวเอง

---

## 26. No Hidden Convenience

POC-01 ต้องไม่ผ่านด้วย shortcut ต่อไปนี้:

- runtime = DB owner
- runtime = superuser
- application startup auto-migrates schema
- H2 แทน PostgreSQL
- disabled integration tests
- swallowed exception
- broad `catch (Exception)` แล้ว return success
- network call ใน transaction
- hard-coded secret
- logging secret
- disabling DB constraint เพื่อให้ test ผ่าน
- broadening privilege เพื่อแก้ error
- controller-owned business rule
- undocumented manual database repair

---

## 27. Required Evidence

สร้าง:

```text
validation/poc-01/evidence/RESULTS.md
```

อย่างน้อยต้องบันทึก:

```text
Environment
JDK version
Spring Boot version
Gradle version
PostgreSQL version
Testcontainers version
Architecture-test library/version

Repository commit tested
Commands executed
Tests executed
Test counts
Failures/skips
Migration result
Runtime/migrator privilege result
Rollback result
Constraint result
Optimistic-version result
Health/readiness result
HTTP error-contract result
Artifact build result

Decisions supported by evidence
Known limitations
Deferred questions
Independent review findings
```

ห้ามเขียน `PASS` ให้สิ่งที่ไม่ได้ execute

---

## 28. Required Manual Verification

นอกจาก automated tests ต้องมี manual smoke ที่ reproduce ได้:

```text
1. start dedicated local PostgreSQL
2. migrate using migrator identity
3. start application using runtime identity
4. verify liveness
5. verify readiness
6. execute successful reservation
7. execute insufficient-stock request
8. prove runtime DDL denial
9. stop application/infrastructure cleanly
```

command จริงจะถูกบันทึกใน `validation/poc-01/README.md` หลัง exact project layout ถูกเลือก

---

## 29. Exit Gate

POC-01 เป็น PASS ต่อเมื่อทุกข้อด้านล่างมี evidence:

- production-oriented Java project builds
- Spring Boot suitability validated
- exact framework baseline recorded
- project/module layout justified
- module dependency boundaries enforced
- PostgreSQL integration green
- Flyway explicit migration lifecycle works
- runtime/migrator separation proven
- runtime forbidden DDL proven
- representative conditional SQL works
- transaction rollback proven
- DB constraint behavior proven
- optimistic version behavior proven
- configuration validation works
- liveness/readiness semantics verified
- standardized error contract verified
- request ID verified
- structured logging baseline verified
- architecture tests green
- clean build green
- artifact build green
- no hidden skips
- no secret exposure observed
- evidence documented
- independent review complete
- unresolved critical contradiction = 0

ถ้าข้อใดไม่ได้ execute ให้สถานะเป็น `NOT EXECUTED` ไม่ใช่ PASS

---

## 30. Failure Conditions

POC-01 ต้องถือว่า FAIL หรือยังไม่ complete หาก:

- ต้องให้ runtime เป็น owner/superuser
- migration ต้องเกิดจาก runtime startup จึงใช้งานได้
- transaction rollback ไม่สามารถพิสูจน์ได้
- PostgreSQL behavior ถูกแทนด้วย in-memory DB
- module boundary มีแต่ documentation แต่ enforce ไม่ได้
- critical SQL correctness อาศัย read-then-unconditional-write
- error response leak internal exception/SQL/secrets
- test ถูก skip โดยไม่รายงาน
- evidence ไม่ตรงกับ command/result ที่ execute
- implementation ต้องเพิ่ม deferred infrastructure เพื่อให้ foundation ทำงาน

---

## 31. Decision Outputs

เมื่อ POC-01 จบ ต้องมีคำตอบ explicit อย่างน้อย:

```text
Spring Boot primary framework: ACCEPT / REJECT / REVISE
Exact Spring baseline: <version>
Gradle layout: <decision>
Package/module rules: <decision>
Persistence strategy: <decision>
Flyway lifecycle: <decision>
DB ownership/grants: <decision>
Health/readiness: <decision>
Error contract: <decision>
Logging baseline: <decision>
Artifact baseline: <decision>
```

material architecture decision ต้องบันทึก ADR ตาม governance

---

## 32. Independent Review Checklist

ผู้ review ต้องตรวจอย่างน้อย:

- implementation ตรง spec
- canonical docs ไม่ถูก bypass
- no privilege broadening
- no hidden auto migration
- no hidden skipped tests
- DB integration ใช้ PostgreSQL จริง
- rollback proof เชื่อถือได้
- constraint proof เชื่อถือได้
- error contract ไม่ leak internals
- module rules enforce จริง
- persistence choice มี evidence
- dependencies ไม่ถูกเพิ่มโดยไม่มีเหตุผล
- POC-02/03/04/05 scope ไม่รั่วเข้ามา
- evidence reproduce ได้

---

## 33. Git Governance

Git mutation ทำโดย user หลัง review

Automation/Codex ห้ามทำ Git-mutating commands

ก่อนทุก meaningful checkpoint ต้องตรวจ:

```bash
git status --short
git diff --check
```

ก่อน commit ต้อง review staged diff แยกจาก working-tree diff

---

## 34. POC-01 Execution Sequence

หลัง spec ถูก review และเปลี่ยนสถานะเป็น `FROZEN`:

```text
1. Read-only repository audit
2. Record current repository structure
3. Decide exact production source location
4. Decide exact Gradle/module layout
5. Decide exact Spring/dependency baseline
6. Write implementation plan
7. User approves plan
8. Implement smallest production-candidate foundation
9. Add DB roles/migrations
10. Implement representative Inventory Reservation transaction
11. Add required automated tests
12. Run clean verification
13. Run manual smoke
14. Capture RESULTS.md
15. Independent review
16. Resolve findings
17. Record final technology decisions / ADRs
18. Close POC-01
19. User performs Git checkpoint/merge
```

---

## 35. Freeze Rule

หลังสถานะเอกสารนี้เป็น `FROZEN`:

- implementation ต้องไม่เปลี่ยน requirement เงียบ ๆ
- ถ้าพบว่า spec ผิดหรือพิสูจน์ไม่ได้ ให้แก้ spec แบบ explicit พร้อมเหตุผลก่อนเดินต่อ
- evidence ต้องสะท้อนสิ่งที่ execute จริง
- POC-01 ต้อง optimize เพื่อ learning/evidence ที่ใช้ production foundation ได้ ไม่ใช่เพื่อ demo speed
