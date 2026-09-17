#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

cleanup() {
  if [ -n "${BACKEND_PID}" ]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 3 VERIFICATION"
echo "========================================================================"

echo
echo "===== COMPOSE CONFIGURATION ====="

docker compose config >/dev/null

echo "[PASS] COMPOSE_CONFIGURATION"

echo
echo "===== START POSTGRESQL ====="

docker compose up -d postgres

for attempt in $(seq 1 30); do
  if docker compose exec -T postgres \
      pg_isready \
      -U terrascope \
      -d terrascope \
      >/dev/null 2>&1; then

    echo "[PASS] POSTGRESQL_READY"
    break
  fi

  if [ "${attempt}" -eq 30 ]; then
    echo "[FAIL] POSTGRESQL_NOT_READY"
    exit 1
  fi

  sleep 1
done

echo
echo "===== BACKEND TESTS ====="

(
  cd apps/backend
  mvn test
)

echo "[PASS] BACKEND_TESTS"

echo
echo "===== BACKEND PACKAGE ====="

(
  cd apps/backend
  mvn -DskipTests package
)

echo "[PASS] BACKEND_PACKAGE"

echo
echo "===== FRONTEND LINT ====="

(
  cd apps/web
  npm run lint
)

echo "[PASS] FRONTEND_LINT"

echo
echo "===== FRONTEND TYPECHECK ====="

(
  cd apps/web
  npm run typecheck
)

echo "[PASS] FRONTEND_TYPECHECK"

echo
echo "===== FRONTEND BUILD ====="

(
  cd apps/web
  npm run build
)

echo "[PASS] FRONTEND_BUILD"

echo
echo "===== BACKEND RUNTIME ====="

BACKEND_LOG="/tmp/terrascope-step3-backend.log"

rm -f "${BACKEND_LOG}"

(
  cd apps/backend
  mvn spring-boot:run
) >"${BACKEND_LOG}" 2>&1 &

BACKEND_PID=$!

for attempt in $(seq 1 45); do
  if curl \
      --fail \
      --silent \
      "http://localhost:8087/actuator/health" \
      >/tmp/terrascope-step3-health.json \
      2>/dev/null; then

    echo "[PASS] BACKEND_RUNTIME_READY"
    break
  fi

  if ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    echo "[FAIL] BACKEND_STOPPED_DURING_STARTUP"
    cat "${BACKEND_LOG}"
    exit 1
  fi

  if [ "${attempt}" -eq 45 ]; then
    echo "[FAIL] BACKEND_STARTUP_TIMEOUT"
    cat "${BACKEND_LOG}"
    exit 1
  fi

  sleep 1
done

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step3-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step3-status.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["status"] == "operational"
assert payload["version"] == "0.3.0"

sources = {
    item["id"]: item["status"]
    for item in payload["dataSources"]
}

assert sources["usgs-earthquakes"] == "configured"
assert sources["nasa-eonet-volcanoes"] == "configured"
assert sources["nasa-eonet-wildfires"] == "configured"
assert sources["nasa-eonet-storms"] == "configured"

print("[PASS] STATUS_ENDPOINT")
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/earthquakes?period=day&minMagnitude=4.5&maxDepth=1000&limit=5&sort=magnitude" \
  >/tmp/terrascope-step3-earthquakes.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step3-earthquakes.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["source"] in {
    "USGS",
    "DEMO_FALLBACK",
}

assert payload["returnedEventCount"] <= 5
assert isinstance(payload["events"], list)

print(
    "[PASS] EARTHQUAKE_ENDPOINT "
    f"SOURCE={payload['source']} "
    f"RETURNED={payload['returnedEventCount']}"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/volcanoes?status=open&days=3650&limit=5" \
  >/tmp/terrascope-step3-volcanoes.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step3-volcanoes.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["source"] in {
    "NASA_EONET",
    "DEMO_FALLBACK",
}

assert payload["returnedEventCount"] <= 5
assert isinstance(payload["events"], list)

print(
    "[PASS] VOLCANO_ENDPOINT "
    f"SOURCE={payload['source']} "
    f"RETURNED={payload['returnedEventCount']}"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/events?type=wildfires&status=open&days=90&limit=5" \
  >/tmp/terrascope-step3-wildfires.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step3-wildfires.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["hazardType"] == "wildfires"

assert payload["source"] in {
    "NASA_EONET",
    "DEMO_FALLBACK",
}

assert payload["returnedEventCount"] <= 5
assert isinstance(payload["events"], list)

for event in payload["events"]:
    assert event["hazardType"] == "wildfires"
    assert isinstance(event["latitude"], (int, float))
    assert isinstance(event["longitude"], (int, float))

print(
    "[PASS] WILDFIRE_ENDPOINT "
    f"SOURCE={payload['source']} "
    f"RETURNED={payload['returnedEventCount']}"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/events?type=severe-storms&status=open&days=90&limit=5" \
  >/tmp/terrascope-step3-storms.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step3-storms.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["hazardType"] == "severe-storms"

assert payload["source"] in {
    "NASA_EONET",
    "DEMO_FALLBACK",
}

assert payload["returnedEventCount"] <= 5
assert isinstance(payload["events"], list)

for event in payload["events"]:
    assert event["hazardType"] == "severe-storms"
    assert isinstance(event["latitude"], (int, float))
    assert isinstance(event["longitude"], (int, float))

print(
    "[PASS] STORM_ENDPOINT "
    f"SOURCE={payload['source']} "
    f"RETURNED={payload['returnedEventCount']}"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/layers" \
  >/tmp/terrascope-step3-layers.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step3-layers.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert isinstance(payload, list)
assert len(payload) == 4

by_id = {
    item["id"]: item
    for item in payload
}

expected = {
    "earthquakes",
    "volcanoes",
    "wildfires",
    "severe-storms",
}

assert set(by_id.keys()) == expected

for item in payload:
    assert item["available"] is True
    assert item["status"] == "available"

print(
    "[PASS] HAZARD_LAYERS_ENDPOINT "
    "COUNT=4 AVAILABLE=4"
)
PY

echo
echo "===== DATABASE MIGRATIONS ====="

DATABASE_TABLE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.tables
      WHERE table_schema = 'public'
        AND table_name IN (
          'saved_disaster_events',
          'simulation_scenarios',
          'simulation_runs',
          'flyway_schema_history'
        );
      " \
    | tr -d '[:space:]'
)"

echo "DATABASE_TABLE_COUNT=${DATABASE_TABLE_COUNT}"

if [ "${DATABASE_TABLE_COUNT}" != "4" ]; then
  echo "[FAIL] DATABASE_TABLE_COUNT"
  exit 1
fi

for version in 1 2 3; do
  COUNT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version = '${version}'
          AND success = TRUE;
        " \
      | tr -d '[:space:]'
  )"

  echo "FLYWAY_V${version}_COUNT=${COUNT}"

  if [ "${COUNT}" != "1" ]; then
    echo "[FAIL] FLYWAY_V${version}"
    exit 1
  fi
done

V3_COLUMN_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'saved_disaster_events'
        AND column_name IN (
          'last_observed_at',
          'observation_count',
          'magnitude_value',
          'magnitude_unit'
        );
      " \
    | tr -d '[:space:]'
)"

echo "V3_COLUMN_COUNT=${V3_COLUMN_COUNT}"

if [ "${V3_COLUMN_COUNT}" != "4" ]; then
  echo "[FAIL] V3_COLUMNS"
  exit 1
fi

echo "[PASS] DATABASE_MIGRATIONS"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 3 VERIFICATION PASSED"
echo "========================================================================"
