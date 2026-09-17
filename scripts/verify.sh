#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step24-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 24 VERIFICATION"
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
  >/tmp/terrascope-step24-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.24.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 21 22 23 24
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

echo "[PASS] FLYWAY_V24_RUNTIME"

echo
echo "===== CLEAN STEP 24 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id IN (
          'verification-step24-open',
          'verification-step24-certified',
          'verification-step24-untested'
      );
    " \
  >/dev/null

echo "[PASS] STEP24_PRE_CLEAN"

echo
echo "===== BASELINE PORTFOLIO SNAPSHOT ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/snapshots" \
  >/tmp/terrascope-step24-baseline-snapshot.json

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-baseline-snapshot.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

echo "BASELINE_SNAPSHOT_ID=${BASELINE_SNAPSHOT_ID}"

echo "[PASS] PORTFOLIO_REVIEW_BASELINE_SNAPSHOT"

echo
echo "===== CREATE OPEN INCIDENT ====="

cat > \
  /tmp/terrascope-step24-open.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step24-open",
  "hazardType": "wildfires",
  "title": "Verification Review Open Incident",
  "description": "Step 24 open incident fixture.",
  "latitude": 36.80,
  "longitude": 10.18,
  "severity": "HIGH",
  "owner": "Operations"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-open.json \
  "http://localhost:8087/api/v1/incidents" \
  >/dev/null

echo "[PASS] PORTFOLIO_REVIEW_OPEN_INCIDENT"

echo
echo "===== CREATE CERTIFIED + AUDITED INCIDENT ====="

cat > \
  /tmp/terrascope-step24-certified.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step24-certified",
  "hazardType": "earthquakes",
  "title": "Verification Review Certified Incident",
  "description": "Step 24 certified incident fixture.",
  "latitude": 36.81,
  "longitude": 10.19,
  "severity": "SEVERE",
  "owner": "Incident Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-certified.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step24-certified-response.json

CERTIFIED_INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-certified-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step24-close.json \
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
  --data @/tmp/terrascope-step24-close.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step24-certified-aar.json

CERTIFIED_AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-certified-aar.json",
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
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/sitrep/reports" \
  >/dev/null

cat > \
  /tmp/terrascope-step24-certified-improvement.json \
<<JSON
{
  "afterActionReviewId": "${CERTIFIED_AAR_ID}",
  "category": "PROCESS",
  "title": "Executive review validated improvement",
  "description": "Certified Step 24 improvement.",
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
  --data @/tmp/terrascope-step24-certified-improvement.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step24-certified-improvement-response.json

CERTIFIED_IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-certified-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step24-complete.json \
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
  --data @/tmp/terrascope-step24-complete.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/improvements/${CERTIFIED_IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step24-exercise.json \
<<JSON
{
  "improvementActionId": "${CERTIFIED_IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Executive review assurance validation",
  "description": "Validate executive portfolio assurance.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-exercise.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step24-exercise-response.json

CERTIFIED_EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step24-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 95,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Executive assurance controls executed successfully.",
  "findings": "No material validation gaps remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-assessment.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/preparedness-exercises/${CERTIFIED_EXERCISE_ID}/assess" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/resilience-certification" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/resilience-evidence/packages" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/resilience-audit-ledger/receipts" \
  >/dev/null

echo "[PASS] PORTFOLIO_REVIEW_CERTIFIED_INCIDENT"

echo
echo "===== CREATE UNTESTED CLOSED INCIDENT ====="

cat > \
  /tmp/terrascope-step24-untested.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step24-untested",
  "hazardType": "severe-storms",
  "title": "Verification Review Untested Incident",
  "description": "Step 24 assurance debt fixture.",
  "latitude": 36.82,
  "longitude": 10.20,
  "severity": "HIGH",
  "owner": "Incident Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-untested.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step24-untested-response.json

UNTESTED_INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-untested-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-close.json \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step24-untested-aar.json

UNTESTED_AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-untested-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step24-untested-improvement.json \
<<JSON
{
  "afterActionReviewId": "${UNTESTED_AAR_ID}",
  "category": "COMMUNICATIONS",
  "title": "Executive review untested improvement",
  "description": "Completed but not validated.",
  "priority": "MEDIUM",
  "owner": "Preparedness Office",
  "dueAt": "2030-03-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-untested-improvement.json \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step24-untested-improvement-response.json

UNTESTED_IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-untested-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step24-complete.json \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/improvements/${UNTESTED_IMPROVEMENT_ID}/status" \
  >/dev/null

echo "[PASS] PORTFOLIO_REVIEW_ASSURANCE_DEBT_FIXTURE"

echo
echo "===== LIVE PORTFOLIO ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio" \
  >/tmp/terrascope-step24-live-portfolio.json

echo
echo "===== REVIEW PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/review-preview" \
  >/tmp/terrascope-step24-review-preview.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-baseline-snapshot.json",
    encoding="utf-8",
) as handle:
    baseline = json.load(handle)

with open(
    "/tmp/terrascope-step24-live-portfolio.json",
    encoding="utf-8",
) as handle:
    current = json.load(handle)

with open(
    "/tmp/terrascope-step24-review-preview.json",
    encoding="utf-8",
) as handle:
    preview = json.load(handle)

def debt(payload):
    return (
        payload["untestedImprovementCount"] * 2
        + payload["needsRevalidationCount"] * 3
        + payload["failedImprovementCount"] * 5
        + payload["openCorrectiveActionCount"] * 2
    )

baseline_debt = debt(
    baseline,
)

current_debt = debt(
    current,
)

score_delta = (
    current["score"]
    - baseline["score"]
)

debt_delta = (
    current_debt
    - baseline_debt
)

if score_delta >= 5:
    trend = "IMPROVING"

elif score_delta <= -5:
    trend = "DECLINING"

elif (
    debt_delta <= -3
    and score_delta >= 0
):
    trend = "IMPROVING"

elif (
    debt_delta >= 3
    and score_delta <= 0
):
    trend = "DECLINING"

else:
    trend = "STABLE"

assert (
    preview["previousSnapshotId"]
    == baseline["id"]
)

assert (
    preview["previousSnapshotNumber"]
    == baseline["snapshotNumber"]
)

assert preview["trend"] == trend

assert (
    preview["currentScore"]
    == current["score"]
)

assert (
    preview["previousScore"]
    == baseline["score"]
)

assert (
    preview["scoreDelta"]
    == score_delta
)

assert (
    preview["currentAssuranceDebt"]
    == current_debt
)

assert (
    preview["previousAssuranceDebt"]
    == baseline_debt
)

assert (
    preview["assuranceDebtDelta"]
    == debt_delta
)

assert (
    preview["certifiedIncidentDelta"]
    == (
        current["certifiedIncidentCount"]
        - baseline["certifiedIncidentCount"]
    )
)

assert (
    preview["validatedImprovementDelta"]
    == (
        current["validatedImprovementCount"]
        - baseline["validatedImprovementCount"]
    )
)

assert (
    preview["auditVerifiedIncidentDelta"]
    == (
        current["auditVerifiedIncidentCount"]
        - baseline["auditVerifiedIncidentCount"]
    )
)

print(
    f"REVIEW_TREND={trend}"
)

print(
    f"REVIEW_SCORE_DELTA={score_delta}"
)

print(
    f"REVIEW_ASSURANCE_DEBT_DELTA={debt_delta}"
)

print(
    "[PASS] PORTFOLIO_REVIEW_PREVIEW"
)

print(
    "[PASS] PORTFOLIO_REVIEW_TREND_AUTHORITY"
)

print(
    "[PASS] PORTFOLIO_REVIEW_DEBT_AUTHORITY"
)
PY

echo
echo "===== CAPTURE EXECUTIVE REVIEW ====="

REVIEW_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step24-review.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/reviews"
)"

echo "PORTFOLIO_REVIEW_HTTP=${REVIEW_HTTP}"

[[ "${REVIEW_HTTP}" == "200" ]]

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-review-preview.json",
    encoding="utf-8",
) as handle:
    preview = json.load(handle)

with open(
    "/tmp/terrascope-step24-review.json",
    encoding="utf-8",
) as handle:
    review = json.load(handle)

assert review["reviewNumber"] >= 1

assert (
    review["previousSnapshotId"]
    == preview["previousSnapshotId"]
)

assert (
    review["previousSnapshotNumber"]
    == preview["previousSnapshotNumber"]
)

assert (
    review["trend"]
    == preview["trend"]
)

assert (
    review["currentScore"]
    == preview["currentScore"]
)

assert (
    review["previousScore"]
    == preview["previousScore"]
)

assert (
    review["scoreDelta"]
    == preview["scoreDelta"]
)

assert (
    review["currentAssuranceDebt"]
    == preview["currentAssuranceDebt"]
)

assert (
    review["previousAssuranceDebt"]
    == preview["previousAssuranceDebt"]
)

assert (
    review["assuranceDebtDelta"]
    == preview["assuranceDebtDelta"]
)

assert (
    review["certifiedIncidentDelta"]
    == preview["certifiedIncidentDelta"]
)

assert (
    review["validatedImprovementDelta"]
    == preview["validatedImprovementDelta"]
)

assert (
    review["auditVerifiedIncidentDelta"]
    == preview["auditVerifiedIncidentDelta"]
)

print(
    review["id"]
)
PY
)"

CURRENT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step24-review.json",
    encoding="utf-8",
) as handle:
    review = json.load(handle)

print(
    review["currentSnapshotId"]
)
PY
)"

echo "REVIEW_ID=${REVIEW_ID}"
echo "CURRENT_SNAPSHOT_ID=${CURRENT_SNAPSHOT_ID}"

echo "[PASS] PORTFOLIO_REVIEW_CREATE"

echo
echo "===== REVIEW HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step24-review-history.json

python3 - "${REVIEW_ID}" <<'PY'
import json
import sys

review_id = sys.argv[1]

with open(
    "/tmp/terrascope-step24-review-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert len(
    payload["reviews"]
) >= 1

assert (
    payload["reviews"][0]["id"]
    == review_id
)

print(
    "[PASS] PORTFOLIO_REVIEW_HISTORY"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

REVIEW_COLUMNS="$(
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
            'resilience_portfolio_reviews';
      " \
    | tr -d '[:space:]'
)"

REVIEW_ROWS="$(
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

CURRENT_SNAPSHOT_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_portfolio_reviews'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%current_snapshot_id%';
      " \
    | tr -d '[:space:]'
)"

REVIEW_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_resilience_portfolio_review_number';
      " \
    | tr -d '[:space:]'
)"

echo "PORTFOLIO_REVIEW_COLUMN_COUNT=${REVIEW_COLUMNS}"
echo "PORTFOLIO_REVIEW_ROW_COUNT=${REVIEW_ROWS}"
echo "PORTFOLIO_REVIEW_CURRENT_SNAPSHOT_FK_COUNT=${CURRENT_SNAPSHOT_FK}"
echo "PORTFOLIO_REVIEW_UNIQUE_COUNT=${REVIEW_UNIQUE}"

[[ "${REVIEW_COLUMNS}" == "18" ]]
[[ "${REVIEW_ROWS}" == "1" ]]
[[ "${CURRENT_SNAPSHOT_FK}" == "1" ]]
[[ "${REVIEW_UNIQUE}" == "1" ]]

echo "[PASS] PORTFOLIO_REVIEW_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 24 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_portfolio_reviews
      WHERE id =
            '${REVIEW_ID}'::uuid;

      DELETE
      FROM resilience_portfolio_snapshots
      WHERE id IN (
          '${CURRENT_SNAPSHOT_ID}'::uuid,
          '${BASELINE_SNAPSHOT_ID}'::uuid
      );

      DELETE
      FROM incident_cases
      WHERE source_event_id IN (
          'verification-step24-open',
          'verification-step24-certified',
          'verification-step24-untested'
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
      WHERE source_event_id IN (
          'verification-step24-open',
          'verification-step24-certified',
          'verification-step24-untested'
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
          '${CURRENT_SNAPSHOT_ID}'::uuid,
          '${BASELINE_SNAPSHOT_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "VERIFICATION_REVIEW_COUNT=${VERIFY_REVIEW_COUNT}"
echo "VERIFICATION_REVIEW_SNAPSHOT_COUNT=${VERIFY_SNAPSHOT_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_REVIEW_COUNT}" == "0" ]]
[[ "${VERIFY_SNAPSHOT_COUNT}" == "0" ]]

echo "[PASS] PORTFOLIO_REVIEW_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 24 VERIFICATION PASSED"
echo "========================================================================"
