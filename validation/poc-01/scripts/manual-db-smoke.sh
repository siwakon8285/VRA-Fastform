#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE="$ROOT/validation/poc-01/compose.yaml"
BOOTSTRAP="$ROOT/validation/poc-01/db/bootstrap.sql"
SECRET_DIR="$ROOT/.local/poc-01"
MIGRATION_JAR="$ROOT/backend/migration/build/libs/migration-0.1.0-SNAPSHOT.jar"

ADMIN_PASSWORD_FILE="$SECRET_DIR/postgres_admin_password"
MIGRATOR_PASSWORD_FILE="$SECRET_DIR/migrator_password"
RUNTIME_PASSWORD_FILE="$SECRET_DIR/runtime_password"

for file in \
  "$ADMIN_PASSWORD_FILE" \
  "$MIGRATOR_PASSWORD_FILE" \
  "$RUNTIME_PASSWORD_FILE"
do
  test -s "$file" || {
    echo "ABORT: required local secret file is missing or blank: $file"
    exit 1
  }
done

if command -v lsof >/dev/null 2>&1; then
  if lsof -nP -iTCP:55433 -sTCP:LISTEN >/dev/null 2>&1; then
    existing="$(docker compose -f "$COMPOSE" ps -q postgres 2>/dev/null || true)"
    if [ -z "$existing" ]; then
      echo "ABORT: TCP port 55433 is already in use by another process."
      exit 1
    fi
  fi
fi

echo "== START DEDICATED POSTGRESQL 17.11 =="

docker compose -f "$COMPOSE" up -d postgres

container_id="$(docker compose -f "$COMPOSE" ps -q postgres)"

test -n "$container_id" || {
  echo "ABORT: PostgreSQL container was not created."
  exit 1
}

healthy=""

for _ in $(seq 1 30); do
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"

  if [ "$status" = "healthy" ]; then
    healthy="yes"
    break
  fi

  if [ "$status" = "unhealthy" ]; then
    echo "ABORT: PostgreSQL healthcheck became unhealthy."
    exit 1
  fi

  sleep 2
done

test "$healthy" = "yes" || {
  echo "ABORT: PostgreSQL did not become healthy in time."
  exit 1
}

echo "PostgreSQL is healthy."

admin_password="$(cat "$ADMIN_PASSWORD_FILE")"
migrator_password="$(cat "$MIGRATOR_PASSWORD_FILE")"
runtime_password="$(cat "$RUNTIME_PASSWORD_FILE")"

echo
echo "== BOOTSTRAP OWNER / MIGRATOR / RUNTIME =="

docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U postgres \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -v migrator_password="$migrator_password" \
  -v runtime_password="$runtime_password" \
  < "$BOOTSTRAP"

echo
echo "== ROLE ATTRIBUTES =="

docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U postgres \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "
    SELECT
        rolname,
        rolcanlogin,
        rolsuper,
        rolcreatedb,
        rolcreaterole,
        rolbypassrls
    FROM pg_roles
    WHERE rolname IN ('vra_owner', 'vra_migrator', 'vra_runtime')
    ORDER BY rolname;
  "

echo
echo "== MIGRATOR MEMBERSHIP =="

docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U postgres \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "
    SELECT
        granted_role.rolname AS granted_role,
        member_role.rolname AS member,
        membership.admin_option,
        membership.inherit_option,
        membership.set_option
    FROM pg_auth_members membership
    JOIN pg_roles granted_role
      ON granted_role.oid = membership.roleid
    JOIN pg_roles member_role
      ON member_role.oid = membership.member
    WHERE granted_role.rolname = 'vra_owner'
      AND member_role.rolname = 'vra_migrator';
  "

echo
echo "== FRESH MIGRATION PRECONDITION =="

history_exists="$(
  docker compose -f "$COMPOSE" exec -T \
    -e PGPASSWORD="$admin_password" \
    postgres \
    psql \
    -h 127.0.0.1 \
    -U postgres \
    -d vra_poc01 \
    -At \
    -v ON_ERROR_STOP=1 \
    -c "SELECT to_regclass('vra.flyway_schema_history') IS NOT NULL;"
)"

if [ "$history_exists" != "f" ]; then
  echo "ABORT: vra.flyway_schema_history already exists."
  echo "This smoke requires a fresh, unmigrated dedicated POC-01 database."
  echo "No volume is deleted automatically."
  exit 1
fi

echo "PASS: database is fresh and unmigrated."

echo
echo "== BUILD MIGRATION ARTIFACT =="

"$ROOT/backend/gradlew" \
  -p "$ROOT/backend" \
  --no-daemon \
  :migration:bootJar

test -f "$MIGRATION_JAR" || {
  echo "ABORT: migration artifact not found: $MIGRATION_JAR"
  exit 1
}

run_migration() {
  VRA_MIGRATION_DB_URL="jdbc:postgresql://127.0.0.1:55433/vra_poc01" \
  VRA_MIGRATION_DB_USERNAME="vra_migrator" \
  VRA_MIGRATION_DB_PASSWORD="$migrator_password" \
  java -jar "$MIGRATION_JAR"
}

echo
echo "== FIRST MIGRATION =="

first_result="$(run_migration)"
printf '%s\n' "$first_result"

printf '%s\n' "$first_result" \
  | grep -Fq "migrations executed: 1" \
  || {
    echo "ABORT: first migration did not report exactly 1 executed migration."
    exit 1
  }

echo
echo "== SECOND MIGRATION / RERUN =="

second_result="$(run_migration)"
printf '%s\n' "$second_result"

printf '%s\n' "$second_result" \
  | grep -Fq "migrations executed: 0" \
  || {
    echo "ABORT: migration rerun did not report 0 executed migrations."
    exit 1
  }

echo
echo "== APPLICATION TABLE OWNERS =="

docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U postgres \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "
    SELECT tablename, tableowner
    FROM pg_tables
    WHERE schemaname = 'vra'
    ORDER BY tablename;
  "

echo
echo "== RUNTIME ALLOWED DML =="

docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U postgres \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "
    SET ROLE vra_owner;

    INSERT INTO vra.inventory_balance (
        sku_id,
        owner_id,
        location_id,
        stock_status,
        on_hand,
        reserved,
        version
    )
    VALUES (
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000201',
        '00000000-0000-0000-0000-000000000301',
        'AVAILABLE',
        10,
        0,
        0
    );
  "

docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$runtime_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U vra_runtime \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "
    SELECT on_hand, reserved, version
    FROM vra.inventory_balance
    WHERE sku_id = '00000000-0000-0000-0000-000000000101'
      AND owner_id = '00000000-0000-0000-0000-000000000201'
      AND location_id = '00000000-0000-0000-0000-000000000301'
      AND stock_status = 'AVAILABLE';

    UPDATE vra.inventory_balance
    SET reserved = reserved + 1,
        version = version + 1
    WHERE sku_id = '00000000-0000-0000-0000-000000000101'
      AND owner_id = '00000000-0000-0000-0000-000000000201'
      AND location_id = '00000000-0000-0000-0000-000000000301'
      AND stock_status = 'AVAILABLE';

    INSERT INTO vra.inventory_reservation (
        reservation_id,
        sku_id,
        owner_id,
        location_id,
        stock_status,
        quantity,
        created_at
    )
    VALUES (
        '00000000-0000-0000-0000-000000000401',
        '00000000-0000-0000-0000-000000000101',
        '00000000-0000-0000-0000-000000000201',
        '00000000-0000-0000-0000-000000000301',
        'AVAILABLE',
        1,
        CURRENT_TIMESTAMP
    );
  "

echo
echo "PASS: runtime SELECT/UPDATE/INSERT succeeded where explicitly granted."

echo
echo "== RUNTIME DDL DENIAL =="

if docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$runtime_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U vra_runtime \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "CREATE TABLE vra.runtime_forbidden (id BIGINT)"
then
  echo "FAIL: runtime unexpectedly created a table."
  exit 1
else
  echo "PASS: runtime CREATE TABLE denied."
fi

echo
echo "== RUNTIME FLYWAY HISTORY DENIAL =="

if docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$runtime_password" \
  postgres \
  psql \
  -h 127.0.0.1 \
  -U vra_runtime \
  -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "SELECT * FROM vra.flyway_schema_history"
then
  echo "FAIL: runtime unexpectedly read Flyway history."
  exit 1
else
  echo "PASS: runtime Flyway history access denied."
fi

echo
echo "== RUNTIME MIGRATION DENIAL =="

if VRA_MIGRATION_DB_URL="jdbc:postgresql://127.0.0.1:55433/vra_poc01" \
   VRA_MIGRATION_DB_USERNAME="vra_runtime" \
   VRA_MIGRATION_DB_PASSWORD="$runtime_password" \
   java -jar "$MIGRATION_JAR" >/dev/null 2>&1
then
  echo "FAIL: runtime credentials unexpectedly executed migration process."
  exit 1
else
  echo "PASS: runtime credentials cannot execute migration process."
fi

echo
echo "== CLEAN STOP =="

docker compose -f "$COMPOSE" down

unset admin_password migrator_password runtime_password first_result second_result

echo
echo "GATE 2B MANUAL DATABASE SMOKE PASSED"
echo "Container/network stopped cleanly."
echo "Dedicated POC-01 database volume was preserved."
