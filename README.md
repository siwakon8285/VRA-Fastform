# VRA-Fastform

VRA (วีล่า) คือแพลตฟอร์ม marketplace, commerce, fulfillment และ logistics ที่กำลังพัฒนาด้วยแนวทาง **security-first, correctness-first และ evidence-driven architecture**

Repository นี้อยู่ในช่วงสร้าง production foundation หลังจากปิดการทดลอง JVM language validation แล้ว ระบบยัง **ไม่ถือว่า production-ready** จนกว่าจะผ่าน validation, security, recovery, observability และ production-readiness gates ที่กำหนดไว้ใน canonical documentation

## สถานะปัจจุบัน

### POC-00 — JVM Language Validation

**Status:** CLOSED

POC-00 เปรียบเทียบ Java และ Kotlin บน JVM/Spring/PostgreSQL baseline เดียวกัน พร้อม controlled-evolution experiment และ independent review

ผลการตัดสิน:

```text
Primary JVM Language = Java
```

Decision record:

- [ADR-001 — Primary JVM Language](docs/adr/ADR-001-primary-jvm-language.md)

Historical validation evidence:

- [POC-00 overview](validation/poc-00/README.md)
- [Shared specification](validation/poc-00/SHARED_SPEC.md)
- [Java evidence](validation/poc-00/evidence/java.md)
- [Kotlin evidence](validation/poc-00/evidence/kotlin.md)
- [Controlled evolution evidence](validation/poc-00/evidence/evolution.md)

เนื้อหาใต้ `validation/` เป็น validation spec, harness และ historical evidence ไม่ใช่ production source โดยอัตโนมัติ

## Canonical Documentation

เอกสารต่อไปนี้เป็น source of truth หลักของ VRA baseline v1:

- [PRODUCT.md](docs/PRODUCT.md) — VRA คืออะไร, business concepts และ product invariants
- [DESIGN.md](docs/DESIGN.md) — architecture, domain ownership, transactions, data และ integration boundaries
- [SECURITY.md](docs/SECURITY.md) — identity, authorization, trust boundaries, secrets และ security invariants
- [TESTING.md](docs/TESTING.md) — วิธีพิสูจน์ correctness, concurrency, security, recovery และ performance
- [OPERATIONS.md](docs/OPERATIONS.md) — deployment, migration, observability, backup, restore, recovery และ runbooks
- [BRAND.md](docs/BRAND.md) — brand identity, UI principles, accessibility และ motion direction
- [ROADMAP.md](docs/ROADMAP.md) — ลำดับ phase และทิศทางการพัฒนาระดับระบบ
- [BRANCH_PLAN.md](docs/BRANCH_PLAN.md) — execution map ระดับ Git branch, scope, dependency และ exit gate
- [Architecture Decision Records](docs/adr/README.md) — ประวัติและเหตุผลของ architectural decisions

เมื่อ code และ canonical documentation ขัดกัน ต้อง review ความขัดแย้งอย่าง explicit ห้ามแก้ documentation ให้ตาม implementation โดยอัตโนมัติ

## Architecture Direction

Baseline architecture:

```text
Modular Transactional Core
+ Explicit Domain Boundaries
+ PostgreSQL Authoritative OLTP
+ Transactional Outbox
+ Worker Plane
+ Derived Search / Tracking / Analytics
+ Evidence-Driven Service Extraction
```

หลักสำคัญ:

- ไม่เริ่มจาก fine-grained microservices
- ไม่สร้าง fake localhost HTTP ระหว่าง modules
- ไม่มี global distributed ACID เป็น default
- authoritative business truth ต้องมี owner ชัดเจน
- external uncertainty ต้องรองรับ `UNKNOWN` และ reconciliation เมื่อเหมาะสม
- complexity ใหม่ต้องมี requirement หรือ evidence รองรับ

## Technology Status

### Accepted / Direction Locked

- Java เป็น primary JVM language
- Spring Boot เป็น primary backend framework; POC-01 accepted baseline = 4.1.1
- PostgreSQL เป็น authoritative OLTP datastore
- backend build ใช้ `runtime` + `migration` two-module baseline ตาม ADR-002
- persistence baseline ใช้ JPA สำหรับ aggregate-shaped persistence และ explicit SQL/JdbcClient สำหรับ correctness-critical state transitions
- Flyway migration รันเป็น explicit migration process แยกจาก runtime
- PostgreSQL ownership baseline แยก `vra_owner` / `vra_migrator` / `vra_runtime`
- Next.js + TypeScript เป็น web direction
- REST-style HTTP/JSON + OpenAPI เป็น external/client API direction
- transactional outbox เป็น asynchronous integration baseline
- OCI/container artifacts เป็น artifact direction
- Docker Compose เป็น initial runtime orchestration
- Nginx เป็น initial reverse-proxy boundary
- OpenTelemetry-compatible instrumentation เป็น observability direction
- Bruno, Playwright และ k6 เป็น baseline verification tools ตามขอบเขตที่เกี่ยวข้อง

### Validate Next

POC-01 ปิดแล้วและ material foundation decisions ถูกบันทึกใน
[`ADR-002`](docs/adr/ADR-002-backend-production-foundation.md)

POC-02 **CLOSED**: พิสูจน์ PostgreSQL concurrency และ idempotency correctness
สำหรับ Inventory Reservation ซึ่งเป็น representative slice
([evidence](validation/poc-02/evidence/RESULTS.md)) ผลลัพธ์ยืนยัน foundation
ที่ยอมรับไว้ จึงไม่ต้องสร้างหรือแก้ ADR

Next validation target: **POC-03 — Outbox / Workers / Recovery** (PLANNED)

### Deferred Until Evidence

ตัวอย่าง technology ที่ยังไม่เพิ่มโดยอัตโนมัติ:

- Redis
- Kafka / RabbitMQ / NATS
- OpenSearch
- ClickHouse
- Kubernetes
- GraphQL
- internal gRPC
- service mesh
- sharding
- multi-region active-active
- CDC platform

## ขั้นต่อไป

Current validation state:

```text
POC-01 Transactional Core / Production Foundation
→ CLOSED
→ ADR-002 accepted
POC-02 Concurrency / Idempotency
→ CLOSED
→ evidence: validation/poc-02/evidence/RESULTS.md
→ no new/revised ADR required
Next: POC-03 Outbox / Workers / Recovery
→ PLANNED
```

ก่อนเริ่ม POC-03 implementation ต้อง review/freeze branch-specific specification
และ verification plan ตาม [BRANCH_PLAN.md](docs/BRANCH_PLAN.md)

## Repository Governance

Git mutation เป็น deliberate review gate

Automation/Codex สามารถช่วยวิเคราะห์และ implement ตามขอบเขตที่ได้รับมอบหมาย แต่ต้องไม่ทำ Git mutation

คำสั่งอย่าง `add`, `commit`, `merge`, `rebase`, `reset`, `push`, `pull`, `switch`, `stash` หรือ `amend` ให้ user เป็นผู้รันเองหลัง review

POC evidence ต้องเก็บตามข้อเท็จจริงและไม่ rewrite ย้อนหลังเพื่อให้เข้ากับ decision ที่เกิดภายหลัง
