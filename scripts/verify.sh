#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step6-backend.log"

cleanup() {
  if [ -n "${BACKEND_PID}" ]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 6 VERIFICATION"
echo "========================================================================"

echo
echo "===== COMPOSE ====="

docker compose config >/dev/null

echo "[PASS] COMPOSE_CONFIGURATION"

echo
echo "===== POSTGRESQL ====="

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
  npx eslint . --max-warnings=0
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

rm -f "${BACKEND_LOG}"

(
  cd apps/backend
  mvn spring-boot:run
) > "${BACKEND_LOG}" 2>&1 &

BACKEND_PID=$!

for attempt in $(seq 1 45); do

  if curl \
      --fail \
      --silent \
      "http://localhost:8087/actuator/health" \
      >/tmp/terrascope-step6-health.json \
      2>/dev/null; then

    echo "[PASS] BACKEND_RUNTIME_READY"
    break
  fi

  if ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    cat "${BACKEND_LOG}"
    exit 1
  fi

  if [ "${attempt}" -eq 45 ]; then
    cat "${BACKEND_LOG}"
    exit 1
  fi

  sleep 1
done

echo
echo "===== STATUS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step6-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step6-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["status"] == "operational"
assert payload["version"] == "0.6.0"

print("[PASS] STATUS_ENDPOINT")
PY

echo
echo "===== STEP 5 REGRESSION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/simulations" \
  >/tmp/terrascope-step6-simulations.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/watchlist" \
  >/tmp/terrascope-step6-watchlist.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/layers" \
  >/tmp/terrascope-step6-layers.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step6-simulations.json",
    encoding="utf-8",
) as handle:
    simulations = json.load(handle)

with open(
    "/tmp/terrascope-step6-watchlist.json",
    encoding="utf-8",
) as handle:
    watchlist = json.load(handle)

with open(
    "/tmp/terrascope-step6-layers.json",
    encoding="utf-8",
) as handle:
    layers = json.load(handle)

assert isinstance(
    simulations["runs"],
    list,
)

assert isinstance(
    watchlist["entries"],
    list,
)

assert len(layers) == 4

print(
    "[PASS] STEP5_RUNTIME_REGRESSION"
)
PY

echo
echo "===== FLYWAY V6 ====="

for VERSION in 1 2 3 4 5 6; do

  COUNT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version = '${VERSION}'
          AND success = TRUE;
        " \
      | tr -d '[:space:]'
  )"

  echo "FLYWAY_V${VERSION}_COUNT=${COUNT}"

  if [ "${COUNT}" != "1" ]; then
    exit 1
  fi
done

echo "[PASS] FLYWAY_V6_RUNTIME"

echo
echo "===== CLEAN VERIFICATION REGIONS ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM regional_analysis_runs
      WHERE region_name LIKE 'Verification %';
    " \
  >/dev/null

echo "[PASS] REGIONAL_PRE_CLEAN"

echo
echo "===== REGIONAL HISTORY CONTRACT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/regional-intelligence/history" \
  >/tmp/terrascope-step6-history-before.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step6-history-before.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert isinstance(
    payload["runs"],
    list,
)

assert "totalCount" in payload
assert "criticalCount" in payload
assert "highCount" in payload
assert "averageActivityIndex" in payload

print(
    "[PASS] REGIONAL_HISTORY_CONTRACT"
)
PY

echo
echo "===== RUN REGIONAL ANALYSIS ====="

cat > \
  /tmp/terrascope-step6-analysis.json \
<<'JSON'
{
  "regionName": "Verification regional analysis",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "radiusKm": 500.0,
  "points": [
    {
      "id": "verification-eq",
      "hazardType": "earthquakes",
      "title": "Verification earthquake",
      "latitude": 36.95,
      "longitude": 10.20,
      "intensity": 6.5,
      "observedAt": "2026-09-17T12:00:00Z"
    },
    {
      "id": "verification-fire",
      "hazardType": "wildfires",
      "title": "Verification wildfire",
      "latitude": 37.10,
      "longitude": 9.90,
      "intensity": null,
      "observedAt": "2026-09-17T11:00:00Z"
    },
    {
      "id": "verification-storm",
      "hazardType": "severe-storms",
      "title": "Verification storm",
      "latitude": 36.50,
      "longitude": 10.40,
      "intensity": 80.0,
      "observedAt": "2026-09-17T10:00:00Z"
    },
    {
      "id": "verification-far-volcano",
      "hazardType": "volcanoes",
      "title": "Far verification volcano",
      "latitude": 46.0,
      "longitude": 10.0,
      "intensity": null,
      "observedAt": "2026-09-17T09:00:00Z"
    }
  ]
}
JSON

ANALYZE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step6-analysis-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step6-analysis.json \
    "http://localhost:8087/api/v1/regional-intelligence/analyze"
)"

echo "REGIONAL_ANALYZE_HTTP=${ANALYZE_HTTP}"

if [ "${ANALYZE_HTTP}" != "200" ]; then
  cat /tmp/terrascope-step6-analysis-response.json
  exit 1
fi

ANALYSIS_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step6-analysis-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["regionName"]
    == "Verification regional analysis"
)

assert payload["sourcePointCount"] == 4
assert payload["matchedEventCount"] == 3

breakdown = payload["breakdown"]

assert breakdown["earthquakeCount"] == 1
assert breakdown["volcanoCount"] == 0
assert breakdown["wildfireCount"] == 1
assert breakdown["severeStormCount"] == 1

assert payload["nearestEvent"] is not None

assert (
    payload["nearestEvent"]["id"]
    == "verification-eq"
)

assert (
    0
    <= payload["activityIndex"]
    <= 100
)

assert payload["activityBand"] in {
    "QUIET",
    "GUARDED",
    "ELEVATED",
    "HIGH",
    "CRITICAL",
}

assert payload["averageDistanceKm"] > 0

print(payload["id"])
PY
)"

echo "ANALYSIS_ID=${ANALYSIS_ID}"

echo "[PASS] REGIONAL_ANALYSIS"

echo
echo "===== REGIONAL PERSISTENCE ====="

ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM regional_analysis_runs
      WHERE id = '${ANALYSIS_ID}';
      " \
    | tr -d '[:space:]'
)"

echo "REGIONAL_ROW_COUNT=${ROW_COUNT}"

if [ "${ROW_COUNT}" != "1" ]; then
  exit 1
fi

echo "[PASS] REGIONAL_PERSISTENCE"

echo
echo "===== REGIONAL HISTORY AFTER ANALYSIS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/regional-intelligence/history" \
  >/tmp/terrascope-step6-history-after.json

python3 - "${ANALYSIS_ID}" <<'PY'
import json
import sys

analysis_id = sys.argv[1]

with open(
    "/tmp/terrascope-step6-history-after.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

matching = [
    item
    for item in payload["runs"]
    if item["id"] == analysis_id
]

assert len(matching) == 1
assert payload["totalCount"] >= 1

print(
    "[PASS] REGIONAL_HISTORY"
)
PY

echo
echo "===== SPATIAL FILTERING CONTRACT ====="

cat > \
  /tmp/terrascope-step6-tight.json \
<<'JSON'
{
  "regionName": "Verification tight radius",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "radiusKm": 10.0,
  "points": [
    {
      "id": "near",
      "hazardType": "earthquakes",
      "title": "Near point",
      "latitude": 36.81,
      "longitude": 10.18,
      "intensity": 5.0,
      "observedAt": "2026-09-17T12:00:00Z"
    },
    {
      "id": "outside",
      "hazardType": "wildfires",
      "title": "Outside point",
      "latitude": 37.50,
      "longitude": 10.80,
      "intensity": null,
      "observedAt": "2026-09-17T12:00:00Z"
    }
  ]
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step6-tight.json \
  "http://localhost:8087/api/v1/regional-intelligence/analyze" \
  >/tmp/terrascope-step6-tight-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step6-tight-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["sourcePointCount"] == 2
assert payload["matchedEventCount"] == 1

assert (
    payload["breakdown"]["earthquakeCount"]
    == 1
)

assert (
    payload["breakdown"]["wildfireCount"]
    == 0
)

assert payload["nearestEvent"]["id"] == "near"

print(
    "[PASS] REGIONAL_RADIUS_FILTERING"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

COLUMN_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'regional_analysis_runs'
        AND column_name IN (
          'id',
          'region_name',
          'center_latitude',
          'center_longitude',
          'radius_km',
          'source_point_count',
          'matched_event_count',
          'earthquake_count',
          'volcano_count',
          'wildfire_count',
          'severe_storm_count',
          'nearest_event_id',
          'nearest_event_type',
          'nearest_event_title',
          'nearest_event_distance_km',
          'average_distance_km',
          'activity_index',
          'activity_band',
          'analyzed_at'
        );
      " \
    | tr -d '[:space:]'
)"

echo "REGIONAL_COLUMN_COUNT=${COLUMN_COUNT}"

if [ "${COLUMN_COUNT}" != "19" ]; then
  exit 1
fi

echo "[PASS] REGIONAL_DATABASE_CONTRACT"

echo
echo "===== CLEAN VERIFICATION DATA ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM regional_analysis_runs
      WHERE region_name LIKE 'Verification %';
    " \
  >/dev/null

VERIFY_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM regional_analysis_runs
      WHERE region_name LIKE 'Verification %';
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_REGIONAL_COUNT=${VERIFY_COUNT}"

if [ "${VERIFY_COUNT}" != "0" ]; then
  exit 1
fi

echo "[PASS] REGIONAL_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 6 VERIFICATION PASSED"
echo "========================================================================"
