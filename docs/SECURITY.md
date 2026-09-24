# ความปลอดภัยของ VRA

**เอกสาร:** `docs/SECURITY.md`
**สถานะ:** ACTIVE — canonical baseline v1
**ผลิตภัณฑ์:** VRA (วีล่า)
**ขอบเขต:** หลักการและข้อกำหนดด้าน security สำหรับ identity, authentication, authorization, privileged operations, session, workload identity, database access, secrets, integrations, uploads, audit, incident response และ recovery

> เอกสารนี้กำหนด security intent และ security invariants ของ VRA
> รายละเอียด product semantics อยู่ใน `PRODUCT.md`
> รายละเอียด architecture อยู่ใน `DESIGN.md`
> วิธีพิสูจน์ security controls อยู่ใน `TESTING.md`
> deployment/runbook/recovery procedure อยู่ใน `OPERATIONS.md`

---

## 1. Security Objective

เป้าหมายของ VRA ไม่ใช่ “มี login แล้วถือว่าปลอดภัย”

VRA ต้องรักษาคุณสมบัติอย่างน้อยดังนี้:

1. ผู้ใช้เข้าถึงเฉพาะข้อมูลและการกระทำที่ตนมีสิทธิ์
2. seller หนึ่งรายไม่สามารถเข้าถึง private data ของ seller อื่นได้
3. customer ไม่สามารถเข้าถึง staff, finance, seller หรือ logistics authority ได้จาก identifier เพียงอย่างเดียว
4. staff privilege ถูกจำกัดตาม role และ context
5. privileged action สามารถ audit ย้อนหลังได้
6. workload identity ได้สิทธิ์เท่าที่จำเป็น
7. runtime database identity ไม่เป็น schema owner
8. secret ไม่อยู่ใน source code หรือ log
9. external callback/webhook ไม่ได้รับความเชื่อถือจาก network location เพียงอย่างเดียว
10. uncertain external outcome ไม่ถูกเปลี่ยนเป็น success/failure แบบเดา
11. recovery จาก incident หรือ restore ไม่ทำให้ security/correctness guarantee หายไป
12. security control ต้องสามารถ test ได้

---

## 2. Security Principles

### 2.1 Zero Implicit Trust

VRA ไม่ใช้สมมติฐานว่า:

```text
อยู่ใน private network
= trusted

มาจาก internal service
= trusted

มี authenticated user
= authorized

รู้ object ID
= allowed

เป็น staff
= unrestricted
```

Trust ต้อง explicit และผูกกับ identity, permission, object ownership, state และ context

### 2.2 Least Privilege

ทุก identity ต้องได้รับสิทธิ์เท่าที่จำเป็นต่อหน้าที่

ใช้กับ:

- customer
- seller
- staff
- driver/carrier
- service/workload
- database role
- CI/CD identity
- backup identity
- object-storage credential
- external integration credential

### 2.3 Fail Closed for Privileged Decisions

หากระบบไม่สามารถพิสูจน์สิทธิ์สำหรับ privileged หรือ high-risk action ได้ ต้อง deny

ตัวอย่าง:

```text
authorization data unavailable
→ deny privileged mutation

MFA state unknown
→ deny step-up-required action

approval state ambiguous
→ do not execute
```

### 2.4 Defense in Depth

Critical invariant ไม่ควรพึ่ง control ชั้นเดียว

ตัวอย่าง seller isolation อาจใช้:

```text
route authorization
+
application ownership check
+
query scoping
+
database permissions where practical
+
audit
+
tests
```

### 2.5 Secure by Default

Default ต้องเลือกค่าที่ปลอดภัยกว่า เช่น:

- object storage private
- session cookie secure
- database loopback/private only
- unknown privilege denied
- unknown provider signature rejected
- debug endpoint disabled
- no public admin signup
- no broad wildcard permission

---

## 3. Threat Model Scope

VRA ต้องพิจารณา threat จากอย่างน้อย:

- unauthenticated internet attacker
- authenticated malicious customer
- malicious seller
- compromised customer account
- compromised seller account
- compromised staff account
- over-privileged staff
- compromised workload identity
- leaked API credential
- leaked database credential
- malicious/forged webhook
- replayed request
- duplicate provider event
- CSRF
- XSS
- SSRF
- insecure direct object reference / BOLA
- privilege escalation
- mass assignment
- SQL injection
- unsafe file upload
- secret leakage
- log leakage
- dependency compromise
- CI/CD credential compromise
- backup exposure
- database restore inconsistencies
- insider misuse
- operational mistakes

Threat model ต้อง evolve ตาม product scope

---

## 4. Identity Layers

VRA แยก identity อย่างชัดเจน:

```text
Authentication Identity
Business Account
Organization / Seller Membership
Role / Permission
Resource Ownership
Workload Identity
External Integration Identity
```

ระบบห้าม assume ว่า identifier จาก IdP เพียงอย่างเดียวเท่ากับ business authorization

---

## 5. Account Lifecycle

Baseline account states:

```text
ACTIVE
LOCKED
DISABLED
CLOSED
```

### ACTIVE

ใช้งานได้ตาม authorization ปัจจุบัน

### LOCKED

ถูกล็อกชั่วคราวหรือระหว่างการตรวจสอบ

### DISABLED

ห้าม login/use ตาม policy แต่ข้อมูลยังอาจต้องเก็บตาม business/legal requirement

### CLOSED

account ถูกปิดในเชิง lifecycle

การเปลี่ยน state ต้อง:

- explicit
- authorized
- audit ได้
- ไม่ลบ financial/audit evidence ที่ต้องเก็บ

---

## 6. Authentication

Authentication ตอบคำถาม:

> actor นี้คือใคร?

ไม่ตอบคำถาม:

> actor นี้ทำ operation นี้ได้หรือไม่?

Authentication mechanism ต้องใช้มาตรฐานที่ได้รับการยอมรับ

Baseline direction:

```text
OIDC / OAuth 2.x
```

VRA ไม่สร้าง custom authentication protocol

---

## 7. Password Handling

หาก VRA หรือ IdP ที่ VRA ควบคุมต้องจัดการ password:

- ห้ามเก็บ plaintext password
- ห้าม log password
- ใช้ password hashing algorithm ที่เหมาะสม
- salt ต้องถูกใช้ตาม algorithm
- password reset token ต้อง short-lived และ single-use
- password reset ต้อง invalidate ตาม policy ที่กำหนด
- brute-force/rate-limit controls ต้องมีตาม risk

หากใช้ external IdP เป็นผู้จัดการ password ข้อกำหนดเหล่านี้ต้องถูกประเมินใน provider selection

---

## 8. MFA / Passkeys

Privileged account และ high-risk operation ต้องรองรับ MFA

Direction:

- staff privileged access ต้องใช้ MFA
- passkeys/WebAuthn เป็น strategic direction
- sensitive seller operation อาจ require step-up
- financial operation บางประเภทอาจ require step-up
- recovery path ต้องไม่ bypass MFA security โดยง่าย

MFA requirement ที่แน่นอนจะถูก validate ใน POC-04

---

## 9. Step-Up Authentication

บาง operation ต้องการ assurance สูงกว่า ordinary session

ตัวอย่าง:

- เปลี่ยน payout destination
- เพิ่ม privileged staff role
- disable security control
- high-value refund
- seller ownership transfer
- sensitive credential change

Flow ต้องสามารถบังคับ:

```text
existing session
→ step-up challenge
→ fresh assurance
→ execute operation
```

Step-up result ต้องมีอายุจำกัด

---

## 10. Authorization Model

Authorization ตอบคำถาม:

> actor นี้ทำ action นี้กับ resource นี้ ใน state/context นี้ได้หรือไม่?

Baseline model:

```text
RBAC
+
Ownership
+
Resource State
+
Organization/Seller Relationship
+
Context
+
Risk
+
Approval Requirements
```

RBAC อย่างเดียวไม่เพียงพอสำหรับ VRA

---

## 11. Object-Level Authorization

ทุก endpoint ที่รับ resource ID ต้องตรวจ object-level authorization

ตัวอย่าง:

```text
GET /orders/{orderId}
```

ต้องตรวจว่า actor มีสิทธิ์เห็น order นั้นจริง

ห้าม:

```text
authenticated = true
→ SELECT by ID
→ return object
```

Identifier randomness ไม่ใช่ security boundary

---

## 12. Seller Isolation

Seller isolation เป็น critical security invariant

Seller A ต้องไม่สามารถ:

- อ่าน private order ของ Seller B
- แก้ offer ของ Seller B
- ดู settlement ของ Seller B
- ดู inventory ownership ของ Seller B ที่ไม่ควรเปิดเผย
- เรียก operation โดยเปลี่ยน sellerId ใน request

Authorization ต้อง derive seller scope จาก trusted server-side identity/context ไม่เชื่อ sellerId จาก client อย่างเดียว

---

## 13. Customer Isolation

Customer A ต้องไม่สามารถเข้าถึง:

- order ของ Customer B
- address ของ Customer B
- payment metadata ของ Customer B
- return/refund ของ Customer B
- support information ของ Customer B

ทุก customer resource access ต้องมี ownership check

---

## 14. Staff Authorization

Staff ไม่ใช่ superuser โดย default

ตัวอย่าง role อาจประกอบด้วย:

- customer_support
- seller_support
- warehouse_ops
- logistics_ops
- finance_ops
- risk_ops
- compliance
- security_admin

สิทธิ์ต้อง granular ตาม operation และ data scope

---

## 15. Staff-Assisted Actions

เมื่อ staff ทำ operation แทน customer/seller ต้องเก็บอย่างน้อย:

```text
actor = staff account
subject = customer/seller
action
target
reason
timestamp
result
request/correlation id
```

UI/UX ต้องไม่ทำให้ดูเหมือน customer เป็นคนทำ operation เอง

---

## 16. Maker-Checker

High-impact operation สามารถ require:

```text
maker
→ proposed change
→ checker
→ approval
→ execution
```

Rule:

- maker ห้าม approve request ของตนเอง
- approval ต้องผูกกับ exact change
- change หลัง approval ต้อง invalidate approval
- approval/rejection ต้อง audit
- timeout/expiry ต้อง explicit

---

## 17. Public Signup Policy

Customer signup อาจ public ตาม product scope

แต่:

```text
staff signup
seller privileged role
finance role
security admin role
```

ต้องไม่เป็น unrestricted public signup

Onboarding ต้องผ่าน controlled process

---

## 18. Browser Session Architecture

Baseline direction สำหรับ browser:

```text
Browser
→ opaque server-side session
→ VRA backend authority
```

เป้าหมายคือไม่ expose long-lived privileged bearer token ให้ JavaScript โดยไม่จำเป็น

Session cookie ต้องใช้:

- `Secure`
- `HttpOnly`
- appropriate `SameSite`
- bounded lifetime
- rotation policy
- revocation capability

---

## 19. CSRF

หากใช้ cookie-authenticated state-changing requests ต้องมี CSRF protection

ตัวอย่าง control:

- anti-CSRF token
- same-site policy
- origin validation
- method discipline

ห้ามถือว่า `SameSite` เพียงอย่างเดียวแก้ CSRF ทุกกรณี

---

## 20. XSS

Frontend และ backend ต้องลด XSS risk

หลัก:

- escape output ตาม context
- หลีกเลี่ยง raw HTML
- sanitize user-controlled rich content เมื่อจำเป็น
- Content Security Policy ตาม capability
- no token in unsafe browser storage
- dependency review

XSS ที่ขโมย session หรือทำ privileged action ถือเป็น critical threat

---

## 21. Mobile Authentication

Future native mobile baseline:

```text
Authorization Code
+ PKCE
```

Mobile application เป็น public client

ห้ามฝัง confidential client secret แล้วถือว่าปลอดภัย

Token storage ต้องใช้ platform secure storage ตาม OS

---

## 22. Session Revocation

ต้องสามารถ revoke session เมื่อ:

- account disabled
- password/security credential changed
- suspicious login
- staff privilege revoked
- incident response
- user logout ตาม policy

Revocation architecture ต้องไม่พึ่งเพียง client ลบ cookie/token เอง

---

## 23. Session Fixation / Rotation

Session ID ต้อง rotate เมื่อ assurance level เปลี่ยน เช่น:

- login สำเร็จ
- step-up สำเร็จ
- privilege elevation
- recovery completion

ห้าม reuse unauthenticated session identifier เป็น privileged session โดยไม่มี rotation

---

## 24. Authorization Decision Logging

Sensitive authorization denial/approval บางประเภทควรมี security telemetry

แต่ห้าม log sensitive payload เกินจำเป็น

ต้องสามารถ detect:

- repeated forbidden resource access
- privilege probing
- unusual seller scope changes
- abnormal staff operations

---

## 25. Service / Workload Identity

แต่ละ workload ต้องมี identity ของตนเอง

ห้ามใช้ credential เดียวร่วมทุก service/worker

ตัวอย่าง:

```text
api-runtime
outbox-worker
reconciliation-worker
migration-job
backup-job
```

แต่ละ identity ได้ permission เท่าที่ต้องใช้

---

## 26. Database Identity Separation

Production database ต้องแยกอย่างน้อย:

```text
vra_migrator
vra_runtime
```

### `vra_migrator`

ใช้สำหรับ:

- schema creation/change
- migration
- ownership ที่จำเป็นต่อ migration

### `vra_runtime`

ใช้สำหรับ:

- SELECT
- INSERT
- UPDATE
- DELETE เฉพาะ object ที่ application ต้องใช้
- sequence/function permission เท่าที่จำเป็น

`vra_runtime` ต้องไม่:

- own schema โดยไม่จำเป็น
- create/drop table
- alter schema
- grant privilege
- become superuser

---

## 27. Database Network Exposure

PostgreSQL production ต้องไม่เปิด public internet port

Baseline:

```text
private/local network
+
firewall
+
explicit application access
```

ใน safe-host initial deployment:

```text
127.0.0.1 / private container network
```

ตาม topology จริง

---

## 28. Database TLS

หาก database traffic ข้าม trust boundary หรือ network ที่ไม่ถือว่า local secure transport ต้องใช้ TLS

Local single-host Unix socket/private loopback decision ต้องประเมินตาม deployment threat model

ห้ามตีความว่า private IP = encrypted

---

## 29. Database Row-Level Security

PostgreSQL RLS สามารถใช้เป็น defense-in-depth ในบาง domain

แต่ไม่ควรเพิ่มโดยอัตโนมัติทุก table

ต้องพิจารณา:

- ownership semantics
- connection identity model
- operational/debug complexity
- migration impact
- query planning
- staff access model

Application authorization ยังคงจำเป็น

---

## 30. SQL Injection

ทุก SQL input ต้อง parameterized

ห้าม concat user input เข้า SQL

Dynamic identifier/order-by ที่ parameterize ไม่ได้ต้อง whitelist

Review ต้องตรวจ query ที่สร้างจาก:

- filter
- sort
- search
- export
- admin tooling

---

## 31. Mass Assignment

DTO จาก client ต้องไม่ bind ตรงเข้า persistence entity แล้ว update ทุก field

ตัวอย่างห้าม:

```text
PATCH body
→ ORM merge entity
```

ต้อง map เฉพาะ field ที่ operation อนุญาต

Sensitive field เช่น:

- role
- ownerId
- sellerId
- status
- payoutDestination
- verificationState

ห้าม client เปลี่ยนได้เพราะ field มีอยู่ใน object

---

## 32. API Input Validation

Transport validation ต้องครอบคลุม:

- type
- length
- required field
- format
- enum
- numeric bounds
- object structure
- unknown field policy

Domain validation ต้องตรวจ business invariant อีกชั้น

Frontend validation ไม่ถือเป็น security control

---

## 33. Stable Error Safety

Error response ต้องไม่เปิดเผย:

- stack trace
- SQL
- table name
- filesystem path
- internal hostname
- secret
- access token
- provider credential
- raw database error
- cryptographic key material

Client ใช้ stable error code แทน parsing internal exception

---

## 34. Rate Limiting

Rate limiting ต้องใช้ตาม threat/abuse model

candidate areas:

- login
- password reset
- OTP/MFA
- account creation
- search abuse
- checkout
- payment initiation
- refund request
- webhook endpoint
- expensive export

Rate limiting ต้องไม่เป็น control เดียวของ authorization

---

## 35. Brute Force Protection

Authentication endpoint ต้องมี protections เช่น:

- rate limit
- progressive delay
- suspicious behavior detection
- lock/risk policy
- MFA

Account enumeration ต้องลดเท่าที่สมเหตุสมผล

---

## 36. Account Enumeration

Public auth/recovery response ไม่ควรเปิดเผยโดยไม่จำเป็นว่า:

```text
email นี้มี account
เบอร์นี้เป็น seller
account นี้ถูก disable
```

แต่ usability และ support requirement ต้อง balance อย่าง deliberate

---

## 37. Secrets Definition

Secret คือข้อมูลที่การเปิดเผยทำให้ attacker ได้ authority หรือ material advantage

ตัวอย่าง:

- DB password
- API secret
- webhook signing secret
- OAuth client secret
- encryption key
- signing key
- tunnel token
- backup credential
- session encryption/signing key

---

## 38. Secrets Storage

Secret ห้ามอยู่ใน:

- Git
- source code
- README
- committed `.env`
- test snapshot
- screenshot
- CI log
- issue tracker โดยไม่ป้องกัน

Initial deployment สามารถใช้ file-based secret ที่ permission จำกัด

Future secret management อาจใช้ SOPS หรือ managed secret manager หลัง validation

---

## 39. Secret Injection

Application ควรรับ secret ผ่าน controlled runtime mechanism เช่น:

- mounted secret file
- secure environment injection
- secret manager client

ห้าม bake secret ลง container image

---

## 40. Secret Rotation

Critical secret ต้องสามารถ rotate ได้

Rotation plan ต้องตอบ:

- generate ใหม่อย่างไร
- distribute อย่างไร
- support overlap หรือไม่
- revoke ของเก่าอย่างไร
- impact ต่อ session/worker คืออะไร
- audit อย่างไร

---

## 41. Cryptography Policy

VRA ไม่สร้าง custom cryptographic algorithm

ใช้ vetted standards/library

Security design ต้องแยก:

```text
encryption
signing
hashing
password hashing
key derivation
```

ห้ามใช้สิ่งหนึ่งแทนอีกสิ่งหนึ่งเพราะ API ดูคล้ายกัน

---

## 42. Key Management

Cryptographic key ต้องมี:

- owner
- purpose
- access scope
- rotation policy
- storage boundary
- revocation/replacement plan

Signing key ที่ verify public data อาจมี public counterpart ได้ แต่ private key ต้อง protected

---

## 43. Data in Transit

External traffic ต้องใช้ TLS

TLS termination boundary ต้องชัดเจน

Initial safe-host:

```text
Internet
→ Cloudflare
→ tunnel
→ HTTPS origin / Nginx
→ internal application
```

ต้องไม่เปิด parallel insecure public path โดยไม่ตั้งใจ

---

## 44. Data at Rest

At-rest protection ต้องประเมินตาม data sensitivity:

- host disk encryption
- database storage
- backups
- object storage
- CI artifacts
- developer machine

Encryption at rest ไม่แทน authorization

---

## 45. Sensitive Data Classification

ข้อมูลควรถูกจัด sensitivity tier เช่น:

```text
S0 Public
S1 Internal
S2 Confidential
S3 Sensitive
S4 Highly Sensitive / Security-Critical
```

ตัวอย่างโดยประมาณ:

- public product content → S0
- internal operational metadata → S1
- customer contact → S2/S3
- payout/bank-related data → S3/S4
- password/secret/private key → S4

Exact classification จะ finalize ใน security implementation phase

---

## 46. PII Minimization

เก็บ PII เท่าที่จำเป็น

ทุก field ควรมีคำตอบว่า:

- ใช้ทำอะไร
- ใครเข้าถึง
- เก็บนานเท่าไร
- ลบ/anonymize อย่างไร
- backup retention เป็นอย่างไร

ห้ามเก็บข้อมูล “เผื่อไว้ก่อน” โดยไม่มี purpose

---

## 47. Logging Sensitive Data

ห้าม log:

- password
- session token
- access token
- refresh token
- full payment credential
- private key
- DB password
- webhook secret

PII อื่นต้องใช้ minimization/masking ตาม operational requirement

---

## 48. Payment Data Boundary

VRA ควรลด PCI/security scope โดยไม่เก็บ sensitive payment credential เกินจำเป็น

ใช้ provider tokenization/hosted payment capability เมื่อเหมาะสม

ห้าม log raw card data

ห้ามออกแบบ custom card vault โดยไม่มี requirement และ compliance program ที่เหมาะสม

---

## 49. Financial Operation Security

Payment/refund/payout operation ต้อง:

- authenticated
- authorized
- idempotent
- auditable
- amount/currency validated
- state-aware
- duplicate-safe
- reconciliation-capable

High-risk finance operation อาจ require maker-checker และ step-up

---

## 50. Payout Destination Changes

การแก้ payout destination เป็น high-risk operation

ควรพิจารณา:

- step-up authentication
- notification
- cooling period
- maker-checker
- risk review
- old/new destination audit
- payout hold หลัง change

Exact policy ต้องมาจาก business/risk requirement

---

## 51. Webhook Verification

Inbound webhook ต้อง verify authenticity

อาจใช้:

- HMAC signature
- asymmetric signature
- mTLS
- provider-specific signed headers

ต้อง verify:

- canonical payload ตาม provider spec
- timestamp/expiry เมื่อรองรับ
- replay protection
- correct secret/key

IP allowlist อย่างเดียวไม่เพียงพอเป็น cryptographic authenticity

---

## 52. Webhook Replay

Webhook สามารถถูกส่งซ้ำอย่าง legitimate

ระบบต้องแยก:

```text
duplicate legitimate delivery
vs
malicious replay
```

ใช้ provider event ID/idempotency และ signature freshness ตาม capability

---

## 53. Outbound Webhook Security

Outbound webhook ของ VRA ต้อง:

- signed
- use HTTPS
- retry bounded
- not leak internal secret
- expose stable event ID
- support receiver deduplication
- have audit/attempt history

---

## 54. SSRF

ระบบที่ fetch URL จาก user/provider input ต้องป้องกัน SSRF

ต้องพิจารณา:

- scheme whitelist
- DNS resolution
- loopback/private network blocking
- redirect handling
- cloud metadata endpoint
- port restriction
- response size/time limit

Image import, webhook tester, document fetcher และ callback URL เป็นจุดเสี่ยง

---

## 55. File Upload Security

Upload ต้องตรวจ:

- size
- MIME/content
- extension policy
- filename normalization
- storage path
- malware scanning เมื่อ risk ต้องการ
- image/document processing isolation
- authorization
- retention

ห้ามใช้ client filename เป็น filesystem path โดยตรง

---

## 56. Object Storage Security

Object storage default:

```text
private
```

Access ใช้:

- signed URL อายุสั้น
- authenticated proxy
- explicit public publication

Bucket/object permission ต้องแยกตาม use case

Public product image และ private customer document ห้าม share policy เดียวกันโดยไม่ตั้งใจ

---

## 57. Image Processing

Image processing library เป็น attack surface

ต้อง:

- limit dimensions
- limit decompressed size
- reject malformed format
- isolate processing เมื่อเหมาะสม
- patch dependency
- strip sensitive metadata เมื่อ requirement ต้องการ

---

## 58. Supply Chain Security

Dependency ต้อง:

- version controlled
- reviewed
- updatedตาม risk
- scan vulnerability
- avoid unmaintained package เมื่อมีทางเลือก
- avoid dependency ที่ไม่จำเป็น

Build ต้อง trace ได้ถึง source commit

---

## 59. Build Integrity

Production artifact ต้องมาจาก controlled build

ห้าม:

```text
SSH server
git pull
gradle build
run whatever came down
```

เป็น release process หลัก

Artifact promotion ต้องชัดเจนและ reproducible เท่าที่ practical

---

## 60. CI/CD Credentials

CI/CD identity ต้องใช้ least privilege

แยก:

- read source
- publish artifact
- deploy staging
- deploy production
- migrate database

ห้ามใช้ production admin credential ในทั่วไป CI job

---

## 61. Branch / Review Governance

Security-significant change ต้อง review

ตัวอย่าง:

- auth/authz
- cryptography
- session
- DB privilege
- secret handling
- payment
- webhook
- upload
- audit
- backup access

Repository workflow ต้องไม่ให้ automation เปลี่ยน security policy โดยเงียบ ๆ

---

## 62. Dependency on Documentation

Implementation ที่ขัด `SECURITY.md` ต้องถูก treat เป็น:

```text
defect
หรือ
security architecture change
```

ห้ามแก้ docs ให้ตาม implementation โดยอัตโนมัติ

ถ้าต้องเปลี่ยน policy ต้อง review และอาจต้อง ADR

---

## 63. Security Event Model

Security event ควรรองรับ:

- authentication success/failure
- MFA challenge
- privilege change
- role assignment
- suspicious access
- repeated authorization denial
- session revocation
- sensitive config change
- secret rotation
- webhook signature failure
- audit tamper attempt
- backup access

Security event ไม่ควรเก็บ secret เอง

---

## 64. Audit Protection

Audit record ต้องมี protection มากกว่า ordinary mutable business row

ต้องควบคุม:

- append/update permission
- delete permission
- retention
- access
- export
- integrity monitoring

Runtime business user ไม่ควรแก้ audit history ได้โดยอิสระ

---

## 65. Audit Content

Audit record ควรเก็บ:

```text
actor
actor type
subject
action
target
before/after reference or safe delta
reason
approval context
request id
result
timestamp
source context
```

อย่าเก็บ secret/plain sensitive payload เกินจำเป็น

---

## 66. Security vs Privacy

Security และ privacy เกี่ยวข้องกันแต่ไม่เหมือนกัน

Security:

> ใครทำอะไรได้

Privacy:

> ข้อมูลอะไรควรถูกเก็บ ใช้ เปิดเผย หรือลบ

VRA ต้องรองรับทั้งสองอย่าง

---

## 67. Support Tool Security

Staff support tool ต้องไม่เป็น bypass channel

ต้องมี:

- staff authentication
- role authorization
- customer/seller scope
- reason capture เมื่อเหมาะสม
- audit
- masking sensitive data
- step-up สำหรับ high-risk action

---

## 68. Impersonation

หากอนาคตมี support impersonation:

- ต้อง explicit
- UI ต้องเห็นชัดว่าอยู่ใน impersonation mode
- actor staff ต้องคงอยู่ใน audit
- scope/time จำกัด
- high-risk actions อาจถูกห้าม
- customer credential ห้ามถูกเปิดเผยให้ staff

---

## 69. Search Security

Search result ต้อง honor authorization

ห้าม:

```text
search index มี document
→ return ให้ทุก authenticated user
```

Derived search data ต้องเก็บ security-relevant scope หรือ query ผ่าน authorized boundary

Sensitive object ไม่ควร leak ผ่าน autocomplete/count/facet

---

## 70. Analytics Security

Analytics/export มักรวมข้อมูลจำนวนมาก จึงเป็น high-risk surface

ต้องมี:

- strict role
- scoped dataset
- export audit
- rate/volume controls
- masking
- retention
- secure delivery

---

## 71. Bulk Export

Bulk export ต้องไม่ใช้ ordinary list endpoint แบบ unbounded

ควรเป็น controlled operation:

```text
request export
→ authorize
→ generate asynchronously
→ store private artifact
→ signed/temporary access
→ audit
→ expire
```

---

## 72. Admin Endpoints

Admin endpoint ต้อง:

- ไม่ public โดยไม่จำเป็น
- แยก authorization
- no shared “admin=true” shortcut
- audit
- step-up เมื่อ high-risk
- protect from CSRF/session abuse ตาม client type

---

## 73. Debug / Actuator Endpoints

Production debug/actuator endpoint ต้อง expose เท่าที่จำเป็น

Sensitive endpoint เช่น:

- env
- heap dump
- config
- mappings
- beans
- thread dump

ต้องไม่เปิด public โดย default

Health endpoint ต้องไม่ leak secret/internal topology เกินจำเป็น

---

## 74. Error Correlation

Error response ใช้ request/correlation ID

Operator สามารถใช้ ID ค้น log/traces ได้

แต่ request ID ไม่ใช่ secret และห้ามใช้เป็น authorization credential

---

## 75. Request ID Trust

Inbound request ID จาก client อาจถูกเก็บเป็น client correlation แต่ server ควรสามารถสร้าง/normalize authoritative request correlation ของตนเอง

ห้ามเชื่อ arbitrary client ID เป็น trusted trace identity โดยไม่มี validation

---

## 76. API Version / Compatibility Security

Backward compatibility ต้องไม่บังคับให้เก็บ insecure behavior ตลอดไป

เมื่อ deprecate insecure API:

- announce
- monitor usage
- provide migration
- sunset
- disableตาม policy

---

## 77. CORS

CORS เป็น browser policy ไม่ใช่ authorization

กำหนด allowed origin แบบ explicit

ห้ามใช้:

```text
Access-Control-Allow-Origin: *
```

ร่วมกับ credentialed sensitive API โดยไม่ตั้งใจ

---

## 78. Content Security Policy

Web application ควรมี CSP ที่ลด XSS surface

Policy ต้อง evolve ตาม frontend architecture

หลีกเลี่ยง broad:

```text
unsafe-inline
unsafe-eval
*
```

หากต้องใช้ชั่วคราวต้อง document risk

---

## 79. Clickjacking

Sensitive web UI ควรใช้ frame protection:

- CSP `frame-ancestors`
- legacy header เมื่อเหมาะสม

เพื่อป้องกันการ embed โดย origin ที่ไม่ได้รับอนุญาต

---

## 80. Browser Security Headers

Baseline ต้องพิจารณา:

- HSTS
- CSP
- X-Content-Type-Options
- Referrer-Policy
- Permissions-Policy
- frame protection

Exact set อยู่ที่ edge/frontend architecture

---

## 81. Open Redirect

Redirect target จาก user input ต้อง whitelist/validate

Auth/login flow เป็นจุดเสี่ยงสำคัญ

---

## 82. URL / Identifier Leakage

Sensitive data ไม่ควรอยู่ใน URL เมื่อหลีกเลี่ยงได้ เพราะ URL อาจถูกเก็บใน:

- browser history
- proxy log
- analytics
- referrer

Token/reset secret ต้องออกแบบให้ exposure จำกัด

---

## 83. Token Lifetime

Access capability ที่ bearer ใช้ได้ต้องมี lifetime สั้นตาม risk

Refresh credential ถ้ามีต้องถูกป้องกันมากกว่า access token

Long-lived permanent API key ต้องจำกัด use case และมี rotation/revocation

---

## 84. API Keys

API key สำหรับ integration ต้อง:

- scoped
- revocable
- attributable
- rotated
- never logged
- stored hashed เมื่อ use case รองรับ

อย่าใช้ key เดียวทั้ง organization โดยไม่มีเหตุผล

---

## 85. External Partner Access

Partner integration ต้องมี:

- identity
- scope
- contract
- rate limit
- audit
- revocation
- data-minimization

Partner network location ไม่ใช่ authorization

---

## 86. Driver / Courier Security

Driver client ได้ข้อมูลเท่าที่งานต้องใช้

ห้าม expose:

- seller-wide data
- customer financial data
- unrelated addresses
- staff notes
- internal risk signal

Assignment authorization ต้องตรวจ active job/custody relationship

---

## 87. Location Data

Location/GPS เป็น sensitive operational data

ต้องกำหนด:

- collection purpose
- frequency
- retention
- access
- customer-facing precision
- staff visibility
- deletion/anonymization

Raw location ไม่ควรถูก expose เกิน business need

---

## 88. Warehouse Device Security

Warehouse/device account ต้อง:

- unique identity
- scoped permission
- revocable
- no shared permanent credential เมื่อหลีกเลี่ยงได้
- offline command safety
- audit

Device compromise ต้องไม่ให้ authority ข้าม warehouse/domain

---

## 89. Offline Command Security

Offline command ต้อง:

- signed/authenticated เมื่อเหมาะสม
- carry actor/device identity
- have operation ID
- be replay-safe
- have validity window
- validate current authoritative state เมื่อ sync

Client offline state ไม่ override server invariant

---

## 90. Idempotency Security

Idempotency key ต้อง scope ตาม:

```text
actor / client
+
operation
+
resource/business scope
```

ห้ามให้ attacker reuse key ของ actor อื่นเพื่ออ่านผล operation หรือ cause confusion

Stored idempotency response ต้องไม่ leak data ข้าม authorization scope

---

## 91. Replay Protection

Operation ที่มี financial/security impact ต้องพิจารณา replay

Controls อาจรวม:

- idempotency key
- nonce
- timestamp
- short-lived signature
- sequence/version
- one-time token

เลือกตาม protocol

---

## 92. Concurrency and Authorization

Authorization check กับ mutation ต้องไม่เกิด race ที่เปิด privilege bypass

ตัวอย่าง:

```text
check owner
...
ownership changes
...
mutate
```

Critical check อาจต้องอยู่ใน transaction หรือใช้ expected version/conditional write

---

## 93. TOCTOU

Time-of-check/time-of-use risk ต้องพิจารณาใน:

- stock
- role
- approval
- payout destination
- object ownership
- seller state
- coupon/entitlement

Security-sensitive precondition ต้องถูก enforce ใกล้ mutation

---

## 94. Sensitive State Changes

ตัวอย่าง action ที่ควร audit และอาจ step-up:

- role grant/revoke
- staff activation
- account unlock
- seller verification change
- payout destination
- refund override
- inventory correction
- order force transition
- webhook secret rotation
- API key creation

---

## 95. Manual Database Access

Direct production DB access ต้องเป็น exceptional operation

ต้องมี:

- authorized operator
- reason
- least privilege
- time-bound access เมื่อทำได้
- audit
- runbook
- change/recovery record

Routine support ห้ามแก้ DB โดยตรง

---

## 96. Break-Glass Access

ถ้าต้องมี emergency access:

- แยก credential
- disable by default หรือ tightly controlled
- strong authentication
- short duration
- alert เมื่อใช้
- full audit
- post-incident review
- rotate/revoke หลังใช้ตาม policy

Break-glass ไม่ใช่ everyday admin account

---

## 97. Backup Security

Backup มีข้อมูลระดับ production จึงต้องถือเป็น sensitive asset

ต้อง:

- encrypt
- restrict access
- store off-host
- separate backup credential
- retention policy
- restore test
- access logging

Public bucket หรือ shared developer credential ห้ามใช้เก็บ production backup

---

## 98. Restore Security

Restore process ต้องตรวจ:

- backup provenance
- integrity
- access control
- target environment
- secret handling
- external side-effect reconciliation

Restored DB ห้ามถูกต่อเข้ากับ production provider แบบไม่ตั้งใจใน test environment

---

## 99. Disaster Recovery Security

DR environment ต้องมี security baseline ไม่ต่ำกว่า production ในส่วน critical

Emergency recovery ห้าม:

- disable auth indefinitely
- reuse exposed credential
-เปิด DB public
- bypass audit
- turn off TLS เพื่อความเร็ว

Temporary exception ต้อง document และ revoke

---

## 100. Incident Response

Security incident workflow ต้องรองรับ:

```text
detect
→ contain
→ preserve evidence
→ revoke/rotate
→ remediate
→ restore
→ reconcile
→ review
```

Incident response ต้องไม่ทำลาย evidence โดยไม่ตั้งใจ

---

## 101. Credential Compromise

เมื่อ credential รั่ว ต้องสามารถ:

- identify scope
- revoke
- rotate
- identify affected operations
- review logs/audit
- notify owner
- monitor abuse

Shared credential ทำให้ blast radius ใหญ่ จึงต้องหลีกเลี่ยง

---

## 102. Vulnerability Handling

Security issue ต้องมี severity/triage

Critical vulnerability ที่กระทบ internet-facing หรือ privileged path ต้องมี fast patch process

แต่ patch ต้องไม่ bypass testing/release integrity โดยไม่จำเป็น

---

## 103. Dependency Vulnerability

Scanner result ไม่เท่ากับ exploitable risk ทุกกรณี

ต้องพิจารณา:

- affected version
- reachable code
- exposure
- exploitability
- mitigation
- patch availability

แต่ known critical exploitable dependency ต้องไม่ถูกปล่อยไว้เพราะ scanner “มี false positive บ้าง”

---

## 104. Container Security

Container image ควร:

- minimal
- non-root เมื่อ practical
- no development tool ที่ไม่จำเป็น
- no embedded secret
- pinned base/version policy
- vulnerability scanned
- immutable at runtime เท่าที่ practical

---

## 105. Host Security

Initial safe-host ต้องมี:

- SSH key auth
- firewall default deny
- no public DB
- limited exposed ports
- patched OS
- controlled sudo
- filesystem permission
- backup
- monitoring

Host access เป็น privileged security boundary

---

## 106. Reverse Proxy Security

Nginx/edge ต้อง handle ตาม design:

- TLS
- request size limits
- timeout
- security headers
- trusted proxy headers
- client IP normalization
- upstream isolation

Config change ต้อง `nginx -t` ก่อน reload

---

## 107. Trusted Proxy Headers

Application ห้ามเชื่อ `X-Forwarded-For`, `X-Forwarded-Proto` จาก arbitrary client

ต้อง trust เฉพาะ proxy chain ที่กำหนด

ไม่เช่นนั้น attacker อาจ spoof scheme/IP

---

## 108. Cloudflare / Tunnel Boundary

Cloudflare/tunnel เป็น transport/edge control แต่ไม่แทน application auth

Tunnel token/certificate เป็น secret

ห้าม expose ใน:

- command output
- screenshots
- docs
- Git

Origin ต้องไม่เปิด parallel path สู่ internet โดยไม่ควบคุม

---

## 109. Environment Separation

DEV, TEST, LOAD, STAGING, PROD ต้องแยก credential และ data

ห้ามใช้:

```text
production API key
ใน local test
```

หรือ destructive test กับ production/demo data

---

## 110. Test Credentials

Automated test ใช้ test-only identity

Test secret ต้องไม่ reuse production secret

Test fixture ไม่ควรใช้ real customer PII

---

## 111. Security Testing Baseline

`TESTING.md` ต้องครอบคลุมอย่างน้อย:

- authn
- authz
- seller/customer isolation
- IDOR/BOLA
- CSRF
- session lifecycle
- invalid/expired token
- role escalation
- maker-checker
- DB least privilege
- webhook signature/replay
- secret leakage
- error leakage
- upload validation
- SSRF
- SQL injection resistance
- restore/recovery security

---

## 112. Negative Testing

Security test ต้องไม่ได้มีเฉพาะ happy path

ตัวอย่าง:

```text
Customer A → Order B = denied
Seller A → Seller B offer = denied
Staff role without finance permission → refund override = denied
Runtime DB user → ALTER TABLE = denied
Unsigned webhook = denied
Expired signed URL = denied
```

---

## 113. Authorization Matrix

Production system ต้องมี machine-reviewable หรือ testable authorization matrix ตาม capability

ตัวอย่าง columns:

```text
Actor
Role
Resource
Action
Ownership
State
Step-up
Approval
Result
```

Matrix ไม่จำเป็นต้องเป็น single giant table แต่ semantics ต้องไม่กระจัดกระจายจนตรวจไม่ได้

---

## 114. Security Invariants

ตัวอย่าง invariants หลัก:

```text
Unauthenticated actor cannot perform authenticated mutation.

Authenticated customer cannot access another customer's private resource.

Seller cannot mutate another seller's owned resource.

Runtime DB identity cannot alter schema.

Privileged staff action is attributable.

High-risk approved change cannot be executed after approval payload changes.

Unknown payment outcome is not converted to retry-safe failure without reconciliation.

Secret is never part of committed source.

Private object is not publicly readable by default.
```

---

## 115. Security Decision Records

Decision ที่เปลี่ยน security boundary อย่างมีนัยสำคัญควร ADR เช่น:

- identity provider
- browser session model
- MFA/passkey strategy
- secrets management
- key management
- DB privilege architecture
- encryption architecture

POC-04 จะสร้าง evidence สำหรับ decisions เหล่านี้

---

## 116. POC-04 Scope

POC-04 ต้อง validate อย่างน้อย:

- authentication integration
- account/business identity mapping
- authorization boundary
- browser session / BFF model
- CSRF
- staff MFA/step-up direction
- security validation/hardening ของ database runtime vs migrator roles ที่สร้างใน POC-01
- secret delivery
- workload identity
- safe error handling
- audit events
- webhook verification representative flow

POC-04 ห้าม bypass security เพื่อให้ demo ผ่าน

---

## 117. Security Acceptance Gate

Security baseline ถือว่าพร้อม production foundation เมื่อ:

- authn/authz boundaries documented
- account lifecycle enforced
- object-level authorization testable
- staff privilege scoped
- runtime DB least privilege proven
- migration identity separate
- secrets not committed
- session/CSRF model proven
- safe errors proven
- webhook verification proven
- audit path proven
- backup access controlled
- recovery does not require disabling core controls
- known critical security blockers = 0

---

## 118. Forbidden Security Shortcuts

ห้าม:

- hard-code secret
- commit `.env` ที่มี credential
- use one admin credential everywhere
- runtime DB as owner/superuser
- public PostgreSQL
- trust internal network implicitly
- trust client-provided seller/user ID without server scope
- disable authorization for “internal” endpoint
- use frontend hidden button as authorization
- log password/token
- blind retry financial operation with unknown result
- create custom crypto
- expose debug endpoint publicly
- allow support to mutate DB directlyเป็น routine workflow
- treat UUID secrecy as access control
- disable TLS in productionเพื่อความสะดวก
- weaken security testเพื่อให้ CI green

---

## 119. Security Review Questions

ก่อน merge security-significant change ให้ถาม:

1. Actor คือใคร
2. Authentication assurance เท่าไร
3. Authorization decision อยู่ที่ไหน
4. Resource ownership ตรวจอย่างไร
5. State/context มีผลไหม
6. ต้อง step-up หรือ approval ไหม
7. Replay/duplicate ทำอะไรได้
8. Secret/PII ถูก handle อย่างไร
9. Error/log leak อะไรได้บ้าง
10. Audit พอหรือไม่
11. DB privilege เปลี่ยนหรือไม่
12. Recovery path ปลอดภัยหรือไม่
13. Threat ใหม่คืออะไร
14. Test negative path แล้วหรือยัง
15. ต้อง ADR หรือไม่

---

## 120. Canonical Security Rule

หลักสุดท้ายของ VRA คือ:

> **ไม่มี actor, service, network, database connection หรือ external integration ใดได้รับความเชื่อถือโดยปริยาย**

ทุก authority ต้อง:

```text
identify
→ authenticate
→ authorize
→ constrain
→ observe
→ audit
→ revoke
→ recover
```

Security ต้องเป็นส่วนหนึ่งของ product และ architecture ตั้งแต่ต้น ไม่ใช่ checklist ที่เพิ่มก่อน production launch เท่านั้น
