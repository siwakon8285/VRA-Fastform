# POC-01 — Implementation Plan

**สถานะ:** APPROVED — POC-01 implementation baseline
**Branch:** `poc/01-transactional-core`
**Frozen spec:** `validation/poc-01/SHARED_SPEC.md`
**Plan basis:** read-only repository audit + canonical VRA documentation

---

## 1. Audit Conclusions

Repository ปัจจุบันยังไม่มี production JVM source tree

สิ่งที่มีอยู่:

```text
docs/
validation/
docker-compose.yml        # historical POC-00 local PostgreSQL
```

Gradle/Spring source ทั้งหมดอยู่ใต้ `validation/poc-00/`

ดังนั้น POC-01 ไม่ต้อง migrate production source เก่า และสามารถสร้าง production-candidate tree ใหม่ได้โดยไม่ rewrite historical POC-00

POC-00 ให้ evidence ที่ reuse ได้ในเชิงแนวคิด:

```text
Java 21
explicit migration lifecycle
real PostgreSQL Testcontainers
separate regular/integration tests
ArchUnit
conditional SQL
safe HTTP error mapping
request ID
```

แต่ POC-00 source ยังคงเป็น historical validation evidence และไม่ถูกย้ายเข้าฝั่ง production แบบ copy ทั้งชุด

---

## 2. Proposed Technology Baseline

```text
Java                 21
Spring Boot          4.1.1 candidate
Gradle Wrapper       9.7.1 candidate
PostgreSQL           17.11
Flyway               Spring Boot 4.1.1 managed baseline
Testcontainers       Spring Boot 4.1.1 managed baseline
ArchUnit             explicit dependency; validate compatibility in build
Build DSL            Kotlin DSL
```

เหตุผลหลัก:

- Java 21 ถูก accepted แล้วจาก ADR-001
- Spring Boot 3.5.16 เป็น historical POC-00 baseline ไม่ใช่ production lock
- POC-01 เป็นจุดที่เหมาะสำหรับพิสูจน์ Spring Boot 4.x เพราะเป็น production foundation ใหม่
- PostgreSQL 17.11 ยังคงเป็น supported/current minor ของ major 17 และลด unrelated variable จาก POC-00
- dependency ที่ Spring Boot manage ได้ ให้ใช้ Boot dependency management ก่อนการ pin เองโดยไม่มีเหตุผล

หาก build/test evidence พบ incompatibility ต้องบันทึกและ revise plan/spec อย่าง explicit

---

## 3. Proposed Repository Layout

```text
VRA-Fastform/
├── backend/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gradle/
│   │   └── wrapper/
│   │
│   ├── runtime/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/dev/vra/
│   │       │   └── resources/
│   │       └── test/
│   │
│   └── migration/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── java/dev/vra/migration/
│           │   └── resources/db/migration/
│           └── test/
│
├── validation/
│   ├── poc-00/                 # historical evidence; unchanged
│   └── poc-01/
│       ├── SHARED_SPEC.md      # frozen
│       ├── README.md           # reproducible manual commands
│       ├── compose.yaml        # dedicated local POC-01 PostgreSQL only
│       └── evidence/
│           └── RESULTS.md
│
├── docs/
└── docker-compose.yml          # historical POC-00 compose; unchanged
```

---

## 4. Gradle Layout Decision

เลือก **small two-module backend build**

```text
backend
├── runtime
└── migration
```

ไม่แตก domain เป็น Gradle module ใน POC-01

เหตุผล:

```text
runtime
→ application runtime identity
→ HTTP/business execution
→ ห้ามมี schema-migration responsibility

migration
→ migrator identity
→ Flyway lifecycle
→ separate executable artifact/process
```

นี่เป็น module boundary ที่มาจาก security/operations requirement จริง ไม่ใช่การสร้าง module เพื่อความสวยงาม

Domain boundaries ภายใน `runtime` ใช้ package boundaries + ArchUnit ก่อน

หากภายหลัง compile-time module boundary ให้ประโยชน์จริง ค่อย revisit หลังมีหลาย bounded contexts

---

## 5. Runtime Package Layout

Baseline:

```text
dev.vra
├── VraApplication
│
├── inventory
│   ├── domain
│   ├── application
│   │   └── port
│   └── adapter
│       ├── in
│       │   └── web
│       └── out
│           └── persistence
│
└── platform
    ├── configuration
    ├── error
    ├── health
    └── web
```

Rule:

```text
domain
→ no Spring MVC/JPA/JDBC dependency

application
→ use cases + ports + transaction intent

adapter.in.web
→ HTTP mapping only

adapter.out.persistence
→ JPA/JDBC implementation

platform
→ narrow cross-cutting technical concerns
```

`platform` ห้ามกลายเป็น generic dumping ground

---

## 6. Representative Inventory Model

POC-01 ต้องรักษา canonical dimensions:

```text
InventoryKey
= SKU
+ Owner
+ Location
+ Status
```

ขั้นต่ำ:

```text
InventoryBalance
- InventoryKey
- onHand
- reserved
- version

Reservation
- reservationId
- InventoryKey
- quantity
- createdAt
```

ต้องมีอย่างน้อย:

```text
AVAILABLE     # reservable
QUARANTINED   # non-reservable representative status
```

ชื่อ final สามารถเปลี่ยนได้ถ้า semantics ไม่เปลี่ยน

---

## 7. Persistence Experiment

POC-01 จะพิสูจน์ hybrid candidate จริง ไม่ lock จากความเชื่อ:

```text
InventoryBalance critical transition
→ explicit SQL / Spring JdbcClient
→ conditional UPDATE
→ availability enforced at write boundary

Reservation persistence
→ JPA candidate
→ persistence entity อยู่ใน adapter.out.persistence
→ domain object ไม่เป็น ORM entity โดยอัตโนมัติ
```

ต้องพิสูจน์ว่า JPA write และ explicit JDBC write เข้าร่วม transaction เดียวกันได้ถูกต้อง

ถ้า evidence บอกว่า hybrid นี้เพิ่ม complexity โดยไม่มีประโยชน์ ให้ revise persistence decision

jOOQ ยังไม่เพิ่มใน POC-01 เพราะ JdbcClient เพียงพอสำหรับพิสูจน์ explicit SQL boundary โดยไม่เพิ่ม code generation/build complexity

---

## 8. Transaction Shape

Success:

```text
BEGIN

conditional UPDATE inventory_balance
WHERE key matches
  AND status reservable
  AND on_hand - reserved >= quantity

INSERT reservation

COMMIT
```

Forced failure:

```text
conditional inventory UPDATE succeeds
→ forced failure before reservation commit
→ ROLLBACK
→ inventory state unchanged
```

ห้าม external network call ใน transaction

---

## 9. Database Identity Model

POC-01 candidate:

```text
bootstrap/admin
    │
    ├── creates database/roles for local test harness only
    │
    ▼
vra_owner            NOLOGIN
    ▲
    │ controlled membership / SET ROLE
vra_migrator         LOGIN
    │
    └── migration process only

vra_runtime          LOGIN
    └── application DML only
```

Target semantics:

```text
vra_owner
→ owns application schema/objects
→ never used by runtime

vra_migrator
→ no SUPERUSER
→ no CREATEDB
→ no CREATEROLE
→ can assume only required owner role for migration

vra_runtime
→ CONNECT
→ schema USAGE
→ required SELECT/INSERT/UPDATE
→ DELETE only if demonstrated necessary
→ no CREATE TABLE
→ no ALTER TABLE
→ no DROP TABLE
→ no CREATE SCHEMA
→ not member of vra_owner
```

Exact bootstrap/grant SQL ต้องมี automated verification

---

## 10. Migration Process

`backend:migration` เป็น executable migration process แยกจาก runtime

Lifecycle:

```text
migrator credentials
→ migration executable
→ SET ROLE / ownership model as validated
→ Flyway migrate
→ Flyway validate/history
→ exit

runtime credentials
→ runtime application
→ no migration execution
```

`runtime` ไม่ควรมี Flyway auto-migration responsibility

migration resources อยู่กับ migration module

runtime integration tests สามารถใช้ migration module บน test classpath เพื่อสร้าง disposable database แต่ production runtime artifact ไม่ต้องรับ migration responsibility

---

## 11. HTTP Baseline

Representative API candidate:

```text
POST /api/v1/inventory/reservations
```

Response/error baseline ต้องพิสูจน์:

```text
success
malformed request
invalid quantity
unknown inventory key
non-reservable status
insufficient stock
optimistic conflict
safe unexpected 5xx
```

Error shape:

```json
{
  "code": "INVENTORY_INSUFFICIENT_STOCK",
  "message": "Insufficient stock",
  "request_id": "server-generated-id"
}
```

---

## 12. Health / Readiness

Candidate endpoints:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Semantics:

```text
liveness
→ process health
→ DB outage must not cause restart loop

readiness
→ traffic eligibility
→ required DB unavailable = not ready
```

Exact actuator configuration ต้องถูกทดสอบ ไม่ assume จาก default

---

## 13. Request ID / Logging

Runtime ต้องสร้าง server-controlled request ID

ต้องปรากฏใน:

```text
response header
error response
structured log context
```

caller-provided ID ห้ามถูก trust เป็น canonical request ID โดยตรง

POC-01 ทำ logging baseline เท่านั้น; full tracing/OTel อยู่ POC-05

---

## 14. Test Layout

Runtime:

```text
unit/domain tests
HTTP/controller contract tests
architecture tests
PostgreSQL integration tests
transaction rollback tests
runtime privilege tests
```

Migration:

```text
empty DB migration
rerun migrate => up-to-date
Flyway validate/history
migration with migrator succeeds
migration with runtime fails
checksum drift detection
```

Integration database:

```text
PostgreSQL 17.11 Testcontainers
disposable per test suite/context
never persistent Compose DB
```

No H2/SQLite substitute

---

## 15. Architecture Tests

อย่างน้อย enforce:

```text
inventory.domain
  !-> Spring MVC
  !-> JPA
  !-> JDBC
  !-> adapter

inventory.application
  !-> adapter

adapter.in.web
  !-> adapter.out.persistence

domain
  !-> platform implementation details
```

architecture rule ต้อง match package design จริงหลัง implementation

---

## 16. Local Manual Environment

ไม่แก้ root `docker-compose.yml` เพราะเป็น historical POC-00 harness

สร้าง:

```text
validation/poc-01/compose.yaml
```

สำหรับ dedicated POC-01 PostgreSQL เท่านั้น

database/credentials ต้องแยกจาก POC-00

secret values อยู่ใน ignored local secret files และห้าม embed ใน compose/source

---

## 17. CI Baseline

Repository audit พบว่ายังไม่มี `.github/workflows/`

POC-01 ต้องทำ build ให้ CI-compatible ก่อน

หลัง local verification stable ให้เพิ่ม minimal backend workflow:

```text
JDK 21
./backend/gradlew clean check
artifact build
PostgreSQL integration tests through Testcontainers
```

CI ต้องไม่ silently skip Docker/PostgreSQL tests

CI workflow ไม่ใช่เหตุผลให้ลด test gate หาก runner configuration ผิด

---

## 18. Implementation Sequence

```text
1. create backend Gradle wrapper/build
2. create runtime + migration modules
3. verify empty clean build
4. add migration executable + initial schema
5. add PostgreSQL role bootstrap for POC-01 local/Testcontainers harness
6. prove migrator success/runtime migration denial
7. implement InventoryKey/domain invariants
8. implement persistence adapters
9. implement reservation transaction
10. implement HTTP/error/request-ID boundary
11. configure health/readiness
12. add architecture tests
13. add transaction/constraint/privilege integration tests
14. add reproducible POC-01 README
15. run clean verification
16. manual smoke
17. capture RESULTS.md
18. independent review
19. resolve findings
20. record final POC-01 decisions/ADR if material
```

---

## 19. Files That Must Not Be Rewritten

POC-01 implementation must not rewrite historical POC-00 evidence/source merely to share code

Preserve:

```text
validation/poc-00/**
root docker-compose.yml
POC-00 evidence
ADR-001 history
```

shared production code may be created deliberately under `backend/`; do not symlink/copy historical validation tree wholesale

---

## 20. Implementation Approval Gate

No production-candidate source implementation begins until user approves this plan

Approval means authorizing the implementation plan, **not** authorizing Git mutation

Git add/commit/merge/push/switch/rebase/reset/stash remain user-controlled
