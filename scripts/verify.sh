#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step14-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 14 VERIFICATION"
echo "========================================================================"

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
  >/tmp/terrascope-step14-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.14.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13 14; do
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

echo "[PASS] FLYWAY_V14_RUNTIME"

echo
echo "===== CLEAN VERIFICATION FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step14-incident';
    " \
  >/dev/null

echo "[PASS] STEP14_PRE_CLEAN"

echo
echo "===== CREATE COMMAND INCIDENT ====="

cat > \
  /tmp/terrascope-step14-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step14-incident",
  "hazardType": "earthquakes",
  "title": "Verification command pulse incident",
  "description": "Cross-functional command pressure verification fixture.",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "severity": "SEVERE",
  "owner": "Incident Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step14-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] COMMAND_PULSE_INCIDENT_CREATE"

echo
echo "===== EMPTY COMMAND PULSE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/command-pulse" \
  >/tmp/terrascope-step14-pulse-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-pulse-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["score"] == 0
assert payload["band"] == "STABLE"

assert payload["openActionCount"] == 0
assert payload["overdueActionCount"] == 0
assert payload["blockedActionCount"] == 0
assert payload["resourceShortageCount"] == 0

assert payload["evacuationRemaining"] == 0
assert payload["shelterRemainingCapacity"] == 0

assert payload["lifelineDisruptedCount"] == 0
assert payload["lifelineAffectedPopulation"] == 0

assert payload["unresolvedDamageLossMusd"] == 0
assert payload["criticalDamageCount"] == 0

assert payload["blockers"] == []

print(
    "[PASS] COMMAND_PULSE_EMPTY_CONTRACT"
)
PY

echo
echo "===== CREATE OVERDUE BLOCKED ACTION ====="

cat > \
  /tmp/terrascope-step14-action.json \
<<'JSON'
{
  "title": "Open emergency access corridor",
  "description": "Verification command pulse action.",
  "priority": "CRITICAL",
  "owner": "Operations",
  "dueAt": "2020-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-action.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions" \
  >/tmp/terrascope-step14-action-response.json

ACTION_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-action-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step14-action-blocked.json \
<<'JSON'
{
  "status": "BLOCKED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-action-blocked.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/status" \
  >/dev/null

echo "[PASS] COMMAND_ACTION_PRESSURE_FIXTURE"

echo
echo "===== CREATE RESOURCE SHORTAGE ====="

cat > \
  /tmp/terrascope-step14-resource.json \
<<'JSON'
{
  "category": "MEDICAL",
  "name": "Emergency medical kits",
  "description": "Verification resource shortage.",
  "priority": "CRITICAL",
  "requestedQuantity": 10,
  "unit": "kits",
  "owner": "Logistics"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-resource.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources" \
  >/dev/null

echo "[PASS] COMMAND_RESOURCE_PRESSURE_FIXTURE"

echo
echo "===== CREATE EVACUATION BACKLOG ====="

cat > \
  /tmp/terrascope-step14-zone.json \
<<'JSON'
{
  "name": "Command Verification Zone",
  "description": "High population evacuation backlog.",
  "priority": "CRITICAL",
  "populationAtRisk": 10000,
  "evacuatedPopulation": 0,
  "owner": "Evacuation Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-zone.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones" \
  >/dev/null

echo "[PASS] COMMAND_EVACUATION_PRESSURE_FIXTURE"

echo
echo "===== CREATE SHELTER CAPACITY ====="

cat > \
  /tmp/terrascope-step14-shelter.json \
<<'JSON'
{
  "name": "Command Verification Shelter",
  "address": "Verification address",
  "capacity": 100,
  "occupancy": 10,
  "owner": "Shelter Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-shelter.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters" \
  >/dev/null

echo "[PASS] COMMAND_SHELTER_PRESSURE_FIXTURE"

echo
echo "===== CREATE LIFELINE OUTAGE ====="

cat > \
  /tmp/terrascope-step14-lifeline.json \
<<'JSON'
{
  "category": "POWER",
  "name": "Command Verification Grid",
  "description": "Critical grid outage.",
  "priority": "CRITICAL",
  "status": "OUTAGE",
  "affectedPopulation": 5000,
  "owner": "Grid Operations",
  "restorationEta": "2030-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-lifeline.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines" \
  >/dev/null

echo "[PASS] COMMAND_LIFELINE_PRESSURE_FIXTURE"

echo
echo "===== CREATE DAMAGE BACKLOG ====="

cat > \
  /tmp/terrascope-step14-damage.json \
<<'JSON'
{
  "category": "BRIDGE",
  "assetName": "Command Verification Bridge",
  "description": "Major structural damage.",
  "severity": "MAJOR",
  "affectedUnits": 1,
  "estimatedLossMusd": 25.5,
  "owner": "Recovery Command",
  "recoveryEta": "2031-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step14-damage.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage" \
  >/dev/null

echo "[PASS] COMMAND_DAMAGE_PRESSURE_FIXTURE"

echo
echo "===== VERIFY UNIFIED COMMAND PULSE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/command-pulse" \
  >/tmp/terrascope-step14-pulse.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-pulse.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["openActionCount"] == 1
assert payload["overdueActionCount"] == 1
assert payload["blockedActionCount"] == 1

assert payload["resourceShortageCount"] == 1

assert payload["evacuationRemaining"] == 10000
assert payload["shelterRemainingCapacity"] == 90

assert payload["lifelineDisruptedCount"] == 1
assert payload["criticalLifelineDisruptionCount"] == 1
assert payload["lifelineAffectedPopulation"] == 5000

assert payload["unresolvedDamageLossMusd"] == 25.5
assert payload["criticalDamageCount"] == 1
assert payload["averageRepairProgressPct"] == 0

# Deterministic model:
# overdue 4
# blocked 5
# shortage 3
# evacuation 15
# shelter gap 10
# lifeline 7
# damage 5
# total 49
assert payload["score"] == 49
assert payload["band"] == "ELEVATED"

assert len(payload["blockers"]) == 7

codes = {
    blocker["code"]
    for blocker
    in payload["blockers"]
}

for expected in (
    "OVERDUE_ACTIONS",
    "BLOCKED_ACTIONS",
    "RESOURCE_SHORTAGES",
    "EVACUATION_BACKLOG",
    "SHELTER_CAPACITY_GAP",
    "LIFELINE_DISRUPTION",
    "RECOVERY_BACKLOG",
):
    assert expected in codes

assert payload["blockers"][0]["weight"] == 15

print(
    "[PASS] UNIFIED_COMMAND_PULSE"
)

print(
    "[PASS] COMMAND_PRESSURE_SCORE"
)

print(
    "[PASS] COMMAND_BLOCKER_PRIORITIZATION"
)
PY

echo
echo "===== CREATE COMMAND SNAPSHOT ====="

SNAPSHOT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step14-snapshot.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/command-pulse/snapshots"
)"

echo "COMMAND_SNAPSHOT_HTTP=${SNAPSHOT_HTTP}"

[[ "${SNAPSHOT_HTTP}" == "200" ]]

SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-snapshot.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["score"] == 49
assert payload["band"] == "ELEVATED"
assert payload["blockerCount"] == 7

assert payload["openActionCount"] == 1
assert payload["overdueActionCount"] == 1
assert payload["blockedActionCount"] == 1

assert payload["resourceShortageCount"] == 1

assert payload["evacuationRemaining"] == 10000
assert payload["shelterRemainingCapacity"] == 90

assert payload["lifelineDisruptedCount"] == 1
assert payload["criticalLifelineDisruptionCount"] == 1
assert payload["lifelineAffectedPopulation"] == 5000

assert payload["unresolvedDamageLossMusd"] == 25.5
assert payload["criticalDamageCount"] == 1

print(
    payload["id"]
)
PY
)"

echo "SNAPSHOT_ID=${SNAPSHOT_ID}"

echo "[PASS] COMMAND_SNAPSHOT_CREATE"

echo
echo "===== COMMAND SNAPSHOT HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/command-pulse/history" \
  >/tmp/terrascope-step14-history.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert len(
    payload["snapshots"]
) == 1

snapshot = payload["snapshots"][0]

assert snapshot["score"] == 49
assert snapshot["band"] == "ELEVATED"
assert snapshot["blockerCount"] == 7

print(
    "[PASS] COMMAND_SNAPSHOT_HISTORY"
)
PY

echo
echo "===== COMMAND TIMELINE EVIDENCE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step14-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step14-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "COMMAND_SNAPSHOT_CREATED" in types

assert "ACTION_CREATED" in types
assert "ACTION_STATUS_CHANGED" in types

assert "RESOURCE_REQUESTED" in types

assert "EVACUATION_ZONE_CREATED" in types
assert "SHELTER_CREATED" in types

assert "LIFELINE_ASSET_CREATED" in types

assert "DAMAGE_ASSESSMENT_CREATED" in types

print(
    "[PASS] COMMAND_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

SNAPSHOT_COLUMNS="$(
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
            'incident_command_snapshots';
      " \
    | tr -d '[:space:]'
)"

SNAPSHOT_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_command_snapshots
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
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
            LIKE '%COMMAND_SNAPSHOT_CREATED%';
      " \
    | tr -d '[:space:]'
)"

echo "COMMAND_SNAPSHOT_COLUMN_COUNT=${SNAPSHOT_COLUMNS}"
echo "COMMAND_SNAPSHOT_ROW_COUNT=${SNAPSHOT_ROW_COUNT}"
echo "COMMAND_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${SNAPSHOT_COLUMNS}" == "18" ]]
[[ "${SNAPSHOT_ROW_COUNT}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] COMMAND_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 14 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step14-incident';
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
            'verification-step14-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_SNAPSHOT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_command_snapshots snapshot
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                snapshot.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_COMMAND_SNAPSHOT_COUNT=${ORPHAN_SNAPSHOT_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_SNAPSHOT_COUNT}" == "0" ]]

echo "[PASS] COMMAND_PULSE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 14 VERIFICATION PASSED"
echo "========================================================================"
