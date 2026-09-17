#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step17-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 17 VERIFICATION"
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
  >/tmp/terrascope-step17-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.17.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17; do
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

echo "[PASS] FLYWAY_V17_RUNTIME"

echo
echo "===== CLEAN STEP 17 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step17-incident';
    " \
  >/dev/null

echo "[PASS] STEP17_PRE_CLEAN"

echo
echo "===== CREATE IMPROVEMENT INCIDENT ====="

cat > \
  /tmp/terrascope-step17-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step17-incident",
  "hazardType": "earthquakes",
  "title": "Verification Improvement Incident",
  "description": "Step 17 lessons-learned verification fixture.",
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
  --data @/tmp/terrascope-step17-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step17-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] IMPROVEMENT_INCIDENT_CREATE"

echo
echo "===== OPEN INCIDENT BOARD ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step17-open-board.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-open-board.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["openCount"] == 0
assert payload["inProgressCount"] == 0
assert payload["completedCount"] == 0
assert payload["dismissedCount"] == 0
assert payload["overdueCount"] == 0
assert payload["actions"] == []

print(
    "[PASS] IMPROVEMENT_BOARD_EMPTY_CONTRACT"
)
PY

echo
echo "===== OPEN INCIDENT MUTATION GUARD ====="

FAKE_REVIEW_ID="$(
  python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

cat > \
  /tmp/terrascope-step17-open-create.json \
<<JSON
{
  "afterActionReviewId": "${FAKE_REVIEW_ID}",
  "category": "PROCESS",
  "title": "Should not be accepted",
  "description": "",
  "priority": "HIGH",
  "owner": "Resilience Office",
  "dueAt": null
}
JSON

OPEN_CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step17-open-create-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step17-open-create.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements"
)"

echo "OPEN_INCIDENT_IMPROVEMENT_HTTP=${OPEN_CREATE_HTTP}"

[[ "${OPEN_CREATE_HTTP}" == "409" ]]

echo "[PASS] OPEN_INCIDENT_IMPROVEMENT_GUARD"

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step17-close.json \
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
  --data @/tmp/terrascope-step17-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/tmp/terrascope-step17-close-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-close-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["incident"]["status"] == "CLOSED"

print(
    "[PASS] IMPROVEMENT_INCIDENT_CLOSED"
)
PY

echo
echo "===== CREATE AFTER-ACTION REVIEW ====="

AAR_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step17-aar.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews"
)"

echo "AAR_HTTP=${AAR_HTTP}"

[[ "${AAR_HTTP}" == "200" ]]

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reviewNumber"] == 1
assert payload["closureReady"] is True

print(
    payload["id"]
)
PY
)"

echo "AAR_ID=${AAR_ID}"

echo "[PASS] IMPROVEMENT_AAR_CREATE"

echo
echo "===== INVALID REVIEW OWNERSHIP GUARD ====="

INVALID_REVIEW_ID="$(
  python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

cat > \
  /tmp/terrascope-step17-invalid-review.json \
<<JSON
{
  "afterActionReviewId": "${INVALID_REVIEW_ID}",
  "category": "PROCESS",
  "title": "Invalid source review",
  "description": "",
  "priority": "HIGH",
  "owner": "Resilience Office",
  "dueAt": null
}
JSON

INVALID_REVIEW_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step17-invalid-review-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step17-invalid-review.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements"
)"

echo "INVALID_REVIEW_HTTP=${INVALID_REVIEW_HTTP}"

[[ "${INVALID_REVIEW_HTTP}" == "400" ]]

echo "[PASS] IMPROVEMENT_AAR_OWNERSHIP_GUARD"

echo
echo "===== CREATE OVERDUE IMPROVEMENT ACTION ====="

cat > \
  /tmp/terrascope-step17-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Formalize incident command handoff checklist",
  "description": "Convert the after-action lesson into a reusable command-transition checklist.",
  "priority": "HIGH",
  "owner": "Resilience Office",
  "dueAt": "2020-01-01T00:00:00Z"
}
JSON

CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step17-create-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step17-improvement.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements"
)"

echo "IMPROVEMENT_CREATE_HTTP=${CREATE_HTTP}"

[[ "${CREATE_HTTP}" == "200" ]]

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-create-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["openCount"] == 1
assert payload["inProgressCount"] == 0
assert payload["completedCount"] == 0
assert payload["dismissedCount"] == 0
assert payload["overdueCount"] == 1

action = payload["actions"][0]

assert action["afterActionReviewId"] is not None
assert action["category"] == "PROCESS"
assert action["title"] == "Formalize incident command handoff checklist"
assert action["priority"] == "HIGH"
assert action["status"] == "OPEN"
assert action["owner"] == "Resilience Office"
assert action["dueAt"] == "2020-01-01T00:00:00Z"
assert action["completedAt"] is None

print(
    action["id"]
)
PY
)"

echo "IMPROVEMENT_ID=${IMPROVEMENT_ID}"

echo "[PASS] IMPROVEMENT_ACTION_CREATE"
echo "[PASS] IMPROVEMENT_OVERDUE_INTELLIGENCE"

echo
echo "===== UPDATE STATUS IN PROGRESS ====="

cat > \
  /tmp/terrascope-step17-in-progress.json \
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
  --data @/tmp/terrascope-step17-in-progress.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/tmp/terrascope-step17-in-progress-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-in-progress-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["openCount"] == 0
assert payload["inProgressCount"] == 1
assert payload["overdueCount"] == 1

action = payload["actions"][0]

assert action["status"] == "IN_PROGRESS"
assert action["completedAt"] is None

print(
    "[PASS] IMPROVEMENT_STATUS_IN_PROGRESS"
)
PY

echo
echo "===== UPDATE PRIORITY ====="

cat > \
  /tmp/terrascope-step17-priority.json \
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
  --data @/tmp/terrascope-step17-priority.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/priority" \
  >/tmp/terrascope-step17-priority-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-priority-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["actions"][0]["priority"]
    == "CRITICAL"
)

print(
    "[PASS] IMPROVEMENT_PRIORITY_UPDATE"
)
PY

echo
echo "===== UPDATE OWNER ====="

cat > \
  /tmp/terrascope-step17-owner.json \
<<'JSON'
{
  "owner": "Preparedness Office"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step17-owner.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/owner" \
  >/tmp/terrascope-step17-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["actions"][0]["owner"]
    == "Preparedness Office"
)

print(
    "[PASS] IMPROVEMENT_OWNER_UPDATE"
)
PY

echo
echo "===== UPDATE DUE DATE ====="

cat > \
  /tmp/terrascope-step17-due.json \
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
  --data @/tmp/terrascope-step17-due.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/due-at" \
  >/tmp/terrascope-step17-due-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-due-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["overdueCount"] == 0

assert (
    payload["actions"][0]["dueAt"]
    == "2035-01-01T00:00:00Z"
)

print(
    "[PASS] IMPROVEMENT_DUE_UPDATE"
)
PY

echo
echo "===== COMPLETE IMPROVEMENT ACTION ====="

cat > \
  /tmp/terrascope-step17-completed.json \
<<'JSON'
{
  "status": "COMPLETED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step17-completed.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/tmp/terrascope-step17-completed-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-completed-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["openCount"] == 0
assert payload["inProgressCount"] == 0
assert payload["completedCount"] == 1
assert payload["overdueCount"] == 0

action = payload["actions"][0]

assert action["status"] == "COMPLETED"
assert action["completedAt"] is not None

print(
    "[PASS] IMPROVEMENT_COMPLETION"
)
PY

echo
echo "===== TIMELINE EVIDENCE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step17-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "AFTER_ACTION_REVIEW_CREATED" in types
assert "IMPROVEMENT_ACTION_CREATED" in types

assert (
    types.count(
        "IMPROVEMENT_STATUS_CHANGED"
    )
    == 2
)

assert "IMPROVEMENT_PRIORITY_CHANGED" in types
assert "IMPROVEMENT_OWNER_CHANGED" in types
assert "IMPROVEMENT_DUE_CHANGED" in types

print(
    "[PASS] IMPROVEMENT_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

IMPROVEMENT_COLUMNS="$(
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
            'incident_improvement_actions';
      " \
    | tr -d '[:space:]'
)"

IMPROVEMENT_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_improvement_actions
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

REVIEW_FK_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'incident_improvement_actions'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%incident_after_action_reviews%';
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
            LIKE '%IMPROVEMENT_ACTION_CREATED%'
        AND pg_get_constraintdef(oid)
            LIKE '%IMPROVEMENT_ACTION_DELETED%';
      " \
    | tr -d '[:space:]'
)"

echo "IMPROVEMENT_COLUMN_COUNT=${IMPROVEMENT_COLUMNS}"
echo "IMPROVEMENT_ROW_COUNT=${IMPROVEMENT_ROW_COUNT}"
echo "IMPROVEMENT_REVIEW_FK_COUNT=${REVIEW_FK_COUNT}"
echo "IMPROVEMENT_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${IMPROVEMENT_COLUMNS}" == "13" ]]
[[ "${IMPROVEMENT_ROW_COUNT}" == "1" ]]
[[ "${REVIEW_FK_COUNT}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] IMPROVEMENT_DATABASE_CONTRACT"

echo
echo "===== DELETE IMPROVEMENT ACTION ====="

DELETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step17-delete-response.json \
    --write-out '%{http_code}' \
    --request DELETE \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}"
)"

echo "IMPROVEMENT_DELETE_HTTP=${DELETE_HTTP}"

[[ "${DELETE_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-delete-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["actions"] == []

print(
    "[PASS] IMPROVEMENT_ACTION_DELETE"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step17-detail-after-delete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step17-detail-after-delete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "IMPROVEMENT_ACTION_DELETED" in types

print(
    "[PASS] IMPROVEMENT_DELETE_TIMELINE"
)
PY

echo
echo "===== CLEAN STEP 17 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step17-incident';
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
            'verification-step17-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_IMPROVEMENT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_improvement_actions improvement
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                improvement.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_IMPROVEMENT_COUNT=${ORPHAN_IMPROVEMENT_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_IMPROVEMENT_COUNT}" == "0" ]]

echo "[PASS] IMPROVEMENT_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 17 VERIFICATION PASSED"
echo "========================================================================"
