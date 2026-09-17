#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step15-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 15 VERIFICATION"
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
  >/tmp/terrascope-step15-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.15.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
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

echo "[PASS] FLYWAY_V15_RUNTIME"

echo
echo "===== CLEAN STEP 15 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step15-incident';
    " \
  >/dev/null

echo "[PASS] STEP15_PRE_CLEAN"

echo
echo "===== CREATE SITREP INCIDENT ====="

cat > \
  /tmp/terrascope-step15-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step15-incident",
  "hazardType": "earthquakes",
  "title": "Verification SITREP Incident",
  "description": "Step 15 situation report verification fixture.",
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
  --data @/tmp/terrascope-step15-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step15-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] SITREP_INCIDENT_CREATE"

echo
echo "===== EMPTY SITREP PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/preview" \
  >/tmp/terrascope-step15-preview-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-preview-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["incidentTitle"] == "Verification SITREP Incident"
assert payload["incidentStatus"] == "OPEN"
assert payload["incidentSeverity"] == "SEVERE"

assert payload["commandScore"] == 0
assert payload["commandBand"] == "STABLE"

assert payload["openActionCount"] == 0
assert payload["resourceShortageCount"] == 0
assert payload["evacuationRemaining"] == 0
assert payload["lifelineDisruptedCount"] == 0
assert payload["unresolvedDamageLossMusd"] == 0

assert len(payload["priorities"]) == 1

assert (
    "no scored operational blockers"
    in payload["priorities"][0]
)

assert (
    "Verification SITREP Incident"
    in payload["executiveSummary"]
)

print(
    "[PASS] SITREP_EMPTY_PREVIEW"
)
PY

echo
echo "===== EMPTY SITREP HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/tmp/terrascope-step15-history-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-history-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reports"] == []

print(
    "[PASS] SITREP_EMPTY_HISTORY"
)
PY

echo
echo "===== CREATE COMMAND PRESSURE FIXTURES ====="

cat > \
  /tmp/terrascope-step15-action.json \
<<'JSON'
{
  "title": "Open emergency access corridor",
  "description": "SITREP verification action.",
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
  --data @/tmp/terrascope-step15-action.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions" \
  >/tmp/terrascope-step15-action-response.json

ACTION_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-action-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step15-blocked.json \
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
  --data @/tmp/terrascope-step15-blocked.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step15-resource.json \
<<'JSON'
{
  "category": "MEDICAL",
  "name": "Emergency medical kits",
  "description": "SITREP shortage fixture.",
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
  --data @/tmp/terrascope-step15-resource.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources" \
  >/dev/null

cat > \
  /tmp/terrascope-step15-zone.json \
<<'JSON'
{
  "name": "SITREP Verification Zone",
  "description": "Large evacuation backlog.",
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
  --data @/tmp/terrascope-step15-zone.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/zones" \
  >/dev/null

cat > \
  /tmp/terrascope-step15-shelter.json \
<<'JSON'
{
  "name": "SITREP Verification Shelter",
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
  --data @/tmp/terrascope-step15-shelter.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/evacuation/shelters" \
  >/dev/null

cat > \
  /tmp/terrascope-step15-lifeline.json \
<<'JSON'
{
  "category": "POWER",
  "name": "SITREP Verification Grid",
  "description": "Critical power outage.",
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
  --data @/tmp/terrascope-step15-lifeline.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines" \
  >/dev/null

cat > \
  /tmp/terrascope-step15-damage.json \
<<'JSON'
{
  "category": "BRIDGE",
  "assetName": "SITREP Verification Bridge",
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
  --data @/tmp/terrascope-step15-damage.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage" \
  >/dev/null

echo "[PASS] SITREP_PRESSURE_FIXTURES"

echo
echo "===== VERIFY SITREP PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/preview" \
  >/tmp/terrascope-step15-preview.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-preview.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["commandScore"] == 49
assert payload["commandBand"] == "ELEVATED"

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

assert len(payload["priorities"]) == 3

assert (
    "People remaining to evacuate"
    in payload["priorities"][0]
)

assert (
    "Shelter capacity below evacuation backlog"
    in payload["priorities"][1]
)

assert (
    "Critical infrastructure disruption"
    in payload["priorities"][2]
)

summary = payload["executiveSummary"]

assert "49/100" in summary
assert "ELEVATED" in summary
assert "25.5 MUSD" in summary
assert "5000 people" in summary

print(
    "[PASS] SITREP_PREVIEW_AGGREGATION"
)

print(
    "[PASS] SITREP_EXECUTIVE_NARRATIVE"
)

print(
    "[PASS] SITREP_PRIORITY_EXTRACTION"
)
PY

echo
echo "===== CREATE SITREP #1 ====="

REPORT_ONE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step15-report-one.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports"
)"

echo "SITREP_ONE_HTTP=${REPORT_ONE_HTTP}"

[[ "${REPORT_ONE_HTTP}" == "200" ]]

REPORT_ONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-report-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportNumber"] == 1

assert payload["incidentTitle"] == "Verification SITREP Incident"
assert payload["incidentStatus"] == "OPEN"
assert payload["incidentSeverity"] == "SEVERE"

assert payload["commandScore"] == 49
assert payload["commandBand"] == "ELEVATED"

assert len(payload["priorities"]) == 3

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

echo "REPORT_ONE_ID=${REPORT_ONE_ID}"

echo "[PASS] SITREP_REPORT_ONE_CREATE"

echo
echo "===== CREATE SITREP #2 ====="

REPORT_TWO_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step15-report-two.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports"
)"

echo "SITREP_TWO_HTTP=${REPORT_TWO_HTTP}"

[[ "${REPORT_TWO_HTTP}" == "200" ]]

REPORT_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-report-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportNumber"] == 2
assert payload["commandScore"] == 49
assert payload["commandBand"] == "ELEVATED"

print(
    payload["id"]
)
PY
)"

echo "REPORT_TWO_ID=${REPORT_TWO_ID}"

echo "[PASS] SITREP_REPORT_SEQUENCE"

echo
echo "===== SITREP HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/tmp/terrascope-step15-history.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

reports = payload["reports"]

assert len(reports) == 2

assert reports[0]["reportNumber"] == 2
assert reports[1]["reportNumber"] == 1

assert reports[0]["commandScore"] == 49
assert reports[0]["commandBand"] == "ELEVATED"

assert len(
    reports[0]["priorities"]
) == 3

print(
    "[PASS] SITREP_HISTORY"
)
PY

echo
echo "===== SITREP TIMELINE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step15-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert (
    types.count(
        "SITREP_CREATED"
    )
    == 2
)

assert "ACTION_CREATED" in types
assert "RESOURCE_REQUESTED" in types
assert "EVACUATION_ZONE_CREATED" in types
assert "SHELTER_CREATED" in types
assert "LIFELINE_ASSET_CREATED" in types
assert "DAMAGE_ASSESSMENT_CREATED" in types

print(
    "[PASS] SITREP_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

SITREP_COLUMNS="$(
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
            'incident_situation_reports';
      " \
    | tr -d '[:space:]'
)"

SITREP_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_situation_reports
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

SITREP_UNIQUE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_incident_sitrep_number';
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
            LIKE '%SITREP_CREATED%';
      " \
    | tr -d '[:space:]'
)"

echo "SITREP_COLUMN_COUNT=${SITREP_COLUMNS}"
echo "SITREP_ROW_COUNT=${SITREP_ROW_COUNT}"
echo "SITREP_UNIQUE_NUMBER_COUNT=${SITREP_UNIQUE_COUNT}"
echo "SITREP_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${SITREP_COLUMNS}" == "25" ]]
[[ "${SITREP_ROW_COUNT}" == "2" ]]
[[ "${SITREP_UNIQUE_COUNT}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] SITREP_DATABASE_CONTRACT"

echo
echo "===== CLOSED INCIDENT FINAL REPORT ====="

cat > \
  /tmp/terrascope-step15-close.json \
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
  --data @/tmp/terrascope-step15-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/tmp/terrascope-step15-close-response.json

FINAL_REPORT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step15-final-report.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports"
)"

echo "FINAL_SITREP_HTTP=${FINAL_REPORT_HTTP}"

[[ "${FINAL_REPORT_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step15-final-report.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportNumber"] == 3
assert payload["incidentStatus"] == "CLOSED"

print(
    "[PASS] CLOSED_INCIDENT_FINAL_SITREP"
)
PY

echo
echo "===== CLEAN STEP 15 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step15-incident';
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
            'verification-step15-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_SITREP_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_situation_reports report
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                report.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_SITREP_COUNT=${ORPHAN_SITREP_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_SITREP_COUNT}" == "0" ]]

echo "[PASS] SITREP_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 15 VERIFICATION PASSED"
echo "========================================================================"
