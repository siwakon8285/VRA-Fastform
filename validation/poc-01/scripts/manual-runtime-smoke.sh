#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE="$ROOT/validation/poc-01/compose.yaml"
BOOTSTRAP="$ROOT/validation/poc-01/db/bootstrap.sql"
SECRET_DIR="$ROOT/.local/poc-01"

ADMIN_PASSWORD_FILE="$SECRET_DIR/postgres_admin_password"
MIGRATOR_PASSWORD_FILE="$SECRET_DIR/migrator_password"
RUNTIME_PASSWORD_FILE="$SECRET_DIR/runtime_password"
MIGRATION_JAR="$ROOT/backend/migration/build/libs/migration-0.1.0-SNAPSHOT.jar"
RUNTIME_JAR="$ROOT/backend/runtime/build/libs/runtime-0.1.0-SNAPSHOT.jar"
RUNTIME_PORT=18080
RUNTIME_LOG="$SECRET_DIR/runtime-smoke.log"

runtime_pid=""

cleanup() {
  status=$?

  if [ -n "$runtime_pid" ] && kill -0 "$runtime_pid" 2>/dev/null; then
    kill "$runtime_pid" 2>/dev/null || true
    wait "$runtime_pid" 2>/dev/null || true
  fi

  docker compose -f "$COMPOSE" down >/dev/null 2>&1 || true

  if [ "$status" -ne 0 ]; then
    echo
    echo "Gate 6A smoke failed."
    echo "Runtime log preserved at: $RUNTIME_LOG"
  fi

  exit "$status"
}

trap cleanup EXIT INT TERM

for file in \
  "$ADMIN_PASSWORD_FILE" \
  "$MIGRATOR_PASSWORD_FILE" \
  "$RUNTIME_PASSWORD_FILE"
do
  test -s "$file" || {
    echo "ABORT: missing or blank local secret file: $file"
    exit 1
  }
done

if command -v lsof >/dev/null 2>&1 \
   && lsof -nP -iTCP:${RUNTIME_PORT} -sTCP:LISTEN >/dev/null 2>&1; then
  echo "ABORT: TCP port ${RUNTIME_PORT} is already in use."
  exit 1
fi

admin_password="$(cat "$ADMIN_PASSWORD_FILE")"
migrator_password="$(cat "$MIGRATOR_PASSWORD_FILE")"
runtime_password="$(cat "$RUNTIME_PASSWORD_FILE")"

echo "== START DEDICATED POSTGRESQL =="
docker compose -f "$COMPOSE" up -d postgres

container_id="$(docker compose -f "$COMPOSE" ps -q postgres)"
test -n "$container_id" || { echo "ABORT: PostgreSQL container missing"; exit 1; }

healthy=""
for _ in $(seq 1 30); do
  state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
  if [ "$state" = "healthy" ]; then
    healthy="yes"
    break
  fi
  if [ "$state" = "unhealthy" ]; then
    echo "ABORT: PostgreSQL became unhealthy."
    exit 1
  fi
  sleep 2
done

test "$healthy" = "yes" || { echo "ABORT: PostgreSQL did not become healthy"; exit 1; }
echo "PostgreSQL is healthy."

echo
echo "== BOOTSTRAP IDENTITIES / SCHEMA =="
docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" \
  postgres \
  psql -h 127.0.0.1 -U postgres -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -v migrator_password="$migrator_password" \
  -v runtime_password="$runtime_password" \
  < "$BOOTSTRAP" >/dev/null

echo "Bootstrap ready."

echo
echo "== BUILD ARTIFACTS =="
"$ROOT/backend/gradlew" -p "$ROOT/backend" --no-daemon \
  :migration:bootJar :runtime:bootJar

test -f "$MIGRATION_JAR" || { echo "ABORT: migration jar missing"; exit 1; }
test -f "$RUNTIME_JAR" || { echo "ABORT: runtime jar missing"; exit 1; }

echo
echo "== EXPLICIT MIGRATION STEP =="
VRA_MIGRATION_DB_URL="jdbc:postgresql://127.0.0.1:55433/vra_poc01" \
VRA_MIGRATION_DB_USERNAME="vra_migrator" \
VRA_MIGRATION_DB_PASSWORD="$migrator_password" \
java -jar "$MIGRATION_JAR"

echo
echo "== START RUNTIME AS vra_runtime =="
: > "$RUNTIME_LOG"

VRA_RUNTIME_DB_URL="jdbc:postgresql://127.0.0.1:55433/vra_poc01" \
VRA_RUNTIME_DB_USERNAME="vra_runtime" \
VRA_RUNTIME_DB_PASSWORD="$runtime_password" \
SERVER_PORT="$RUNTIME_PORT" \
java -jar "$RUNTIME_JAR" >"$RUNTIME_LOG" 2>&1 &
runtime_pid=$!

ready=""
for _ in $(seq 1 60); do
  if ! kill -0 "$runtime_pid" 2>/dev/null; then
    echo "ABORT: runtime exited before readiness."
    tail -80 "$RUNTIME_LOG" || true
    exit 1
  fi

  code="$(curl -sS -o "$SECRET_DIR/readiness.json" -w '%{http_code}' --max-time 3 \
    "http://127.0.0.1:${RUNTIME_PORT}/actuator/health/readiness" 2>/dev/null || true)"

  if [ "$code" = "200" ] && python3 - "$SECRET_DIR/readiness.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    body = json.load(f)
raise SystemExit(0 if body.get("status") == "UP" else 1)
PY
  then
    ready="yes"
    break
  fi

  sleep 0.5
done

test "$ready" = "yes" || { echo "ABORT: runtime did not become ready"; tail -80 "$RUNTIME_LOG" || true; exit 1; }
echo "PASS: readiness is UP."

echo
echo "== LIVENESS =="
live_code="$(curl -sS -o "$SECRET_DIR/liveness.json" -w '%{http_code}' --max-time 3 \
  "http://127.0.0.1:${RUNTIME_PORT}/actuator/health/liveness")"

test "$live_code" = "200" || { echo "ABORT: liveness HTTP $live_code"; exit 1; }
python3 - "$SECRET_DIR/liveness.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    body = json.load(f)
if body.get("status") != "UP":
    raise SystemExit("ABORT: liveness is not UP")
PY

echo "PASS: liveness is UP."

echo
echo "== SEED INVENTORY AS OWNER =="
read -r sku_id owner_id location_id <<EOF_IDS
$(python3 - <<'PY'
import uuid
print(uuid.uuid4(), uuid.uuid4(), uuid.uuid4())
PY
)
EOF_IDS

docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" \
  postgres \
  psql -h 127.0.0.1 -U postgres -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -v sku_id="$sku_id" \
  -v owner_id="$owner_id" \
  -v location_id="$location_id" \
  >/dev/null <<'SQL'
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
  :'sku_id',
  :'owner_id',
  :'location_id',
  'AVAILABLE',
  10,
  0,
  0
);
SQL

echo "Inventory seed created."

echo
echo "== REAL HTTP RESERVATION SUCCESS =="
cat > "$SECRET_DIR/runtime-smoke-request.json" <<EOF_JSON
{
  "skuId": "$sku_id",
  "ownerId": "$owner_id",
  "locationId": "$location_id",
  "stockStatus": "AVAILABLE",
  "quantity": 3,
  "expectedVersion": 0
}
EOF_JSON

success_code="$(curl -sS \
  -D "$SECRET_DIR/runtime-smoke-success.headers" \
  -o "$SECRET_DIR/runtime-smoke-success.json" \
  -w '%{http_code}' --max-time 5 \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: client-controlled-value' \
  --data-binary "@$SECRET_DIR/runtime-smoke-request.json" \
  "http://127.0.0.1:${RUNTIME_PORT}/api/v1/inventory/reservations")"

test "$success_code" = "201" || {
  echo "ABORT: reservation success path returned HTTP $success_code"
  cat "$SECRET_DIR/runtime-smoke-success.json"
  exit 1
}

reservation_id="$(python3 - "$SECRET_DIR/runtime-smoke-success.json" "$SECRET_DIR/runtime-smoke-success.headers" <<'PY'
import json, sys, uuid
body_path, headers_path = sys.argv[1:3]
with open(body_path, encoding="utf-8") as f:
    body = json.load(f)
reservation_id = body["reservation_id"]
uuid.UUID(reservation_id)
if body["inventory_version"] != 1:
    raise SystemExit("ABORT: expected inventory_version=1")
body_request_id = body["request_id"]
uuid.UUID(body_request_id)
header_request_id = None
with open(headers_path, encoding="iso-8859-1") as f:
    for line in f:
        if line.lower().startswith("x-request-id:"):
            header_request_id = line.split(":", 1)[1].strip()
            break
if header_request_id is None:
    raise SystemExit("ABORT: X-Request-Id response header missing")
uuid.UUID(header_request_id)
if header_request_id == "client-controlled-value":
    raise SystemExit("ABORT: client X-Request-Id was trusted")
if header_request_id != body_request_id:
    raise SystemExit("ABORT: request_id body/header mismatch")
print(reservation_id)
PY
)"

echo "PASS: HTTP 201 and server-controlled request ID verified."

echo
echo "== VERIFY DATABASE STATE =="
db_row="$(docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" postgres \
  psql -h 127.0.0.1 -U postgres -d vra_poc01 -At -F '|' \
  -v ON_ERROR_STOP=1 \
  -v reservation_id="$reservation_id" \
  -v sku_id="$sku_id" \
  -v owner_id="$owner_id" \
  -v location_id="$location_id" <<'SQL'
SELECT
  b.on_hand,
  b.reserved,
  b.version,
  r.quantity
FROM vra.inventory_balance b
JOIN vra.inventory_reservation r
  ON r.sku_id = b.sku_id
 AND r.owner_id = b.owner_id
 AND r.location_id = b.location_id
 AND r.stock_status = b.stock_status
WHERE b.sku_id = :'sku_id'
  AND b.owner_id = :'owner_id'
  AND b.location_id = :'location_id'
  AND b.stock_status = 'AVAILABLE'
  AND r.reservation_id = :'reservation_id';
SQL
)"

test "$db_row" = "10|3|1|3" || { echo "ABORT: unexpected DB state: $db_row"; exit 1; }
echo "PASS: inventory and reservation persisted correctly."

echo
echo "== REAL HTTP INSUFFICIENT STOCK =="
cat > "$SECRET_DIR/runtime-smoke-insufficient.json" <<EOF_JSON
{
  "skuId": "$sku_id",
  "ownerId": "$owner_id",
  "locationId": "$location_id",
  "stockStatus": "AVAILABLE",
  "quantity": 100,
  "expectedVersion": 1
}
EOF_JSON

failure_code="$(curl -sS \
  -o "$SECRET_DIR/runtime-smoke-insufficient-response.json" \
  -w '%{http_code}' --max-time 5 \
  -H 'Content-Type: application/json' \
  --data-binary "@$SECRET_DIR/runtime-smoke-insufficient.json" \
  "http://127.0.0.1:${RUNTIME_PORT}/api/v1/inventory/reservations")"

test "$failure_code" = "409" || { echo "ABORT: insufficient stock returned HTTP $failure_code"; exit 1; }
python3 - "$SECRET_DIR/runtime-smoke-insufficient-response.json" <<'PY'
import json, sys, uuid
with open(sys.argv[1], encoding="utf-8") as f:
    body = json.load(f)
if body.get("code") != "INVENTORY_INSUFFICIENT_STOCK":
    raise SystemExit("ABORT: wrong insufficient-stock error code")
if body.get("message") != "Insufficient stock":
    raise SystemExit("ABORT: wrong insufficient-stock message")
uuid.UUID(body["request_id"])
PY

unchanged="$(docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$admin_password" postgres \
  psql -h 127.0.0.1 -U postgres -d vra_poc01 -At -F '|' \
  -v ON_ERROR_STOP=1 \
  -v sku_id="$sku_id" \
  -v owner_id="$owner_id" \
  -v location_id="$location_id" <<'SQL'
SELECT
  on_hand,
  reserved,
  version
FROM vra.inventory_balance
WHERE sku_id = :'sku_id'
  AND owner_id = :'owner_id'
  AND location_id = :'location_id'
  AND stock_status = 'AVAILABLE';
SQL
)"

test "$unchanged" = "10|3|1" || { echo "ABORT: failure path mutated inventory: $unchanged"; exit 1; }
echo "PASS: HTTP 409 stable error and no inventory mutation."

echo
echo "== RUNTIME DDL DENIAL =="
if docker compose -f "$COMPOSE" exec -T \
  -e PGPASSWORD="$runtime_password" postgres \
  psql -h 127.0.0.1 -U vra_runtime -d vra_poc01 \
  -v ON_ERROR_STOP=1 \
  -c "CREATE TABLE vra.runtime_smoke_forbidden (id BIGINT)" \
  >/dev/null 2>&1
then
  echo "FAIL: runtime unexpectedly created a table."
  exit 1
else
  echo "PASS: runtime CREATE TABLE denied."
fi

echo
echo "== CLEAN STOP =="
kill "$runtime_pid"
wait "$runtime_pid" 2>/dev/null || true
runtime_pid=""

docker compose -f "$COMPOSE" down
trap - EXIT INT TERM

unset admin_password migrator_password runtime_password

echo
echo "GATE 6A MANUAL RUNTIME SMOKE PASSED"
echo "Runtime stopped cleanly."
echo "Container/network stopped cleanly."
echo "Dedicated POC-01 PostgreSQL volume was preserved."
