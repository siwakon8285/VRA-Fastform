# POC-01 Local Validation Runbook

This directory contains the POC-01 branch specification and local validation harness.

## Local PostgreSQL

Dedicated local database:

```text
PostgreSQL image: postgres:17.11
database:         vra_poc01
host bind:        127.0.0.1:55433
```

This database is separate from POC-00, DEV, demo and production.

The root `docker-compose.yml` belongs to historical POC-00 validation and is not reused.

## Local Secrets

The local harness reads ignored files:

```text
.local/poc-01/postgres_admin_password
.local/poc-01/migrator_password
.local/poc-01/runtime_password
```

Secret values must never be committed, printed into evidence, screenshots, logs or documentation.

## Database Roles

```text
vra_owner
- NOLOGIN
- owns schema/application objects

vra_migrator
- LOGIN
- NOINHERIT
- can SET ROLE vra_owner
- not SUPERUSER / CREATEDB / CREATEROLE

vra_runtime
- LOGIN
- not a member of vra_owner
- application DML only
- no schema DDL
```

Bootstrap:

```text
validation/poc-01/db/bootstrap.sql
```

The bootstrap is local/validation infrastructure. Production provisioning remains an operations concern and must not reuse local passwords.

## Gate 2B Manual Database Smoke

Run from the repository root:

```bash
bash validation/poc-01/scripts/manual-db-smoke.sh
```

The script intentionally requires a fresh database with no existing `vra.flyway_schema_history`.

It verifies:

```text
dedicated PostgreSQL becomes healthy
owner/migrator/runtime bootstrap
role attributes and migrator membership
first migration executes exactly once
migration rerun executes zero migrations
application objects are owned by vra_owner
runtime permitted DML succeeds
runtime CREATE TABLE is denied
runtime Flyway history access is denied
runtime credentials cannot run migration
clean Compose shutdown
```

The script does not delete the named PostgreSQL volume.

If a prior migrated POC-01 volume exists, the script stops rather than deleting it automatically. Destructive reset must be an explicit operator action.

## Start / Stop Without Smoke

Start:

```bash
docker compose -f validation/poc-01/compose.yaml up -d postgres
```

Status:

```bash
docker compose -f validation/poc-01/compose.yaml ps
```

Stop while preserving the volume:

```bash
docker compose -f validation/poc-01/compose.yaml down
```

Do not use `down -v` unless the dedicated POC-01 database is intentionally being destroyed.
