#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step16-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 16 VERIFICATION"
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
  >/tmp/terrascope-step16-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.16.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do
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

echo "[PASS] FLYWAY_V16_RUNTIME"

echo
echo "===== CLEAN STEP 16 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step16-incident';
    " \
  >/dev/null

echo "[PASS] STEP16_PRE_CLEAN"

echo
echo "===== CREATE CLOSURE INCIDENT ====="

cat > \
  /tmp/terrascope-step16-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step16-incident",
  "hazardType": "earthquakes",
  "title": "Verification Closure Incident",
  "description": "Step 16 closure readiness and after-action fixture.",
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
  --data @/tmp/terrascope-step16-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step16-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] CLOSURE_INCIDENT_CREATE"

echo
echo "===== INITIAL CLOSURE READINESS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/closure-readiness" \
  >/tmp/terrascope-step16-ready-initial.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-ready-initial.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is True
assert payload["status"] == "READY"

assert payload["commandScore"] == 0
assert payload["commandBand"] == "STABLE"

assert payload["openActionCount"] == 0
assert payload["resourceShortageCount"] == 0
assert payload["activeEvacuationZoneCount"] == 0
assert payload["activeShelterCount"] == 0
assert payload["disruptedLifelineCount"] == 0
assert payload["unresolvedDamageCount"] == 0
assert payload["unresolvedItemCount"] == 0

assert payload["blockers"] == []

print(
    "[PASS] INITIAL_CLOSURE_READINESS"
)
PY

echo
echo "===== CREATE ACTION PRESSURE ====="

cat > \
  /tmp/terrascope-step16-action.json \
<<'JSON'
{
  "title": "Clear emergency access corridor",
  "description": "Closure readiness action fixture.",
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
  --data @/tmp/terrascope-step16-action.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions" \
  >/tmp/terrascope-step16-action-response.json

ACTION_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-action-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step16-action-blocked.json \
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
  --data @/tmp/terrascope-step16-action-blocked.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/status" \
  >/dev/null

echo "[PASS] CLOSURE_ACTION_FIXTURE"

echo
echo "===== CREATE RESOURCE PRESSURE ====="

cat > \
  /tmp/terrascope-step16-resource.json \
<<'JSON'
{
  "category": "MEDICAL",
  "name": "Closure verification medical kits",
  "description": "Resource shortage fixture.",
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
  --data @/tmp/terrascope-step16-resource.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources" \
  >/tmp/terrascope-step16-resource-response.json

RESOURCE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-resource-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["resources"][0]["id"]
)
PY
)"

echo "[PASS] CLOSURE_RESOURCE_FIXTURE"

echo
echo "===== CREATE EVACUATION PRESSURE ====="

cat > \
  /tmp/terrascope-step16-zone.json \
<<'JSON'
{
  "name": "Closure Verification Zone",
  "description": "Active evacuation fixture.",
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
  --data @/tmp/terrascope-step16-zone.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones" \
  >/tmp/terrascope-step16-zone-response.json

ZONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-zone-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["zones"][0]["id"]
)
PY
)"

echo "[PASS] CLOSURE_EVACUATION_FIXTURE"

echo
echo "===== CREATE SHELTER PRESSURE ====="

cat > \
  /tmp/terrascope-step16-shelter.json \
<<'JSON'
{
  "name": "Closure Verification Shelter",
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
  --data @/tmp/terrascope-step16-shelter.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters" \
  >/tmp/terrascope-step16-shelter-response.json

SHELTER_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-shelter-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["shelters"][0]["id"]
)
PY
)"

echo "[PASS] CLOSURE_SHELTER_FIXTURE"

echo
echo "===== CREATE LIFELINE PRESSURE ====="

cat > \
  /tmp/terrascope-step16-lifeline.json \
<<'JSON'
{
  "category": "POWER",
  "name": "Closure Verification Grid",
  "description": "Critical outage fixture.",
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
  --data @/tmp/terrascope-step16-lifeline.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines" \
  >/tmp/terrascope-step16-lifeline-response.json

LIFELINE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-lifeline-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["assets"][0]["id"]
)
PY
)"

echo "[PASS] CLOSURE_LIFELINE_FIXTURE"

echo
echo "===== CREATE DAMAGE PRESSURE ====="

cat > \
  /tmp/terrascope-step16-damage.json \
<<'JSON'
{
  "category": "BRIDGE",
  "assetName": "Closure Verification Bridge",
  "description": "Major structural damage fixture.",
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
  --data @/tmp/terrascope-step16-damage.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage" \
  >/tmp/terrascope-step16-damage-response.json

DAMAGE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-damage-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["assessments"][0]["id"]
)
PY
)"

echo "[PASS] CLOSURE_DAMAGE_FIXTURE"

echo
echo "===== CAPTURE PEAK COMMAND SNAPSHOT ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/command-pulse/snapshots" \
  >/tmp/terrascope-step16-command-snapshot.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-command-snapshot.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["score"] == 49
assert payload["band"] == "ELEVATED"

print(
    "[PASS] CLOSURE_PEAK_COMMAND_SNAPSHOT"
)
PY

echo
echo "===== CAPTURE PRESSURE SITREP ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/tmp/terrascope-step16-pressure-sitrep.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-pressure-sitrep.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportNumber"] == 1
assert payload["commandScore"] == 49
assert payload["commandBand"] == "ELEVATED"

print(
    "[PASS] CLOSURE_PRESSURE_SITREP"
)
PY

echo
echo "===== BLOCKED CLOSURE READINESS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/closure-readiness" \
  >/tmp/terrascope-step16-blocked.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-blocked.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False
assert payload["status"] == "BLOCKED"

assert payload["commandScore"] == 49
assert payload["commandBand"] == "ELEVATED"

assert payload["openActionCount"] == 1
assert payload["resourceShortageCount"] == 1
assert payload["activeEvacuationZoneCount"] == 1
assert payload["activeShelterCount"] == 1
assert payload["disruptedLifelineCount"] == 1
assert payload["unresolvedDamageCount"] == 1

assert payload["unresolvedItemCount"] == 6

assert len(
    payload["blockers"]
) == 7

codes = {
    blocker["code"]
    for blocker
    in payload["blockers"]
}

for expected in (
    "OPEN_ACTIONS",
    "RESOURCE_SHORTAGES",
    "ACTIVE_EVACUATIONS",
    "ACTIVE_SHELTERS",
    "LIFELINE_DISRUPTIONS",
    "UNRESOLVED_DAMAGE",
    "COMMAND_PRESSURE",
):
    assert expected in codes

print(
    "[PASS] BLOCKED_CLOSURE_READINESS"
)
PY

echo
echo "===== OPEN INCIDENT AAR GUARD ====="

OPEN_AAR_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step16-open-aar.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews"
)"

echo "OPEN_INCIDENT_AAR_HTTP=${OPEN_AAR_HTTP}"

[[ "${OPEN_AAR_HTTP}" == "409" ]]

echo "[PASS] OPEN_INCIDENT_AAR_GUARD"

echo
echo "===== RESOLVE ACTION ====="

cat > \
  /tmp/terrascope-step16-action-done.json \
<<'JSON'
{
  "status": "DONE"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step16-action-done.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/status" \
  >/dev/null

echo "[PASS] CLOSURE_ACTION_RESOLVED"

echo
echo "===== RESOLVE RESOURCE ====="

cat > \
  /tmp/terrascope-step16-resource-full.json \
<<'JSON'
{
  "allocatedQuantity": 10
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step16-resource-full.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources/${RESOURCE_ID}/allocation" \
  >/dev/null

echo "[PASS] CLOSURE_RESOURCE_RESOLVED"

echo
echo "===== RESOLVE EVACUATION ====="

cat > \
  /tmp/terrascope-step16-zone-clear.json \
<<'JSON'
{
  "evacuatedPopulation": 10000
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step16-zone-clear.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones/${ZONE_ID}/progress" \
  >/dev/null

echo "[PASS] CLOSURE_EVACUATION_RESOLVED"

echo
echo "===== CLOSE SHELTER ====="

cat > \
  /tmp/terrascope-step16-shelter-close.json \
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
  --data @/tmp/terrascope-step16-shelter-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters/${SHELTER_ID}/status" \
  >/dev/null

echo "[PASS] CLOSURE_SHELTER_RESOLVED"

echo
echo "===== RESTORE LIFELINE ====="

cat > \
  /tmp/terrascope-step16-lifeline-restored.json \
<<'JSON'
{
  "status": "RESTORED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step16-lifeline-restored.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines/${LIFELINE_ID}/status" \
  >/dev/null

echo "[PASS] CLOSURE_LIFELINE_RESOLVED"

echo
echo "===== RESTORE DAMAGE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${DAMAGE_ID}/verify" \
  >/dev/null

cat > \
  /tmp/terrascope-step16-damage-restored.json \
<<'JSON'
{
  "repairProgressPct": 100
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step16-damage-restored.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${DAMAGE_ID}/progress" \
  >/dev/null

echo "[PASS] CLOSURE_DAMAGE_RESOLVED"

echo
echo "===== READY CLOSURE READINESS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/closure-readiness" \
  >/tmp/terrascope-step16-ready.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-ready.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is True
assert payload["status"] == "READY"

assert payload["commandScore"] == 0
assert payload["commandBand"] == "STABLE"

assert payload["openActionCount"] == 0
assert payload["resourceShortageCount"] == 0
assert payload["activeEvacuationZoneCount"] == 0
assert payload["activeShelterCount"] == 0
assert payload["disruptedLifelineCount"] == 0
assert payload["unresolvedDamageCount"] == 0
assert payload["unresolvedItemCount"] == 0

assert payload["blockers"] == []

print(
    "[PASS] READY_CLOSURE_READINESS"
)
PY

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step16-close.json \
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
  --data @/tmp/terrascope-step16-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/tmp/terrascope-step16-close-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-close-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["incident"]["status"] == "CLOSED"
assert payload["incident"]["closedAt"] is not None

print(
    "[PASS] INCIDENT_CLOSED"
)
PY

echo
echo "===== CAPTURE FINAL SITREP ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/tmp/terrascope-step16-final-sitrep.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-final-sitrep.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportNumber"] == 2
assert payload["incidentStatus"] == "CLOSED"

assert payload["commandScore"] == 0
assert payload["commandBand"] == "STABLE"

print(
    "[PASS] CLOSURE_FINAL_SITREP"
)
PY

echo
echo "===== CREATE AFTER-ACTION REVIEW ====="

AAR_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step16-aar.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews"
)"

echo "AFTER_ACTION_REVIEW_HTTP=${AAR_HTTP}"

[[ "${AAR_HTTP}" == "200" ]]

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reviewNumber"] == 1

assert payload["incidentTitle"] == "Verification Closure Incident"
assert payload["hazardType"] == "earthquakes"
assert payload["incidentSeverity"] == "SEVERE"

assert payload["durationMinutes"] >= 0

assert payload["closureReadinessStatus"] == "READY"
assert payload["closureReady"] is True

assert payload["finalCommandScore"] == 0
assert payload["finalCommandBand"] == "STABLE"

assert payload["peakCommandScore"] == 49

assert payload["sitrepCount"] == 2

assert payload["actionCount"] == 1
assert payload["resourceRequestCount"] == 1
assert payload["evacuationZoneCount"] == 1
assert payload["shelterCount"] == 1
assert payload["lifelineAssetCount"] == 1
assert payload["damageAssessmentCount"] == 1

assert payload["unresolvedItemCount"] == 0

assert (
    "Closure readiness was READY"
    in payload["executiveSummary"]
)

assert (
    "highest recorded command pressure was 49/100"
    in payload["executiveSummary"]
)

print(
    payload["id"]
)
PY
)"

echo "AAR_ID=${AAR_ID}"

echo "[PASS] AFTER_ACTION_REVIEW_CREATE"
echo "[PASS] PEAK_COMMAND_PRESSURE_EVIDENCE"
echo "[PASS] INCIDENT_LIFECYCLE_SUMMARY"

echo
echo "===== AFTER-ACTION HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step16-aar-history.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-aar-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert len(
    payload["reviews"]
) == 1

review = payload["reviews"][0]

assert review["reviewNumber"] == 1
assert review["closureReady"] is True
assert review["peakCommandScore"] == 49
assert review["sitrepCount"] == 2

print(
    "[PASS] AFTER_ACTION_REVIEW_HISTORY"
)
PY

echo
echo "===== AFTER-ACTION TIMELINE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step16-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step16-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "COMMAND_SNAPSHOT_CREATED" in types
assert types.count("SITREP_CREATED") == 2
assert "AFTER_ACTION_REVIEW_CREATED" in types

assert "ACTION_STATUS_CHANGED" in types
assert "RESOURCE_ALLOCATION_CHANGED" in types
assert "EVACUATION_PROGRESS_CHANGED" in types
assert "SHELTER_STATUS_CHANGED" in types
assert "LIFELINE_STATUS_CHANGED" in types
assert "DAMAGE_PROGRESS_CHANGED" in types

print(
    "[PASS] AFTER_ACTION_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

AAR_COLUMNS="$(
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
            'incident_after_action_reviews';
      " \
    | tr -d '[:space:]'
)"

AAR_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_after_action_reviews
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

AAR_UNIQUE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_after_action_review_number';
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
            LIKE '%AFTER_ACTION_REVIEW_CREATED%';
      " \
    | tr -d '[:space:]'
)"

echo "AFTER_ACTION_COLUMN_COUNT=${AAR_COLUMNS}"
echo "AFTER_ACTION_ROW_COUNT=${AAR_ROW_COUNT}"
echo "AFTER_ACTION_UNIQUE_COUNT=${AAR_UNIQUE_COUNT}"
echo "AFTER_ACTION_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${AAR_COLUMNS}" == "25" ]]
[[ "${AAR_ROW_COUNT}" == "1" ]]
[[ "${AAR_UNIQUE_COUNT}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] AFTER_ACTION_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 16 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step16-incident';
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
            'verification-step16-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_AAR_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_after_action_reviews review
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                review.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_AFTER_ACTION_COUNT=${ORPHAN_AAR_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_AAR_COUNT}" == "0" ]]

echo "[PASS] AFTER_ACTION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 16 VERIFICATION PASSED"
echo "========================================================================"
