#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step12-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 12 VERIFICATION"
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
  >/tmp/terrascope-step12-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.12.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12; do
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

echo "[PASS] FLYWAY_V12_RUNTIME"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step12-incident';
    " \
  >/dev/null

cat > \
  /tmp/terrascope-step12-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step12-incident",
  "hazardType": "severe-storms",
  "title": "Verification lifeline incident",
  "description": "Step 12 critical infrastructure verification.",
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
  --data @/tmp/terrascope-step12-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step12-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"
echo "[PASS] LIFELINE_INCIDENT_CREATE"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines" \
  >/tmp/terrascope-step12-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["disruptedCount"] == 0
assert payload["affectedPopulation"] == 0
assert payload["assets"] == []

print(
    "[PASS] LIFELINE_BOARD_EMPTY_CONTRACT"
)
PY

cat > \
  /tmp/terrascope-step12-asset.json \
<<'JSON'
{
  "category": "POWER",
  "name": "Verification Grid Alpha",
  "description": "Primary electrical distribution grid.",
  "priority": "CRITICAL",
  "status": "OUTAGE",
  "affectedPopulation": 5000,
  "owner": "Utility Alpha",
  "restorationEta": "2030-01-01T00:00:00Z"
}
JSON

CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step12-asset-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step12-asset.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines"
)"

echo "LIFELINE_CREATE_HTTP=${CREATE_HTTP}"

[[ "${CREATE_HTTP}" == "200" ]]

ASSET_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-asset-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["outageCount"] == 1
assert payload["degradedCount"] == 0
assert payload["disruptedCount"] == 1
assert payload["criticalDisruptionCount"] == 1
assert payload["affectedPopulation"] == 5000
assert payload["etaTrackedCount"] == 1

asset = payload["assets"][0]

assert asset["category"] == "POWER"
assert asset["status"] == "OUTAGE"
assert asset["priority"] == "CRITICAL"
assert asset["affectedPopulation"] == 5000
assert asset["owner"] == "Utility Alpha"
assert asset["restorationEta"] == "2030-01-01T00:00:00Z"

print(asset["id"])
PY
)"

echo "ASSET_ID=${ASSET_ID}"

echo "[PASS] LIFELINE_ASSET_CREATE"
echo "[PASS] LIFELINE_DISRUPTION_INTELLIGENCE"

cat > \
  /tmp/terrascope-step12-owner.json \
<<'JSON'
{
  "owner": "Grid Response Team"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step12-owner.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines/${ASSET_ID}/owner" \
  >/tmp/terrascope-step12-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["assets"][0]["owner"]
    == "Grid Response Team"
)

print(
    "[PASS] LIFELINE_OWNER_UPDATE"
)
PY

cat > \
  /tmp/terrascope-step12-eta.json \
<<'JSON'
{
  "restorationEta": "2031-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step12-eta.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines/${ASSET_ID}/restoration-eta" \
  >/tmp/terrascope-step12-eta-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-eta-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["assets"][0]["restorationEta"]
    == "2031-01-01T00:00:00Z"
)

assert payload["etaTrackedCount"] == 1

print(
    "[PASS] LIFELINE_ETA_UPDATE"
)
PY

cat > \
  /tmp/terrascope-step12-impact.json \
<<'JSON'
{
  "affectedPopulation": 3000
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step12-impact.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines/${ASSET_ID}/impact" \
  >/tmp/terrascope-step12-impact-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-impact-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["affectedPopulation"] == 3000

assert (
    payload["assets"][0]["affectedPopulation"]
    == 3000
)

print(
    "[PASS] LIFELINE_IMPACT_UPDATE"
)
PY

cat > \
  /tmp/terrascope-step12-restored.json \
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
  --data @/tmp/terrascope-step12-restored.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines/${ASSET_ID}/status" \
  >/tmp/terrascope-step12-restored-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-restored-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["outageCount"] == 0
assert payload["restoredCount"] == 1
assert payload["disruptedCount"] == 0
assert payload["affectedPopulation"] == 0
assert payload["etaTrackedCount"] == 0

asset = payload["assets"][0]

assert asset["status"] == "RESTORED"
assert asset["affectedPopulation"] == 0
assert asset["restorationEta"] is None
assert asset["restoredAt"] is not None

print(
    "[PASS] LIFELINE_RESTORATION"
)
PY

RESTORED_IMPACT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step12-restored-impact-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step12-impact.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines/${ASSET_ID}/impact"
)"

echo "RESTORED_IMPACT_HTTP=${RESTORED_IMPACT_HTTP}"

[[ "${RESTORED_IMPACT_HTTP}" == "409" ]]

echo "[PASS] RESTORED_LIFELINE_MUTATION_GUARD"

cat > \
  /tmp/terrascope-step12-invalid-operational.json \
<<'JSON'
{
  "category": "WATER",
  "name": "Invalid operational asset",
  "description": "",
  "priority": "HIGH",
  "status": "OPERATIONAL",
  "affectedPopulation": 100,
  "owner": "Water Ops",
  "restorationEta": null
}
JSON

INVALID_CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step12-invalid-create-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step12-invalid-operational.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines"
)"

echo "INVALID_OPERATIONAL_CREATE_HTTP=${INVALID_CREATE_HTTP}"

[[ "${INVALID_CREATE_HTTP}" == "400" ]]

echo "[PASS] LIFELINE_STATUS_IMPACT_BOUNDARY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step12-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

for expected in (
    "LIFELINE_ASSET_CREATED",
    "LIFELINE_OWNER_CHANGED",
    "LIFELINE_ETA_CHANGED",
    "LIFELINE_IMPACT_CHANGED",
    "LIFELINE_STATUS_CHANGED",
):
    assert expected in types

print(
    "[PASS] LIFELINE_TIMELINE_CONTRACT"
)
PY

LIFELINE_COLUMNS="$(
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
            'incident_lifeline_assets';
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
            LIKE '%LIFELINE_ASSET_CREATED%'
        AND pg_get_constraintdef(oid)
            LIKE '%LIFELINE_ASSET_DELETED%';
      " \
    | tr -d '[:space:]'
)"

echo "LIFELINE_COLUMN_COUNT=${LIFELINE_COLUMNS}"
echo "LIFELINE_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${LIFELINE_COLUMNS}" == "13" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] LIFELINE_DATABASE_CONTRACT"

curl \
  --fail \
  --silent \
  --request DELETE \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/lifelines/${ASSET_ID}" \
  >/tmp/terrascope-step12-delete-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-delete-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["assets"] == []

print(
    "[PASS] LIFELINE_ASSET_DELETE"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step12-detail-after-delete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step12-detail-after-delete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "LIFELINE_ASSET_DELETED" in types

print(
    "[PASS] LIFELINE_DELETE_TIMELINE"
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
            'verification-step12-incident';
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
            'verification-step12-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_LIFELINE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_lifeline_assets asset
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                asset.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_LIFELINE_COUNT=${ORPHAN_LIFELINE_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_LIFELINE_COUNT}" == "0" ]]

echo "[PASS] LIFELINE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 12 VERIFICATION PASSED"
echo "========================================================================"
