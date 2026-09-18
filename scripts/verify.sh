#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step26-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 26 VERIFICATION"
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
  >/tmp/terrascope-step26-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.26.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 \
  21 22 23 24 25 26
do
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

echo "[PASS] FLYWAY_V26_RUNTIME"

echo
echo "===== CLEAN STEP 26 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step26-achieved';
    " \
  >/dev/null

echo "[PASS] STEP26_PRE_CLEAN"

echo
echo "===== CREATE BASELINE EXECUTIVE REVIEW ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step26-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

REVIEW_NUMBER="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["reviewNumber"]
)
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["currentSnapshotId"]
)
PY
)"

BASELINE_SNAPSHOT_NUMBER="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["currentSnapshotNumber"]
)
PY
)"

echo "REVIEW_ID=${REVIEW_ID}"
echo "REVIEW_NUMBER=${REVIEW_NUMBER}"
echo "BASELINE_SNAPSHOT_ID=${BASELINE_SNAPSHOT_ID}"
echo "BASELINE_SNAPSHOT_NUMBER=${BASELINE_SNAPSHOT_NUMBER}"

echo "[PASS] OUTCOME_BASELINE_REVIEW"

echo
echo "===== CREATE STRATEGIC OBJECTIVE ====="

cat > \
  /tmp/terrascope-step26-objective.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Increase certified and verified portfolio assurance",
  "description": "Use the executive governance plan to raise certification, validation, and tamper-evident audit coverage.",
  "priority": "HIGH",
  "owner": "Executive Resilience Office",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Portfolio assurance demonstrates measurable certification and verified audit growth."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step26-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step26-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-objective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["objectives"][0]["id"]
)
PY
)"

echo "OBJECTIVE_ID=${OBJECTIVE_ID}"

echo "[PASS] OUTCOME_OBJECTIVE_CREATE"

echo
echo "===== INCOMPLETE OBJECTIVE OUTCOME GUARD ====="

INCOMPLETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step26-incomplete.json \
    --write-out '%{http_code}' \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes/preview"
)"

echo "INCOMPLETE_OBJECTIVE_OUTCOME_HTTP=${INCOMPLETE_HTTP}"

[[ "${INCOMPLETE_HTTP}" == "409" ]]

echo "[PASS] OUTCOME_COMPLETED_OBJECTIVE_GUARD"

echo
echo "===== COMPLETE STRATEGIC OBJECTIVE ====="

cat > \
  /tmp/terrascope-step26-complete.json \
<<'JSON'
{
  "completionEvidence": "Executive governance implementation was completed and approved for post-completion benefit validation."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step26-complete.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

echo "[PASS] OUTCOME_OBJECTIVE_COMPLETED"

echo
echo "===== PARTIAL OUTCOME PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes/preview" \
  >/tmp/terrascope-step26-partial-preview.json

python3 \
  - "${OBJECTIVE_ID}" \
  "${BASELINE_SNAPSHOT_ID}" \
  "${BASELINE_SNAPSHOT_NUMBER}" \
<<'PY'
import json
import sys

objective_id = sys.argv[1]
baseline_id = sys.argv[2]
baseline_number = int(
    sys.argv[3]
)

with open(
    "/tmp/terrascope-step26-partial-preview.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["objectiveId"] == objective_id

assert payload["baselineSnapshotId"] == baseline_id

assert (
    payload["baselineSnapshotNumber"]
    == baseline_number
)

assert payload["scoreDelta"] == 0
assert payload["assuranceDebtDelta"] == 0

assert payload["certifiedIncidentDelta"] == 0
assert payload["validatedImprovementDelta"] == 0
assert payload["auditVerifiedIncidentDelta"] == 0

assert payload["outcome"] == "PARTIAL"

print(
    "[PASS] OUTCOME_PARTIAL_PREVIEW"
)

print(
    "[PASS] OUTCOME_BASELINE_LINKAGE"
)
PY

echo
echo "===== CAPTURE PARTIAL VALIDATION #1 ====="

VALIDATION_ONE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step26-validation-one.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes"
)"

echo "OUTCOME_VALIDATION_ONE_HTTP=${VALIDATION_ONE_HTTP}"

[[ "${VALIDATION_ONE_HTTP}" == "200" ]]

VALIDATION_ONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-validation-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["validationNumber"] == 1
assert payload["outcome"] == "PARTIAL"

assert payload["scoreDelta"] == 0
assert payload["assuranceDebtDelta"] == 0

print(
    payload["id"]
)
PY
)"

VALIDATION_ONE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-validation-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["validationSnapshotId"]
)
PY
)"

echo "VALIDATION_ONE_ID=${VALIDATION_ONE_ID}"
echo "VALIDATION_ONE_SNAPSHOT_ID=${VALIDATION_ONE_SNAPSHOT_ID}"

echo "[PASS] OUTCOME_PARTIAL_VALIDATION"

echo
echo "===== CREATE ACHIEVED BENEFIT FIXTURE ====="

cat > \
  /tmp/terrascope-step26-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step26-achieved",
  "hazardType": "earthquakes",
  "title": "Verification Outcome Achieved Incident",
  "description": "Step 26 realized-benefit verification fixture.",
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
  --data @/tmp/terrascope-step26-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step26-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step26-close.json \
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
  --data @/tmp/terrascope-step26-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step26-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/dev/null

cat > \
  /tmp/terrascope-step26-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Institutionalize validated enterprise resilience assurance",
  "description": "Benefit-realization fixture improvement.",
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
  --data @/tmp/terrascope-step26-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step26-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step26-complete-improvement.json \
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
  --data @/tmp/terrascope-step26-complete-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step26-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Benefit realization validation exercise",
  "description": "Validate realized strategic resilience benefit.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step26-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step26-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step26-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 96,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Strategic resilience benefits were operationally validated.",
  "findings": "No material assurance gaps remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step26-assessment.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/assess" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/receipts" \
  >/dev/null

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] OUTCOME_ACHIEVED_FIXTURE"

echo
echo "===== ACHIEVED OUTCOME PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes/preview" \
  >/tmp/terrascope-step26-achieved-preview.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-achieved-preview.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["outcome"] == "ACHIEVED"

assert payload["scoreDelta"] >= 5

assert payload["certifiedIncidentDelta"] >= 1
assert payload["validatedImprovementDelta"] >= 1
assert payload["auditVerifiedIncidentDelta"] >= 1

assert (
    "classified ACHIEVED"
    in payload["executiveSummary"]
)

print(
    f"ACHIEVED_SCORE_DELTA={payload['scoreDelta']}"
)

print(
    f"ACHIEVED_ASSURANCE_DEBT_DELTA={payload['assuranceDebtDelta']}"
)

print(
    "[PASS] OUTCOME_ACHIEVED_PREVIEW"
)
PY

echo
echo "===== CAPTURE ACHIEVED VALIDATION #2 ====="

VALIDATION_TWO_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step26-validation-two.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes"
)"

echo "OUTCOME_VALIDATION_TWO_HTTP=${VALIDATION_TWO_HTTP}"

[[ "${VALIDATION_TWO_HTTP}" == "200" ]]

VALIDATION_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-validation-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["validationNumber"] == 2
assert payload["outcome"] == "ACHIEVED"

assert payload["scoreDelta"] >= 5

assert payload["certifiedIncidentDelta"] >= 1
assert payload["validatedImprovementDelta"] >= 1
assert payload["auditVerifiedIncidentDelta"] >= 1

print(
    payload["id"]
)
PY
)"

VALIDATION_TWO_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-validation-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["validationSnapshotId"]
)
PY
)"

echo "VALIDATION_TWO_ID=${VALIDATION_TWO_ID}"
echo "VALIDATION_TWO_SNAPSHOT_ID=${VALIDATION_TWO_SNAPSHOT_ID}"

echo "[PASS] OUTCOME_ACHIEVED_VALIDATION"

echo
echo "===== VALIDATION HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step26-validation-history.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-validation-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

validations = payload["validations"]

assert len(validations) == 2

assert validations[0]["validationNumber"] == 2
assert validations[0]["outcome"] == "ACHIEVED"

assert validations[1]["validationNumber"] == 1
assert validations[1]["outcome"] == "PARTIAL"

print(
    "[PASS] OUTCOME_VALIDATION_HISTORY"
)

print(
    "[PASS] OUTCOME_SEQUENTIAL_VALIDATIONS"
)
PY

echo
echo "===== OBJECTIVE HISTORY EVIDENCE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/history" \
  >/tmp/terrascope-step26-objective-history.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step26-objective-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["events"]
]

assert types.count(
    "OBJECTIVE_OUTCOME_VALIDATED"
) == 2

assert "OBJECTIVE_CREATED" in types
assert "OBJECTIVE_COMPLETED" in types

print(
    "[PASS] OUTCOME_OBJECTIVE_HISTORY_EVIDENCE"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

VALIDATION_COLUMNS="$(
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
            'resilience_portfolio_objective_validations';
      " \
    | tr -d '[:space:]'
)"

VALIDATION_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objective_validations
      WHERE objective_id =
            '${OBJECTIVE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

OBJECTIVE_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_portfolio_objective_validations'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_portfolio_objectives%';
      " \
    | tr -d '[:space:]'
)"

BASELINE_SNAPSHOT_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_portfolio_objective_validations'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%baseline_snapshot_id%';
      " \
    | tr -d '[:space:]'
)"

VALIDATION_SNAPSHOT_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_portfolio_objective_validations'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%validation_snapshot_id%';
      " \
    | tr -d '[:space:]'
)"

VALIDATION_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_portfolio_objective_validation_number';
      " \
    | tr -d '[:space:]'
)"

EVENT_CONSTRAINT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_portfolio_objective_events'::regclass
        AND conname =
            'ck_resilience_portfolio_objective_event_type'
        AND pg_get_constraintdef(oid)
            LIKE '%OBJECTIVE_OUTCOME_VALIDATED%';
      " \
    | tr -d '[:space:]'
)"

echo "OBJECTIVE_VALIDATION_COLUMN_COUNT=${VALIDATION_COLUMNS}"
echo "OBJECTIVE_VALIDATION_ROW_COUNT=${VALIDATION_ROWS}"
echo "OBJECTIVE_VALIDATION_OBJECTIVE_FK_COUNT=${OBJECTIVE_FK}"
echo "OBJECTIVE_VALIDATION_BASELINE_SNAPSHOT_FK_COUNT=${BASELINE_SNAPSHOT_FK}"
echo "OBJECTIVE_VALIDATION_CURRENT_SNAPSHOT_FK_COUNT=${VALIDATION_SNAPSHOT_FK}"
echo "OBJECTIVE_VALIDATION_UNIQUE_COUNT=${VALIDATION_UNIQUE}"
echo "OBJECTIVE_VALIDATION_EVENT_CONSTRAINT_COUNT=${EVENT_CONSTRAINT}"

[[ "${VALIDATION_COLUMNS}" == "19" ]]
[[ "${VALIDATION_ROWS}" == "2" ]]
[[ "${OBJECTIVE_FK}" == "1" ]]
[[ "${BASELINE_SNAPSHOT_FK}" == "1" ]]
[[ "${VALIDATION_SNAPSHOT_FK}" == "1" ]]
[[ "${VALIDATION_UNIQUE}" == "1" ]]
[[ "${EVENT_CONSTRAINT}" == "1" ]]

echo "[PASS] OUTCOME_VALIDATION_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 26 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step26-achieved';

      DELETE
      FROM resilience_portfolio_reviews
      WHERE id =
            '${REVIEW_ID}'::uuid;

      DELETE
      FROM resilience_portfolio_snapshots
      WHERE id IN (
          '${BASELINE_SNAPSHOT_ID}'::uuid,
          '${VALIDATION_ONE_SNAPSHOT_ID}'::uuid,
          '${VALIDATION_TWO_SNAPSHOT_ID}'::uuid
      );
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
            'verification-step26-achieved';
      " \
    | tr -d '[:space:]'
)"

VERIFY_OBJECTIVE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objectives
      WHERE id =
            '${OBJECTIVE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

VERIFY_VALIDATION_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objective_validations
      WHERE objective_id =
            '${OBJECTIVE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

VERIFY_REVIEW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_reviews
      WHERE id =
            '${REVIEW_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

VERIFY_SNAPSHOT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_snapshots
      WHERE id IN (
          '${BASELINE_SNAPSHOT_ID}'::uuid,
          '${VALIDATION_ONE_SNAPSHOT_ID}'::uuid,
          '${VALIDATION_TWO_SNAPSHOT_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "VERIFICATION_OBJECTIVE_COUNT=${VERIFY_OBJECTIVE_COUNT}"
echo "VERIFICATION_OBJECTIVE_VALIDATION_COUNT=${VERIFY_VALIDATION_COUNT}"
echo "VERIFICATION_REVIEW_COUNT=${VERIFY_REVIEW_COUNT}"
echo "VERIFICATION_OUTCOME_SNAPSHOT_COUNT=${VERIFY_SNAPSHOT_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_OBJECTIVE_COUNT}" == "0" ]]
[[ "${VERIFY_VALIDATION_COUNT}" == "0" ]]
[[ "${VERIFY_REVIEW_COUNT}" == "0" ]]
[[ "${VERIFY_SNAPSHOT_COUNT}" == "0" ]]

echo "[PASS] OUTCOME_VALIDATION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 26 VERIFICATION PASSED"
echo "========================================================================"
