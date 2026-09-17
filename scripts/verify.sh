#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step18-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 18 VERIFICATION"
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
  >/tmp/terrascope-step18-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.18.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18; do
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

echo "[PASS] FLYWAY_V18_RUNTIME"

echo
echo "===== CLEAN STEP 18 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step18-incident';
    " \
  >/dev/null

echo "[PASS] STEP18_PRE_CLEAN"

echo
echo "===== CREATE PREPAREDNESS INCIDENT ====="

cat > \
  /tmp/terrascope-step18-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step18-incident",
  "hazardType": "earthquakes",
  "title": "Verification Preparedness Incident",
  "description": "Step 18 continuous-improvement validation fixture.",
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
  --data @/tmp/terrascope-step18-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step18-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] PREPAREDNESS_INCIDENT_CREATE"

echo
echo "===== EMPTY PREPAREDNESS BOARD ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step18-empty-board.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-empty-board.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["plannedCount"] == 0
assert payload["inProgressCount"] == 0
assert payload["completedCount"] == 0
assert payload["cancelledCount"] == 0

assert payload["passedCount"] == 0
assert payload["partialCount"] == 0
assert payload["failedCount"] == 0

assert payload["overdueCount"] == 0
assert payload["averageReadinessScore"] == 0.0
assert payload["exercises"] == []

print(
    "[PASS] PREPAREDNESS_BOARD_EMPTY_CONTRACT"
)
PY

echo
echo "===== OPEN INCIDENT PREPAREDNESS GUARD ====="

FAKE_IMPROVEMENT_ID="$(
  python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

cat > \
  /tmp/terrascope-step18-open-create.json \
<<JSON
{
  "improvementActionId": "${FAKE_IMPROVEMENT_ID}",
  "exerciseType": "TABLETOP",
  "title": "Should not be accepted",
  "description": "",
  "owner": "Preparedness Office",
  "scheduledAt": null
}
JSON

OPEN_CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step18-open-create-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step18-open-create.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises"
)"

echo "OPEN_INCIDENT_PREPAREDNESS_HTTP=${OPEN_CREATE_HTTP}"

[[ "${OPEN_CREATE_HTTP}" == "409" ]]

echo "[PASS] OPEN_INCIDENT_PREPAREDNESS_GUARD"

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step18-close.json \
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
  --data @/tmp/terrascope-step18-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/tmp/terrascope-step18-close-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-close-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["incident"]["status"] == "CLOSED"

print(
    "[PASS] PREPAREDNESS_INCIDENT_CLOSED"
)
PY

echo
echo "===== CREATE AFTER-ACTION REVIEW ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step18-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-aar.json",
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

echo "[PASS] PREPAREDNESS_AAR_CREATE"

echo
echo "===== CREATE IMPROVEMENT ACTION ====="

cat > \
  /tmp/terrascope-step18-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Formalize command handoff checklist",
  "description": "Improvement to validate through exercise.",
  "priority": "HIGH",
  "owner": "Resilience Office",
  "dueAt": "2030-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step18-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step18-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["openCount"] == 1

print(
    payload["actions"][0]["id"]
)
PY
)"

echo "IMPROVEMENT_ID=${IMPROVEMENT_ID}"

echo "[PASS] PREPAREDNESS_IMPROVEMENT_CREATE"

echo
echo "===== INCOMPLETE IMPROVEMENT VALIDATION GUARD ====="

cat > \
  /tmp/terrascope-step18-incomplete-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TABLETOP",
  "title": "Validate command handoff",
  "description": "Exercise should be rejected before improvement completion.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-01-02T10:00:00Z"
}
JSON

INCOMPLETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step18-incomplete-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step18-incomplete-exercise.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises"
)"

echo "INCOMPLETE_IMPROVEMENT_EXERCISE_HTTP=${INCOMPLETE_HTTP}"

[[ "${INCOMPLETE_HTTP}" == "409" ]]

echo "[PASS] COMPLETED_IMPROVEMENT_VALIDATION_GUARD"

echo
echo "===== COMPLETE IMPROVEMENT ACTION ====="

cat > \
  /tmp/terrascope-step18-improvement-completed.json \
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
  --data @/tmp/terrascope-step18-improvement-completed.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/tmp/terrascope-step18-improvement-completed-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-improvement-completed-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["completedCount"] == 1
assert payload["actions"][0]["status"] == "COMPLETED"
assert payload["actions"][0]["completedAt"] is not None

print(
    "[PASS] PREPAREDNESS_IMPROVEMENT_COMPLETED"
)
PY

echo
echo "===== CREATE OVERDUE PREPAREDNESS EXERCISE ====="

cat > \
  /tmp/terrascope-step18-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TABLETOP",
  "title": "Validate incident command handoff",
  "description": "Exercise the improved command handoff process under a simulated escalation.",
  "owner": "Preparedness Office",
  "scheduledAt": "2020-01-01T00:00:00Z"
}
JSON

CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step18-create-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step18-exercise.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises"
)"

echo "PREPAREDNESS_CREATE_HTTP=${CREATE_HTTP}"

[[ "${CREATE_HTTP}" == "200" ]]

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-create-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["plannedCount"] == 1
assert payload["inProgressCount"] == 0
assert payload["completedCount"] == 0
assert payload["overdueCount"] == 1
assert payload["averageReadinessScore"] == 0.0

exercise = payload["exercises"][0]

assert exercise["improvementActionId"] is not None
assert exercise["exerciseType"] == "TABLETOP"
assert exercise["status"] == "PLANNED"
assert exercise["validationResult"] == "NOT_ASSESSED"
assert exercise["readinessScore"] is None
assert exercise["owner"] == "Preparedness Office"
assert exercise["scheduledAt"] == "2020-01-01T00:00:00Z"

print(
    exercise["id"]
)
PY
)"

echo "EXERCISE_ID=${EXERCISE_ID}"

echo "[PASS] PREPAREDNESS_EXERCISE_CREATE"
echo "[PASS] PREPAREDNESS_OVERDUE_INTELLIGENCE"

echo
echo "===== UPDATE OWNER ====="

cat > \
  /tmp/terrascope-step18-owner.json \
<<'JSON'
{
  "owner": "Training and Exercise Office"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step18-owner.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/owner" \
  >/tmp/terrascope-step18-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["exercises"][0]["owner"]
    == "Training and Exercise Office"
)

print(
    "[PASS] PREPAREDNESS_OWNER_UPDATE"
)
PY

echo
echo "===== RESCHEDULE EXERCISE ====="

cat > \
  /tmp/terrascope-step18-schedule.json \
<<'JSON'
{
  "scheduledAt": "2035-01-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step18-schedule.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/schedule" \
  >/tmp/terrascope-step18-schedule-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-schedule-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["overdueCount"] == 0

assert (
    payload["exercises"][0]["scheduledAt"]
    == "2035-01-01T10:00:00Z"
)

print(
    "[PASS] PREPAREDNESS_SCHEDULE_UPDATE"
)
PY

echo
echo "===== START EXERCISE ====="

cat > \
  /tmp/terrascope-step18-progress.json \
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
  --data @/tmp/terrascope-step18-progress.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/status" \
  >/tmp/terrascope-step18-progress-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-progress-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["plannedCount"] == 0
assert payload["inProgressCount"] == 1

assert (
    payload["exercises"][0]["status"]
    == "IN_PROGRESS"
)

print(
    "[PASS] PREPAREDNESS_STATUS_IN_PROGRESS"
)
PY

echo
echo "===== DIRECT COMPLETION GUARD ====="

cat > \
  /tmp/terrascope-step18-direct-completed.json \
<<'JSON'
{
  "status": "COMPLETED"
}
JSON

DIRECT_COMPLETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step18-direct-completed-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step18-direct-completed.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/status"
)"

echo "DIRECT_COMPLETE_HTTP=${DIRECT_COMPLETE_HTTP}"

[[ "${DIRECT_COMPLETE_HTTP}" == "400" ]]

echo "[PASS] PREPAREDNESS_ASSESSMENT_REQUIRED_GUARD"

echo
echo "===== ASSESS PREPAREDNESS EXERCISE ====="

cat > \
  /tmp/terrascope-step18-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 92,
  "performedAt": "2030-01-02T12:00:00Z",
  "evidenceSummary": "Observers confirmed successful command transfer using the new checklist.",
  "findings": "All critical handoff fields were transferred. Minor timing variance remained.",
  "correctiveAction": "Update exercise guidance with a five-minute target for command transfer."
}
JSON

ASSESS_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step18-assessment-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step18-assessment.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/assess"
)"

echo "PREPAREDNESS_ASSESS_HTTP=${ASSESS_HTTP}"

[[ "${ASSESS_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-assessment-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["plannedCount"] == 0
assert payload["inProgressCount"] == 0
assert payload["completedCount"] == 1
assert payload["cancelledCount"] == 0

assert payload["passedCount"] == 1
assert payload["partialCount"] == 0
assert payload["failedCount"] == 0

assert payload["overdueCount"] == 0
assert payload["averageReadinessScore"] == 92.0

exercise = payload["exercises"][0]

assert exercise["status"] == "COMPLETED"
assert exercise["validationResult"] == "PASSED"
assert exercise["readinessScore"] == 92

assert (
    exercise["performedAt"]
    == "2030-01-02T12:00:00Z"
)

assert exercise["completedAt"] is not None

assert (
    "successful command transfer"
    in exercise["evidenceSummary"]
)

assert (
    "Minor timing variance"
    in exercise["findings"]
)

assert (
    "five-minute target"
    in exercise["correctiveAction"]
)

print(
    "[PASS] PREPAREDNESS_ASSESSMENT"
)

print(
    "[PASS] PREPAREDNESS_READINESS_SCORING"
)

print(
    "[PASS] PREPAREDNESS_EVIDENCE_CAPTURE"
)
PY

echo
echo "===== COMPLETED EXERCISE IMMUTABILITY ====="

IMMUTABLE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step18-immutable-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step18-owner.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/owner"
)"

echo "COMPLETED_EXERCISE_MUTATION_HTTP=${IMMUTABLE_HTTP}"

[[ "${IMMUTABLE_HTTP}" == "409" ]]

echo "[PASS] COMPLETED_EXERCISE_IMMUTABILITY"

echo
echo "===== PREPAREDNESS TIMELINE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step18-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "AFTER_ACTION_REVIEW_CREATED" in types
assert "IMPROVEMENT_ACTION_CREATED" in types
assert "IMPROVEMENT_STATUS_CHANGED" in types

assert "PREPAREDNESS_EXERCISE_CREATED" in types
assert "PREPAREDNESS_EXERCISE_OWNER_CHANGED" in types
assert "PREPAREDNESS_EXERCISE_SCHEDULE_CHANGED" in types
assert "PREPAREDNESS_EXERCISE_STATUS_CHANGED" in types
assert "PREPAREDNESS_EXERCISE_ASSESSED" in types

print(
    "[PASS] PREPAREDNESS_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

PREPAREDNESS_COLUMNS="$(
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
            'incident_preparedness_exercises';
      " \
    | tr -d '[:space:]'
)"

PREPAREDNESS_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_preparedness_exercises
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

IMPROVEMENT_FK_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'incident_preparedness_exercises'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%incident_improvement_actions%';
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
            LIKE '%PREPAREDNESS_EXERCISE_CREATED%'
        AND pg_get_constraintdef(oid)
            LIKE '%PREPAREDNESS_EXERCISE_ASSESSED%'
        AND pg_get_constraintdef(oid)
            LIKE '%PREPAREDNESS_EXERCISE_DELETED%';
      " \
    | tr -d '[:space:]'
)"

echo "PREPAREDNESS_COLUMN_COUNT=${PREPAREDNESS_COLUMNS}"
echo "PREPAREDNESS_ROW_COUNT=${PREPAREDNESS_ROW_COUNT}"
echo "PREPAREDNESS_IMPROVEMENT_FK_COUNT=${IMPROVEMENT_FK_COUNT}"
echo "PREPAREDNESS_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${PREPAREDNESS_COLUMNS}" == "18" ]]
[[ "${PREPAREDNESS_ROW_COUNT}" == "1" ]]
[[ "${IMPROVEMENT_FK_COUNT}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] PREPAREDNESS_DATABASE_CONTRACT"

echo
echo "===== DELETE PREPAREDNESS EXERCISE ====="

DELETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step18-delete-response.json \
    --write-out '%{http_code}' \
    --request DELETE \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}"
)"

echo "PREPAREDNESS_DELETE_HTTP=${DELETE_HTTP}"

[[ "${DELETE_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-delete-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["exercises"] == []

print(
    "[PASS] PREPAREDNESS_EXERCISE_DELETE"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step18-detail-after-delete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step18-detail-after-delete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "PREPAREDNESS_EXERCISE_DELETED" in types

print(
    "[PASS] PREPAREDNESS_DELETE_TIMELINE"
)
PY

echo
echo "===== CLEAN STEP 18 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step18-incident';
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
            'verification-step18-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_PREPAREDNESS_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_preparedness_exercises exercise
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                exercise.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_PREPAREDNESS_COUNT=${ORPHAN_PREPAREDNESS_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_PREPAREDNESS_COUNT}" == "0" ]]

echo "[PASS] PREPAREDNESS_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 18 VERIFICATION PASSED"
echo "========================================================================"
