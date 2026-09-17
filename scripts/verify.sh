#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step23-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 23 VERIFICATION"
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
  >/tmp/terrascope-step23-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.23.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 21 22 23
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

echo "[PASS] FLYWAY_V23_RUNTIME"

echo
echo "===== CLEAN STEP 23 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id IN (
          'verification-step23-open',
          'verification-step23-certified',
          'verification-step23-untested'
      );
    " \
  >/dev/null

echo "[PASS] STEP23_PRE_CLEAN"

echo
echo "===== CAPTURE BASELINE PORTFOLIO ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio" \
  >/tmp/terrascope-step23-baseline.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-baseline.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert 0 <= payload["score"] <= 100

assert payload["band"] in {
    "FOUNDATIONAL",
    "DEVELOPING",
    "ASSURED",
    "HIGH_ASSURANCE",
}

print(
    "[PASS] PORTFOLIO_BASELINE"
)
PY

echo
echo "===== CREATE OPEN INCIDENT ====="

cat > \
  /tmp/terrascope-step23-open.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step23-open",
  "hazardType": "wildfires",
  "title": "Verification Open Portfolio Incident",
  "description": "Open incident portfolio fixture.",
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
  --data @/tmp/terrascope-step23-open.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step23-open-response.json

OPEN_INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-open-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "OPEN_INCIDENT_ID=${OPEN_INCIDENT_ID}"

echo "[PASS] PORTFOLIO_OPEN_INCIDENT"

echo
echo "===== CREATE CERTIFIED INCIDENT ====="

cat > \
  /tmp/terrascope-step23-certified.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step23-certified",
  "hazardType": "earthquakes",
  "title": "Verification Certified Portfolio Incident",
  "description": "Certified portfolio fixture.",
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
  --data @/tmp/terrascope-step23-certified.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step23-certified-response.json

CERTIFIED_INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-certified-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step23-close.json \
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
  --data @/tmp/terrascope-step23-close.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step23-certified-aar.json

CERTIFIED_AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-certified-aar.json",
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
  /tmp/terrascope-step23-certified-improvement.json \
<<JSON
{
  "afterActionReviewId": "${CERTIFIED_AAR_ID}",
  "category": "PROCESS",
  "title": "Validate portfolio assurance process",
  "description": "Certified portfolio fixture improvement.",
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
  --data @/tmp/terrascope-step23-certified-improvement.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step23-certified-improvement-response.json

CERTIFIED_IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-certified-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step23-complete.json \
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
  --data @/tmp/terrascope-step23-complete.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/improvements/${CERTIFIED_IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step23-exercise.json \
<<JSON
{
  "improvementActionId": "${CERTIFIED_IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Portfolio assurance validation exercise",
  "description": "Validate enterprise resilience assurance.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step23-exercise.json \
  "http://localhost:8087/api/v1/incidents/${CERTIFIED_INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step23-exercise-response.json

CERTIFIED_EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step23-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 95,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Enterprise assurance controls executed successfully.",
  "findings": "No material validation gaps remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step23-assessment.json \
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

echo "CERTIFIED_INCIDENT_ID=${CERTIFIED_INCIDENT_ID}"

echo "[PASS] PORTFOLIO_CERTIFIED_INCIDENT"

echo
echo "===== CREATE CLOSED UNTESTED INCIDENT ====="

cat > \
  /tmp/terrascope-step23-untested.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step23-untested",
  "hazardType": "severe-storms",
  "title": "Verification Untested Portfolio Incident",
  "description": "Untested assurance-debt fixture.",
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
  --data @/tmp/terrascope-step23-untested.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step23-untested-response.json

UNTESTED_INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-untested-response.json",
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
  --data @/tmp/terrascope-step23-close.json \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step23-untested-aar.json

UNTESTED_AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-untested-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step23-untested-improvement.json \
<<JSON
{
  "afterActionReviewId": "${UNTESTED_AAR_ID}",
  "category": "COMMUNICATIONS",
  "title": "Untested portfolio improvement",
  "description": "Completed but not yet validated.",
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
  --data @/tmp/terrascope-step23-untested-improvement.json \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step23-untested-improvement-response.json

UNTESTED_IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-untested-improvement-response.json",
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
  --data @/tmp/terrascope-step23-complete.json \
  "http://localhost:8087/api/v1/incidents/${UNTESTED_INCIDENT_ID}/improvements/${UNTESTED_IMPROVEMENT_ID}/status" \
  >/dev/null

echo "UNTESTED_INCIDENT_ID=${UNTESTED_INCIDENT_ID}"

echo "[PASS] PORTFOLIO_UNTESTED_INCIDENT"

echo
echo "===== PORTFOLIO DELTA CONTRACT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio" \
  >/tmp/terrascope-step23-portfolio.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-baseline.json",
    encoding="utf-8",
) as handle:
    baseline = json.load(handle)

with open(
    "/tmp/terrascope-step23-portfolio.json",
    encoding="utf-8",
) as handle:
    current = json.load(handle)

assert (
    current["totalIncidentCount"]
    == baseline["totalIncidentCount"] + 3
)

assert (
    current["openIncidentCount"]
    == baseline["openIncidentCount"] + 1
)

assert (
    current["closedIncidentCount"]
    == baseline["closedIncidentCount"] + 2
)

assert (
    current["certifiedIncidentCount"]
    == baseline["certifiedIncidentCount"] + 1
)

assert (
    current["evidencePackagedIncidentCount"]
    == baseline["evidencePackagedIncidentCount"] + 1
)

assert (
    current["auditLedgerIncidentCount"]
    == baseline["auditLedgerIncidentCount"] + 1
)

assert (
    current["auditVerifiedIncidentCount"]
    == baseline["auditVerifiedIncidentCount"] + 1
)

assert (
    current["completedImprovementCount"]
    == baseline["completedImprovementCount"] + 2
)

assert (
    current["validatedImprovementCount"]
    == baseline["validatedImprovementCount"] + 1
)

assert (
    current["untestedImprovementCount"]
    == baseline["untestedImprovementCount"] + 1
)

assert (
    current["totalExerciseCount"]
    == baseline["totalExerciseCount"] + 1
)

assert (
    current["completedExerciseCount"]
    == baseline["completedExerciseCount"] + 1
)

assert (
    current["passedExerciseCount"]
    == baseline["passedExerciseCount"] + 1
)

assert 0 <= current["score"] <= 100

assert current["band"] in {
    "FOUNDATIONAL",
    "DEVELOPING",
    "ASSURED",
    "HIGH_ASSURANCE",
}

assert (
    0.0
    <= current["averageLatestReadinessScore"]
    <= 100.0
)

print(
    "[PASS] PORTFOLIO_AGGREGATION_CONTRACT"
)

print(
    "[PASS] PORTFOLIO_AUDIT_VERIFICATION_CONTRACT"
)

print(
    "[PASS] PORTFOLIO_ASSURANCE_DEBT_CONTRACT"
)
PY

echo
echo "===== PORTFOLIO SCORE AUTHORITY ====="

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-portfolio.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

def weighted(weight, numerator, denominator):
    if denominator <= 0:
        return 0

    ratio = min(
        1.0,
        max(
            0.0,
            numerator / denominator,
        ),
    )

    return int(
        weight * ratio
        + 0.5
    )

score = 0

score += weighted(
    35,
    payload["certifiedIncidentCount"],
    payload["closedIncidentCount"],
)

score += weighted(
    20,
    payload["evidencePackagedIncidentCount"],
    payload["closedIncidentCount"],
)

score += weighted(
    15,
    payload["auditVerifiedIncidentCount"],
    payload["closedIncidentCount"],
)

score += weighted(
    20,
    payload["validatedImprovementCount"],
    payload["completedImprovementCount"],
)

score += weighted(
    10,
    payload["passedExerciseCount"],
    payload["completedExerciseCount"],
)

debt = (
    payload["untestedImprovementCount"] * 2
    + payload["needsRevalidationCount"] * 3
    + payload["failedImprovementCount"] * 5
    + payload["openCorrectiveActionCount"] * 2
)

penalty = min(
    20,
    debt,
)

score = max(
    0,
    min(
        100,
        score - penalty,
    ),
)

assert payload["score"] == score

if score >= 80:
    expected_band = "HIGH_ASSURANCE"

elif score >= 60:
    expected_band = "ASSURED"

elif score >= 40:
    expected_band = "DEVELOPING"

else:
    expected_band = "FOUNDATIONAL"

assert payload["band"] == expected_band

print(
    f"PORTFOLIO_SCORE={score}"
)

print(
    f"PORTFOLIO_BAND={expected_band}"
)

print(
    "[PASS] PORTFOLIO_SCORE_AUTHORITY"
)
PY

echo
echo "===== CREATE PORTFOLIO SNAPSHOT ====="

SNAPSHOT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step23-snapshot.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/snapshots"
)"

echo "PORTFOLIO_SNAPSHOT_HTTP=${SNAPSHOT_HTTP}"

[[ "${SNAPSHOT_HTTP}" == "200" ]]

SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step23-portfolio.json",
    encoding="utf-8",
) as handle:
    live = json.load(handle)

with open(
    "/tmp/terrascope-step23-snapshot.json",
    encoding="utf-8",
) as handle:
    snapshot = json.load(handle)

assert snapshot["snapshotNumber"] >= 1

for field in (
    "score",
    "band",

    "totalIncidentCount",
    "openIncidentCount",
    "closedIncidentCount",

    "certifiedIncidentCount",
    "evidencePackagedIncidentCount",
    "auditLedgerIncidentCount",
    "auditVerifiedIncidentCount",

    "completedImprovementCount",
    "validatedImprovementCount",
    "untestedImprovementCount",
    "needsRevalidationCount",
    "failedImprovementCount",

    "openCorrectiveActionCount",

    "totalExerciseCount",
    "completedExerciseCount",
    "passedExerciseCount",

    "averageLatestReadinessScore",
):
    assert snapshot[field] == live[field], (
        field,
        snapshot[field],
        live[field],
    )

print(
    snapshot["id"]
)
PY
)"

echo "SNAPSHOT_ID=${SNAPSHOT_ID}"

echo "[PASS] PORTFOLIO_SNAPSHOT_CREATE"

echo
echo "===== PORTFOLIO HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/history" \
  >/tmp/terrascope-step23-history.json

python3 - "${SNAPSHOT_ID}" <<'PY'
import json
import sys

snapshot_id = sys.argv[1]

with open(
    "/tmp/terrascope-step23-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert len(
    payload["snapshots"]
) >= 1

assert (
    payload["snapshots"][0]["id"]
    == snapshot_id
)

print(
    "[PASS] PORTFOLIO_SNAPSHOT_HISTORY"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

PORTFOLIO_COLUMNS="$(
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
            'resilience_portfolio_snapshots';
      " \
    | tr -d '[:space:]'
)"

SNAPSHOT_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_snapshots
      WHERE id =
            '${SNAPSHOT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

SNAPSHOT_UNIQUE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_resilience_portfolio_snapshot_number';
      " \
    | tr -d '[:space:]'
)"

echo "PORTFOLIO_COLUMN_COUNT=${PORTFOLIO_COLUMNS}"
echo "PORTFOLIO_SNAPSHOT_ROW_COUNT=${SNAPSHOT_ROW_COUNT}"
echo "PORTFOLIO_SNAPSHOT_UNIQUE_COUNT=${SNAPSHOT_UNIQUE_COUNT}"

[[ "${PORTFOLIO_COLUMNS}" == "22" ]]
[[ "${SNAPSHOT_ROW_COUNT}" == "1" ]]
[[ "${SNAPSHOT_UNIQUE_COUNT}" == "1" ]]

echo "[PASS] PORTFOLIO_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 23 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_portfolio_snapshots
      WHERE id =
            '${SNAPSHOT_ID}'::uuid;

      DELETE
      FROM incident_cases
      WHERE source_event_id IN (
          'verification-step23-open',
          'verification-step23-certified',
          'verification-step23-untested'
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
          'verification-step23-open',
          'verification-step23-certified',
          'verification-step23-untested'
      );
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
      WHERE id =
            '${SNAPSHOT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "VERIFICATION_PORTFOLIO_SNAPSHOT_COUNT=${VERIFY_SNAPSHOT_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_SNAPSHOT_COUNT}" == "0" ]]

echo "[PASS] PORTFOLIO_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 23 VERIFICATION PASSED"
echo "========================================================================"
