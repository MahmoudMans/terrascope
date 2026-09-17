#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step11-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

docker compose config >/dev/null
echo "[PASS] COMPOSE_CONFIGURATION"

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

  if [[ "${attempt}" -eq 30 ]]; then
    exit 1
  fi

  sleep 1
done

(
  cd apps/backend
  mvn test
)

echo "[PASS] BACKEND_TESTS"

(
  cd apps/backend
  mvn -DskipTests package
)

echo "[PASS] BACKEND_PACKAGE"

(
  cd apps/web
  npx eslint . --max-warnings=0
  npm run typecheck
  npm run build
)

echo "[PASS] FRONTEND_QUALITY"

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
      >/dev/null 2>&1; then

    echo "[PASS] BACKEND_RUNTIME_READY"
    break
  fi

  if ! kill -0 \
      "${BACKEND_PID}" \
      >/dev/null 2>&1; then

    cat "${BACKEND_LOG}"
    exit 1
  fi

  if [[ "${attempt}" -eq 45 ]]; then
    cat "${BACKEND_LOG}"
    exit 1
  fi

  sleep 1
done

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step11-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.11.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11; do
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

  [[ "${COUNT}" == "1" ]]
done

echo "[PASS] FLYWAY_V11_RUNTIME"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step11-incident';
    " \
  >/dev/null

cat > \
  /tmp/terrascope-step11-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step11-incident",
  "hazardType": "wildfires",
  "title": "Verification evacuation incident",
  "description": "Step 11 evacuation and shelter verification.",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "severity": "SEVERE",
  "owner": "Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step11-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step11-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"
echo "[PASS] EVACUATION_INCIDENT_CREATE"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation" \
  >/tmp/terrascope-step11-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["zoneCount"] == 0
assert payload["shelterCount"] == 0
assert payload["populationAtRisk"] == 0
assert payload["remainingShelterCapacity"] == 0

print(
    "[PASS] EVACUATION_BOARD_EMPTY_CONTRACT"
)
PY

cat > \
  /tmp/terrascope-step11-zone.json \
<<'JSON'
{
  "name": "Verification Zone Alpha",
  "description": "High-priority evacuation area.",
  "priority": "CRITICAL",
  "populationAtRisk": 1000,
  "evacuatedPopulation": 100,
  "owner": "Evac Alpha"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step11-zone.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones" \
  >/tmp/terrascope-step11-zone-response.json

ZONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-zone-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["zoneCount"] == 1
assert payload["activeZoneCount"] == 1
assert payload["populationAtRisk"] == 1000
assert payload["evacuatedPopulation"] == 100
assert payload["remainingToEvacuate"] == 900

zone = payload["zones"][0]

assert zone["status"] == "EVACUATING"
assert zone["priority"] == "CRITICAL"

print(zone["id"])
PY
)"

echo "ZONE_ID=${ZONE_ID}"
echo "[PASS] EVACUATION_ZONE_CREATE"

cat > \
  /tmp/terrascope-step11-progress.json \
<<'JSON'
{
  "evacuatedPopulation": 1000
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step11-progress.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones/${ZONE_ID}/progress" \
  >/tmp/terrascope-step11-progress-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-progress-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["activeZoneCount"] == 0
assert payload["clearedZoneCount"] == 1
assert payload["evacuatedPopulation"] == 1000
assert payload["remainingToEvacuate"] == 0

zone = payload["zones"][0]

assert zone["status"] == "CLEARED"
assert zone["completedAt"] is not None

print(
    "[PASS] EVACUATION_AUTO_CLEAR"
)
PY

cat > \
  /tmp/terrascope-step11-invalid-progress.json \
<<'JSON'
{
  "evacuatedPopulation": 1001
}
JSON

INVALID_EVAC_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step11-invalid-progress-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step11-invalid-progress.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones/${ZONE_ID}/progress"
)"

echo "INVALID_EVACUATION_HTTP=${INVALID_EVAC_HTTP}"

[[ "${INVALID_EVAC_HTTP}" == "400" ]]

echo "[PASS] EVACUATION_POPULATION_BOUNDARY"

cat > \
  /tmp/terrascope-step11-shelter.json \
<<'JSON'
{
  "name": "Verification Shelter Alpha",
  "address": "Verification address",
  "capacity": 100,
  "occupancy": 20,
  "owner": "Shelter Alpha"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step11-shelter.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters" \
  >/tmp/terrascope-step11-shelter-response.json

SHELTER_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-shelter-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["shelterCount"] == 1
assert payload["openShelterCount"] == 1
assert payload["fullShelterCount"] == 0

assert payload["shelterCapacity"] == 100
assert payload["shelterOccupancy"] == 20
assert payload["remainingShelterCapacity"] == 80

shelter = payload["shelters"][0]

assert shelter["status"] == "OPEN"

print(shelter["id"])
PY
)"

echo "SHELTER_ID=${SHELTER_ID}"
echo "[PASS] SHELTER_CREATE"
echo "[PASS] SHELTER_CAPACITY_INTELLIGENCE"

cat > \
  /tmp/terrascope-step11-occupancy.json \
<<'JSON'
{
  "occupancy": 100
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step11-occupancy.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters/${SHELTER_ID}/occupancy" \
  >/tmp/terrascope-step11-occupancy-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-occupancy-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["openShelterCount"] == 0
assert payload["fullShelterCount"] == 1

assert payload["shelterOccupancy"] == 100
assert payload["remainingShelterCapacity"] == 0

shelter = payload["shelters"][0]

assert shelter["status"] == "FULL"

print(
    "[PASS] SHELTER_AUTO_FULL"
)
PY

cat > \
  /tmp/terrascope-step11-invalid-occupancy.json \
<<'JSON'
{
  "occupancy": 101
}
JSON

INVALID_SHELTER_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step11-invalid-shelter-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step11-invalid-occupancy.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters/${SHELTER_ID}/occupancy"
)"

echo "INVALID_SHELTER_HTTP=${INVALID_SHELTER_HTTP}"

[[ "${INVALID_SHELTER_HTTP}" == "400" ]]

echo "[PASS] SHELTER_OCCUPANCY_BOUNDARY"

cat > \
  /tmp/terrascope-step11-shelter-close.json \
<<'JSON'
{
  "status": "CLOSED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step11-shelter-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters/${SHELTER_ID}/status" \
  >/tmp/terrascope-step11-shelter-close-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-shelter-close-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["closedShelterCount"] == 1
assert payload["shelterCapacity"] == 0
assert payload["shelterOccupancy"] == 0

shelter = payload["shelters"][0]

assert shelter["status"] == "CLOSED"
assert shelter["closedAt"] is not None

print(
    "[PASS] SHELTER_CLOSE"
)
PY

CLOSED_OCCUPANCY_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step11-closed-occupancy-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step11-occupancy.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters/${SHELTER_ID}/occupancy"
)"

echo "CLOSED_SHELTER_MUTATION_HTTP=${CLOSED_OCCUPANCY_HTTP}"

[[ "${CLOSED_OCCUPANCY_HTTP}" == "409" ]]

echo "[PASS] CLOSED_SHELTER_MUTATION_GUARD"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step11-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

for expected in (
    "EVACUATION_ZONE_CREATED",
    "EVACUATION_PROGRESS_CHANGED",
    "SHELTER_CREATED",
    "SHELTER_OCCUPANCY_CHANGED",
    "SHELTER_STATUS_CHANGED",
):
    assert expected in types

print(
    "[PASS] EVACUATION_TIMELINE_CONTRACT"
)
PY

ZONE_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name =
            'incident_evacuation_zones';
      " \
    | tr -d '[:space:]'
)"

SHELTER_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name =
            'incident_shelters';
      " \
    | tr -d '[:space:]'
)"

TIMELINE_CONSTRAINT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'incident_timeline_events'::regclass
        AND conname =
            'ck_incident_timeline_type'
        AND pg_get_constraintdef(oid)
            LIKE '%EVACUATION_ZONE_CREATED%'
        AND pg_get_constraintdef(oid)
            LIKE '%SHELTER_CREATED%';
      " \
    | tr -d '[:space:]'
)"

echo "EVACUATION_ZONE_COLUMN_COUNT=${ZONE_COLUMNS}"
echo "SHELTER_COLUMN_COUNT=${SHELTER_COLUMNS}"
echo "EVACUATION_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${ZONE_COLUMNS}" == "13" ]]
[[ "${SHELTER_COLUMNS}" == "11" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] EVACUATION_DATABASE_CONTRACT"

curl \
  --fail \
  --silent \
  --request DELETE \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones/${ZONE_ID}" \
  >/tmp/terrascope-step11-zone-delete.json

curl \
  --fail \
  --silent \
  --request DELETE \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters/${SHELTER_ID}" \
  >/tmp/terrascope-step11-shelter-delete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step11-zone-delete.json",
    encoding="utf-8",
) as handle:
    zone_payload = json.load(handle)

with open(
    "/tmp/terrascope-step11-shelter-delete.json",
    encoding="utf-8",
) as handle:
    shelter_payload = json.load(handle)

assert zone_payload["zoneCount"] == 0

assert shelter_payload["zoneCount"] == 0
assert shelter_payload["shelterCount"] == 0

print(
    "[PASS] EVACUATION_ENTITY_DELETE"
)
PY

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step11-incident';
    " \
  >/dev/null

VERIFY_INCIDENT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_cases
      WHERE source_event_id =
            'verification-step11-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_ZONE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_evacuation_zones zone
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                zone.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

ORPHAN_SHELTER_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_shelters shelter
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                shelter.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_EVACUATION_ZONE_COUNT=${ORPHAN_ZONE_COUNT}"
echo "ORPHAN_SHELTER_COUNT=${ORPHAN_SHELTER_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_ZONE_COUNT}" == "0" ]]
[[ "${ORPHAN_SHELTER_COUNT}" == "0" ]]

echo "[PASS] EVACUATION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 11 VERIFICATION PASSED"
echo "========================================================================"
