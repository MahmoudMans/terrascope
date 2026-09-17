#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step9-backend.log"

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
echo " TERRASCOPE — STEP 9 VERIFICATION"
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
      >/tmp/terrascope-step9-health.json \
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
  >/tmp/terrascope-step9-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["status"] == "operational"
assert payload["version"] == "0.9.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

echo
echo "===== STEP 8 REGRESSION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step9-incidents.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/alerts" \
  >/tmp/terrascope-step9-alerts.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/regional-intelligence/history" \
  >/tmp/terrascope-step9-regional.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-incidents.json",
    encoding="utf-8",
) as handle:
    incidents = json.load(handle)

with open(
    "/tmp/terrascope-step9-alerts.json",
    encoding="utf-8",
) as handle:
    alerts = json.load(handle)

with open(
    "/tmp/terrascope-step9-regional.json",
    encoding="utf-8",
) as handle:
    regional = json.load(handle)

assert isinstance(
    incidents["incidents"],
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

print(
    "[PASS] STEP8_RUNTIME_REGRESSION"
)
PY

echo
echo "===== FLYWAY V9 ====="

for VERSION in 1 2 3 4 5 6 7 8 9; do

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

echo "[PASS] FLYWAY_V9_RUNTIME"

echo
echo "===== CLEAN VERIFICATION INCIDENT ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id = 'verification-step9-incident';
    " \
  >/dev/null

echo "[PASS] STEP9_PRE_CLEAN"

echo
echo "===== CREATE RESPONSE INCIDENT ====="

cat > \
  /tmp/terrascope-step9-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step9-incident",
  "hazardType": "severe-storms",
  "title": "Verification response coordination incident",
  "description": "Incident fixture for Step 9 response action verification.",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "severity": "HIGH",
  "owner": "Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step9-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step9-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["incident"]["status"]
    == "OPEN"
)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] RESPONSE_INCIDENT_CREATE"

echo
echo "===== EMPTY ACTION BOARD ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions" \
  >/tmp/terrascope-step9-board-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-board-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["todoCount"] == 0
assert payload["inProgressCount"] == 0
assert payload["blockedCount"] == 0
assert payload["doneCount"] == 0
assert payload["overdueCount"] == 0
assert payload["actions"] == []

print(
    "[PASS] ACTION_BOARD_EMPTY_CONTRACT"
)
PY

echo
echo "===== CREATE OVERDUE ACTION ====="

cat > \
  /tmp/terrascope-step9-action.json \
<<'JSON'
{
  "title": "Secure field access",
  "description": "Establish a safe operational perimeter and confirm access routes.",
  "priority": "HIGH",
  "owner": "Ops Alpha",
  "dueAt": "2020-01-01T00:00:00Z"
}
JSON

CREATE_ACTION_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step9-action-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step9-action.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions"
)"

echo "ACTION_CREATE_HTTP=${CREATE_ACTION_HTTP}"

if [[ "${CREATE_ACTION_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step9-action-response.json
  exit 1
fi

ACTION_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-action-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["todoCount"] == 1
assert payload["overdueCount"] == 1

assert len(
    payload["actions"]
) == 1

action = payload["actions"][0]

assert (
    action["title"]
    == "Secure field access"
)

assert action["priority"] == "HIGH"
assert action["status"] == "TODO"
assert action["owner"] == "Ops Alpha"
assert action["completedAt"] is None

print(action["id"])
PY
)"

echo "ACTION_ID=${ACTION_ID}"

echo "[PASS] ACTION_CREATE"
echo "[PASS] ACTION_OVERDUE_INTELLIGENCE"

echo
echo "===== UPDATE ACTION STATUS ====="

cat > \
  /tmp/terrascope-step9-action-status.json \
<<'JSON'
{
  "status": "IN_PROGRESS"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step9-action-status.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/status" \
  >/tmp/terrascope-step9-action-status-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-action-status-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["todoCount"] == 0
assert payload["inProgressCount"] == 1
assert payload["overdueCount"] == 1

action = payload["actions"][0]

assert action["status"] == "IN_PROGRESS"
assert action["completedAt"] is None

print(
    "[PASS] ACTION_STATUS_UPDATE"
)
PY

echo
echo "===== UPDATE ACTION PRIORITY ====="

cat > \
  /tmp/terrascope-step9-action-priority.json \
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
  --data @/tmp/terrascope-step9-action-priority.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/priority" \
  >/tmp/terrascope-step9-action-priority-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-action-priority-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["actions"][0]["priority"]
    == "CRITICAL"
)

print(
    "[PASS] ACTION_PRIORITY_UPDATE"
)
PY

echo
echo "===== UPDATE ACTION OWNER ====="

cat > \
  /tmp/terrascope-step9-action-owner.json \
<<'JSON'
{
  "owner": "Field Team"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step9-action-owner.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/owner" \
  >/tmp/terrascope-step9-action-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-action-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["actions"][0]["owner"]
    == "Field Team"
)

print(
    "[PASS] ACTION_OWNER_UPDATE"
)
PY

echo
echo "===== UPDATE ACTION DUE DATE ====="

cat > \
  /tmp/terrascope-step9-action-due.json \
<<'JSON'
{
  "dueAt": "2035-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step9-action-due.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/due-at" \
  >/tmp/terrascope-step9-action-due-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-action-due-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["overdueCount"] == 0

assert (
    payload["actions"][0]["dueAt"]
    == "2035-01-01T00:00:00Z"
)

print(
    "[PASS] ACTION_DUE_DATE_UPDATE"
)
PY

echo
echo "===== COMPLETE ACTION ====="

cat > \
  /tmp/terrascope-step9-action-done.json \
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
  --data @/tmp/terrascope-step9-action-done.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}/status" \
  >/tmp/terrascope-step9-action-done-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-action-done-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["doneCount"] == 1
assert payload["inProgressCount"] == 0
assert payload["overdueCount"] == 0

action = payload["actions"][0]

assert action["status"] == "DONE"
assert action["completedAt"] is not None

print(
    "[PASS] ACTION_COMPLETION"
)
PY

echo
echo "===== ACTION TIMELINE CONTRACT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step9-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

for expected in (
    "ACTION_CREATED",
    "ACTION_STATUS_CHANGED",
    "ACTION_PRIORITY_CHANGED",
    "ACTION_OWNER_CHANGED",
    "ACTION_DUE_CHANGED",
):
    assert expected in types

# TODO -> IN_PROGRESS and IN_PROGRESS -> DONE.
assert (
    types.count(
        "ACTION_STATUS_CHANGED"
    )
    == 2
)

print(
    "[PASS] ACTION_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DELETE ACTION ====="

DELETE_ACTION_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step9-delete-body.json \
    --write-out '%{http_code}' \
    --request DELETE \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/actions/${ACTION_ID}"
)"

echo "ACTION_DELETE_HTTP=${DELETE_ACTION_HTTP}"

if [[ "${DELETE_ACTION_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step9-delete-body.json
  exit 1
fi

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-delete-body.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["actions"] == []

print(
    "[PASS] ACTION_DELETE"
)
PY

echo
echo "===== DELETE TIMELINE EVIDENCE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step9-detail-after-delete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step9-detail-after-delete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "ACTION_DELETED" in types

print(
    "[PASS] ACTION_DELETE_TIMELINE"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

ACTION_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'incident_action_items'
        AND column_name IN (
          'id',
          'incident_id',
          'title',
          'description',
          'priority',
          'status',
          'owner_name',
          'due_at',
          'created_at',
          'updated_at',
          'completed_at'
        );
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
            LIKE '%ACTION_CREATED%'
        AND pg_get_constraintdef(oid)
            LIKE '%ACTION_DELETED%';
      " \
    | tr -d '[:space:]'
)"

echo "ACTION_COLUMN_COUNT=${ACTION_COLUMNS}"
echo "EXTENDED_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

if [[ "${ACTION_COLUMNS}" != "11" ]]; then
  exit 1
fi

if [[ "${TIMELINE_CONSTRAINT_COUNT}" != "1" ]]; then
  exit 1
fi

echo "[PASS] ACTION_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 9 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step9-incident';
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
            'verification-step9-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_ACTION_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_action_items action
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                action.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_ACTION_COUNT=${ORPHAN_ACTION_COUNT}"

if [[ "${VERIFY_INCIDENT_COUNT}" != "0" ]]; then
  exit 1
fi

if [[ "${ORPHAN_ACTION_COUNT}" != "0" ]]; then
  exit 1
fi

echo "[PASS] ACTION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 9 VERIFICATION PASSED"
echo "========================================================================"
