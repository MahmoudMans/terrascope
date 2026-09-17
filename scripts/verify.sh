#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step8-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then

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
echo " TERRASCOPE — STEP 8 VERIFICATION"
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
      >/tmp/terrascope-step8-health.json \
      2>/dev/null; then

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

echo
echo "===== STATUS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step8-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["status"] == "operational"
assert payload["version"] == "0.8.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

echo
echo "===== STEP 7 REGRESSION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/alerts" \
  >/tmp/terrascope-step8-alerts.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/regional-intelligence/history" \
  >/tmp/terrascope-step8-regional.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/simulations" \
  >/tmp/terrascope-step8-simulations.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-alerts.json",
    encoding="utf-8",
) as handle:
    alerts = json.load(handle)

with open(
    "/tmp/terrascope-step8-regional.json",
    encoding="utf-8",
) as handle:
    regional = json.load(handle)

with open(
    "/tmp/terrascope-step8-simulations.json",
    encoding="utf-8",
) as handle:
    simulations = json.load(handle)

assert isinstance(
    alerts["rules"],
    list,
)

assert isinstance(
    alerts["alerts"],
    list,
)

assert isinstance(
    regional["runs"],
    list,
)

assert isinstance(
    simulations["runs"],
    list,
)

print(
    "[PASS] STEP7_RUNTIME_REGRESSION"
)
PY

echo
echo "===== FLYWAY V8 ====="

for VERSION in 1 2 3 4 5 6 7 8; do

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

  if [[ "${COUNT}" != "1" ]]; then
    exit 1
  fi
done

echo "[PASS] FLYWAY_V8_RUNTIME"

echo
echo "===== CLEAN VERIFICATION INCIDENTS ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id LIKE 'verification-incident-%';
    " \
  >/dev/null

echo "[PASS] INCIDENT_PRE_CLEAN"

echo
echo "===== INCIDENT DASHBOARD CONTRACT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step8-dashboard-before.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-dashboard-before.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert isinstance(
    payload["incidents"],
    list,
)

for key in (
    "totalCount",
    "activeCount",
    "respondingCount",
    "criticalCount",
    "closedCount",
):
    assert key in payload

print(
    "[PASS] INCIDENT_DASHBOARD_CONTRACT"
)
PY

echo
echo "===== CREATE INCIDENT ====="

cat > \
  /tmp/terrascope-step8-create.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-incident-source-1",
  "hazardType": "earthquakes",
  "title": "Verification earthquake incident",
  "description": "Step 8 operational incident verification.",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "severity": "SEVERE",
  "owner": "Unassigned"
}
JSON

CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step8-create-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step8-create.json \
    "http://localhost:8087/api/v1/incidents"
)"

echo "INCIDENT_CREATE_HTTP=${CREATE_HTTP}"

if [[ "${CREATE_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step8-create-response.json
  exit 1
fi

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-create-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

incident = payload["incident"]
timeline = payload["timeline"]

assert (
    incident["sourceEventId"]
    == "verification-incident-source-1"
)

assert (
    incident["hazardType"]
    == "earthquakes"
)

assert incident["severity"] == "SEVERE"
assert incident["status"] == "OPEN"
assert incident["owner"] == "Unassigned"

assert len(timeline) == 1

assert (
    timeline[0]["eventType"]
    == "INCIDENT_OPENED"
)

print(
    incident["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] INCIDENT_CREATE"

echo
echo "===== IDEMPOTENT INCIDENT CREATE ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step8-create.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step8-create-second.json

python3 - "${INCIDENT_ID}" <<'PY'
import json
import sys

expected = sys.argv[1]

with open(
    "/tmp/terrascope-step8-create-second.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["incident"]["id"] == expected

assert len(payload["timeline"]) == 1

print(
    "[PASS] INCIDENT_IDEMPOTENT_CREATE"
)
PY

INCIDENT_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_cases
      WHERE hazard_type = 'earthquakes'
        AND source_event_id = 'verification-incident-source-1';
      " \
    | tr -d '[:space:]'
)"

echo "INCIDENT_SOURCE_ROW_COUNT=${INCIDENT_ROW_COUNT}"

if [[ "${INCIDENT_ROW_COUNT}" != "1" ]]; then
  exit 1
fi

echo "[PASS] INCIDENT_DATABASE_DEDUPLICATION"

echo
echo "===== UPDATE SEVERITY ====="

cat > \
  /tmp/terrascope-step8-severity.json \
<<'JSON'
{
  "severity": "CRITICAL"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step8-severity.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/severity" \
  >/tmp/terrascope-step8-severity-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-severity-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["incident"]["severity"]
    == "CRITICAL"
)

assert any(
    event["eventType"]
    == "SEVERITY_CHANGED"
    for event in payload["timeline"]
)

print(
    "[PASS] INCIDENT_SEVERITY_UPDATE"
)
PY

echo
echo "===== UPDATE OWNER ====="

cat > \
  /tmp/terrascope-step8-owner.json \
<<'JSON'
{
  "owner": "Ops Alpha"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step8-owner.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/owner" \
  >/tmp/terrascope-step8-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["incident"]["owner"]
    == "Ops Alpha"
)

assert any(
    event["eventType"]
    == "OWNER_CHANGED"
    for event in payload["timeline"]
)

print(
    "[PASS] INCIDENT_OWNER_UPDATE"
)
PY

echo
echo "===== UPDATE STATUS ====="

cat > \
  /tmp/terrascope-step8-status-update.json \
<<'JSON'
{
  "status": "RESPONDING"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step8-status-update.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/tmp/terrascope-step8-status-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-status-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["incident"]["status"]
    == "RESPONDING"
)

assert any(
    event["eventType"]
    == "STATUS_CHANGED"
    for event in payload["timeline"]
)

print(
    "[PASS] INCIDENT_STATUS_UPDATE"
)
PY

echo
echo "===== ADD OPERATIONAL NOTE ====="

cat > \
  /tmp/terrascope-step8-note.json \
<<'JSON'
{
  "message": "Verification operational note."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step8-note.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/notes" \
  >/tmp/terrascope-step8-note-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-note-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

matching = [
    event
    for event in payload["timeline"]
    if (
        event["eventType"]
        == "NOTE_ADDED"
        and event["message"]
        == "Verification operational note."
    )
]

assert len(matching) == 1

print(
    "[PASS] INCIDENT_NOTE"
)
PY

echo
echo "===== INCIDENT DETAIL CONTRACT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step8-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

incident = payload["incident"]
timeline = payload["timeline"]

assert incident["severity"] == "CRITICAL"
assert incident["status"] == "RESPONDING"
assert incident["owner"] == "Ops Alpha"
assert incident["closedAt"] is None

assert len(timeline) == 5

event_types = {
    event["eventType"]
    for event in timeline
}

assert event_types == {
    "INCIDENT_OPENED",
    "SEVERITY_CHANGED",
    "OWNER_CHANGED",
    "STATUS_CHANGED",
    "NOTE_ADDED",
}

print(
    "[PASS] INCIDENT_TIMELINE_CONTRACT"
)
PY

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step8-close.json \
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
  --data @/tmp/terrascope-step8-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/tmp/terrascope-step8-close-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step8-close-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

incident = payload["incident"]

assert incident["status"] == "CLOSED"

assert incident["closedAt"] is not None

assert len(payload["timeline"]) == 6

print(
    "[PASS] INCIDENT_CLOSE"
)
PY

echo
echo "===== DASHBOARD AFTER WORKFLOW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step8-dashboard-after.json

python3 - "${INCIDENT_ID}" <<'PY'
import json
import sys

incident_id = sys.argv[1]

with open(
    "/tmp/terrascope-step8-dashboard-after.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

matching = [
    incident
    for incident in payload["incidents"]
    if incident["id"] == incident_id
]

assert len(matching) == 1
assert matching[0]["status"] == "CLOSED"

assert payload["closedCount"] >= 1

print(
    "[PASS] INCIDENT_DASHBOARD_WORKFLOW"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

INCIDENT_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'incident_cases'
        AND column_name IN (
          'id',
          'alert_event_id',
          'source_event_id',
          'hazard_type',
          'title',
          'description',
          'event_latitude',
          'event_longitude',
          'severity',
          'status',
          'owner_name',
          'opened_at',
          'updated_at',
          'closed_at'
        );
      " \
    | tr -d '[:space:]'
)"

TIMELINE_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'incident_timeline_events'
        AND column_name IN (
          'id',
          'incident_id',
          'event_type',
          'message',
          'created_at'
        );
      " \
    | tr -d '[:space:]'
)"

UNIQUE_SOURCE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname = 'uq_incident_source';
      " \
    | tr -d '[:space:]'
)"

echo "INCIDENT_COLUMN_COUNT=${INCIDENT_COLUMNS}"
echo "TIMELINE_COLUMN_COUNT=${TIMELINE_COLUMNS}"
echo "INCIDENT_UNIQUE_SOURCE_COUNT=${UNIQUE_SOURCE_COUNT}"

if [[ "${INCIDENT_COLUMNS}" != "14" ]]; then
  exit 1
fi

if [[ "${TIMELINE_COLUMNS}" != "5" ]]; then
  exit 1
fi

if [[ "${UNIQUE_SOURCE_COUNT}" != "1" ]]; then
  exit 1
fi

echo "[PASS] INCIDENT_DATABASE_CONTRACT"

echo
echo "===== CLEAN VERIFICATION INCIDENTS ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id LIKE 'verification-incident-%';
    " \
  >/dev/null

INCIDENT_VERIFY_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_cases
      WHERE source_event_id LIKE 'verification-incident-%';
      " \
    | tr -d '[:space:]'
)"

TIMELINE_VERIFY_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_timeline_events timeline
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id = timeline.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${INCIDENT_VERIFY_COUNT}"
echo "ORPHAN_TIMELINE_COUNT=${TIMELINE_VERIFY_COUNT}"

if [[ "${INCIDENT_VERIFY_COUNT}" != "0" ]]; then
  exit 1
fi

if [[ "${TIMELINE_VERIFY_COUNT}" != "0" ]]; then
  exit 1
fi

echo "[PASS] INCIDENT_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 8 VERIFICATION PASSED"
echo "========================================================================"
