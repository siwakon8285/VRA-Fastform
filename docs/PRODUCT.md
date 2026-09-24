# นิยามผลิตภัณฑ์ VRA

**เอกสาร:** `docs/PRODUCT.md`
**สถานะ:** ACTIVE — canonical baseline v1
**ผลิตภัณฑ์:** VRA (วีล่า)
**วัตถุประสงค์:** กำหนดว่า VRA คืออะไร ให้บริการใคร เป็นเจ้าของความสามารถทางธุรกิจใดบ้าง และมี product-level invariants อะไรที่ implementation ต้องรักษา

> เอกสารนี้กำหนดเจตนาของผลิตภัณฑ์และขอบเขตทางธุรกิจ
> เอกสารนี้ไม่ได้กำหนดรายละเอียดสถาปัตยกรรมซอฟต์แวร์ การควบคุมความปลอดภัย การ deploy หรือกลยุทธ์การทดสอบ ซึ่งจะอยู่ใน `DESIGN.md`, `SECURITY.md`, `OPERATIONS.md` และ `TESTING.md`

---

## 1. ภาพรวมผลิตภัณฑ์

VRA คือแพลตฟอร์ม marketplace, commerce, fulfillment และ logistics ที่ออกแบบมาเพื่อประสานวงจรชีวิตของสินค้า ตั้งแต่ผู้ขายมีสินค้าพร้อมขาย การเลือกซื้อของลูกค้า การชำระเงิน การจัดเตรียมสินค้า การขนส่ง การส่งมอบ การคืนสินค้า การคืนเงิน การ settlement ตลอดจนการ reconciliation ทางปฏิบัติการ

VRA ไม่ใช่เพียง storefront แต่เป็นระบบที่ประสาน “ความจริงทางธุรกิจ” ระหว่างลูกค้า ผู้ขาย พนักงาน เจ้าของสินค้าคงคลัง คลังสินค้า ฝ่าย fulfillment คนขับหรือ carrier และฝ่ายการเงิน

ผลิตภัณฑ์มีเป้าหมายเติบโตเป็นกลุ่มความสามารถภายใต้ชื่อ VRA ตัวอย่างเช่น:

- VRA Seller
- VRA Fulfill
- VRA Drive
- VRA Food

`VRA Pay` ยังไม่อยู่ในขอบเขตผลิตภัณฑ์ระยะแรก และถูกเลื่อนไว้โดยเจตนา

ประสบการณ์ของ VRA ควรให้ความรู้สึกอบอุ่น ชัดเจน ใช้งานง่าย เชื่อถือได้ ทันสมัย และเป็นมนุษย์ มีคุณภาพระดับ premium โดยไม่สร้างความรู้สึกแบ่งแยกหรือ exclusivity

---

## 2. เป้าหมายของผลิตภัณฑ์

VRA มีเป้าหมายสร้างแพลตฟอร์ม commerce และ fulfillment ที่เชื่อถือได้ โดยข้อเท็จจริงสำคัญทางธุรกิจยังคงถูกต้องแม้เกิดเหตุการณ์ เช่น ผู้ใช้กดซ้ำ เครือข่ายล้มเหลว external provider ให้ผลลัพธ์ไม่แน่นอน worker restart หรือมีหลาย actor ทำงานพร้อมกัน

ผลิตภัณฑ์ต้องทำให้:

1. ลูกค้าสามารถค้นหา ซื้อ ติดตาม รับสินค้า และเมื่อ policy อนุญาต สามารถยกเลิก คืนสินค้า หรือขอคืนเงินได้
2. ผู้ขายสามารถจัดการสินค้า offer availability order และ fulfillment obligation ของตนเองได้
3. ฝ่าย fulfillment สามารถจัดการ inventory, allocation, picking, packing, custody, dispatch และ shipment execution ได้
4. คนขับหรือ carrier สามารถทำงานขนส่งได้โดยไม่กลายเป็นเจ้าของ authoritative order state
5. เจ้าหน้าที่สามารถช่วยเหลือการปฏิบัติงานได้อย่างปลอดภัย ด้วยสิทธิ์ที่ชัดเจนและตรวจสอบย้อนหลังได้
6. ฝ่ายการเงินสามารถ reconciliation payment, refund, settlement และ payout ได้โดยไม่ทำให้มูลค่าเงินถูกสร้างซ้ำหรือสูญหายอย่างเงียบ ๆ
7. ระบบสามารถฟื้นตัวจาก partial failure ได้โดยไม่ต้องพึ่งการแก้ฐานข้อมูลด้วยมือแบบไม่ปลอดภัย

VRA ต้องให้ความสำคัญกับความน่าเชื่อถือและ correctness ก่อน optimization ที่เพิ่มความสะดวกแต่ทำให้ guarantee อ่อนลง

---

## 3. หลักการของผลิตภัณฑ์

### 3.1 Security First

Security เป็นคุณสมบัติของผลิตภัณฑ์ ไม่ใช่งาน infrastructure ที่ค่อยทำภายหลัง

Privileged action, sensitive business operation, staff tooling, payment-related action, account recovery และ administrative workflow ต้องออกแบบโดยมี authorization และ auditability ที่ชัดเจน

### 3.2 Correctness Before Convenience

VRA ต้องเลือกสถานะที่ซื่อสัตย์ต่อความจริง เช่น `UNKNOWN`, `PENDING` หรือ `REQUIRES_RECONCILIATION` แทนการแสดงผลสำเร็จหรือล้มเหลวที่ระบบยังพิสูจน์ไม่ได้

Critical operation ต้องไม่สร้าง inventory reservation, charge, refund, shipment, settlement หรือ payout ซ้ำอย่างเงียบ ๆ

### 3.3 Explicit Business State

Lifecycle สำคัญต้องถูกแทนด้วย state ที่ explicit ไม่ใช่อนุมานจาก field อื่นที่ไม่ได้มีความหมายเดียวกัน

ตัวอย่างเช่น:

- order lifecycle
- payment status
- reservation status
- fulfillment status
- shipment และ custody status
- account status
- publication และ compliance state
- reconciliation state

### 3.4 Ownership of Truth

ข้อเท็จจริงทางธุรกิจที่สำคัญแต่ละประเภทต้องมี authoritative owner เพียงหนึ่งเดียว

Derived view, search document, analytics, tracking view, cache และ projection สามารถช่วยเรื่องประสบการณ์หรือประสิทธิภาพได้ แต่ต้องไม่กลายเป็น authoritative source ของ transactional truth โดยเงียบ ๆ

### 3.5 Failure Must Be Recoverable

ผลิตภัณฑ์ต้องถือว่าเหตุการณ์เหล่านี้สามารถเกิดขึ้นได้ตามปกติ:

- duplicated request
- delayed callback
- provider timeout
- uncertain payment outcome
- worker restart
- partial infrastructure failure
- stale projection
- temporary network loss
- operational mistake

Recovery, retry, reconciliation และ auditability เป็นส่วนหนึ่งของพฤติกรรมผลิตภัณฑ์

### 3.6 Complexity Requires Evidence

VRA ไม่ควรเพิ่ม specialized infrastructure, service boundary, distributed coordination หรือ operational machinery เพียงเพราะรูปแบบเหล่านั้นนิยมใช้ในระบบขนาดใหญ่

ความซับซ้อนเพิ่มเติมต้องแก้ requirement, risk หรือ bottleneck ที่พิสูจน์ได้

---

## 4. Actor หลัก

### 4.1 ลูกค้า

ลูกค้าสามารถ:

- มี account และ customer profile
- ค้นหาและดู product/offer
- จัดการ cart
- เริ่ม checkout
- เลือก fulfillment หรือ delivery option
- สร้าง order
- ชำระเงินผ่าน payment method ที่รองรับ
- ดูสถานะ order และ shipment
- รับสินค้า
- ขอ cancellation, return หรือ refund เมื่อ policy อนุญาต
- ติดต่อ support

ลูกค้าต้องไม่สามารถเข้าถึง private seller, warehouse, driver, staff หรือ financial data เพียงเพราะรู้ identifier ของ object

### 4.2 ผู้ขาย / Merchant

ผู้ขายสามารถ:

- จัดการ seller identity และข้อมูลธุรกิจ
- สร้างและดูแล commercial product data
- สร้าง offer สำหรับ SKU ที่ขายได้
- จัดการราคาและ availability ภายใต้ platform policy
- ดูและดำเนินการกับ order ที่ตนเป็นเจ้าของ
- เข้าร่วม fulfillment workflow
- ดู settlement หรือ payout information
- จัดการ seller-side return หรือ dispute เมื่อได้รับอนุญาต

ผู้ขายต้องไม่สามารถเข้าถึง private data ของผู้ขายรายอื่นโดยไม่มี authorization ที่ชัดเจน

### 4.3 Fulfillment / Warehouse Operator

ฝ่าย fulfillment จัดการ physical stock และ execution เช่น:

- receiving
- storage
- stock status
- reservation/allocation support
- picking
- packing
- handoff
- damage/quarantine
- inventory correction ผ่าน controlled movement

ทุก operation ต้องรักษา inventory ownership, location และ status

### 4.4 Driver / Courier / Carrier

ผู้ดำเนินการขนส่งได้รับเฉพาะข้อมูลที่จำเป็นต่อการทำงาน สามารถอัปเดต custody หรือ delivery progress และรายงานผลลัพธ์ได้

Driver หรือ carrier ไม่ใช่เจ้าของ order

### 4.5 Staff / Operations

เจ้าหน้าที่ภายในอาจทำงานด้าน:

- customer support
- seller support
- fulfillment
- logistics
- risk
- finance
- compliance
- dispute
- reconciliation

Staff capability ต้องจำกัดตาม role และ audit ได้ High-risk action อาจต้องใช้ step-up authentication, maker-checker หรือ context เพิ่มเติม

Staff account ไม่ใช่ public self-service account

### 4.6 System / Worker Identity

Background worker และ system integration ทำงานในฐานะ workload identity

แต่ละ identity ต้องมีสิทธิ์เท่าที่จำเป็น และห้ามถือว่า trusted เพียงเพราะอยู่ใน VRA infrastructure

---

## 5. โมเดล Commerce หลัก

VRA แยก product identity ออกจาก inventory และเงื่อนไขการขาย

ลำดับหลักคือ:

```text
Product
  └─ Variant
      └─ SKU
          └─ Offer
```

### Product

แทนตัวสินค้าหลักที่ผู้ใช้รับรู้

### Variant

แทนรูปแบบย่อยที่เลือกได้ เช่น ขนาด สี configuration หรือ option อื่น

### SKU

แทน stock-keeping identity ที่ใช้ใน inventory และ fulfillment

SKU ต้อง stable และห้ามบรรจุ seller price, current stock หรือ customer-facing publication state ไว้ใน identity เดียวกัน

### Offer

แทนข้อเสนอทางการค้าของ seller สำหรับ SKU

Offer อาจประกอบด้วย seller, price, availability policy, sale status และ terms ที่เกี่ยวข้อง

ในอนาคต seller หลายรายอาจเสนอ SKU เดียวกันได้ หาก business model อนุญาต

---

## 6. Publication, Compliance และ Buyability

Product lifecycle, compliance state, publication state และ buyability เป็นคนละแนวคิด

การที่ product เป็น `PUBLISHED` ไม่ได้แปลว่าลูกค้าสามารถซื้อได้ทันที

Buyability อาจขึ้นอยู่กับ:

- active offer
- valid pricing
- inventory availability
- seller state
- compliance rule
- delivery eligibility
- geographic restriction
- operational policy

ระบบห้ามรวม concern เหล่านี้เป็น boolean เดียวที่คลุมเครือ

---

## 7. Cart, Checkout, Order, Payment และ Reservation

แนวคิดต่อไปนี้ต้องไม่ถูกรวมเข้าด้วยกัน:

```text
Cart
≠ Checkout Session
≠ Order
≠ Payment
≠ Inventory Reservation
```

### Cart

เป็น mutable planning object ของลูกค้า

Cart ไม่ใช่ guarantee เรื่องราคา stock หรือ fulfillment

### Checkout Session

เป็น bounded snapshot ของ purchase intent ใช้สำหรับตรวจสอบและเตรียม order

ข้อมูลทางการค้าและ fulfillment ที่ resolve แล้วใน checkout ต้องไม่ถูกเปลี่ยนย้อนหลังอย่างเงียบ ๆ เพราะ catalog เปลี่ยน

### Order

เป็น durable commercial record ที่ VRA สร้างและเป็นเจ้าของ

ต้องมี durable order ก่อนที่จะใช้ผลจาก external payment เป็นพื้นฐาน authoritative ของ purchase workflow

### Payment

เป็น financial lifecycle แยกจาก order

ห้ามอนุมาน payment state จาก order state อย่างเดียว

### Inventory Reservation

เป็น temporary claim ต่อ sellable inventory

Reservation ไม่เท่ากับ payment, allocation, picking, packing, shipment หรือ ownership transfer

---

## 8. Checkout และ Order Grouping

VRA ออกแบบให้รองรับ marketplace purchase ที่มีมากกว่าหนึ่ง seller

Customer-facing checkout จึงอาจสร้าง parent commercial grouping พร้อม seller-specific obligation ภายใน

Policy เริ่มต้นคือ selected checkout แบบ all-or-nothing เว้นแต่มี product decision ภายหลังเปลี่ยนอย่างชัดเจน

ระบบห้ามสร้าง partial checkout อย่างเงียบ ๆ หากลูกค้าได้รับการนำเสนอว่าเป็นการซื้อแบบ atomic ครั้งเดียว

---

## 9. Order Lifecycle

Order state ต้อง explicit และ valid transition ต้องถูกควบคุม

Production state machine ที่ละเอียดจะกำหนดใน domain design และสามารถ evolve จาก validation POC ได้

Product-level requirement คือ:

- state transition ต้อง explicit
- terminal state ห้ามย้อนกลับเป็น active state โดยเงียบ ๆ
- cancellation, expiration, confirmation, refund, fulfillment และ delivery เป็นคนละ concept
- payment uncertainty ห้ามถูกแปลงเป็น final order outcome โดยไม่มีหลักฐาน
- reason สำคัญของ state change ควรเป็น structured data เมื่อมี operational value

Order record ไม่ใช่ตัวแทนของ payment, fulfillment, shipment หรือ inventory state

---

## 10. Inventory Model

Inventory ต้องแทน physical truth และ commercial truth โดยไม่ลดทุกอย่างเหลือ mutable quantity เดียว

Stock identity ต้องรวม เมื่อเกี่ยวข้อง:

```text
SKU
+ Owner
+ Location
+ Status
```

Status อาจประกอบด้วย sellable, reserved, allocated, damaged, quarantined หรือ state อื่นที่กำหนดชัดเจน

### 10.1 Physical vs Commercial Availability

Physical stock และ commercially available stock ไม่จำเป็นต้องเท่ากัน

สินค้าที่ damaged, quarantined, held, allocated หรือ restricted ต้องไม่ถูกนับเป็น sellable โดยอัตโนมัติ

### 10.2 Reservation vs Allocation

Reservation และ allocation เป็นคนละแนวคิด

Reservation ปกป้อง stock สำหรับ commercial workflow

Allocation ผูก stock เข้ากับ fulfillment execution อย่างชัดเจนกว่า

### 10.3 Inventory History

Material inventory change ต้องผ่าน controlled operation และมี movement history

Routine stock management ห้ามพึ่ง unrestricted `set quantity = X`

### 10.4 Availability Invariant

Available-to-promise stock ต้องไม่ติดลบ

Concurrency ต้องรักษา invariant นี้แม้หลาย buyer พยายามซื้อหน่วยสุดท้ายพร้อมกัน

---

## 11. Payment

Payment เป็น external-facing financial workflow จึงต้องแทน uncertainty อย่าง explicit

Provider timeout หรือ response หาย ไม่ได้พิสูจน์ว่า charge ล้มเหลว

Payment อาจต้องมี state เช่น:

- pending
- succeeded
- failed
- unknown
- reconciliation required

`UNKNOWN` เป็น business state ที่ถูกต้องเมื่อยังไม่รู้ authoritative provider outcome

ระบบต้อง reconcile uncertain outcome แทน blind retry ที่อาจสร้าง financial side effect ซ้ำ

---

## 12. Refund

Refund เป็น financial operation แยกจาก payment เดิม

ระบบต้อง enforce ว่า cumulative refund obligation ที่สำเร็จหรือกำลัง pending ต้องไม่เกิน refundable amount ที่อนุญาต

Retry ต้องไม่สร้าง refund ซ้ำ

Provider timeout ห้ามถูกตีความว่า refund ไม่เกิดขึ้นแน่นอน

---

## 13. Ledger, Settlement และ Payout

Financial accounting ต้องรักษามูลค่าแยกจาก convenient order status

หาก VRA เป็นเจ้าของ financial ledger:

- entry ต้อง immutable
- journal ต้อง balanced ตาม accounting model
- monetary representation ต้อง exact

Settlement และ payout ต้อง explicit

Payout provider timeout ต้องสามารถเข้าสู่ uncertain outcome และ reconciliation path ได้ ห้าม blind retry เมื่อ payout ก่อนหน้าอาจสำเร็จแล้ว

Money ต้องใช้ exact decimal หรือ minor-unit semantics ที่เหมาะสมกับ currency ห้ามใช้ binary floating point เป็น authoritative representation

---

## 14. Fulfillment Model

Order, fulfillment, shipment และ package เป็นคนละ concept

ความสัมพันธ์เชิงแนวคิด:

```text
Order
  └─ Fulfillment
      └─ Shipment
          └─ Package
```

Order หนึ่งอาจมีหลาย fulfillment หรือ shipment

ระบบห้ามสมมุติว่า 1 order = 1 package = 1 delivery event เสมอ

---

## 15. Logistics และ Delivery

Logistics planning/execution ต้องแยกจาก customer order ownership

โมเดลอาจประกอบด้วย:

```text
Plan
  └─ Legs
      └─ Jobs / Trips
```

VRA ต้องรักษา:

- assignment
- custody
- handoff
- tracking observation
- delivery outcome

ห้ามใช้ `order.driver_id` เป็นโมเดล logistics ทั้งหมด

ควรมี active assignment เดียวสำหรับ exclusive delivery responsibility เดียวกัน เว้นแต่ business process รองรับรูปแบบอื่นอย่าง explicit

---

## 16. Custody

สำหรับ physical goods ระบบต้องระบุได้ว่าใครหรือ operational unit ใดถือครอง custody เมื่อเรื่องนี้มีผลทางธุรกิจ

Custody transition ต้อง explicit และ audit ได้ เช่น:

- warehouse possession
- packed and waiting pickup
- handed to courier
- transferred between logistics legs
- delivered to recipient
- returned to facility

Tracking location เพียงอย่างเดียวไม่พิสูจน์ custody

---

## 17. Tracking

Tracking data เป็น observation และอาจ late, duplicated, out-of-order หรือไม่แม่นยำ

VRA แยก:

1. raw tracking observation
2. current operational tracking state
3. customer-facing tracking view

Customer-facing view สามารถ derive จาก operational data แต่ห้ามกลายเป็น authoritative owner ของ shipment truth

---

## 18. Offline และ Intermittent Connectivity

Logistics และ operational client อาจสูญเสีย network ชั่วคราว

เมื่อรองรับ offline operation คำสั่งต้องถูกออกแบบให้ reconnect/retry แล้วไม่สร้าง irreversible operation ซ้ำ

Offline client ไม่ได้รับ authority เหนือ order, payment, inventory หรือ custody truth

---

## 19. Identity และ Account Lifecycle

Account มี lifecycle ชัดเจน

Baseline account states:

- `ACTIVE`
- `LOCKED`
- `DISABLED`
- `CLOSED`

Authentication, verification, authorization, recovery และ business eligibility เป็นคนละ concern

Authentication สำเร็จไม่ได้หมายความว่าได้รับสิทธิ์ทำทุก business action

Authorization อาจขึ้นอยู่กับ:

- role
- ownership
- object state
- organization / seller relationship
- risk context
- step-up authentication
- approval requirement

---

## 20. Privileged Operation และ Maker-Checker

High-impact operation อาจต้องแยกคนเสนอและคนอนุมัติ

ตัวอย่าง:

- sensitive seller change
- high-risk financial operation
- exceptional refund
- payout change
- privileged access change
- destructive operational correction

รายละเอียด policy จะอยู่ใน `SECURITY.md` และ domain specification แต่ product ต้องรองรับ capability นี้ได้

---

## 21. Customer Support

Support workflow ต้องช่วยผู้ใช้โดยไม่ bypass product invariant

Staff action ห้ามพึ่ง direct database mutation เป็นวิธีทำงานปกติ

เมื่อ staff ทำงานแทน customer หรือ seller ระบบควรแยกข้อมูลให้ชัดเจนว่า:

- business subject คือใคร
- staff actor คือใคร
- operation คืออะไร
- reason คืออะไร
- audit result คืออะไร

---

## 22. Auditability

VRA ต้องสามารถอธิบาย significant business action และ privileged action หลังเหตุการณ์ได้

Audit context ควรระบุอย่างน้อย:

- ใครหรือ workload ใดเป็นผู้กระทำ
- operation ใดถูกพยายามทำ
- object ใดได้รับผล
- เมื่อไร
- สำเร็จหรือไม่
- reason/approval context ที่เกี่ยวข้อง

Operational log ไม่ใช่สิ่งทดแทน protected audit record

---

## 23. Derived Systems

Search, analytics, customer tracking view, dashboard, notification และ read-optimized system อื่นสามารถใช้ derived data ได้

Derived data อาจ stale ชั่วคราว

หาก derived state ขัดกับ authoritative transactional state ต้องมี owner of truth และ recovery/rebuild path ที่ชัดเจน

Ordinary transactional operation ไม่ควรต้องรอ global synchronous consistency จากทุก derived surface

---

## 24. Reconciliation

Reconciliation เป็น first-class product capability เมื่อ VRA เชื่อมต่อระบบที่อาจให้ผลลัพธ์ delayed, duplicated หรือ uncertain

ตัวอย่าง:

- payment
- refund
- settlement
- payout
- logistics callback
- inventory integration
- external webhook

Workflow ไม่ถือว่า operationally complete เพียงเพราะ original request ตอบกลับสำเร็จ

---

## 25. Product Capability Areas

VRA คาดว่าจะมี business capability areas เช่น:

- Identity and Access
- Customer
- Seller / Merchant
- Catalog
- Product / Variant / SKU
- Offer and Pricing
- Cart
- Checkout
- Order
- Inventory
- Reservation and Allocation
- Payment
- Refund
- Ledger / Settlement / Payout
- Fulfillment
- Shipment and Package
- Logistics / Delivery
- Tracking
- Notification
- Support / Operations
- Risk / Compliance
- Audit
- Reconciliation

ชื่อเหล่านี้คือ product ownership boundaries ไม่ได้หมายความว่าต้องมี deployable service แยกหนึ่งตัวต่อ capability

---

## 26. ขอบเขตผลิตภัณฑ์ระยะแรก

ระบบระยะแรกควรให้ความสำคัญกับ transactional path ที่พิสูจน์ operating model หลักของ VRA:

```text
identity
→ sellable catalog / offer
→ inventory availability
→ checkout
→ durable order
→ reservation
→ payment coordination
→ fulfillment
→ shipment / delivery
→ refund / reconciliation เมื่อจำเป็น
```

ลำดับ implementation ที่แท้จริงควบคุมโดย `ROADMAP.md`

ระบบต้องสร้าง correctness และ operational recovery ก่อน feature expansion จำนวนมาก

---

## 27. ขอบเขตที่เลื่อนไว้อย่างชัดเจน

สิ่งต่อไปนี้ไม่ถือว่าอยู่ใน first production scope โดยอัตโนมัติ เว้นแต่ product decision ภายหลังเพิ่มเข้ามา:

- VRA Pay ในฐานะ standalone payment product
- social-commerce feature ขนาดใหญ่
- advertising platform
- loyalty program ที่มี financial liability model ของตัวเอง
- unrestricted international marketplace
- multi-region active-active transaction processing
- arbitrary seller-defined workflow scripting
- feature ที่ต้องลด inventory, financial, identity หรือ audit invariant เพื่อเร่ง delivery

Deferred ไม่ได้แปลว่าปฏิเสธถาวร แต่หมายถึงต้องมี explicit requirement และ design review ก่อน

---

## 28. เกณฑ์ความสำเร็จของผลิตภัณฑ์

VRA ควรถูกมองว่าประสบความสำเร็จเมื่อรองรับ real commerce ได้โดยยังรักษาความเชื่อถือภายใต้ normal load และ common failure modes

ความสำเร็จไม่ได้วัดจาก latency หรือจำนวน feature อย่างเดียว

ผลลัพธ์สำคัญ ได้แก่:

- ลูกค้าไม่เห็น false purchase success
- stock เดียวกันไม่ถูกขายเกิน policy ให้หลาย buyer
- เงินไม่ถูกสร้างซ้ำหรือสูญหายอย่างเงียบ ๆ
- retry ปลอดภัยใน operation ที่ระบบประกาศว่ารองรับ retry
- uncertain provider outcome เข้าสู่ reconciliation แทนการเดา
- seller/customer data ถูกแยกอย่างถูกต้อง
- privileged action มี attribution
- inventory และ financial record อธิบายย้อนหลังได้
- worker/integration failure ฟื้นตัวได้โดยไม่พึ่ง unsafe manual DB edit
- authoritative state restore และ reconcile ได้หลัง recovery

---

## 29. ความสัมพันธ์กับ Canonical Documents อื่น

เอกสารนี้ตอบคำถามว่า:

> VRA คืออะไร เป็นเจ้าของ business concept ใด และ product behavior ใดต้องคงอยู่เสมอ

เอกสารอื่นมีหน้าที่ต่างกัน:

- `BRAND.md` — VRA แสดงตัวตนและสื่อสารอย่างไร
- `DESIGN.md` — software architecture รักษา product requirement อย่างไร
- `SECURITY.md` — trust, identity, authorization, secret และ threat ถูกจัดการอย่างไร
- `TESTING.md` — invariant และ failure behavior ถูกพิสูจน์อย่างไร
- `OPERATIONS.md` — VRA ถูก deploy, operate, recover และ reconcile อย่างไร
- `ROADMAP.md` — validation และ implementation เกิดตามลำดับใด
- `docs/adr/` — เหตุผลของ architectural decision สำคัญ

เมื่อ implementation และ product intent ขัดกัน ต้องมี explicit review ห้าม implementation เปลี่ยน product semantics โดยเงียบ ๆ

---

## 30. Change Governance

การแก้เอกสารนี้แบบ material ต้องผ่าน deliberate review

ถือว่าเป็น material change เมื่อเปลี่ยน เช่น:

- authoritative business ownership
- financial/inventory invariant
- actor permission
- checkout atomicity
- seller/customer isolation
- order/payment/fulfillment semantics
- recovery/reconciliation expectation
- product capability boundary

Implementation convenience อย่างเดียวไม่เพียงพอสำหรับการลดความเข้มแข็งของ product invariant

หาก product change ทำให้เกิด architectural decision สำคัญ ต้องบันทึก architecture change ผ่าน ADR process ด้วย
