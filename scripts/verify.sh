#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step4-backend.log"

cleanup() {
  if [ -n "${BACKEND_PID}" ]; then

    kill \
      "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true

    wait \
      "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true

  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 4 VERIFICATION"
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

    docker compose logs postgres

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

  npx eslint . \
    --max-warnings=0
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
      > /tmp/terrascope-step4-health.json \
      2>/dev/null; then

    echo "[PASS] BACKEND_RUNTIME_READY"
    break
  fi

  if ! kill -0 \
      "${BACKEND_PID}" \
      >/dev/null 2>&1; then

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

echo
echo "===== STATUS ENDPOINT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  > /tmp/terrascope-step4-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step4-status.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["status"] == "operational"
assert payload["version"] == "0.4.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

echo
echo "===== EXISTING HAZARD ENDPOINTS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/earthquakes?period=day&minMagnitude=4.5&maxDepth=1000&limit=3&sort=magnitude" \
  > /tmp/terrascope-step4-earthquakes.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/volcanoes?status=open&days=3650&limit=3" \
  > /tmp/terrascope-step4-volcanoes.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/events?type=wildfires&status=open&days=90&limit=3" \
  > /tmp/terrascope-step4-wildfires.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/events?type=severe-storms&status=open&days=90&limit=3" \
  > /tmp/terrascope-step4-storms.json

python3 - <<'PY'
import json

checks = (
    (
        "/tmp/terrascope-step4-earthquakes.json",
        "EARTHQUAKE",
        {
            "USGS",
            "DEMO_FALLBACK",
        },
    ),
    (
        "/tmp/terrascope-step4-volcanoes.json",
        "VOLCANO",
        {
            "NASA_EONET",
            "DEMO_FALLBACK",
        },
    ),
    (
        "/tmp/terrascope-step4-wildfires.json",
        "WILDFIRE",
        {
            "NASA_EONET",
            "DEMO_FALLBACK",
        },
    ),
    (
        "/tmp/terrascope-step4-storms.json",
        "STORM",
        {
            "NASA_EONET",
            "DEMO_FALLBACK",
        },
    ),
)

for path, label, allowed_sources in checks:

    with open(
        path,
        "r",
        encoding="utf-8",
    ) as handle:
        payload = json.load(handle)

    assert (
        payload["source"]
        in allowed_sources
    )

    assert isinstance(
        payload["events"],
        list,
    )

    print(
        f"[PASS] {label}_ENDPOINT "
        f"SOURCE={payload['source']} "
        f"RETURNED={payload['returnedEventCount']}"
    )
PY

echo
echo "===== HAZARD LAYERS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/layers" \
  > /tmp/terrascope-step4-layers.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step4-layers.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert len(payload) == 4

assert all(
    item["available"]
    for item in payload
)

print(
    "[PASS] HAZARD_LAYERS_ENDPOINT "
    "COUNT=4 AVAILABLE=4"
)
PY

echo
echo "===== FLYWAY V4 PRE-WATCHLIST AUTHORITY ====="

for VERSION in 1 2 3 4; do

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

    echo "[FAIL] FLYWAY_V${VERSION}"

    exit 1
  fi
done

WATCHLIST_TABLE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.tables
      WHERE table_schema = 'public'
        AND table_name = 'hazard_watchlist_entries';
      " \
    | tr -d '[:space:]'
)"

echo "WATCHLIST_TABLE_COUNT=${WATCHLIST_TABLE_COUNT}"

if [ "${WATCHLIST_TABLE_COUNT}" != "1" ]; then
  echo "[FAIL] WATCHLIST_TABLE"
  exit 1
fi

echo "[PASS] FLYWAY_V4_RUNTIME"

echo
echo "===== CLEAN OLD VERIFICATION ROWS ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM hazard_watchlist_entries
      WHERE source_event_id
            LIKE 'verification-%';
    " \
  >/dev/null

echo "[PASS] WATCHLIST_VERIFICATION_PRE_CLEAN"

echo
echo "===== WATCHLIST LIST CONTRACT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/watchlist" \
  > /tmp/terrascope-step4-watchlist-before.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step4-watchlist-before.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert isinstance(
    payload["entries"],
    list,
)

summary = payload["summary"]

for key in (
    "totalCount",
    "criticalCount",
    "highCount",
    "earthquakeCount",
    "volcanoCount",
    "wildfireCount",
    "severeStormCount",
    "latestSavedAt",
):
    assert key in summary

print(
    "[PASS] WATCHLIST_LIST_CONTRACT"
)
PY

echo
echo "===== SAVE WATCHLIST EVENT ====="

cat > \
  /tmp/terrascope-step4-save.json \
<<'JSON'
{
  "sourceEventId": "verification-fire-001",
  "hazardType": "wildfires",
  "title": "Verification wildfire",
  "description": "TerraScope Step 4 persistence verification.",
  "latitude": 34.2001,
  "longitude": -118.421,
  "observedAt": "2026-09-17T12:00:00Z",
  "sourceProvider": "NASA EONET",
  "sourceUrl": "https://eonet.gsfc.nasa.gov/",
  "magnitudeValue": null,
  "magnitudeUnit": "",
  "priority": "HIGH",
  "notes": ""
}
JSON

SAVE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step4-saved.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --header 'Accept: application/json' \
    --data @/tmp/terrascope-step4-save.json \
    "http://localhost:8087/api/v1/watchlist"
)"

echo "SAVE_HTTP=${SAVE_HTTP}"

if [ "${SAVE_HTTP}" != "200" ]; then

  cat \
    /tmp/terrascope-step4-saved.json

  exit 1
fi

WATCHLIST_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step4-saved.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["sourceEventId"]
    == "verification-fire-001"
)

assert (
    payload["hazardType"]
    == "wildfires"
)

assert (
    payload["priority"]
    == "HIGH"
)

assert payload["id"]

print(
    payload["id"]
)
PY
)"

echo "WATCHLIST_ID=${WATCHLIST_ID}"

echo "[PASS] WATCHLIST_SAVE"

echo
echo "===== IDEMPOTENT SAVE ====="

cat > \
  /tmp/terrascope-step4-save-update.json \
<<'JSON'
{
  "sourceEventId": "verification-fire-001",
  "hazardType": "wildfires",
  "title": "Verification wildfire updated",
  "description": "Updated source snapshot.",
  "latitude": 34.2001,
  "longitude": -118.421,
  "observedAt": "2026-09-17T12:05:00Z",
  "sourceProvider": "NASA EONET",
  "sourceUrl": "https://eonet.gsfc.nasa.gov/",
  "magnitudeValue": null,
  "magnitudeUnit": "",
  "priority": "MEDIUM",
  "notes": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --data @/tmp/terrascope-step4-save-update.json \
  "http://localhost:8087/api/v1/watchlist" \
  > /tmp/terrascope-step4-saved-update.json

python3 - "${WATCHLIST_ID}" <<'PY'
import json
import sys

expected_id = sys.argv[1]

with open(
    "/tmp/terrascope-step4-saved-update.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["id"]
    == expected_id
)

assert (
    payload["title"]
    == "Verification wildfire updated"
)

# Re-saving the source snapshot must not silently downgrade
# an analyst's existing watch priority.
assert (
    payload["priority"]
    == "HIGH"
)

print(
    "[PASS] WATCHLIST_IDEMPOTENT_UPSERT"
)
PY

ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM hazard_watchlist_entries
      WHERE source_event_id
            = 'verification-fire-001'
        AND hazard_type
            = 'wildfires';
      " \
    | tr -d '[:space:]'
)"

echo "WATCHLIST_SOURCE_ROW_COUNT=${ROW_COUNT}"

if [ "${ROW_COUNT}" != "1" ]; then
  echo "[FAIL] WATCHLIST_DUPLICATE_PREVENTION"
  exit 1
fi

echo "[PASS] WATCHLIST_DUPLICATE_PREVENTION"

echo
echo "===== UPDATE PRIORITY ====="

cat > \
  /tmp/terrascope-step4-priority.json \
<<'JSON'
{
  "priority": "CRITICAL"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --data @/tmp/terrascope-step4-priority.json \
  "http://localhost:8087/api/v1/watchlist/${WATCHLIST_ID}/priority" \
  > /tmp/terrascope-step4-priority-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step4-priority-response.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["priority"]
    == "CRITICAL"
)

print(
    "[PASS] WATCHLIST_PRIORITY_UPDATE"
)
PY

echo
echo "===== WATCHLIST SUMMARY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/watchlist" \
  > /tmp/terrascope-step4-watchlist.json

python3 - "${WATCHLIST_ID}" <<'PY'
import json
import sys

expected_id = sys.argv[1]

with open(
    "/tmp/terrascope-step4-watchlist.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

matching = [
    entry
    for entry in payload["entries"]
    if entry["id"] == expected_id
]

assert len(matching) == 1

entry = matching[0]

assert (
    entry["sourceEventId"]
    == "verification-fire-001"
)

assert (
    entry["hazardType"]
    == "wildfires"
)

assert (
    entry["priority"]
    == "CRITICAL"
)

summary = payload["summary"]

assert summary["totalCount"] >= 1
assert summary["criticalCount"] >= 1
assert summary["wildfireCount"] >= 1

print(
    "[PASS] WATCHLIST_LIST_AND_SUMMARY"
)
PY

echo
echo "===== DELETE WATCHLIST EVENT ====="

DELETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step4-delete-body.txt \
    --write-out '%{http_code}' \
    --request DELETE \
    "http://localhost:8087/api/v1/watchlist/${WATCHLIST_ID}"
)"

echo "DELETE_HTTP=${DELETE_HTTP}"

if [ "${DELETE_HTTP}" != "204" ]; then

  cat \
    /tmp/terrascope-step4-delete-body.txt

  exit 1
fi

FINAL_SOURCE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM hazard_watchlist_entries
      WHERE source_event_id
            = 'verification-fire-001';
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_VERIFICATION_SOURCE_COUNT=${FINAL_SOURCE_COUNT}"

if [ "${FINAL_SOURCE_COUNT}" != "0" ]; then
  echo "[FAIL] WATCHLIST_DELETE"
  exit 1
fi

echo "[PASS] WATCHLIST_DELETE"

echo
echo "===== DATABASE MIGRATIONS ====="

for VERSION in 1 2 3 4; do

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

    echo "[FAIL] FLYWAY_V${VERSION}"

    exit 1
  fi
done

echo "[PASS] DATABASE_MIGRATIONS"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 4 VERIFICATION PASSED"
echo "========================================================================"
