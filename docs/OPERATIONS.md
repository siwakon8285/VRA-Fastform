# การปฏิบัติการและการกู้คืน VRA

**เอกสาร:** `docs/OPERATIONS.md`
**สถานะ:** ACTIVE — canonical baseline v1
**ผลิตภัณฑ์:** VRA (วีล่า)
**ขอบเขต:** การ build, deploy, migrate, operate, observe, backup, restore, recover, reconcile และดูแล VRA ตั้งแต่ local development จนถึง initial production deployment

> เอกสารนี้กำหนด operational intent ของ VRA
> Product semantics อยู่ใน `PRODUCT.md`
> Architecture อยู่ใน `DESIGN.md`
> Security controls อยู่ใน `SECURITY.md`
> Verification strategy อยู่ใน `TESTING.md`

---

## 1. เป้าหมายของ Operations

Operations ของ VRA ต้องทำให้ระบบ:

1. deploy ได้อย่างควบคุม
2. rollback/roll-forward ได้โดยมีข้อมูลประกอบการตัดสิน
3. migrate database ได้โดยไม่ใช้ runtime identity เป็น schema owner
4. ตรวจ health ได้จริง
5. หา root cause จาก logs/metrics/traces ได้
6. backup และ restore ได้จริง
7. recover จาก host/process/database failure ได้
8. reconcile กับ external systems หลัง failure/restore ได้
9. ลด blast radius ของ human error
10. audit operational action สำคัญได้
11. แยก environment และ credential อย่างชัดเจน
12. ไม่ใช้ production เป็นพื้นที่ทดลอง

---

## 2. Operational Principles

### 2.1 Build Once, Promote Artifact

VRA ใช้หลัก:

```text
source commit
→ CI build
→ immutable artifact
→ verify
→ promote
→ deploy
```

ห้ามใช้ production server เป็น build workstation โดยไม่มีเหตุผลเฉพาะ

Production deployment ไม่ควรอาศัย:

```text
ssh server
git pull
build on host
run latest source
```

เป็น release mechanism หลัก

### 2.2 Explicit Migration

Database migration เป็นขั้นตอน deployment ที่ explicit

ห้ามถือว่า application startup ต้องมี schema-owner privilege เสมอ

### 2.3 Least Privilege Runtime

Runtime application identity ต้องมีเฉพาะ permission ที่จำเป็นต่อ application workload

Migration identity ต้องแยกจาก runtime identity

### 2.4 Recovery Is Part of Design

ระบบถือว่ายัง operate ไม่สมบูรณ์ หากไม่มีคำตอบว่า:

- restore อย่างไร
- external state drift อย่างไร
- reconcile อย่างไร
- operator ตรวจอะไรหลัง recovery

### 2.5 Observe Before Change

เมื่อ incident เกิด:

```text
observe
→ classify
→ contain
→ collect evidence
→ change
→ verify
```

หลีกเลี่ยงการแก้แบบสุ่มหลายอย่างพร้อมกันจนสูญเสียข้อมูล root cause

---

## 3. Environment Model

Baseline environments:

```text
DEV
TEST
LOAD
STAGING
PROD
```

### DEV

ใช้สำหรับ local development และ manual developer verification

### TEST

ใช้ automated tests / disposable databases / CI

### LOAD

ใช้ performance/load/concurrency experiments กับ dedicated test data

### STAGING

ใช้ production-like deployment validation เมื่อมี infrastructure พร้อม

### PROD

ใช้ real production traffic และ real business data

---

## 4. Environment Isolation

แต่ละ environment ต้องแยกอย่างน้อย:

- database
- credential
- secret
- external provider account/sandbox mode
- object-storage namespace
- callback/webhook endpoint
- deployment config

ห้าม:

```text
TEST → PROD database
LOAD → PROD data
DEV → production provider secret
```

โดยไม่ตั้งใจ

---

## 5. Local Development

Local development สามารถใช้ Docker Compose สำหรับ infrastructure dependency

แนวทาง:

```text
frontend/backend
→ run manually for development

PostgreSQL / infrastructure
→ Docker Compose
```

Automated integration test ใช้ Testcontainers ไม่ใช้ persistent local Compose DB

Local secret ต้องอยู่นอก Git

ตัวอย่าง:

```text
.local/secrets/
```

---

## 6. Initial Production Topology

Initial self-hosted topology ของ VRA ใช้แนวทาง:

```text
Internet
   ↓
Cloudflare
   ↓
Named Tunnel / cloudflared
   ↓
HTTPS origin on loopback/private boundary
   ↓
Nginx
   ↓
VRA application containers
   ↓
PostgreSQL
```

หลักสำคัญ:

- ไม่เปิด PostgreSQL สู่ internet
- application port ไม่เปิด public โดยตรง
- public traffic ต้องผ่าน controlled edge/proxy path
- SSH ใช้ key-based access
- firewall default deny
- host เป็น single failure domain และต้องไม่เรียกว่า HA

---

## 7. Initial Safe-Host Reality

Initial safe-host เป็น single-host deployment

ดังนั้น:

```text
host failure
= VRA unavailable
```

แม้มีหลาย container ก็ยังไม่ใช่ high availability

Mitigation ในระยะแรก:

- off-host backup
- tested restore
- monitoring
- controlled deployment
- recovery runbook
- reconciliation หลัง restore
- spare/rebuild plan

---

## 8. Host Layout

แนะนำแยก path ตาม responsibility เช่น:

```text
/srv/apps/proxy
/srv/apps/postgres
/srv/apps/projects
/srv/data/postgres
/srv/backups
```

หลัก:

- application config แยกจาก data
- persistent DB data ไม่อยู่ใน source checkout
- backup path ชัดเจน
- secret file permission จำกัด
- deployment artifact เปลี่ยนได้โดยไม่กระทบ persistent state

---

## 9. Host Access

Production host access ต้อง:

- SSH key only
- จำกัด user
- sudo เฉพาะจำเป็น
- audit shell access ตาม capability
- disable password auth เมื่อ practical
- patch OS
- firewall enabled
- no shared root credential

---

## 10. Firewall

Default:

```text
deny inbound
allow required only
```

Publicly exposed port ต้องมีเหตุผล

โดยทั่วไปไม่ควร public:

```text
5432
3000
8080
```

80/443 exposure ขึ้นกับ edge/tunnel topology

---

## 11. Nginx Change Procedure

ก่อน reload Nginx ต้อง:

```bash
nginx -t
```

เสมอ

Flow:

```text
edit config
→ nginx -t
→ inspect result
→ reload
→ verify health
```

ห้าม reload config ที่ syntax validation ไม่ผ่าน

---

## 12. Cloudflare / Tunnel Operations

Tunnel credential ถือเป็น secret

ห้าม:

- commit
- paste ลง issue
- ใส่ screenshot
- log
- hard-code ใน Compose

ต้องมี revoke/rotate plan

Origin ต้องไม่เปิด parallel public path ที่ bypass tunnel โดยไม่ตั้งใจ

---

## 13. Build Artifacts

Primary artifact direction:

```text
OCI container image
```

Artifact ต้อง trace กลับไปได้ถึง:

- Git commit
- build timestamp
- version
- dependency baseline
- CI result

---

## 14. Release Identification

ทุก release ควรมี identifier เช่น:

```text
Git SHA
semantic/release version
image digest
```

Production deployment record ต้องตอบได้ว่า:

> ตอนนี้รัน source commit ไหน?

---

## 15. Artifact Immutability

หลัง artifact ผ่าน verification:

- ห้ามแก้ไฟล์ใน image
- ห้าม rebuild tag เดิมแล้วชี้ content ใหม่
- prefer digest/reference ที่ตรวจสอบได้

---

## 16. Deployment Sequence

Baseline deployment:

```text
1. select artifact
2. verify artifact metadata
3. review release notes/change scope
4. backup/check recovery readiness when needed
5. run approved migration
6. deploy application
7. readiness check
8. smoke check
9. observe metrics/logs
10. declare release healthy
```

---

## 17. Deployment Preflight

ก่อน deploy production ต้องตรวจ:

- working artifact identified
- migration reviewed
- required secret present
- disk space
- DB healthy
- backup recent
- restore capability not known-broken
- external dependency status เมื่อ relevant
- rollback/roll-forward plan
- operator access works

---

## 18. Database Identities

อย่างน้อย:

```text
vra_migrator
vra_runtime
```

### `vra_migrator`

ใช้สำหรับ schema migration

### `vra_runtime`

ใช้สำหรับ application DML/SELECT ตาม scope

ห้ามเพิ่ม runtime privilege แบบกว้างเพียงเพื่อแก้ migration error

---

## 19. Migration Execution

Migration ต้อง:

- ใช้ exact artifact/version
- ใช้ migrator identity
- run explicit
- capture result
- verify Flyway history
- fail deployment เมื่อ migration fail

Application runtime ไม่ควร auto-own migration permission ใน production

---

## 20. Migration Preflight

ก่อน migration สำคัญตรวจ:

- schema version ปัจจุบัน
- migration checksum
- estimated lock
- table size
- disk space
- backup/recovery
- old/new app compatibility
- expected runtime

---

## 21. Migration Failure

ถ้า migration fail:

1. หยุด deployment
2. เก็บ exact error
3. ตรวจว่ามี partial transactional effect หรือไม่
4. ห้าม rerun แบบเดาสุ่ม
5. ห้าม grant privilege เพิ่มแบบ broad
6. เลือก forward-fix หรือ rollback ตาม migration semantics
7. verify database integrity

---

## 22. Expand → Backfill → Switch → Contract

สำหรับ change ขนาดใหญ่ใช้:

```text
Expand
→ deploy compatible code
→ Backfill
→ verify
→ Switch
→ Contract later
```

ห้าม destructive contract ใน release เดียวถ้า backward compatibility ยังจำเป็น

---

## 23. Application Deployment

Application deployment ต้อง:

- pull immutable image
- apply environment config
- inject secret securely
- start new process/container
- verify readiness
- switch traffic ตาม topology
- observe errors/latency

---

## 24. Readiness

Readiness ตอบ:

> instance พร้อมรับ traffic หรือไม่?

อาจขึ้นกับ:

- DB connectivity
- required internal dependency
- critical initialization

แต่ต้องระวังไม่ผูกกับ external provider ทุกตัวจนระบบไม่พร้อมเพราะ third party ชั่วคราว

---

## 25. Liveness

Liveness ตอบ:

> process ยัง alive หรือ deadlock/hung หรือไม่?

Liveness ไม่ควร fail เพราะ:

- payment provider down
- email provider down
- search unavailable

มิฉะนั้น orchestrator อาจ restart healthy process แบบไม่จำเป็น

---

## 26. Business Health

Business health แยกจาก liveness/readiness

ตัวอย่าง:

- payment UNKNOWN backlog สูง
- outbox lag สูง
- reconciliation stale
- refund queue stuck
- inventory inconsistency
- projection lag

Application อาจ technically alive แต่ business health แย่

---

## 27. Smoke Test หลัง Deploy

ใช้ non-destructive smoke:

- `/health`
- readiness
- safe authenticated read
- DB version check
- worker status
- outbox lag
- representative API contract

ห้ามใช้ smoke ที่ createเงินจริง/ส่งเงินจริงโดยไม่ควบคุม

---

## 28. Release Observation Window

หลัง deploy ต้องมี observation period ตาม risk

ดู:

- error rate
- p95/p99
- DB connections
- CPU/RAM
- GC
- outbox lag
- business error code
- security anomalies

---

## 29. Rollback vs Roll-Forward

การตัดสินขึ้นกับ:

- schema compatibility
- data already transformed
- external side effect
- event compatibility
- artifact availability

หลาย database migration ควร prefer roll-forward fix

Code rollback ใช้ได้เมื่อ schema ยัง backward-compatible

---

## 30. Rollback Preparedness

ก่อน deploy ต้องรู้:

- previous image digest
- previous config
- schema compatibility
- command/steps
- expected recovery time

---

## 31. Configuration Management

Config ต้อง:

- versioned เมื่อไม่ secret
- validated on startup
- environment-specific
- no silent default ที่ dangerous
- traceable to release

Secret ไม่ควรอยู่ใน same config repository แบบ plaintext

---

## 32. Secret Operations

Secret lifecycle:

```text
create
→ distribute
→ use
→ rotate
→ revoke
→ audit
```

Production secret ต้องไม่ถูกแสดงใน shell transcript/documentation

---

## 33. Secret Rotation Procedure

Rotation ต้องมี:

- new secret provision
- overlap window ถ้าต้องการ
- application reload/redeploy
- verify new credential
- revoke old
- audit result

---

## 34. Database Backup Strategy

Backup ต้องรองรับ:

- full/base backup
- WAL / PITR เมื่อเปิดใช้
- off-host copy
- retention
- integrity check
- encryption
- restore test

---

## 35. Backup Is Not Recovery

การมีไฟล์ backup ไม่ได้แปลว่ากู้คืนได้

ต้องพิสูจน์:

```text
backup
→ restore
→ app start
→ data validation
→ reconciliation
```

---

## 36. Off-Host Backup

อย่างน้อยหนึ่ง backup copy ต้องอยู่นอก physical host เดียวกับ production

เพราะ:

```text
disk loss / host loss
```

อาจทำลายทั้ง primary และ local backup พร้อมกัน

---

## 37. Backup Retention

Retention ต้องกำหนดตาม:

- RPO
- legal/business requirement
- storage cost
- audit/finance needs

ไม่เก็บ unlimited โดย default

---

## 38. Backup Encryption

Backup production ต้อง encryption at rest และ access จำกัด

Encryption key/credential ต้องไม่เก็บไว้เฉพาะ host เดียวกับ backup จน recovery ทำไม่ได้

---

## 39. Restore Drill

Restore ต้องทดสอบเป็นระยะ

ขั้นต่ำ:

1. provision isolated target
2. restore backup
3. verify schema
4. verify row/data integrity
5. run app
6. run critical smoke
7. run reconciliation simulation
8. record RTO/RPO

---

## 40. Point-in-Time Recovery

เมื่อใช้ PITR ต้องรู้:

- base backup
- WAL availability
- target timestamp
- restore command/process
- expected data loss window
- post-restore reconciliation plan

---

## 41. RPO

Recovery Point Objective ตอบ:

> ยอมเสียข้อมูลย้อนหลังได้เท่าไร?

ต้องกำหนดจาก business impact ไม่ใช่เลือกตัวเลขสุ่ม

---

## 42. RTO

Recovery Time Objective ตอบ:

> ยอมให้ระบบ unavailable ได้นานเท่าไร?

RTO ต้องรวม:

- restore
- validation
- app startup
- reconciliation gate
- traffic reopen

---

## 43. Post-Restore Reconciliation

หลัง restore database ไปอดีต external world อาจเดินต่อไปแล้ว

ตัวอย่าง:

```text
DB restored to 10:00
payment provider processed until 10:07
```

ห้ามเปิด financial write ปกติทันทีโดยไม่ตรวจ

ต้อง reconcile:

- payment
- refund
- payout
- shipment/provider state
- webhook gap
- inventory integration

---

## 44. Financial Recovery Gate

หลัง major restore:

```text
finance reconciliation
must complete to defined threshold
before unrestricted financial operations resume
```

Exact policy ขึ้นกับ incident severity

---

## 45. Worker Recovery

Worker crash ต้อง:

- restart ได้
- reclaim expired work
-ไม่สูญเสีย durable job
- process duplicate safely
- preserve attempt history

---

## 46. Outbox Operations

ต้อง monitor:

- oldest unprocessed age
- pending count
- failed count
- retry count
- processing rate

Outbox lag สูงเป็น business health issue

---

## 47. Dead-Letter Operations

Dead-letter item ต้องมี workflow:

```text
inspect
→ classify
→ fix root cause
→ replay or close
```

ห้าม replay batch แบบ blind

---

## 48. Reconciliation Operations

Reconciliation job ต้องมี:

- schedule
- scope
- checkpoint
- idempotency
- metrics
- failed-item queue
- operator visibility

---

## 49. Logs

Application logs ต้อง structured เมื่อ practical

Field ตัวอย่าง:

```text
timestamp
level
service
requestId
traceId
operation
result
errorCode
duration
```

ห้าม log secret

---

## 50. Audit vs Application Log

Audit record ไม่ใช่ application log

Application log อาจ rotate/delete ตาม operational retention

Audit มี retention/integrity requirement แยก

---

## 51. Metrics

Baseline:

```text
RED
Rate
Errors
Duration

USE
Utilization
Saturation
Errors
```

เพิ่ม business metrics เช่น:

- payment unknown
- refund pending age
- outbox lag
- inventory rejection
- reconciliation backlog

---

## 52. Tracing

Distributed tracing direction:

```text
OpenTelemetry-compatible
```

Trace ต้องช่วยเชื่อม:

```text
edge
→ API
→ application
→ DB
→ worker
→ provider adapter
```

เมื่อมี boundary จริง

---

## 53. Alerting

Alert ต้อง actionable

Alert ที่ดีต้องตอบ:

- อะไรผิด
- severity
- affected service/business capability
- current value
- threshold
- runbook

ห้าม alert ทุก metric จน operator ignore ทั้งหมด

---

## 54. Alert Severity

ตัวอย่าง:

### SEV-1

- security breach active
- financial duplication risk
- widespread transaction corruption
- production fully unavailable

### SEV-2

- major capability unavailable
- high backlog
- critical provider integration stuck

### SEV-3

- partial degradation
- isolated failure
- non-urgent capacity warning

Exact policy ต้องปรับเมื่อ team โตขึ้น

---

## 55. Incident Lifecycle

```text
Detect
→ Acknowledge
→ Contain
→ Diagnose
→ Mitigate
→ Recover
→ Reconcile
→ Close
→ Review
```

---

## 56. Incident Commander

Major incident ควรมี owner/commander คนเดียวที่ coordinate

ลดปัญหา operator หลายคนแก้ production พร้อมกันโดยไม่มี coordination

---

## 57. Change Freeze ระหว่าง Incident

เมื่อ incident รุนแรง:

- หยุด unrelated deploy
- ลด concurrent changes
- preserve evidence
- document command สำคัญ

---

## 58. Production Command Logging

Critical manual command ควรถูกบันทึกใน incident/change record

ห้าม paste secret ลง record

---

## 59. Runbook Standard

Runbook ต้องมี:

```text
symptom
impact
preconditions
diagnostic commands
safe actions
dangerous actions
verification
rollback/recovery
escalation
```

---

## 60. Database Incident

เมื่อ PostgreSQL มีปัญหา ตรวจ:

- process/container
- disk
- connection count
- locks
- long transaction
- WAL
- memory
- CPU
- storage latency

ห้าม restart DB เป็น first action โดยไม่เก็บ evidence ถ้าไม่จำเป็น

---

## 61. Disk Exhaustion

ต้อง monitor disk โดยเฉพาะ:

- PostgreSQL data
- WAL
- Docker
- logs
- backup staging

Disk 100% สามารถสร้าง severe DB failure

Alert ก่อนถึง critical threshold

---

## 62. Connection Exhaustion

เมื่อ connection pool/DB เต็ม:

- inspect active connections
- pool wait
- long transactions
- leaked connections
- traffic spike

ห้ามเพิ่ม max connections อย่างเดียวโดยไม่รู้ root cause

---

## 63. Lock Contention

Monitor:

- blocked sessions
- lock wait age
- transaction age
- query
- migration lock

Critical migration ต้องมี abort threshold

---

## 64. JVM Operations

Monitor:

- heap
- GC
- thread count
- CPU
- allocation rate
- pause
- request latency

Memory leak ต้องพิสูจน์ด้วย trend/heap evidence

---

## 65. Container Operations

Container restart policy ต้องไม่ซ่อน crash loop

ต้อง monitor:

- restart count
- exit code
- OOMKilled
- image version
- health

---

## 66. Docker Host Hygiene

ต้อง monitor:

- unused images
- volumes
- disk growth
- logs
- container resource usage

Cleanup command บน production ต้อง review เพราะ volume/image อาจยังจำเป็นต่อ recovery

---

## 67. Nginx Operations

Monitor:

- 4xx/5xx
- upstream timeout
- connection count
- request rate
- TLS failure

Config change ทุกครั้ง `nginx -t` ก่อน reload

---

## 68. Edge/Tunnel Operations

Monitor:

- tunnel availability
- connection stability
- TLS/origin errors
- unexpected direct origin exposure

---

## 69. DNS / Certificate Operations

ต้องมี:

- ownership
- expiry monitoring
- renewal path
- emergency rotation
- record documentation

---

## 70. External Provider Operations

Provider integration ต้อง monitor:

- success rate
- timeout
- latency
- business rejection
- unknown outcome
- reconciliation lag

---

## 71. Provider Outage Mode

ต้องกำหนด per capability ว่า:

```text
fail closed
degrade
queue
mark unknown
disable feature
```

ตัวอย่าง payment provider timeout อาจเข้าสู่ `UNKNOWN`

ไม่ควรแสดง “failed” ถ้ายังไม่รู้ผลจริง

---

## 72. Notification Provider Outage

Notification outage ไม่ควร rollback business transaction

ควร queue/retry และ expose backlog

---

## 73. Search Outage

Search outage อาจทำให้ browse degraded

แต่ไม่ควร corrupt authoritative transaction

Fallback ขึ้นกับ UX requirement

---

## 74. Analytics Outage

Analytics outage ไม่ควร block ordinary transactional writes

---

## 75. Object Storage Outage

ต้อง define behavior สำหรับ:

- product media
- private documents
- export files

Critical transaction ไม่ควรขึ้นกับ optional media availability โดยไม่จำเป็น

---

## 76. Maintenance Mode

VRA ควรรองรับ business-specific maintenance mode มากกว่า global “down” อย่างเดียว

ตัวอย่าง:

```text
read-only
checkout disabled
payment mutation paused
finance reconciliation only
```

---

## 77. Degraded Mode

Degraded mode ต้อง explicit

เช่น:

```text
search stale but available
notifications delayed
tracking delayed
```

ห้ามซ่อน degradation ที่ทำให้ customer เข้าใจผิดเกี่ยวกับ authoritative state

---

## 78. Deployment During Degraded State

ห้าม deploy unrelated risky change ระหว่าง severe degradation โดยไม่มีเหตุผล

---

## 79. Capacity Planning

Capacity ต้องวัดจาก:

- CPU
- RAM
- DB connections
- storage IOPS
- disk
- network
- workload shape

ไม่ใช้ request count อย่างเดียว

---

## 80. Performance Baseline

ใช้:

- p50
- p95
- p99
- RPS
- error rate
- timeout rate
- resource saturation

---

## 81. Load Test Operations

Load test:

- ใช้ LOAD environment/dedicated data
- จาก separate machine เมื่อเหมาะสม
- ไม่ยิง destructive test production
- correlate k6 กับ server metrics

---

## 82. Concurrency Validation Operations

POC-02 ต้องรัน controlled concurrency test เช่น:

```text
stock = 1
500 buyers
```

และ collect DB/application metrics เพื่อแยก correctness issue จาก capacity issue

---

## 83. Data Growth

Monitor growth ของ:

- orders
- inventory movements
- audit
- outbox
- webhook receipts
- tracking observations
- logs
- object storage

Retention/partition strategy ค่อยเพิ่มตาม evidence

---

## 84. Table Maintenance

PostgreSQL maintenance ต้อง monitor:

- autovacuum
- dead tuples
- index bloat
- analyze
- long transactions

ห้าม disable autovacuum แบบ permanent เพื่อแก้ temporary performance issue

---

## 85. Index Operations

Index เพิ่ม/ลบใน production ต้องพิจารณา:

- build time
- lock
- disk
- write amplification
- query benefit

---

## 86. Vacuum / Analyze

Routine PostgreSQL maintenance ควรอาศัย autovacuum เป็นหลักและ monitor behavior

manual operation ใช้เมื่อมี evidence

---

## 87. WAL Growth

Monitor WAL growth โดยเฉพาะเมื่อ:

- replication
- PITR archive
- long transaction
- failed archive

WAL disk exhaustion เป็น production risk

---

## 88. Backup Monitoring

Alert เมื่อ:

- backup ไม่สำเร็จ
- backup เก่าเกิน policy
- off-host copy fail
- checksum fail
- WAL archive fail

---

## 89. Restore Readiness Monitoring

ต้องมี record ว่า restore test ล่าสุดเมื่อไร

```text
backup age
restore-test age
```

เป็นคนละ metric

---

## 90. Security Operations

Operational security ต้อง monitor:

- failed login burst
- privileged role change
- webhook signature failure
- secret rotation
- unusual staff access
- break-glass use
- suspicious API pattern

---

## 91. Secret Exposure Incident

ถ้า secret ถูกเปิดเผย:

1. identify scope
2. revoke/rotate
3. inspect use
4. redeploy
5. audit affected operations
6. document
7. prevent recurrence

---

## 92. Break-Glass Operations

Emergency credential ต้อง:

- separate
- protected
- rarely used
- alert on use
- audited
- rotated/revoked หลัง incident ตาม policy

---

## 93. Manual Data Repair

Manual database update เป็น last resort

ต้องมี:

- approved reason
- backup/snapshot consideration
- exact SQL review
- affected rows preview
- transaction
- verification
- audit/change record

ถ้ามี recurring repair ต้องสร้าง application/ops workflow แทน

---

## 94. Financial Manual Repair

Finance data repair ต้องเข้มงวดกว่า ordinary row repair

ควรใช้ compensating/adjustment record มากกว่าลบหรือแก้ historical ledger entry

---

## 95. Audit Retention

Audit retention ต้องสัมพันธ์กับ:

- business dispute window
- finance
- legal/compliance
- security investigation

ห้าม operator ลบ audit historyเพื่อแก้ storage pressure แบบ ad hoc

---

## 96. Log Retention

Application/security log retention ต่างกันได้

ต้อง balance:

- investigation
- privacy
- cost
- compliance

---

## 97. PII in Operations

Dashboard/log/export ต้อง mask PII ตาม necessity

Operator ไม่ควรเห็นข้อมูลมากกว่าหน้าที่ต้องใช้

---

## 98. Support Operations

Support action ผ่าน product workflow

Routine support ไม่ควรใช้:

```text
SSH
SQL
manual row edit
```

เป็นวิธีหลัก

---

## 99. Production Access Review

ตรวจ production access เป็นระยะ:

- active SSH users
- staff roles
- DB roles
- CI credentials
- backup credentials
- API keys

Remove stale access

---

## 100. Change Management

Operationally significant change ต้องมี:

- purpose
- owner
- scope
- risk
- verification
- rollback/recovery

---

## 101. Release Notes

Release note ควรระบุ:

- artifact/version
- user-visible changes
- migration
- operational change
- config change
- known risk
- rollback consideration

---

## 102. Dependency Upgrade Operations

Upgrade dependency ต้อง:

- review changelog/security
- run tests
- run migration compatibility ถ้า relevant
- observe performance

Large framework upgrade ควรทำแยกจาก unrelated feature

---

## 103. Java / JVM Upgrade

Java version upgrade ไม่ถือว่าเปลี่ยน ADR-001 โดยอัตโนมัติ

แต่ต้อง validate:

- compiler
- Spring compatibility
- container base
- GC/performance
- observability agent
- tests

---

## 104. PostgreSQL Upgrade

PostgreSQL upgrade ต้องมี:

- compatibility review
- backup
- restore test
- extension review
- migration plan
- downtime/replication strategy
- rollback/recovery

---

## 105. Docker / Host Upgrade

Upgrade Docker/OS ต้องมี maintenance plan

ห้ามอัปเกรด production host แบบทดลองสดโดยไม่มี backup/recovery confidence

---

## 106. Release Frequency

Release frequency ไม่ควรเร็วเกิน operational ability ในช่วงแรก

คุณภาพของ rollback, observability และ migration สำคัญกว่าจำนวน deploy ต่อวัน

---

## 107. Deployment Freeze

สามารถ freeze deployment ชั่วคราวเมื่อ:

- incident active
- backup broken
- restore unverified after major change
- database degraded
- security incident unresolved

---

## 108. Staging Expectations

Staging ควรเหมือน production ใน:

- runtime shape
- proxy path
- migration lifecycle
- secrets mechanism
- database version
- monitoring

แต่ใช้ isolated credentials/data

---

## 109. Production-Like Data

Staging/load data ต้อง synthetic หรือ anonymized

ห้าม copy real production PII แบบ uncontrolled

---

## 110. Operational Ownership Matrix

ทุก component ต้องมี:

```text
owner
health signal
alert
runbook
backup/recovery dependency
escalation
```

---

## 111. SLO Direction

SLO ต้องกำหนดจาก product requirement

ตัวอย่าง categories:

- availability
- latency
- correctness
- freshness
- reconciliation age

ยังไม่ lock ตัวเลขจนมี workload/evidence

---

## 112. Error Budget

เมื่อระบบ mature สามารถใช้ error budget เชื่อม reliability กับ release velocity

ยังไม่จำเป็นใน phase แรก แต่ architecture ต้องเก็บ metric ที่ทำให้กำหนดภายหลังได้

---

## 113. Correctness SLO

บาง metric ไม่ควรเป็น “99.x%”

ตัวอย่าง:

```text
duplicate payout
inventory negative
unauthorized cross-seller access
```

เป้าหมายคือ zero tolerated defect ใน authoritative behavior

---

## 114. Availability vs Correctness

เมื่อ trade-off:

```text
unknown financial state
```

ควรหยุด/queue/reconcile แทนเดาคำตอบเพื่อให้ endpoint “available”

---

## 115. Operational Business Modes

ระบบควรมี mode ต่อ capability เช่น:

```text
NORMAL
READ_ONLY
DEGRADED
PAUSED
RECONCILIATION_REQUIRED
```

ไม่จำเป็นต้อง implement generic enum เดียว แต่ operational semantics ต้องชัด

---

## 116. Startup Validation

Application startup ต้อง validate critical config

Missing config ที่ทำให้ security/correctness เสียต้อง fail fast

---

## 117. Graceful Shutdown

Application/worker ต้อง:

- stop accepting new work
- finish/abort safely
- release lease
- close DB connections
- flush telemetry เท่าที่เหมาะสม

---

## 118. Worker Drain

ก่อน deploy worker:

```text
stop claims
→ finish bounded in-flight work
→ deploy
→ resume
```

เมื่อ architecture รองรับ

---

## 119. Backpressure

เมื่อ downstream ช้า ระบบต้องมี bounded queues/limits

ห้าม queue ใน memory แบบ unbounded

---

## 120. Queue Lag Operations

Lag ต้องมี:

- metric
- alert
- age threshold
- replay/recovery procedure

---

## 121. Idempotency Store Operations

ต้อง monitor:

- growth
- retention
- conflicts
- key reuse anomalies

ห้าม purge เร็วจน retry ที่ยัง valid กลายเป็น duplicate side effect

---

## 122. Reconciliation Backlog

Monitor:

- count
- oldest age
- provider/type
- success/failure rate

Oldest unresolved financial item อาจสำคัญกว่า total count

---

## 123. Time Synchronization

Host clock ต้อง synchronized

Time drift กระทบ:

- token expiry
- TLS
- signature validation
- event ordering
- audit
- reconciliation

---

## 124. Timezone

Server-side authoritative timestamps ใช้ timezone-neutral representation

Operator dashboard แสดง timezone ชัดเจน

---

## 125. DNS Dependency

DNS failure ต้องถูกพิจารณาใน provider/infrastructure incidents

Caching/TTL behavior ต้องเข้าใจ

---

## 126. Certificate Expiry

Certificate/tunnel credential expiry ต้องมี monitoring หรือ renewal mechanism

---

## 127. Capacity Headroom

Production ไม่ควร run steady state ที่ 100% resource

ต้องเหลือ headroom สำหรับ spike, migration และ recovery

---

## 128. Memory / OOM

OOM ต้องถูก alert และ root-cause

Container restart อย่างเดียวไม่ถือว่าแก้แล้ว

---

## 129. CPU Saturation

CPU 100% อาจมาจาก:

- load
- GC
- query
- busy loop
- compression
- TLS

ต้อง correlate metrics

---

## 130. Storage Latency

PostgreSQL performance ต้องดู storage latency ไม่ใช่ CPU อย่างเดียว

---

## 131. Network Latency

External provider latency ต้องแยกจาก internal application latency ผ่าน tracing/metrics

---

## 132. Timeout Budget

Timeout ต้องออกแบบตาม call chain

ห้ามให้ upstream timeout สั้นกว่า downstream retry budget แบบไม่ตั้งใจ

---

## 133. Retry Storm Prevention

Retry policy ต้องใช้:

- bounded attempts
- backoff
- jitter
- circuit/bulkhead เมื่อ evidence ต้องการ

เพื่อป้องกัน outage กลายเป็น retry storm

---

## 134. Maintenance Window

Migration/host upgrade ที่มี risk สูงอาจใช้ maintenance window

แต่ระบบต้องไม่พึ่ง maintenance window เพื่อหลีกเลี่ยงการออกแบบ migration ที่ปลอดภัยโดยตลอด

---

## 135. Runbook: Application Down

High-level:

```text
1. confirm edge/tunnel
2. confirm Nginx
3. confirm container/process
4. confirm readiness
5. confirm DB
6. inspect logs/metrics
7. restore/restart only when evidence supports
8. verify traffic
```

---

## 136. Runbook: Database Unavailable

```text
1. stop risky writes if needed
2. inspect process/container
3. disk
4. connection/locks
5. logs
6. storage
7. backup/WAL status
8. recover
9. integrity check
10. reconcile
```

---

## 137. Runbook: Payment Provider Unknown

```text
1. mark/preserve UNKNOWN
2. do not blind retry
3. query provider if supported
4. run reconciliation
5. update internal state idempotently
6. audit
```

---

## 138. Runbook: Secret Leak

```text
1. contain
2. revoke
3. rotate
4. inspect usage
5. redeploy
6. verify
7. incident review
```

---

## 139. Runbook: Failed Migration

```text
1. stop deployment
2. capture exact failure
3. inspect migration history
4. assess partial effect
5. do not widen runtime privilege
6. choose forward-fix/rollback
7. verify schema/data
8. resume deployment only when safe
```

---

## 140. Runbook: Restore

```text
1. isolate target
2. restore backup/PITR
3. validate schema
4. validate data
5. start app without uncontrolled external side effects
6. reconcile external systems
7. open traffic in stages
8. observe
```

---

## 141. Operational Test Cadence

Cadence ที่ต้องกำหนดภายหลัง:

- backup daily/continuous policy
- restore drill
- secret rotation
- access review
- DR drill
- load test
- dependency patch window

ตัวเลข exact ต้องสัมพันธ์กับ production stage

---

## 142. Safe-Host Preflight

ก่อนนำ VRA ขึ้น host ต้องตรวจแบบ read-only:

- OS/version
- CPU/RAM
- disk
- Docker
- Compose
- Nginx
- firewall
- listening ports
- PostgreSQL state
- existing projects
- backup path
- DNS/tunnel path

ห้ามแก้ host ก่อนเข้าใจ existing topology

---

## 143. Shared-Host Safety

ถ้า host มีหลาย project:

- isolate project paths
- avoid shared port conflict
- avoid deleting shared Docker resources
- avoid broad `docker system prune`
- identify shared Nginx/PostgreSQL
- backup before structural changes

---

## 144. PostgreSQL Shared Instance

ถ้า initial host ใช้ PostgreSQL instance ร่วมหลาย project:

- database แยก
- role แยก
- ownership แยก
- migration scope แยก
- backup/restore impact ต้องเข้าใจ

VRA runtime ห้ามมี permission ต่อ database ของ project อื่น

---

## 145. Deployment Database

Production deployment ต้องใช้ database ใหม่ที่ตั้งใจสำหรับ VRA deployment

ห้าม reuse DEV/test database เพื่อความสะดวก

---

## 146. Safe-Host Reverse Proxy

Nginx เป็น shared infrastructure ได้ แต่ config ต้องแยก per project และ validate ก่อน reload

VRA change ต้องไม่ทำให้ project อื่น outage

---

## 147. Production Secret Output

Command transcript ที่แชร์เพื่อ review ต้อง redact:

- password
- token
- certificate private data
- tunnel secret
- session secret

---

## 148. Operational Evidence

งานสำคัญควรบันทึก evidence เช่น:

- command
- result
- version
- timestamp
- release SHA
- migration version
- health result

โดยไม่รวม secret

---

## 149. Change Review Questions

ก่อน operational change ถาม:

1. เปลี่ยนอะไร
2. ทำไม
3. blast radius เท่าไร
4. backup/recovery พร้อมไหม
5. database/migration impact?
6. security impact?
7. downtime?
8. verification?
9. rollback/roll-forward?
10. external reconciliation?
11. shared-host impact?
12. ใครเป็น owner?

---

## 150. Production Readiness Operational Gate

ก่อนเปิด production จริง ต้องมีอย่างน้อย:

- immutable artifact flow
- deployment steps
- runtime/migrator DB separation
- health/readiness
- observability
- alerting baseline
- backup
- off-host backup
- tested restore
- rollback/roll-forward plan
- incident process
- reconciliation process
- secret handling
- access control
- safe-host firewall/proxy verification

---

## 151. Deferred Operations Complexity

ยังไม่เพิ่มโดยไม่มี evidence:

- Kubernetes
- service mesh
- multi-region active-active
- automatic cross-region failover
- complex GitOps stack
- distributed tracing backend ที่เกิน workload
- expensive centralized log stack
- dedicated broker cluster
- dedicated database cluster

---

## 152. Operations and POC Roadmap

### POC-01

พิสูจน์:

- production project structure
- PostgreSQL
- migration/runtime identity
- Flyway lifecycle
- health/readiness
- config
- CI build

### POC-02

พิสูจน์ concurrency/idempotency

### POC-03

พิสูจน์ outbox/workers/retry/reconciliation

### POC-04

พิสูจน์ auth, secrets และ security hardening ของ least-privilege baseline จาก POC-01

### POC-05

พิสูจน์ observability, performance, fault behavior

---

## 153. Definition of Operationally Ready

Component ถือว่า operationally ready เมื่อ:

```text
deployable
observable
recoverable
reconcilable
secured
documented
owned
tested
```

การที่ “รันได้” เพียงอย่างเดียวไม่เพียงพอ

---

## 154. Canonical Operations Rule

หลักสุดท้ายของ VRA คือ:

> **ทุกระบบที่เรานำขึ้นใช้งานต้องไม่เพียงแค่เริ่มทำงานได้ แต่ต้องสามารถตรวจสอบ หยุด กู้คืน reconcile และอธิบายสิ่งที่เกิดขึ้นได้อย่างปลอดภัย**

และ:

```text
Build once
→ Deploy deliberately
→ Observe
→ Recover
→ Reconcile
→ Learn
```

Operations เป็นส่วนหนึ่งของ architecture และ product correctness ตั้งแต่วันแรก ไม่ใช่งานหลัง feature เสร็จ
