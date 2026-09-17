#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step20-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 20 VERIFICATION"
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

  npx eslint . \
    --max-warnings=0

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
  >/tmp/terrascope-step20-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.20.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
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

echo "[PASS] FLYWAY_V20_RUNTIME"

echo
echo "===== CLEAN STEP 20 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step20-incident';
    " \
  >/dev/null

echo "[PASS] STEP20_PRE_CLEAN"

echo
echo "===== CREATE CERTIFICATION INCIDENT ====="

cat > \
  /tmp/terrascope-step20-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step20-incident",
  "hazardType": "earthquakes",
  "title": "Verification Certification Incident",
  "description": "Step 20 final resilience certification fixture.",
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
  --data @/tmp/terrascope-step20-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step20-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"
echo "[PASS] CERTIFICATION_INCIDENT_CREATE"

echo
echo "===== OPEN INCIDENT READINESS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification/readiness" \
  >/tmp/terrascope-step20-open-readiness.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-open-readiness.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False
assert payload["status"] == "BLOCKED"

codes = {
    blocker["code"]
    for blocker
    in payload["blockers"]
}

assert "INCIDENT_NOT_CLOSED" in codes
assert "AAR_MISSING" in codes
assert "COMPLETED_IMPROVEMENT_MISSING" in codes

print(
    "[PASS] OPEN_INCIDENT_CERTIFICATION_READINESS"
)
PY

OPEN_CERT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step20-open-cert.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification"
)"

echo "OPEN_INCIDENT_CERTIFICATION_HTTP=${OPEN_CERT_HTTP}"

[[ "${OPEN_CERT_HTTP}" == "409" ]]

echo "[PASS] OPEN_INCIDENT_CERTIFICATION_GUARD"

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step20-close.json \
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
  --data @/tmp/terrascope-step20-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

echo "[PASS] CERTIFICATION_INCIDENT_CLOSED"

echo
echo "===== CAPTURE AAR ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step20-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-aar.json",
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
echo "[PASS] CERTIFICATION_AAR_EVIDENCE"

echo
echo "===== CAPTURE FINAL SITREP ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/tmp/terrascope-step20-sitrep.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-sitrep.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportNumber"] == 1
assert payload["incidentStatus"] == "CLOSED"

print(
    "[PASS] CERTIFICATION_SITREP_EVIDENCE"
)
PY

echo
echo "===== CREATE COMPLETED IMPROVEMENT ====="

cat > \
  /tmp/terrascope-step20-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Formalize certified command handoff checklist",
  "description": "Final assurance improvement.",
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
  --data @/tmp/terrascope-step20-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step20-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step20-improvement-complete.json \
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
  --data @/tmp/terrascope-step20-improvement-complete.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

echo "[PASS] CERTIFICATION_IMPROVEMENT_COMPLETED"

echo
echo "===== UNTESTED CERTIFICATION BLOCK ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification/readiness" \
  >/tmp/terrascope-step20-untested.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-untested.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False
assert payload["completedImprovementCount"] == 1
assert payload["untestedCount"] == 1

codes = {
    blocker["code"]
    for blocker
    in payload["blockers"]
}

assert "UNTESTED_IMPROVEMENTS" in codes
assert "READINESS_SCORE_BELOW_THRESHOLD" in codes

print(
    "[PASS] UNTESTED_CERTIFICATION_BLOCK"
)
PY

echo
echo "===== PARTIAL VALIDATION ====="

cat > \
  /tmp/terrascope-step20-exercise-one.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TABLETOP",
  "title": "Initial certification validation",
  "description": "Initial assurance validation.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-01-02T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step20-exercise-one.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step20-exercise-one-response.json

EXERCISE_ONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-exercise-one-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step20-partial.json \
<<'JSON'
{
  "result": "PARTIAL",
  "readinessScore": 65,
  "performedAt": "2030-01-02T12:00:00Z",
  "evidenceSummary": "Most handoff controls passed.",
  "findings": "One mandatory assurance control remained incomplete.",
  "correctiveAction": "Correct the missing assurance control and revalidate."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step20-partial.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ONE_ID}/assess" \
  >/dev/null

echo "[PASS] CERTIFICATION_PARTIAL_VALIDATION"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification/readiness" \
  >/tmp/terrascope-step20-revalidation-block.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-revalidation-block.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False
assert payload["needsRevalidationCount"] == 1

codes = {
    blocker["code"]
    for blocker
    in payload["blockers"]
}

assert "REVALIDATION_REQUIRED" in codes
assert "READINESS_SCORE_BELOW_THRESHOLD" in codes

print(
    "[PASS] REVALIDATION_CERTIFICATION_BLOCK"
)
PY

echo
echo "===== CREATE CORRECTIVE ACTION ====="

cat > \
  /tmp/terrascope-step20-corrective.json \
<<'JSON'
{
  "title": "Close final assurance control gap",
  "description": "Correct the validation finding before certification.",
  "priority": "HIGH",
  "owner": "Preparedness Office",
  "dueAt": "2035-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step20-corrective.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/exercises/${EXERCISE_ONE_ID}/corrective-actions" \
  >/tmp/terrascope-step20-corrective-response.json

CORRECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-corrective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["openCorrectiveActionCount"] == 1

print(
    payload["correctiveActions"][0]["id"]
)
PY
)"

echo "CORRECTIVE_ID=${CORRECTIVE_ID}"

echo "[PASS] CERTIFICATION_CORRECTIVE_ACTION_CREATE"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification/readiness" \
  >/tmp/terrascope-step20-corrective-block.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-corrective-block.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

codes = {
    blocker["code"]
    for blocker
    in payload["blockers"]
}

assert "OPEN_CORRECTIVE_ACTIONS" in codes

print(
    "[PASS] OPEN_CORRECTIVE_CERTIFICATION_BLOCK"
)
PY

echo
echo "===== COMPLETE CORRECTIVE ACTION ====="

cat > \
  /tmp/terrascope-step20-corrective-complete.json \
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
  --data @/tmp/terrascope-step20-corrective-complete.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-validation/corrective-actions/${CORRECTIVE_ID}/status" \
  >/dev/null

echo "[PASS] CERTIFICATION_CORRECTIVE_ACTION_COMPLETED"

echo
echo "===== REVALIDATION EXERCISE ====="

cat > \
  /tmp/terrascope-step20-exercise-two.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Final resilience certification revalidation",
  "description": "Validate the corrected assurance control.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step20-exercise-two.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step20-exercise-two-response.json

EXERCISE_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-exercise-two-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

match = next(
    exercise
    for exercise in payload["exercises"]
    if exercise["title"]
    == "Final resilience certification revalidation"
)

print(
    match["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step20-pass.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 95,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "All assurance controls executed successfully.",
  "findings": "No material validation gaps remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step20-pass.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_TWO_ID}/assess" \
  >/dev/null

echo "[PASS] CERTIFICATION_REVALIDATION_PASSED"

echo
echo "===== READY FOR CERTIFICATION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification/readiness" \
  >/tmp/terrascope-step20-ready.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-ready.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is True
assert payload["status"] == "READY"

assert payload["completedImprovementCount"] == 1

assert payload["validatedCount"] == 1
assert payload["untestedCount"] == 0
assert payload["needsRevalidationCount"] == 0
assert payload["failedCount"] == 0

assert payload["openCorrectiveActionCount"] == 0

assert payload["totalExerciseCount"] == 2
assert payload["passedExerciseCount"] == 1

assert payload["averageLatestReadinessScore"] == 95.0

assert payload["afterActionReviewCount"] == 1
assert payload["sitrepCount"] == 1

assert payload["blockers"] == []

print(
    "[PASS] CERTIFICATION_READY_STATE"
)
PY

echo
echo "===== CERTIFICATION SNAPSHOT #1 ====="

CERT_ONE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step20-cert-one.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification"
)"

echo "CERTIFICATION_ONE_HTTP=${CERT_ONE_HTTP}"

[[ "${CERT_ONE_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-cert-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificationNumber"] == 1
assert payload["status"] == "CERTIFIED"

assert payload["completedImprovementCount"] == 1
assert payload["validatedCount"] == 1

assert payload["untestedCount"] == 0
assert payload["needsRevalidationCount"] == 0
assert payload["failedCount"] == 0
assert payload["openCorrectiveActionCount"] == 0

assert payload["totalExerciseCount"] == 2
assert payload["passedExerciseCount"] == 1
assert payload["averageLatestReadinessScore"] == 95.0

assert payload["afterActionReviewCount"] == 1
assert payload["sitrepCount"] == 1

assert (
    "post-incident assurance lifecycle"
    in payload["executiveSummary"]
)

print(
    "[PASS] RESILIENCE_CERTIFICATION_CREATE"
)
PY

echo
echo "===== CERTIFICATION SNAPSHOT #2 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification" \
  >/tmp/terrascope-step20-cert-two.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-cert-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificationNumber"] == 2
assert payload["status"] == "CERTIFIED"
assert payload["averageLatestReadinessScore"] == 95.0

print(
    "[PASS] RESILIENCE_CERTIFICATION_SEQUENCE"
)
PY

echo
echo "===== CERTIFICATION HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification/history" \
  >/tmp/terrascope-step20-history.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

certifications = payload["certifications"]

assert len(certifications) == 2

assert certifications[0]["certificationNumber"] == 2
assert certifications[1]["certificationNumber"] == 1

assert certifications[0]["status"] == "CERTIFIED"
assert certifications[0]["averageLatestReadinessScore"] == 95.0

print(
    "[PASS] RESILIENCE_CERTIFICATION_HISTORY"
)
PY

echo
echo "===== CERTIFICATION TIMELINE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step20-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step20-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert (
    types.count(
        "RESILIENCE_CERTIFICATION_CREATED"
    )
    == 2
)

assert "AFTER_ACTION_REVIEW_CREATED" in types
assert "IMPROVEMENT_STATUS_CHANGED" in types
assert "PREPAREDNESS_EXERCISE_ASSESSED" in types
assert "PREPAREDNESS_CORRECTIVE_STATUS_CHANGED" in types

print(
    "[PASS] RESILIENCE_CERTIFICATION_TIMELINE"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

CERT_COLUMNS="$(
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
            'incident_resilience_certifications';
      " \
    | tr -d '[:space:]'
)"

CERT_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_resilience_certifications
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

CERT_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_resilience_certification_number';
      " \
    | tr -d '[:space:]'
)"

TIMELINE_CONSTRAINT="$(
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
            LIKE '%RESILIENCE_CERTIFICATION_CREATED%';
      " \
    | tr -d '[:space:]'
)"

echo "CERTIFICATION_COLUMN_COUNT=${CERT_COLUMNS}"
echo "CERTIFICATION_ROW_COUNT=${CERT_ROWS}"
echo "CERTIFICATION_UNIQUE_COUNT=${CERT_UNIQUE}"
echo "CERTIFICATION_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT}"

[[ "${CERT_COLUMNS}" == "17" ]]
[[ "${CERT_ROWS}" == "2" ]]
[[ "${CERT_UNIQUE}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT}" == "1" ]]

echo "[PASS] RESILIENCE_CERTIFICATION_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 20 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step20-incident';
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
            'verification-step20-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_CERTIFICATION_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_resilience_certifications certification
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                certification.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_CERTIFICATION_COUNT=${ORPHAN_CERTIFICATION_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_CERTIFICATION_COUNT}" == "0" ]]

echo "[PASS] CERTIFICATION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 20 VERIFICATION PASSED"
echo "========================================================================"
