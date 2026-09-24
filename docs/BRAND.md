# แบรนด์และระบบการออกแบบ VRA

**เอกสาร:** `docs/BRAND.md`
**สถานะ:** ACTIVE — canonical baseline v1
**แบรนด์:** VRA
**การออกเสียงภาษาไทย:** วีล่า
**ขอบเขต:** Brand identity, visual direction, UI principles, color tokens, typography direction, spacing, shape, motion, accessibility, content voice และกติกาการนำแบรนด์ไปใช้ในผลิตภัณฑ์ VRA

> เอกสารนี้ขยายจาก brand baseline เดิมของ VRA โดยรักษา personality, color palette, UI direction และ motion principles เดิมไว้
> Product semantics อยู่ใน `PRODUCT.md`
> Software architecture อยู่ใน `DESIGN.md`
> Security requirements อยู่ใน `SECURITY.md`
> Testing strategy อยู่ใน `TESTING.md`
> Operational rules อยู่ใน `OPERATIONS.md`

---

## 1. Brand Identity

ชื่อแบรนด์คือ:

```text
VRA
```

การออกเสียงภาษาไทยที่ตั้งใจไว้คือ:

```text
วีล่า
```

VRA ต้องถูกนำเสนอเป็นแบรนด์ที่ร่วมสมัย เข้าถึงง่าย และน่าเชื่อถือ โดยไม่พยายามทำให้ดูหรูหราแบบห่างเหินหรือ exclusive

ชื่อ family ที่สามารถใช้เป็น architecture ของชื่อในอนาคต เช่น:

- VRA Drive
- VRA Food
- VRA Seller
- VRA Fulfill

ชื่อเหล่านี้เป็น **naming architecture** ไม่ใช่ commitment ว่าทุกผลิตภัณฑ์ต้องถูกสร้างทันที

---

## 2. Brand Personality

Personality หลักของ VRA คือ:

```text
Warm
Clear
Effortless
Reliable
Modern
Human
Premium without being exclusive
```

ความหมายเชิงการออกแบบ:

### Warm

ให้ความรู้สึกเป็นมิตร มีชีวิต และไม่แข็งทื่อเหมือน enterprise software แบบดั้งเดิม

### Clear

ข้อมูลสำคัญต้องเข้าใจง่าย hierarchy ชัด ไม่มี visual noise ที่แย่งความสนใจ

### Effortless

งานที่ผู้ใช้ต้องทำควรรู้สึกตรงไปตรงมา จำนวน decision และ interaction ที่ไม่จำเป็นต้องถูกลดลง

### Reliable

UI ต้องสร้างความมั่นใจผ่าน state ที่ชัด feedback ที่ตรงความจริง และ behavior ที่คาดเดาได้

### Modern

ดูร่วมสมัยโดยไม่ไล่ตาม visual trend จนทำให้แบรนด์หมดอายุเร็ว

### Human

ภาษา interaction และ visual rhythm ต้องไม่รู้สึกเหมือนเครื่องจักรที่ไร้บริบท

### Premium without being exclusive

คุณภาพสูง ละเอียด สะอาด และมี restraint แต่ไม่ทำให้ผู้ใช้รู้สึกว่าแบรนด์ตั้งใจแบ่งชนชั้นหรือสร้างระยะห่าง

---

## 3. Brand Principles

### 3.1 Clarity Before Decoration

ทุก visual element ต้องมีเหตุผล

Decoration ห้ามทำให้:

- hierarchy แย่ลง
- action สำคัญมองยาก
- contrast ลด
- loading/state feedback สับสน
- content density สูงเกินไป

### 3.2 Quiet Confidence

VRA ไม่ควร “ตะโกน” ผ่าน UI

ใช้:

- spacing
- typography
- contrast
- subtle motion
- restrained shadows
- disciplined color

แทน effect จำนวนมาก

### 3.3 Product Photography Carries Color

UI canvas ใช้ neutral/warm-neutral เป็นหลัก

สีจาก product photography หรือ content สามารถเป็นพื้นที่ที่สร้าง richness ของหน้าได้

UI chrome เองไม่ควรแข่งขันกับ product content

### 3.4 Familiar Before Clever

Interaction ที่ familiar และเข้าใจเร็วสำคัญกว่า pattern แปลกใหม่ที่ดูโดดเด่นแต่ใช้งานยาก

### 3.5 Accessibility Is Part of Brand Quality

Accessibility ไม่ใช่ข้อยกเว้นหรือ “โหมดพิเศษ”

แบรนด์ที่ premium ต้อง:

- อ่านได้
- keyboard ใช้ได้
- contrast ผ่าน
- motion ปลอดภัย
- responsive
- state ชัด

---

## 4. Color System

Brand baseline เดิมกำหนด palette ดังนี้:

| Token | Color |
| --- | --- |
| Ink Plum | `#402C46` |
| Primary Dark | `#322237` |
| Brand Soft | `#EEE8F0` |
| Canvas / Warm White | `#F7F6F2` |
| Surface | `#FFFFFF` |
| Text Primary | `#1C1C1A` |
| Text Secondary | `#686863` |
| Border Subtle | `#E6E4DE` |

สีเหล่านี้เป็น canonical baseline ของ VRA

---

## 5. Color Roles

### 5.1 Ink Plum — `#402C46`

ใช้สำหรับ brand-bearing element เช่น:

- primary brand accent
- selected state บางประเภท
- emphasis ที่มีความหมายกับแบรนด์
- iconography หรือ visual detail ที่ต้องการ identity
- key interaction บางประเภท

ไม่ควรใช้เป็นพื้นหลังทุกพื้นที่จนแบรนด์ดูหนัก

### 5.2 Primary Dark — `#322237`

ใช้สำหรับ:

- darker brand surface
- high-emphasis element
- foreground บน light canvas
- selected/navigation treatment ที่ต้องการน้ำหนักมากกว่า Ink Plum

ต้องตรวจ contrast ทุกครั้งเมื่อนำไปใช้กับ text

### 5.3 Brand Soft — `#EEE8F0`

ใช้สำหรับ:

- subtle selected background
- soft highlight
- low-emphasis brand area
- contextual grouping
- tag/chip ที่ไม่ควรเด่นเกินไป

### 5.4 Canvas / Warm White — `#F7F6F2`

เป็น background หลักสำหรับหน้าที่ต้องการ warm-neutral feeling

ใช้แทน pure white เมื่อเหมาะสมเพื่อให้ visual tone นุ่มขึ้น

### 5.5 Surface — `#FFFFFF`

ใช้สำหรับ:

- card
- modal
- elevated panel
- input area
- content surface ที่ต้องแยกออกจาก canvas

### 5.6 Text Primary — `#1C1C1A`

ใช้สำหรับ body text และ content หลัก

### 5.7 Text Secondary — `#686863`

ใช้สำหรับ:

- supporting text
- metadata
- secondary labels

ห้ามใช้กับ text เล็กมากถ้า contrast ไม่ผ่าน

### 5.8 Border Subtle — `#E6E4DE`

ใช้สำหรับ:

- divider
- input border
- card boundary
- table boundary

ควรใช้เท่าที่จำเป็น ไม่สร้าง “grid noise”

---

## 6. Semantic Color Policy

Brand color ไม่ควรใช้แทน semantic status ทุกอย่าง

ต้องแยก concept:

```text
Brand
≠ Success
≠ Warning
≠ Error
≠ Info
```

Semantic colors สำหรับ:

- success
- warning
- error
- information
- destructive action

ต้องเลือกโดย:

- accessibility
- contrast
- consistency
- dark/light surface compatibility

ก่อน production UI ต้องกำหนด semantic token แยกจาก brand token

ตัวอย่าง naming:

```text
--color-success-*
--color-warning-*
--color-error-*
--color-info-*
```

ไม่ hard-code สี status ตาม component

---

## 7. Token Philosophy

UI ต้องใช้ semantic tokens มากกว่า raw values กระจายทั่ว code

ตัวอย่าง:

```text
--color-bg-canvas
--color-bg-surface
--color-text-primary
--color-text-secondary
--color-border-subtle
--color-brand-ink
--color-brand-soft
--color-action-primary
--color-focus-ring
```

เป้าหมายคือ:

- theme ปรับได้
- accessibility audit ง่าย
- component consistent
- ลด arbitrary values

---

## 8. Visual Direction

UI direction เดิมของ VRA คือ:

- white / warm-neutral dominant
- minimal visual noise
- premium but friendly
- generous whitespace
- strong hierarchy
- restrained borders and shadows
- product photography เป็นพื้นที่หลักที่นำสีเข้ามา
- interaction ชัดและ responsive

VRA ไม่ใช่ dark-first luxury brand

VRA ไม่ควรใช้ black/charcoal เป็น canvas หลักทุกหน้าโดยอัตโนมัติ

---

## 9. Layout Principles

### 9.1 Generous Whitespace

Whitespace เป็นเครื่องมือสร้าง hierarchy

ไม่ใช่พื้นที่ “ว่างเสียเปล่า”

ใช้ spacing เพื่อ:

- แยก group
- ลด cognitive load
- สร้าง rhythm
- ให้ content สำคัญหายใจ

### 9.2 Strong Hierarchy

หนึ่ง viewport ควรตอบได้ว่า:

- ตอนนี้อยู่ที่ไหน
- task หลักคืออะไร
- action หลักคืออะไร
- information รองคืออะไร

### 9.3 Avoid Dashboard Density by Default

อย่านำ admin/dashboard density ไปใช้กับ customer-facing UI โดยอัตโนมัติ

Customer experience ควรมี progressive disclosure

### 9.4 Content Width

Long-form text ไม่ควรยืดเต็ม viewport จนอ่านยาก

กำหนด readable measure ตาม typography และ breakpoint

---

## 10. Grid and Spacing

ใช้ spacing scale ที่มี rhythm สม่ำเสมอ

ตัวอย่าง direction:

```text
4
8
12
16
24
32
40
48
64
80
96
```

ไม่จำเป็นต้องใช้ทุกค่า

แต่ไม่ควรมี arbitrary spacing จำนวนมาก เช่น:

```text
13px
19px
27px
43px
```

โดยไม่มีเหตุผล

Exact scale ต้อง validate ใน frontend design system

---

## 11. Shape Language

VRA ควรใช้ shape ที่:

- soft enough to feel human
- structured enough to feel reliable
- ไม่กลมจนดู playful เกินไป
- ไม่เหลี่ยมแข็งจนดู enterprise/industrial

Corner radius ต้องเป็น token

ตัวอย่าง categories:

```text
radius-sm
radius-md
radius-lg
radius-pill
```

`radius-pill` ใช้เฉพาะ component ที่ semantic เหมาะ เช่น chip หรือ compact status

---

## 12. Borders

Border เป็นเครื่องมือแยก surface แบบ quiet

ใช้:

- thin
- low contrast
- only when needed

หลีกเลี่ยง card ทุกอย่างมี border

บาง layout ใช้ spacing หรือ background contrast แทน border ได้

---

## 13. Shadows

Shadow ต้อง restrained

ใช้เพื่อแสดง:

- elevation
- overlay
- floating control
- modal

ไม่ควรใช้ heavy shadow กับทุก card

---

## 14. Typography Direction

Typography ต้องให้ความรู้สึก:

```text
clear
modern
calm
human
precise
```

ยังไม่ lock font family ในเอกสารนี้จนกว่าจะผ่าน frontend validation

Font selection ต้องพิจารณา:

- Latin
- Thai
- numeral readability
- UI density
- variable font support
- loading cost
- licensing
- fallback
- accessibility

---

## 15. Thai Typography

VRA ต้องรองรับภาษาไทยเป็น first-class UI language

ต้องตรวจ:

- line-height
- vowel/diacritic clipping
- font fallback
- bold weight
- truncation
- wrapping
- button label height
- input rendering
- numeral alignment

ห้ามเลือก font เพราะ Latin สวย แต่ Thai rendering แย่

---

## 16. Type Hierarchy

UI ควรมี hierarchy ชัด เช่น:

```text
Display
Heading 1
Heading 2
Heading 3
Body
Body Small
Label
Caption
```

Exact pixel/rem values ต้องมาจาก frontend design validation

ไม่ควรกำหนด arbitrary font-size ต่อ component

---

## 17. Text Weight

ใช้ weight เพื่อ hierarchy ไม่ใช่ใช้ bold ทุกอย่าง

แนวทาง:

- heading: medium/semibold
- body: regular
- label: medium เมื่อจำเป็น
- emphasis: semibold
- avoid ultra-bold ใน dense UI

---

## 18. Numbers

ตัวเลขสำคัญ เช่น:

- price
- quantity
- order total
- inventory
- payout
- ETA

ต้องอ่านง่าย

ควรพิจารณา:

- tabular numerals ใน table/finance
- locale formatting
- decimal alignment
- currency placement

---

## 19. Iconography

Icon ต้อง:

- simple
- consistent stroke/fill language
- recognizable
- not decorative-only
- accompanied by label เมื่อ ambiguity สูง

ห้ามใช้ icon-only control หากความหมายไม่ชัด

---

## 20. Photography

Product photography มีบทบาทนำสีและความรู้สึกมาสู่ UI

หลัก:

- product ต้องเด่นกว่า decorative effect
- background ไม่ควรแข่งกับสินค้า
- crop ต้องรักษาสาระ
- image ratio consistent ตาม component
- lazy loading/performance
- alt text เมื่อ semantic

---

## 21. Illustration

Illustration ใช้ได้เมื่อช่วย:

- onboarding
- empty state
- explanation
- brand storytelling

Style ต้องเข้ากับ warm/modern/human direction

ไม่ควรใช้ illustration เพื่อปกปิด information architecture ที่ไม่ชัด

---

## 22. UI Density

Customer UI:

```text
lower density
more breathing room
fewer simultaneous decisions
```

Staff/operations UI:

```text
higher information density
but still structured and readable
```

แบรนด์เดียวกันสามารถมี density ต่างกันตามงาน

---

## 23. Navigation

Navigation ต้อง:

- predictable
- stable
- clear current state
- keyboard accessible
- responsive

Mobile navigation ไม่ควรเป็น desktop nav ที่ย่อขนาดเฉย ๆ

---

## 24. Primary Action

แต่ละ view ควรมี primary action ชัดถ้ามี task หลัก

หลีกเลี่ยง CTA 3–4 ปุ่มที่ visual weight เท่ากัน

---

## 25. Destructive Action

Destructive action ต้อง:

- semantic color
- clear wording
- confirmation ตาม risk
- ไม่ใช้ brand plum เป็นตัวแทน destructive semantics
- undo เมื่อเหมาะสม

---

## 26. Forms

Form ต้อง:

- label ชัด
- error ใกล้ field
- required state ชัด
- ไม่พึ่ง placeholder เป็น label
- keyboard/focus ดี
- support autofill เมื่อเหมาะสม
- preserve user input เมื่อ recoverable error

---

## 27. Validation

Validation message ต้องบอก:

- อะไรผิด
- แก้อย่างไร

ไม่ใช้:

```text
Invalid input
```

ทุกกรณี

แต่ security-sensitive flow ต้องไม่เปิดเผยข้อมูลเกินจำเป็น

---

## 28. Loading States

ต้องแยก:

- initial loading
- inline mutation
- background refresh
- long-running operation

ห้ามใช้ full-page spinner กับทุก action

---

## 29. Skeletons

Skeleton ใช้เมื่อ:

- layout รู้ล่วงหน้า
- loading duration เหมาะสม
-ช่วยลด perceived shift

ไม่ใช้ skeleton เพียงเพราะเป็น trend

---

## 30. Empty States

Empty state ต้องตอบ:

- ทำไมยังไม่มีข้อมูล
- user ทำอะไรต่อได้
- เป็น expected state หรือ error

---

## 31. Error States

UI error ต้องไม่ assume:

```text
timeout = operation failed
```

สำหรับ critical mutation อาจต้องแสดง:

```text
กำลังตรวจสอบผลลัพธ์
```

หรือ equivalent ที่ตรงกับ `UNKNOWN` semantics

---

## 32. Success Feedback

Success feedback ต้องสัมพันธ์กับความจริง authoritative

ห้ามแสดง:

```text
ชำระเงินสำเร็จ
```

ก่อน backend ยืนยัน outcome ที่เหมาะสม

---

## 33. Status Language

Status label ต้องใช้คำที่ผู้ใช้เข้าใจ

Internal state machine อาจมีชื่อ technical มากกว่า UI

mapping ต้อง explicit

---

## 34. Tables

Table ใช้สำหรับข้อมูลที่ต้อง compare rows/columns

ต้องมี:

- readable alignment
- responsive strategy
- keyboard navigation ตามความเหมาะสม
- empty/loading state
- sort indication
- numeric alignment

Mobile อาจใช้ alternate layout แทน squeeze table

---

## 35. Cards

Card ไม่ใช่ default container ของทุกอย่าง

ใช้เมื่อ grouping มี semantic

หลีกเลี่ยง “card inside card inside card”

---

## 36. Modal

Modal ใช้เมื่อ:

- attention ต้องถูกจำกัดชั่วคราว
- task bounded
- context เดิมควรคงอยู่

ไม่ใช้ modal กับ workflow ยาว

---

## 37. Toast

Toast เหมาะกับ transient feedback ที่ไม่ critical

Critical error หรือ action-required state ต้องมี persistent UI

---

## 38. Motion Philosophy

Motion ของ VRA ต้อง:

```text
quiet
responsive
physical
precise
purposeful
```

ไม่ใช้ motion เพื่อ decoration อย่างเดียว

Motion ต้องช่วยอธิบาย:

- state change
- hierarchy
- spatial relationship
- gesture response
- continuity

---

## 39. Motion Technology

เมื่อ frontend ใช้ React:

```text
Motion for React
```

เป็น primary rich-interaction system สำหรับ motion ที่มี:

- state meaning
- layout meaning
- gesture meaning
- enter/exit choreography

Simple effect ใช้ CSS transition ได้

ไม่จำเป็นต้องใช้ animation library ทุก interaction

---

## 40. Reduced Motion

`prefers-reduced-motion` support เป็น mandatory

เมื่อ user ลด motion:

- remove non-essential movement
- shorten/disable large transforms
- preserve state feedback
- avoid parallax
- avoid excessive scaling
- avoid scroll-driven movement ที่ทำให้ไม่สบาย

Accessibility มาก่อน visual spectacle

---

## 41. Motion Duration

Duration ต้องสัมพันธ์กับ distance และ intent

Direction:

```text
micro feedback
→ fast

panel/dialog
→ moderate

large layout transition
→ slightly longer
```

อย่าใช้ duration เดียวทุก animation

Exact values ต้องเป็น token หลัง frontend validation

---

## 42. Easing

Easing ต้องรู้สึก natural

หลีกเลี่ยง:

- cartoon bounce ใน core commerce flow
- overshoot ที่ทำให้ control ดูไม่ stable
- exaggerated spring

Spring ใช้เมื่อช่วย physical continuity

---

## 43. Scroll Motion

Scroll-driven motion ใช้อย่าง restrained

ห้าม:

- hijack scroll
-ทำให้ content อ่านไม่ได้
-สร้าง motion ที่ user ปิดไม่ได้
-ใช้กับ critical task flow โดยไม่จำเป็น

---

## 44. Focus Motion

Focus state ต้องเห็นได้ทันที

ห้าม animate จน focus indicator delayed

---

## 45. Responsive Design

Responsive ไม่ใช่เพียงลดขนาด

ต้อง reconsider:

- hierarchy
- navigation
- layout
- table
- action placement
- density
- touch target

---

## 46. Breakpoint Philosophy

Breakpoint ต้องมาจาก content/layout behavior ไม่ใช่ device model อย่างเดียว

Exact breakpoints จะกำหนดใน frontend implementation

---

## 47. Touch Targets

Interactive control ต้องมี target size ที่ใช้งานได้จริง

Icon ขนาดเล็กสามารถมี hit area ใหญ่กว่า visual icon

---

## 48. Keyboard Accessibility

ทุก core flow ต้องใช้ keyboard ได้

ต้องมี:

- visible focus
- logical tab order
- no keyboard trap
- accessible dialog focus management
- skip/navigation mechanism เมื่อเหมาะสม

---

## 49. Screen Reader Semantics

ใช้ semantic HTML ก่อน ARIA

ARIA ใช้เมื่อ native semantic ไม่พอ

Component custom ต้องมี:

- role
- name
- state
- keyboard behavior

---

## 50. Color Contrast

ทุก text/control/state ต้องตรวจ contrast

ห้ามใช้ brand palette แบบ literal หาก combination ไม่ผ่าน accessibility

สามารถ derive accessible semantic shade เพิ่มได้ แต่ต้องเก็บ relationship กับ canonical palette

---

## 51. Color Is Not the Only Signal

Error/success/selection ต้องมี:

- text
- icon
- shape
- state label

ร่วมกับ color เมื่อเหมาะสม

---

## 52. Focus Ring

Focus ring ต้อง:

- visible
- contrast สูงพอ
-ไม่ถูก `outline: none` ปิดทิ้งโดยไม่มี replacement

Focus token ต้องเป็น semantic token

---

## 53. Language and Tone

เสียงของ VRA ต้อง:

```text
clear
calm
direct
respectful
human
```

หลีกเลี่ยง:

- corporate jargon
- cute language ใน financial/security context
- blame
- ambiguous promise
- exaggerated marketing claim

---

## 54. Microcopy

Microcopy ต้องบอกสิ่งที่เกิดขึ้นจริง

ตัวอย่างที่ดี:

```text
กำลังตรวจสอบการชำระเงิน
```

ดีกว่า:

```text
เกิดข้อผิดพลาด กรุณาลองใหม่
```

เมื่อ backend ยังอยู่ใน `UNKNOWN`

---

## 55. Confirmation Copy

Confirmation ต้องอธิบาย action ที่กำลังจะเกิด โดยเฉพาะ:

- cancellation
- refund
- payout change
- account closure
- destructive inventory correction

---

## 56. Error Copy

Error copy ต้อง:

- บอก user action ถ้ามี
- ไม่ expose internals
- ไม่กล่าวโทษ user
- ไม่ promise retry ถ้า retry อาจสร้าง side effect ซ้ำ

---

## 57. Financial Copy

Financial wording ต้อง precise

แยก:

- pending
- authorized
- paid
- refunded
- refund requested
- payout processing
- payout unknown

ห้ามใช้คำ “สำเร็จ” ครอบ state ที่ยังไม่ final

---

## 58. Time and Date Copy

แสดง timezone/context เมื่อเวลามี business meaning

เช่น:

```text
24 ก.ย. 2026, 14:30 ICT
```

ตาม locale/product context

---

## 59. Number and Currency Formatting

ใช้ locale-aware formatting

ห้าม string concat แบบ arbitrary:

```text
"฿" + amount
```

ถ้าต้องรองรับหลาย currency

---

## 60. Brand Family Naming

Family naming pattern:

```text
VRA + Capability
```

ตัวอย่าง canonical:

```text
VRA Seller
VRA Fulfill
VRA Drive
VRA Food
```

ไม่สร้าง sub-brand ใหม่โดยไม่มี product reason

---

## 61. Family Visual Relationship

Sub-product ต้องรู้สึกเป็น VRA เดียวกันผ่าน:

- typography
- spacing
- base palette
- interaction language
- motion
- icon style
- content voice

แต่สามารถมี contextual accent ได้ในอนาคตหากไม่ทำให้ identity แตก

---

## 62. Logo Usage

จนกว่าจะมี final logo asset/spec:

- ใช้คำว่า `VRA` อย่างเรียบง่าย
- ไม่ stretch/skew
- ไม่ใส่ effect โดยไม่จำเป็น
- clear space ต้องเพียงพอ
- contrast ต้องชัด

Logo construction detailed spec ยัง deferred จนมี approved asset

---

## 63. Favicon / App Icon

ต้อง derive จาก approved VRA identity

ห้ามใช้ random lettermark คนละรูปกับ brand

---

## 64. Dark Mode

Dark mode ยังไม่ถือว่า mandatory product baseline

ถ้าเพิ่มภายหลัง:

- ต้องใช้ semantic token
- re-evaluate contrast
-ไม่ invert สีแบบอัตโนมัติทุกอย่าง
- product images ต้องยังดูถูกต้อง
- shadows/borders ต้องปรับ

---

## 65. Theme Architecture

Design system ควรใช้ semantic tokens เพื่อรองรับ future theme โดยไม่ rewrite component

แต่ไม่สร้าง multi-theme engine ก่อนมี requirement

---

## 66. Design Tokens

Token categories ที่ควรมี:

```text
color
space
radius
type
shadow
motion
z-index
breakpoint
```

Token naming ต้อง semantic เมื่อค่ามี role

---

## 67. Z-Index

อย่าใช้ arbitrary:

```text
9999
999999
```

ควรมี layering model:

```text
base
sticky
dropdown
overlay
modal
toast
```

---

## 68. Component States

ทุก interactive component ต้องพิจารณา:

- default
- hover
- focus
- active
- selected
- disabled
- loading
- error
- success เมื่อ relevant

---

## 69. Disabled State

Disabled control ต้องยังอ่านได้ แต่เห็นว่าใช้งานไม่ได้

ห้ามใช้ opacity ต่ำจน text contrast พัง

ถ้า action unavailable เพราะ business rule ควรบอกเหตุผลเมื่อช่วย user ได้

---

## 70. Button Hierarchy

อย่างน้อย:

```text
Primary
Secondary
Tertiary / Ghost
Destructive
```

ไม่ควรมี variants จำนวนมากจน hierarchy แตก

---

## 71. Link vs Button

ใช้:

```text
link = navigate
button = perform action
```

ไม่ใช้ `<div onClick>` เป็น control

---

## 72. Input Components

Input ต้องใช้ browser/platform behavior ให้มากที่สุด

Custom component ต้องไม่ลด:

- autofill
- keyboard
- screen reader
- mobile input type
- validation semantics

---

## 73. Select / Combobox

ใช้ native select เมื่อ requirement เรียบง่าย

Custom combobox เมื่อจำเป็นต้อง:

- search
- virtualize
- rich options

แต่ต้องทำ keyboard/ARIA ให้ครบ

---

## 74. Search UX

Search ต้องแยก:

- query entry
- suggestions
- results
- filters
- no result
- stale/derived status เมื่อ relevant

ไม่ควรทำให้ user เข้าใจว่า search result คือ inventory guarantee

---

## 75. Commerce UX

Product availability ที่แสดงใน UI เป็น presentation ของ authoritative/derived state ตาม design

Checkout ต้อง revalidate critical facts เช่น:

- price
- offer
- inventory
- seller eligibility
- delivery eligibility

UI ห้าม promise guarantee จาก stale catalog view

---

## 76. Checkout UX

Checkout ต้อง:

- minimize unnecessary steps
- preserve entered data เมื่อ recoverable
- show authoritative totals
- distinguish loading vs processing
- prevent accidental duplicate submit
- handle ambiguous payment state honestly

---

## 77. Payment UX

หลัง submit payment:

- disable accidental duplicate submit
- show processing state
- backend idempotency ยังเป็น authority
- timeout ไม่เท่ากับ failed
- reconciliation state ต้องมี copy ที่เหมาะสม

---

## 78. Order UX

Order status ที่ user เห็นต้อง map จาก business state อย่าง deliberate

ไม่แสดง internal technical state ทุกตัวตรง ๆ ถ้า user ไม่เข้าใจ

---

## 79. Tracking UX

Customer-facing tracking ควรให้ข้อมูลที่มีความหมาย เช่น:

```text
กำลังเตรียมสินค้า
รับสินค้าแล้ว
กำลังนำส่ง
ส่งมอบแล้ว
```

Raw GPS/telemetry ไม่ควรถูก expose โดยไม่มี interpretation

---

## 80. Seller UX

Seller UI สามารถมี density สูงกว่า customer UI แต่ต้องรักษา:

- hierarchy
- scannability
- safe destructive action
- clear status
- role-aware controls

---

## 81. Staff UX

Staff UI ต้อง optimize สำหรับ correctness และ speed พร้อมกัน

Critical operation ต้องแสดง:

- target
- actor scope
- reason
- consequence
- approval requirement

UI ห้ามทำให้ privilege ดู trivial

---

## 82. Financial Operations UX

Finance UI ต้องเน้น:

- exact amount
- currency
- status
- operation history
- UNKNOWN/reconciliation
- audit
- approval

ไม่ใช้ celebratory animation กับ sensitive financial operations

---

## 83. Empty / Zero Financial State

แยก:

```text
0
unknown
not applicable
not loaded
```

ห้ามแสดงทุกอย่างเป็น `0`

---

## 84. Data Visualization

Chart ใช้เมื่อช่วยให้เห็น pattern

ต้อง:

- accessible
- labeled
- not color-only
- meaningful scale
- avoid misleading axis
- provide table/detail เมื่อข้อมูล critical

---

## 85. Motion and Data

Real-time value change อาจใช้ subtle transition เพื่อช่วย eye tracking

ห้าม animate ตัวเลขหนักจน operator อ่านยาก

---

## 86. Performance as UX

Design ต้องคำนึง:

- image weight
- font loading
- hydration
- animation cost
- layout shift
- interaction latency

Visual quality ที่ทำให้ page ช้ามากไม่ถือว่า premium

---

## 87. Loading Priority

Critical content/action ต้องมาก่อน decorative content

Hero media ไม่ควร block checkout/account flows

---

## 88. Responsive Images

ใช้:

- correct dimensions
- modern formats
- lazy loading
- responsive source

ตาม framework support

---

## 89. Motion Performance

Prefer compositor-friendly properties เช่น transform/opacity เมื่อเหมาะสม

หลีกเลี่ยง layout thrashing

---

## 90. Accessibility Acceptance

ก่อน component ถือว่าพร้อม ต้องตรวจตามความเหมาะสม:

- keyboard
- focus
- contrast
- screen-reader semantics
- reduced motion
- zoom
- responsive
- touch target

---

## 91. Design Review

Design review ต้องถาม:

1. hierarchy ชัดไหม
2. task หลักคืออะไร
3. action หลักเด่นพอไหม
4. accessibility ผ่านไหม
5. mobile ใช้ได้จริงไหม
6. error/loading/empty state ครบไหม
7. brand personality ยังตรงไหม
8. motion มี purpose ไหม
9. product truth ถูกนำเสนอถูกไหม
10. visual complexity จำเป็นไหม

---

## 92. No Apple Clone

VRA สามารถยืม design principles เช่น:

- hierarchy
- whitespace
- clarity
- restraint
- responsive interaction
- polish

แต่ห้าม copy Apple UI, layout, component appearance หรือ interaction choreography โดยตรง

เป้าหมายคือ:

```text
Apple-like quality
≠ Apple clone
```

---

## 93. Reference Usage

Design reference ใช้เพื่อเข้าใจ:

- composition
- hierarchy
- rhythm
- motion principles
- quality bar

ไม่ใช่ template ให้ clone

---

## 94. Visual Consistency

เมื่อ component ใหม่ต้องใช้:

- existing tokens
- existing component primitives
- existing motion rules

ก่อนสร้าง variant ใหม่

---

## 95. Component Library

Production frontend ควรมี shared component layer ที่:

- accessible
- tokenized
- composable
- testable

แต่ไม่ควรสร้าง giant design system ก่อนมี real product screens

---

## 96. Design System Growth

เริ่มจาก component ที่เกิดจริง

เมื่อ pattern ซ้ำจึง extract

หลีกเลี่ยง speculative component จำนวนมาก

---

## 97. Semantic HTML

Design implementation ต้องเลือก semantic HTML ก่อน styling convenience

ตัวอย่าง:

```text
button
nav
main
section
form
label
table
dialog
```

---

## 98. Copy and Localization

Text ไม่ควร hard-code กระจายถ้าระบบต้องรองรับหลายภาษา

Localization architecture ต้องรองรับ:

- plural
- date
- number
- currency
- Thai/English layout

---

## 99. English Technical Terms in Thai UI

Canonical documentation สามารถใช้ English technical term เพื่อรักษาความหมาย

แต่ customer-facing copy ควรใช้ภาษาที่ user เข้าใจ ไม่ expose architecture jargon เช่น:

```text
transaction rollback
outbox
idempotency
```

---

## 100. Brand Governance

Material brand change ได้แก่:

- primary palette
- personality
- typography system
- motion philosophy
- family naming
- accessibility standard
- core voice

ต้องผ่าน deliberate review

Implementation convenience ไม่เพียงพอสำหรับเปลี่ยน brand identity

---

## 101. Frontend Relationship

POC-00 ไม่มี frontend และไม่มี frontend dependency ตาม baseline เดิม

ดังนั้น brand document ไม่ถือว่า POC-00 ได้ validate visual implementation แล้ว

Frontend implementation จะเกิดใน production foundation/roadmap ตาม phase ที่กำหนด

---

## 102. Canonical Brand Rule

หลักสุดท้ายของ VRA คือ:

> **UI ของ VRA ต้องรู้สึกสงบ ชัด ใช้งานง่าย เชื่อถือได้ และมีคุณภาพสูง โดยไม่สร้างความซับซ้อนทางสายตาหรือ motion เพียงเพื่อความโดดเด่น**

และ:

```text
Warm
+ Clear
+ Effortless
+ Reliable
+ Modern
+ Human
+ Premium without exclusivity
```

คือ personality baseline ที่การออกแบบ VRA ทุก surface ต้องรักษา
