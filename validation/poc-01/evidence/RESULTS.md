# POC-01 Evidence Results

Status: EVIDENCE COMPLETE — pending final exit review
Date: 2026-09-24
Branch: `poc/01-transactional-core`
Evidence HEAD: `d07a89e`

## 1. Objective

POC-01 validates the production-oriented transactional foundation for VRA
using Inventory Reservation as the representative write path.

The POC is intended to prove:

- explicit domain/application/adapter boundaries,
- PostgreSQL as the authoritative transactional store,
- a least-privilege migration/runtime identity split,
- an explicit migration step outside runtime startup,
- a critical conditional inventory write,
- JDBC + JPA participation in one Spring transaction,
- rollback on downstream persistence failure,
- database-enforced invariants,
- a stable HTTP boundary,
- server-controlled request correlation,
- liveness/readiness semantics,
- runtime behavior for missing or incompatible schema,
- and a real built-artifact smoke path.

This document records what was actually demonstrated. It does not extend the
scope of POC-01.

## 2. Validated stack

The evidence run used:

| Component | Version / decision |
| --- | --- |
| Java | OpenJDK 21.0.12.1 |
| Gradle | 9.7.1 |
| Spring Boot | 4.1.1 |
| PostgreSQL | 17.11 |
| Flyway | 12.4.0 |
| PostgreSQL JDBC | 42.7.13 |
| Hibernate ORM | 7.4.5.Final |
| ArchUnit | 1.4.1 |
| Primary JVM language | Java |

The local evidence host was macOS on ARM64.

## 3. Build and module boundary

POC-01 uses two Gradle modules:

- `backend/runtime`
- `backend/migration`

The split is a real security/operations boundary:

- `migration` owns Flyway execution,
- `runtime` does not contain Flyway,
- runtime startup does not perform migrations,
- runtime connects with the restricted runtime identity.

The final dependency check confirmed that runtime contains neither Flyway nor
jOOQ.

## 4. Database security model

The validated identity model is:

- `vra_owner` — `NOLOGIN`, owns schema objects.
- `vra_migrator` — login identity with controlled ability to `SET ROLE
  vra_owner`.
- `vra_runtime` — login identity with only the DML privileges required by the
  runtime path.

The migration integration proof demonstrated:

- migrated objects are owned by `vra_owner`,
- `vra_migrator` without the controlled role switch cannot create objects as
  the owner,
- `vra_runtime` can perform its required DML,
- `vra_runtime` cannot create, alter, or drop schema objects,
- `vra_runtime` cannot access Flyway history,
- `vra_runtime` cannot run the migration,
- checksum drift is detected,
- rerunning a valid migration reports the schema as up to date.

The manual runtime smoke independently reconfirmed that `vra_runtime` cannot
create a table.

## 5. Inventory reservation model and invariants

Representative model:

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

Validated invariants include:

```text
on_hand >= 0
reserved >= 0
reserved <= on_hand
quantity > 0
available = on_hand - reserved
available >= 0
only reservable stock status may be reserved
reservation inventory key matches the target balance
```

`AVAILABLE` is reservable. `QUARANTINED` is not.

Database constraints were explicitly proven for:

- negative `on_hand`,
- negative `reserved`,
- `reserved > on_hand`,
- invalid stock status,
- non-positive reservation quantity.

## 6. Critical transactional write

The critical inventory mutation is a conditional PostgreSQL update rather than
a read-then-unconditional-write flow:

```sql
UPDATE vra.inventory_balance
SET reserved = reserved + :quantity,
    version = version + 1
WHERE sku_id = :skuId
  AND owner_id = :ownerId
  AND location_id = :locationId
  AND stock_status = :stockStatus
  AND stock_status = 'AVAILABLE'
  AND version = :expectedVersion
  AND on_hand - reserved >= :quantity
RETURNING version
```

The application transaction is:

```text
BEGIN
→ conditional JDBC inventory update
→ JPA reservation persistence
→ COMMIT
```

The PostgreSQL integration suite proved:

- successful reservation updates the balance and persists the reservation in
  one transaction,
- insufficient stock performs no mutation,
- quarantined inventory cannot be reserved,
- owner/location dimensions remain isolated,
- optimistic version conflict performs no mutation,
- a reservation insert failure rolls back the preceding JDBC inventory update.

This validates that `JdbcClient + JPA + @Transactional + PostgreSQL` share the
required transaction semantics for the POC-01 path.

## 7. Architecture boundary evidence

ArchUnit currently enforces:

- domain code does not depend on application, adapters, platform, Spring, JPA,
  or JDBC,
- application code does not depend on adapters,
- JPA entities remain in the persistence adapter,
- web adapters do not depend on persistence adapters,
- REST controllers remain in `..adapter.in.web..`.

The POC intentionally uses package-level architecture enforcement inside the
runtime module rather than creating additional Gradle modules for each layer.

## 8. HTTP contract

Validated endpoint:

```http
POST /api/v1/inventory/reservations
```

Validated behavior includes:

- `201` for successful reservation,
- `400` for malformed or invalid requests,
- `404` for unknown inventory,
- `409` for non-reservable inventory,
- `409` for insufficient stock,
- `409` for optimistic version conflict,
- stable public error codes/messages,
- persistence entities are not used as the API response model,
- request IDs are generated by the server,
- a client-supplied `X-Request-Id` is not trusted,
- response header/body request IDs are consistent.

Representative error contract:

```json
{
  "code": "INVENTORY_INSUFFICIENT_STOCK",
  "message": "Insufficient stock",
  "request_id": "server-generated-uuid"
}
```

## 9. Real HTTP-to-PostgreSQL proof

A real Spring Boot server was started on a random port and exercised through a
real Java HTTP client against PostgreSQL 17.11.

The proof demonstrated:

```text
real HTTP
→ embedded Tomcat
→ RequestIdFilter
→ REST controller
→ application service
→ Spring transaction
→ JdbcClient + JPA
→ PostgreSQL
```

The success path persisted both the inventory change and reservation row.
The insufficient-stock path returned the stable `409` error and left database
state unchanged.

## 10. Liveness and readiness

Actuator health exposure is intentionally limited to health.

Validated semantics:

```text
liveness
→ livenessState

readiness
→ readinessState + db
```

The database-outage integration test proved:

```text
DB available:
  liveness  = UP / HTTP 200
  readiness = UP / HTTP 200

DB unavailable after startup:
  liveness  = UP / HTTP 200
  readiness = DOWN / HTTP 503
```

The application therefore does not convert a database outage into an
application restart loop, while still becoming unavailable for new traffic.

## 11. Manual built-artifact runtime smoke

`validation/poc-01/scripts/manual-runtime-smoke.sh` validated the
operator-visible path using the built migration and runtime JARs:

```text
start dedicated PostgreSQL
→ bootstrap owner / migrator / runtime identities
→ build artifacts
→ execute migration artifact explicitly
→ start runtime using vra_runtime credentials
→ readiness UP
→ liveness UP
→ seed inventory as owner
→ real HTTP reservation returns 201
→ database state is correct
→ client request ID is not trusted
→ insufficient stock returns stable 409
→ failed reservation does not mutate inventory
→ runtime DDL is denied
→ clean runtime shutdown
→ clean Compose shutdown
```

The dedicated POC-01 PostgreSQL volume is intentionally preserved.

During the final smoke the database was already at schema version 1, therefore
the explicit migration step correctly reported zero new migrations and a
schema that was already up to date.

## 12. Missing-schema startup behavior

A temporary empty PostgreSQL database was created with the runtime identity
able to connect but without the `vra` schema.

Observed behavior:

```text
PostgreSQL reachable
→ Hibernate schema validation runs
→ required table is missing
→ ApplicationContext startup fails
→ runtime process exits
```

The failure explicitly identified the missing
`vra.inventory_reservation` table.

After the failed startup attempt:

```text
vra schema                 absent
inventory_balance          absent
inventory_reservation      absent
flyway_schema_history      absent
```

Therefore runtime did not self-migrate or create schema objects.

## 13. Incompatible-schema startup behavior

A temporary database was deliberately created with an incompatible mapping:

```text
vra.inventory_reservation.quantity = TEXT
expected runtime mapping           = BIGINT
```

Observed behavior:

```text
runtime exit code = 1
Hibernate schema validation fails explicitly
wrong column type:
  found     TEXT / VARCHAR
  expected  BIGINT
```

After the failure:

```text
existing incompatible table        preserved
inventory_balance                  absent
flyway_schema_history              absent
quantity column type               still TEXT
```

Runtime did not repair, alter, or migrate the incompatible schema.

## 14. Final automated evidence

Final confirmed automated results:

| Suite | Tests | Skipped | Failures | Errors |
| --- | ---: | ---: | ---: | ---: |
| `:runtime:test` | 20 | 0 | 0 | 0 |
| `:runtime:integrationTest` | 16 | 0 | 0 | 0 |
| `:migration:integrationTest` | 1 | 0 | 0 | 0 |
| **Total** | **37** | **0** | **0** | **0** |

The migration security test belongs to `:migration:integrationTest`, not
`:migration:test`.

The final targeted migration run confirmed:

```text
MigrationSecurityIntegrationTest
provesMigrationOwnershipAndRuntimeLeastPrivilege()
PASS
```

## 15. Evidence commits

Relevant POC-01 implementation checkpoints:

```text
aaf3ca3 build: establish POC-01 backend foundation
3ce4df7 feat: establish secure database migration foundation
956aaae test: add POC-01 local database smoke harness
9a60022 feat: add transactional inventory reservation core
1aec9b4 test: enforce inventory architecture and database constraints
bd277cc feat: expose inventory reservation HTTP API
27743ba test: verify reservation HTTP flow against PostgreSQL
7b0d945 feat: add database-aware readiness and liveness probes
d07a89e test: add POC-01 manual runtime smoke
```

Planning checkpoints immediately preceding implementation:

```text
e530ba5 docs: freeze POC-01 execution plan
e631457 docs: approve POC-01 implementation plan
```

## 16. What POC-01 proves

POC-01 provides evidence that the selected foundation can support a
production-oriented transactional core with:

- explicit modular boundaries,
- PostgreSQL transactional authority,
- least-privilege runtime/migration separation,
- explicit external migration execution,
- safe conditional inventory mutation,
- mixed JDBC/JPA transactional participation,
- rollback correctness,
- database invariant enforcement,
- stable HTTP behavior,
- real HTTP-to-database execution,
- operational health semantics,
- fail-fast schema validation,
- and real built-artifact execution.

## 17. Deliberately not proven by POC-01

POC-01 does not claim to prove:

- high-contention reservation behavior,
- throughput or latency targets,
- retry behavior under contention,
- reservation release/expiry/allocation lifecycle,
- transactional outbox delivery,
- cross-domain workflows,
- authentication/authorization,
- API idempotency-key semantics,
- production reverse-proxy/TLS deployment,
- production backup/restore,
- high availability,
- horizontal scaling,
- multi-region behavior,
- or specialized derived/data planes.

Those concerns require their own later evidence and must not be inferred from
POC-01.

## 18. Exit state

Evidence collection for POC-01 is complete.

The remaining step before closing the POC is an independent exit review against
the frozen `validation/poc-01/SHARED_SPEC.md` and the approved implementation
plan. Any gap found by that review must be resolved or explicitly documented
before POC-01 is declared closed.
