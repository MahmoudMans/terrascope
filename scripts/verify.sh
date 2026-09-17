#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step19-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 19 VERIFICATION"
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
  >/tmp/terrascope-step19-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.19.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19; do
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

echo "[PASS] FLYWAY_V19_RUNTIME"

echo
echo "===== CLEAN STEP 19 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step19-incident';
    " \
  >/dev/null

echo "[PASS] STEP19_PRE_CLEAN"

echo
echo "===== CREATE VALIDATION INCIDENT ====="

cat > \
  /tmp/terrascope-step19-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step19-incident",
  "hazardType": "earthquakes",
  "title": "Verification Resilience Incident",
  "description": "Step 19 resilience validation and revalidation fixture.",
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
  --data @/tmp/terrascope-step19-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step19-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] RESILIENCE_INCIDENT_CREATE"

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step19-close.json \
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
  --data @/tmp/terrascope-step19-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

echo "[PASS] RESILIENCE_INCIDENT_CLOSED"

echo
echo "===== CREATE AAR ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step19-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reviewNumber"] == 1

print(
    payload["id"]
)
PY
)"

echo "AAR_ID=${AAR_ID}"

echo "[PASS] RESILIENCE_AAR_CREATE"

echo
echo "===== CREATE COMPLETED IMPROVEMENT ====="

cat > \
  /tmp/terrascope-step19-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Formalize command handoff checklist",
  "description": "Improvement to validate and revalidate.",
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
  --data @/tmp/terrascope-step19-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step19-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step19-improvement-completed.json \
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
  --data @/tmp/terrascope-step19-improvement-completed.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

echo "[PASS] RESILIENCE_IMPROVEMENT_COMPLETED"

echo
echo "===== UNTESTED VALIDATION STATE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation" \
  >/tmp/terrascope-step19-untested.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-untested.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["completedImprovementCount"] == 1

assert payload["untestedCount"] == 1
assert payload["validatedCount"] == 0
assert payload["needsRevalidationCount"] == 0
assert payload["failedCount"] == 0

assert payload["totalCorrectiveActionCount"] == 0
assert payload["averageLatestReadinessScore"] == 0.0

validation = payload["validations"][0]

assert validation["improvementActionId"] is not None
assert validation["totalExerciseCount"] == 0
assert validation["latestExerciseId"] is None
assert validation["latestValidationResult"] is None
assert validation["latestReadinessScore"] is None
assert validation["validationStatus"] == "UNTESTED"

print(
    "[PASS] RESILIENCE_UNTESTED_STATE"
)
PY

echo
echo "===== CREATE PARTIAL VALIDATION EXERCISE ====="

cat > \
  /tmp/terrascope-step19-exercise-one.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TABLETOP",
  "title": "Initial command handoff validation",
  "description": "Initial validation cycle.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-01-02T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step19-exercise-one.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step19-exercise-one-response.json

EXERCISE_ONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-exercise-one-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step19-partial.json \
<<'JSON'
{
  "result": "PARTIAL",
  "readinessScore": 62,
  "performedAt": "2030-01-02T12:00:00Z",
  "evidenceSummary": "The handoff checklist was used but two mandatory fields were omitted.",
  "findings": "Operational ownership and communications confirmation were incomplete.",
  "correctiveAction": "Revise the checklist and retrain command staff before revalidation."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step19-partial.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ONE_ID}/assess" \
  >/dev/null

echo "EXERCISE_ONE_ID=${EXERCISE_ONE_ID}"

echo "[PASS] RESILIENCE_PARTIAL_EXERCISE"

echo
echo "===== NEEDS REVALIDATION STATE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation" \
  >/tmp/terrascope-step19-needs-revalidation.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-needs-revalidation.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["completedImprovementCount"] == 1

assert payload["untestedCount"] == 0
assert payload["validatedCount"] == 0
assert payload["needsRevalidationCount"] == 1
assert payload["failedCount"] == 0

assert payload["averageLatestReadinessScore"] == 62.0

validation = payload["validations"][0]

assert validation["totalExerciseCount"] == 1
assert validation["latestExerciseId"] is not None
assert validation["latestValidationResult"] == "PARTIAL"
assert validation["latestReadinessScore"] == 62
assert validation["validationStatus"] == "NEEDS_REVALIDATION"

print(
    "[PASS] RESILIENCE_NEEDS_REVALIDATION"
)
PY

echo
echo "===== CREATE OVERDUE CORRECTIVE ACTION ====="

cat > \
  /tmp/terrascope-step19-corrective.json \
<<'JSON'
{
  "title": "Revise mandatory handoff fields",
  "description": "Update the handoff checklist and retrain incident command staff.",
  "priority": "HIGH",
  "owner": "Preparedness Office",
  "dueAt": "2020-01-01T00:00:00Z"
}
JSON

CORRECTIVE_CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step19-corrective-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step19-corrective.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/exercises/${EXERCISE_ONE_ID}/corrective-actions"
)"

echo "CORRECTIVE_CREATE_HTTP=${CORRECTIVE_CREATE_HTTP}"

[[ "${CORRECTIVE_CREATE_HTTP}" == "200" ]]

CORRECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-corrective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCorrectiveActionCount"] == 1
assert payload["openCorrectiveActionCount"] == 1
assert payload["inProgressCorrectiveActionCount"] == 0
assert payload["completedCorrectiveActionCount"] == 0
assert payload["overdueCorrectiveActionCount"] == 1

assert payload["needsRevalidationCount"] == 1

validation = payload["validations"][0]

assert validation["openCorrectiveActionCount"] == 1
assert validation["validationStatus"] == "NEEDS_REVALIDATION"

corrective = payload["correctiveActions"][0]

assert corrective["exerciseId"] is not None
assert corrective["improvementActionId"] is not None

assert corrective["status"] == "OPEN"
assert corrective["priority"] == "HIGH"
assert corrective["owner"] == "Preparedness Office"
assert corrective["dueAt"] == "2020-01-01T00:00:00Z"

print(
    corrective["id"]
)
PY
)"

echo "CORRECTIVE_ID=${CORRECTIVE_ID}"

echo "[PASS] CORRECTIVE_ACTION_CREATE"
echo "[PASS] CORRECTIVE_ACTION_OVERDUE_INTELLIGENCE"

echo
echo "===== UPDATE CORRECTIVE OWNER ====="

cat > \
  /tmp/terrascope-step19-corrective-owner.json \
<<'JSON'
{
  "owner": "Training Office"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step19-corrective-owner.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/corrective-actions/${CORRECTIVE_ID}/owner" \
  >/tmp/terrascope-step19-corrective-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-corrective-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["correctiveActions"][0]["owner"]
    == "Training Office"
)

print(
    "[PASS] CORRECTIVE_ACTION_OWNER_UPDATE"
)
PY

echo
echo "===== UPDATE CORRECTIVE DUE DATE ====="

cat > \
  /tmp/terrascope-step19-corrective-due.json \
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
  --data @/tmp/terrascope-step19-corrective-due.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/corrective-actions/${CORRECTIVE_ID}/due-at" \
  >/tmp/terrascope-step19-corrective-due-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-corrective-due-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["overdueCorrectiveActionCount"] == 0

assert (
    payload["correctiveActions"][0]["dueAt"]
    == "2035-01-01T00:00:00Z"
)

print(
    "[PASS] CORRECTIVE_ACTION_DUE_UPDATE"
)
PY

echo
echo "===== CORRECTIVE ACTION IN PROGRESS ====="

cat > \
  /tmp/terrascope-step19-corrective-progress.json \
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
  --data @/tmp/terrascope-step19-corrective-progress.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/corrective-actions/${CORRECTIVE_ID}/status" \
  >/tmp/terrascope-step19-corrective-progress-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-corrective-progress-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["openCorrectiveActionCount"] == 0
assert payload["inProgressCorrectiveActionCount"] == 1

assert (
    payload["correctiveActions"][0]["status"]
    == "IN_PROGRESS"
)

print(
    "[PASS] CORRECTIVE_ACTION_IN_PROGRESS"
)
PY

echo
echo "===== COMPLETE CORRECTIVE ACTION ====="

cat > \
  /tmp/terrascope-step19-corrective-completed.json \
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
  --data @/tmp/terrascope-step19-corrective-completed.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/corrective-actions/${CORRECTIVE_ID}/status" \
  >/tmp/terrascope-step19-corrective-completed-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-corrective-completed-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["openCorrectiveActionCount"] == 0
assert payload["inProgressCorrectiveActionCount"] == 0
assert payload["completedCorrectiveActionCount"] == 1
assert payload["overdueCorrectiveActionCount"] == 0

corrective = payload["correctiveActions"][0]

assert corrective["status"] == "COMPLETED"
assert corrective["completedAt"] is not None

# Completing the corrective action alone does not
# validate the underlying improvement.
assert payload["needsRevalidationCount"] == 1
assert payload["validatedCount"] == 0

print(
    "[PASS] CORRECTIVE_ACTION_COMPLETION"
)

print(
    "[PASS] REVALIDATION_REQUIRED_AFTER_CORRECTION"
)
PY

echo
echo "===== COMPLETED CORRECTIVE IMMUTABILITY ====="

IMMUTABLE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step19-corrective-immutable.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step19-corrective-owner.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/corrective-actions/${CORRECTIVE_ID}/owner"
)"

echo "COMPLETED_CORRECTIVE_MUTATION_HTTP=${IMMUTABLE_HTTP}"

[[ "${IMMUTABLE_HTTP}" == "409" ]]

echo "[PASS] COMPLETED_CORRECTIVE_IMMUTABILITY"

echo
echo "===== CREATE REVALIDATION EXERCISE ====="

cat > \
  /tmp/terrascope-step19-exercise-two.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Command handoff revalidation",
  "description": "Revalidate the corrected handoff process.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step19-exercise-two.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step19-exercise-two-response.json

EXERCISE_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-exercise-two-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

exercises = payload["exercises"]

match = next(
    exercise
    for exercise in exercises
    if exercise["title"]
    == "Command handoff revalidation"
)

print(
    match["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step19-passed.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 95,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "The revised checklist was completed accurately during the full command-transfer sequence.",
  "findings": "All mandatory handoff fields and communications confirmations were completed.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step19-passed.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_TWO_ID}/assess" \
  >/dev/null

echo "EXERCISE_TWO_ID=${EXERCISE_TWO_ID}"

echo "[PASS] RESILIENCE_REVALIDATION_EXERCISE"

echo
echo "===== VALIDATED RESILIENCE STATE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation" \
  >/tmp/terrascope-step19-validated.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-validated.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["completedImprovementCount"] == 1

assert payload["untestedCount"] == 0
assert payload["validatedCount"] == 1
assert payload["needsRevalidationCount"] == 0
assert payload["failedCount"] == 0

assert payload["openCorrectiveActionCount"] == 0
assert payload["inProgressCorrectiveActionCount"] == 0
assert payload["completedCorrectiveActionCount"] == 1

assert payload["averageLatestReadinessScore"] == 95.0

validation = payload["validations"][0]

assert validation["totalExerciseCount"] == 2
assert validation["latestExerciseId"] is not None
assert validation["latestValidationResult"] == "PASSED"
assert validation["latestReadinessScore"] == 95
assert validation["openCorrectiveActionCount"] == 0
assert validation["validationStatus"] == "VALIDATED"

print(
    "[PASS] RESILIENCE_VALIDATED_STATE"
)

print(
    "[PASS] RESILIENCE_REVALIDATION_CYCLE"
)
PY

echo
echo "===== TIMELINE EVIDENCE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step19-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "PREPAREDNESS_EXERCISE_ASSESSED" in types

assert "PREPAREDNESS_CORRECTIVE_ACTION_CREATED" in types
assert types.count(
    "PREPAREDNESS_CORRECTIVE_STATUS_CHANGED"
) == 2

assert "PREPAREDNESS_CORRECTIVE_OWNER_CHANGED" in types
assert "PREPAREDNESS_CORRECTIVE_DUE_CHANGED" in types

print(
    "[PASS] RESILIENCE_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

CORRECTIVE_COLUMNS="$(
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
            'incident_preparedness_corrective_actions';
      " \
    | tr -d '[:space:]'
)"

CORRECTIVE_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_preparedness_corrective_actions
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

EXERCISE_FK_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'incident_preparedness_corrective_actions'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%incident_preparedness_exercises%';
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
            'incident_preparedness_corrective_actions'::regclass
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
            LIKE '%PREPAREDNESS_CORRECTIVE_ACTION_CREATED%'
        AND pg_get_constraintdef(oid)
            LIKE '%PREPAREDNESS_CORRECTIVE_ACTION_DELETED%';
      " \
    | tr -d '[:space:]'
)"

echo "CORRECTIVE_COLUMN_COUNT=${CORRECTIVE_COLUMNS}"
echo "CORRECTIVE_ROW_COUNT=${CORRECTIVE_ROW_COUNT}"
echo "CORRECTIVE_EXERCISE_FK_COUNT=${EXERCISE_FK_COUNT}"
echo "CORRECTIVE_IMPROVEMENT_FK_COUNT=${IMPROVEMENT_FK_COUNT}"
echo "RESILIENCE_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${CORRECTIVE_COLUMNS}" == "13" ]]
[[ "${CORRECTIVE_ROW_COUNT}" == "1" ]]
[[ "${EXERCISE_FK_COUNT}" == "1" ]]
[[ "${IMPROVEMENT_FK_COUNT}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] RESILIENCE_DATABASE_CONTRACT"

echo
echo "===== DELETE CORRECTIVE ACTION ====="

DELETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step19-delete-response.json \
    --write-out '%{http_code}' \
    --request DELETE \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/corrective-actions/${CORRECTIVE_ID}"
)"

echo "CORRECTIVE_DELETE_HTTP=${DELETE_HTTP}"

[[ "${DELETE_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-delete-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCorrectiveActionCount"] == 0
assert payload["correctiveActions"] == []

assert payload["validatedCount"] == 1
assert payload["validations"][0]["validationStatus"] == "VALIDATED"

print(
    "[PASS] CORRECTIVE_ACTION_DELETE"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step19-detail-after-delete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step19-detail-after-delete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "PREPAREDNESS_CORRECTIVE_ACTION_DELETED" in types

print(
    "[PASS] CORRECTIVE_DELETE_TIMELINE"
)
PY

echo
echo "===== CLEAN STEP 19 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step19-incident';
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
            'verification-step19-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_CORRECTIVE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_preparedness_corrective_actions corrective
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                corrective.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_CORRECTIVE_COUNT=${ORPHAN_CORRECTIVE_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_CORRECTIVE_COUNT}" == "0" ]]

echo "[PASS] RESILIENCE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 19 VERIFICATION PASSED"
echo "========================================================================"
