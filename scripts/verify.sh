#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step27-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 27 VERIFICATION"
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
  >/tmp/terrascope-step27-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.27.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 \
  21 22 23 24 25 26 27
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

echo "[PASS] FLYWAY_V27_RUNTIME"

echo
echo "===== CLEAN STEP 27 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step27-achieved';
    " \
  >/dev/null

echo "[PASS] STEP27_PRE_CLEAN"

echo
echo "===== EMPTY BENEFIT REALIZATION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/benefit-realization" \
  >/tmp/terrascope-step27-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["band"] == "UNPROVEN"
assert payload["closureReady"] is False

assert payload["totalObjectiveCount"] == 0
assert payload["completedObjectiveCount"] == 0
assert payload["validatedObjectiveCount"] == 0
assert payload["achievedCount"] == 0

assert payload["validationCoveragePct"] == 0.0
assert payload["realizedBenefitRatePct"] == 0.0

print(
    "[PASS] BENEFIT_REALIZATION_EMPTY_STATE"
)
PY

echo
echo "===== CREATE EXECUTIVE BASELINE REVIEW ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step27-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["currentSnapshotId"]
)
PY
)"

echo "REVIEW_ID=${REVIEW_ID}"
echo "BASELINE_SNAPSHOT_ID=${BASELINE_SNAPSHOT_ID}"

echo "[PASS] BENEFIT_REALIZATION_REVIEW_FIXTURE"

echo
echo "===== CREATE + COMPLETE STRATEGIC OBJECTIVE ====="

cat > \
  /tmp/terrascope-step27-objective.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Realize enterprise resilience assurance benefits",
  "description": "Strategic Step 27 enterprise benefit-realization fixture.",
  "priority": "HIGH",
  "owner": "Executive Resilience Office",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Latest enterprise outcome validation demonstrates achieved measurable benefit."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step27-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step27-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-objective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["objectives"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step27-complete.json \
<<'JSON'
{
  "completionEvidence": "Executive governance implementation completed and approved for realized-benefit validation."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step27-complete.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

echo "OBJECTIVE_ID=${OBJECTIVE_ID}"

echo "[PASS] BENEFIT_REALIZATION_OBJECTIVE_FIXTURE"

echo
echo "===== CAPTURE PARTIAL OBJECTIVE VALIDATION ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step27-partial-validation.json

PARTIAL_VALIDATION_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-partial-validation.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["validationNumber"] == 1
assert payload["outcome"] == "PARTIAL"

print(
    payload["validationSnapshotId"]
)
PY
)"

echo "PARTIAL_VALIDATION_SNAPSHOT_ID=${PARTIAL_VALIDATION_SNAPSHOT_ID}"

echo "[PASS] BENEFIT_REALIZATION_PARTIAL_OUTCOME_FIXTURE"

echo
echo "===== REALIZING BENEFIT BOARD ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/benefit-realization" \
  >/tmp/terrascope-step27-realizing.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-realizing.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["band"] == "REALIZING"
assert payload["closureReady"] is False

assert payload["totalObjectiveCount"] == 1

assert payload["plannedObjectiveCount"] == 0
assert payload["inProgressObjectiveCount"] == 0
assert payload["blockedObjectiveCount"] == 0

assert payload["completedObjectiveCount"] == 1

assert payload["validatedObjectiveCount"] == 1
assert payload["unvalidatedCompletedObjectiveCount"] == 0

assert payload["achievedCount"] == 0
assert payload["partialCount"] == 1
assert payload["notAchievedCount"] == 0

assert payload["validationCoveragePct"] == 100.0
assert payload["realizedBenefitRatePct"] == 0.0

print(
    "[PASS] BENEFIT_REALIZATION_REALIZING_STATE"
)

print(
    "[PASS] BENEFIT_REALIZATION_VALIDATION_COVERAGE"
)
PY

echo
echo "===== SNAPSHOT REALIZING STATE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/benefit-realization/snapshots" \
  >/tmp/terrascope-step27-snapshot-one.json

BENEFIT_SNAPSHOT_ONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-snapshot-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["snapshotNumber"] == 1
assert payload["band"] == "REALIZING"
assert payload["closureReady"] is False

print(
    payload["id"]
)
PY
)"

echo "BENEFIT_SNAPSHOT_ONE_ID=${BENEFIT_SNAPSHOT_ONE_ID}"

echo "[PASS] BENEFIT_REALIZATION_SNAPSHOT_ONE"

echo
echo "===== CREATE ACHIEVED BENEFIT FIXTURE ====="

cat > \
  /tmp/terrascope-step27-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step27-achieved",
  "hazardType": "earthquakes",
  "title": "Verification Step 27 Achieved Benefit Incident",
  "description": "Enterprise benefit realization fixture.",
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
  --data @/tmp/terrascope-step27-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step27-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step27-close.json \
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
  --data @/tmp/terrascope-step27-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step27-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-aar.json",
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
  /tmp/terrascope-step27-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Institutionalize enterprise benefit assurance",
  "description": "Step 27 realized-benefit fixture improvement.",
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
  --data @/tmp/terrascope-step27-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step27-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step27-complete-improvement.json \
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
  --data @/tmp/terrascope-step27-complete-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step27-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Enterprise benefit realization exercise",
  "description": "Validate enterprise benefit realization.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step27-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step27-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step27-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 97,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Enterprise benefit realization controls were successfully exercised.",
  "findings": "No material assurance gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step27-assessment.json \
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

echo "[PASS] BENEFIT_REALIZATION_ACHIEVED_FIXTURE"

echo
echo "===== CAPTURE ACHIEVED OUTCOME ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step27-achieved-validation.json

ACHIEVED_VALIDATION_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-achieved-validation.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["validationNumber"] == 2
assert payload["outcome"] == "ACHIEVED"
assert payload["scoreDelta"] >= 5

print(
    payload["validationSnapshotId"]
)
PY
)"

echo "ACHIEVED_VALIDATION_SNAPSHOT_ID=${ACHIEVED_VALIDATION_SNAPSHOT_ID}"

echo "[PASS] BENEFIT_REALIZATION_ACHIEVED_OUTCOME"

echo
echo "===== REALIZED BENEFIT BOARD ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/benefit-realization" \
  >/tmp/terrascope-step27-realized.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-realized.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["band"] == "REALIZED"

assert payload["closureReady"] is True

assert payload["totalObjectiveCount"] == 1

assert payload["plannedObjectiveCount"] == 0
assert payload["inProgressObjectiveCount"] == 0
assert payload["blockedObjectiveCount"] == 0
assert payload["completedObjectiveCount"] == 1

assert payload["validatedObjectiveCount"] == 1
assert payload["unvalidatedCompletedObjectiveCount"] == 0

assert payload["achievedCount"] == 1
assert payload["partialCount"] == 0
assert payload["notAchievedCount"] == 0

assert payload["validationCoveragePct"] == 100.0
assert payload["realizedBenefitRatePct"] == 100.0

assert payload["averageScoreDelta"] >= 5.0

print(
    f"REALIZATION_BAND={payload['band']}"
)

print(
    f"REALIZATION_VALIDATION_COVERAGE={payload['validationCoveragePct']}"
)

print(
    f"REALIZATION_BENEFIT_RATE={payload['realizedBenefitRatePct']}"
)

print(
    f"REALIZATION_AVG_SCORE_DELTA={payload['averageScoreDelta']}"
)

print(
    f"REALIZATION_AVG_DEBT_DELTA={payload['averageAssuranceDebtDelta']}"
)

print(
    "[PASS] BENEFIT_REALIZATION_REALIZED_STATE"
)

print(
    "[PASS] BENEFIT_REALIZATION_GOVERNANCE_CLOSURE"
)

print(
    "[PASS] BENEFIT_REALIZATION_LATEST_VALIDATION_AUTHORITY"
)
PY

echo
echo "===== SNAPSHOT REALIZED STATE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/benefit-realization/snapshots" \
  >/tmp/terrascope-step27-snapshot-two.json

BENEFIT_SNAPSHOT_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step27-snapshot-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["snapshotNumber"] == 2

assert payload["band"] == "REALIZED"
assert payload["closureReady"] is True

assert payload["validationCoveragePct"] == 100.0
assert payload["realizedBenefitRatePct"] == 100.0

print(
    payload["id"]
)
PY
)"

echo "BENEFIT_SNAPSHOT_TWO_ID=${BENEFIT_SNAPSHOT_TWO_ID}"

echo "[PASS] BENEFIT_REALIZATION_SNAPSHOT_TWO"

echo
echo "===== BENEFIT REALIZATION HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/benefit-realization/history" \
  >/tmp/terrascope-step27-history.json

python3 \
  - "${BENEFIT_SNAPSHOT_ONE_ID}" \
  "${BENEFIT_SNAPSHOT_TWO_ID}" \
<<'PY'
import json
import sys

first_id = sys.argv[1]
second_id = sys.argv[2]

with open(
    "/tmp/terrascope-step27-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

snapshots = payload["snapshots"]

assert len(snapshots) == 2

assert snapshots[0]["id"] == second_id
assert snapshots[0]["snapshotNumber"] == 2
assert snapshots[0]["band"] == "REALIZED"
assert snapshots[0]["closureReady"] is True

assert snapshots[1]["id"] == first_id
assert snapshots[1]["snapshotNumber"] == 1
assert snapshots[1]["band"] == "REALIZING"
assert snapshots[1]["closureReady"] is False

print(
    "[PASS] BENEFIT_REALIZATION_HISTORY"
)

print(
    "[PASS] BENEFIT_REALIZATION_TRANSITION_HISTORY"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

BENEFIT_COLUMNS="$(
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
            'resilience_benefit_realization_snapshots';
      " \
    | tr -d '[:space:]'
)"

BENEFIT_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_benefit_realization_snapshots
      WHERE id IN (
          '${BENEFIT_SNAPSHOT_ONE_ID}'::uuid,
          '${BENEFIT_SNAPSHOT_TWO_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

BENEFIT_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_benefit_realization_snapshot_number';
      " \
    | tr -d '[:space:]'
)"

BAND_CONSTRAINT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_benefit_realization_snapshots'::regclass
        AND conname =
            'ck_benefit_realization_band';
      " \
    | tr -d '[:space:]'
)"

echo "BENEFIT_REALIZATION_COLUMN_COUNT=${BENEFIT_COLUMNS}"
echo "BENEFIT_REALIZATION_ROW_COUNT=${BENEFIT_ROWS}"
echo "BENEFIT_REALIZATION_UNIQUE_COUNT=${BENEFIT_UNIQUE}"
echo "BENEFIT_REALIZATION_BAND_CONSTRAINT_COUNT=${BAND_CONSTRAINT}"

[[ "${BENEFIT_COLUMNS}" == "20" ]]
[[ "${BENEFIT_ROWS}" == "2" ]]
[[ "${BENEFIT_UNIQUE}" == "1" ]]
[[ "${BAND_CONSTRAINT}" == "1" ]]

echo "[PASS] BENEFIT_REALIZATION_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 27 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_benefit_realization_snapshots
      WHERE id IN (
          '${BENEFIT_SNAPSHOT_ONE_ID}'::uuid,
          '${BENEFIT_SNAPSHOT_TWO_ID}'::uuid
      );

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step27-achieved';

      DELETE
      FROM resilience_portfolio_reviews
      WHERE id =
            '${REVIEW_ID}'::uuid;

      DELETE
      FROM resilience_portfolio_snapshots
      WHERE id IN (
          '${BASELINE_SNAPSHOT_ID}'::uuid,
          '${PARTIAL_VALIDATION_SNAPSHOT_ID}'::uuid,
          '${ACHIEVED_VALIDATION_SNAPSHOT_ID}'::uuid
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
            'verification-step27-achieved';
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

VERIFY_BENEFIT_SNAPSHOT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_benefit_realization_snapshots
      WHERE id IN (
          '${BENEFIT_SNAPSHOT_ONE_ID}'::uuid,
          '${BENEFIT_SNAPSHOT_TWO_ID}'::uuid
      );
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

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "VERIFICATION_OBJECTIVE_COUNT=${VERIFY_OBJECTIVE_COUNT}"
echo "VERIFICATION_OBJECTIVE_VALIDATION_COUNT=${VERIFY_VALIDATION_COUNT}"
echo "VERIFICATION_BENEFIT_SNAPSHOT_COUNT=${VERIFY_BENEFIT_SNAPSHOT_COUNT}"
echo "VERIFICATION_REVIEW_COUNT=${VERIFY_REVIEW_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_OBJECTIVE_COUNT}" == "0" ]]
[[ "${VERIFY_VALIDATION_COUNT}" == "0" ]]
[[ "${VERIFY_BENEFIT_SNAPSHOT_COUNT}" == "0" ]]
[[ "${VERIFY_REVIEW_COUNT}" == "0" ]]

echo "[PASS] BENEFIT_REALIZATION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 27 VERIFICATION PASSED"
echo "========================================================================"
