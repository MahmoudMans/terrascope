#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step25-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 25 VERIFICATION"
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
  >/tmp/terrascope-step25-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.25.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 \
  21 22 23 24 25
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

echo "[PASS] FLYWAY_V25_RUNTIME"

echo
echo "===== EMPTY OBJECTIVE BOARD ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step25-empty-board.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-empty-board.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["objectives"] == []
assert payload["averageProgressPct"] == 0.0

print(
    "[PASS] STRATEGIC_OBJECTIVE_EMPTY_BOARD"
)
PY

echo
echo "===== CREATE EXECUTIVE PORTFOLIO REVIEW ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step25-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reviewNumber"] >= 1

print(
    payload["id"]
)
PY
)"

REVIEW_NUMBER="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["reviewNumber"]
)
PY
)"

SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["currentSnapshotId"]
)
PY
)"

echo "REVIEW_ID=${REVIEW_ID}"
echo "REVIEW_NUMBER=${REVIEW_NUMBER}"
echo "SNAPSHOT_ID=${SNAPSHOT_ID}"

echo "[PASS] STRATEGIC_OBJECTIVE_REVIEW_FIXTURE"

echo
echo "===== INVALID REVIEW LINKAGE GUARD ====="

FAKE_REVIEW_ID="$(
  python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

cat > \
  /tmp/terrascope-step25-invalid-objective.json \
<<JSON
{
  "portfolioReviewId": "${FAKE_REVIEW_ID}",
  "title": "Invalid governance objective",
  "description": "",
  "priority": "HIGH",
  "owner": "Executive Resilience Office",
  "targetAt": null,
  "successMetric": "Should never persist."
}
JSON

INVALID_REVIEW_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step25-invalid-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step25-invalid-objective.json \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives"
)"

echo "INVALID_REVIEW_OBJECTIVE_HTTP=${INVALID_REVIEW_HTTP}"

[[ "${INVALID_REVIEW_HTTP}" == "400" ]]

echo "[PASS] STRATEGIC_OBJECTIVE_REVIEW_LINKAGE_GUARD"

echo
echo "===== CREATE OVERDUE STRATEGIC OBJECTIVE ====="

cat > \
  /tmp/terrascope-step25-objective-one.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Raise verified resilience audit coverage",
  "description": "Expand verified tamper-evident audit coverage across the resilience portfolio.",
  "priority": "HIGH",
  "owner": "Executive Resilience Office",
  "targetAt": "2020-01-01T00:00:00Z",
  "successMetric": "Verified audit-ledger coverage reaches at least 90% of certified incidents."
}
JSON

CREATE_ONE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step25-objective-one-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step25-objective-one.json \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives"
)"

echo "STRATEGIC_OBJECTIVE_ONE_HTTP=${CREATE_ONE_HTTP}"

[[ "${CREATE_ONE_HTTP}" == "200" ]]

OBJECTIVE_ONE_ID="$(
  python3 - "${REVIEW_ID}" "${REVIEW_NUMBER}" <<'PY'
import json
import sys

review_id = sys.argv[1]
review_number = int(
    sys.argv[2]
)

with open(
    "/tmp/terrascope-step25-objective-one-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["plannedCount"] == 1
assert payload["inProgressCount"] == 0
assert payload["blockedCount"] == 0
assert payload["completedCount"] == 0
assert payload["cancelledCount"] == 0

assert payload["overdueCount"] == 1
assert payload["averageProgressPct"] == 0.0

objective = payload["objectives"][0]

assert objective["portfolioReviewId"] == review_id
assert objective["portfolioReviewNumber"] == review_number
assert objective["priority"] == "HIGH"
assert objective["status"] == "PLANNED"
assert objective["progressPct"] == 0

print(
    objective["id"]
)
PY
)"

echo "OBJECTIVE_ONE_ID=${OBJECTIVE_ONE_ID}"

echo "[PASS] STRATEGIC_OBJECTIVE_CREATE"
echo "[PASS] STRATEGIC_OBJECTIVE_OVERDUE_INTELLIGENCE"

echo
echo "===== UPDATE OBJECTIVE OWNER ====="

cat > \
  /tmp/terrascope-step25-owner.json \
<<'JSON'
{
  "owner": "Enterprise Resilience Council"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step25-owner.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/owner" \
  >/tmp/terrascope-step25-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["objectives"][0]["owner"]
    == "Enterprise Resilience Council"
)

print(
    "[PASS] STRATEGIC_OBJECTIVE_OWNER_UPDATE"
)
PY

echo
echo "===== UPDATE OBJECTIVE TARGET ====="

cat > \
  /tmp/terrascope-step25-target.json \
<<'JSON'
{
  "targetAt": "2035-12-31T23:59:59Z"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step25-target.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/target" \
  >/tmp/terrascope-step25-target-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-target-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["overdueCount"] == 0

assert (
    payload["objectives"][0]["targetAt"]
    == "2035-12-31T23:59:59Z"
)

print(
    "[PASS] STRATEGIC_OBJECTIVE_TARGET_UPDATE"
)
PY

echo
echo "===== UPDATE OBJECTIVE PROGRESS ====="

cat > \
  /tmp/terrascope-step25-progress.json \
<<'JSON'
{
  "progressPct": 35
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step25-progress.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/progress" \
  >/tmp/terrascope-step25-progress-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-progress-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["averageProgressPct"] == 35.0
assert payload["objectives"][0]["progressPct"] == 35

print(
    "[PASS] STRATEGIC_OBJECTIVE_PROGRESS_UPDATE"
)
PY

echo
echo "===== PROGRESS 100 REQUIRES COMPLETION EVIDENCE ====="

cat > \
  /tmp/terrascope-step25-progress-100.json \
<<'JSON'
{
  "progressPct": 100
}
JSON

PROGRESS_100_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step25-progress-100-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step25-progress-100.json \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/progress"
)"

echo "STRATEGIC_OBJECTIVE_PROGRESS_100_HTTP=${PROGRESS_100_HTTP}"

[[ "${PROGRESS_100_HTTP}" == "400" ]]

echo "[PASS] STRATEGIC_OBJECTIVE_COMPLETION_EVIDENCE_GATE"

echo
echo "===== OBJECTIVE IN PROGRESS ====="

cat > \
  /tmp/terrascope-step25-in-progress.json \
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
  --data @/tmp/terrascope-step25-in-progress.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/status" \
  >/tmp/terrascope-step25-in-progress-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-in-progress-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["plannedCount"] == 0
assert payload["inProgressCount"] == 1

assert (
    payload["objectives"][0]["status"]
    == "IN_PROGRESS"
)

print(
    "[PASS] STRATEGIC_OBJECTIVE_IN_PROGRESS"
)
PY

echo
echo "===== PRE-COMPLETION HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/history" \
  >/tmp/terrascope-step25-history-before-complete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-history-before-complete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["events"]
]

assert "OBJECTIVE_CREATED" in types
assert "OBJECTIVE_OWNER_CHANGED" in types
assert "OBJECTIVE_TARGET_CHANGED" in types
assert "OBJECTIVE_PROGRESS_CHANGED" in types
assert "OBJECTIVE_STATUS_CHANGED" in types

print(
    "[PASS] STRATEGIC_OBJECTIVE_PERSISTENT_HISTORY"
)
PY

echo
echo "===== COMPLETE OBJECTIVE ====="

cat > \
  /tmp/terrascope-step25-complete-objective.json \
<<'JSON'
{
  "completionEvidence": "The Enterprise Resilience Council approved the governance evidence showing the target success metric was achieved."
}
JSON

COMPLETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step25-complete-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step25-complete-objective.json \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/complete"
)"

echo "STRATEGIC_OBJECTIVE_COMPLETE_HTTP=${COMPLETE_HTTP}"

[[ "${COMPLETE_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-complete-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["completedCount"] == 1
assert payload["inProgressCount"] == 0
assert payload["averageProgressPct"] == 100.0

objective = payload["objectives"][0]

assert objective["status"] == "COMPLETED"
assert objective["progressPct"] == 100
assert objective["completedAt"] is not None

assert (
    "Enterprise Resilience Council"
    in objective["completionEvidence"]
)

print(
    "[PASS] STRATEGIC_OBJECTIVE_COMPLETION"
)

print(
    "[PASS] STRATEGIC_OBJECTIVE_COMPLETION_EVIDENCE"
)
PY

echo
echo "===== COMPLETED OBJECTIVE IMMUTABILITY ====="

IMMUTABLE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step25-immutable-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step25-owner.json \
    "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/owner"
)"

echo "COMPLETED_OBJECTIVE_MUTATION_HTTP=${IMMUTABLE_HTTP}"

[[ "${IMMUTABLE_HTTP}" == "409" ]]

echo "[PASS] STRATEGIC_OBJECTIVE_TERMINAL_IMMUTABILITY"

echo
echo "===== FINAL OBJECTIVE HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ONE_ID}/history" \
  >/tmp/terrascope-step25-history-complete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-history-complete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["events"]
]

assert "OBJECTIVE_COMPLETED" in types

assert len(
    payload["events"]
) >= 6

print(
    "[PASS] STRATEGIC_OBJECTIVE_COMPLETION_HISTORY"
)
PY

echo
echo "===== CREATE BLOCKED OBJECTIVE ====="

cat > \
  /tmp/terrascope-step25-objective-two.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Eliminate unresolved assurance debt",
  "description": "Drive enterprise assurance debt toward zero through executive governance.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Office",
  "targetAt": "2020-01-01T00:00:00Z",
  "successMetric": "Portfolio assurance debt reaches zero."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step25-objective-two.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step25-objective-two-response.json

OBJECTIVE_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-objective-two-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

match = next(
    item
    for item in payload["objectives"]
    if item["title"]
    == "Eliminate unresolved assurance debt"
)

print(
    match["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step25-blocked.json \
<<'JSON'
{
  "status": "BLOCKED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step25-blocked.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_TWO_ID}/status" \
  >/tmp/terrascope-step25-blocked-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step25-blocked-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 2

assert payload["completedCount"] == 1
assert payload["blockedCount"] == 1

assert payload["overdueCount"] == 1

assert payload["averageProgressPct"] == 50.0

blocked = next(
    item
    for item in payload["objectives"]
    if item["status"] == "BLOCKED"
)

assert blocked["priority"] == "CRITICAL"

print(
    "[PASS] STRATEGIC_OBJECTIVE_BLOCKED_WORKFLOW"
)

print(
    "[PASS] STRATEGIC_OBJECTIVE_BOARD_AGGREGATION"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

OBJECTIVE_COLUMNS="$(
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
            'resilience_portfolio_objectives';
      " \
    | tr -d '[:space:]'
)"

EVENT_COLUMNS="$(
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
            'resilience_portfolio_objective_events';
      " \
    | tr -d '[:space:]'
)"

OBJECTIVE_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objectives
      WHERE portfolio_review_id =
            '${REVIEW_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

EVENT_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objective_events event
      JOIN resilience_portfolio_objectives objective
        ON objective.id =
           event.objective_id
      WHERE objective.portfolio_review_id =
            '${REVIEW_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

REVIEW_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_portfolio_objectives'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_portfolio_reviews%';
      " \
    | tr -d '[:space:]'
)"

EVENT_FK="$(
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
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_portfolio_objectives%';
      " \
    | tr -d '[:space:]'
)"

echo "STRATEGIC_OBJECTIVE_COLUMN_COUNT=${OBJECTIVE_COLUMNS}"
echo "STRATEGIC_OBJECTIVE_EVENT_COLUMN_COUNT=${EVENT_COLUMNS}"
echo "STRATEGIC_OBJECTIVE_ROW_COUNT=${OBJECTIVE_ROWS}"
echo "STRATEGIC_OBJECTIVE_EVENT_ROW_COUNT=${EVENT_ROWS}"
echo "STRATEGIC_OBJECTIVE_REVIEW_FK_COUNT=${REVIEW_FK}"
echo "STRATEGIC_OBJECTIVE_EVENT_FK_COUNT=${EVENT_FK}"

[[ "${OBJECTIVE_COLUMNS}" == "15" ]]
[[ "${EVENT_COLUMNS}" == "5" ]]
[[ "${OBJECTIVE_ROWS}" == "2" ]]
[[ "${EVENT_ROWS}" -ge "8" ]]
[[ "${REVIEW_FK}" == "1" ]]
[[ "${EVENT_FK}" == "1" ]]

echo "[PASS] STRATEGIC_OBJECTIVE_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 25 FIXTURE ====="

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
      WHERE id =
            '${SNAPSHOT_ID}'::uuid;
    " \
  >/dev/null

VERIFY_OBJECTIVE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objectives
      WHERE portfolio_review_id =
            '${REVIEW_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

VERIFY_EVENT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objective_events event
      WHERE event.objective_id IN (
          '${OBJECTIVE_ONE_ID}'::uuid,
          '${OBJECTIVE_TWO_ID}'::uuid
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
      WHERE id =
            '${SNAPSHOT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_OBJECTIVE_COUNT=${VERIFY_OBJECTIVE_COUNT}"
echo "VERIFICATION_OBJECTIVE_EVENT_COUNT=${VERIFY_EVENT_COUNT}"
echo "VERIFICATION_REVIEW_COUNT=${VERIFY_REVIEW_COUNT}"
echo "VERIFICATION_SNAPSHOT_COUNT=${VERIFY_SNAPSHOT_COUNT}"

[[ "${VERIFY_OBJECTIVE_COUNT}" == "0" ]]
[[ "${VERIFY_EVENT_COUNT}" == "0" ]]
[[ "${VERIFY_REVIEW_COUNT}" == "0" ]]
[[ "${VERIFY_SNAPSHOT_COUNT}" == "0" ]]

echo "[PASS] STRATEGIC_OBJECTIVE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 25 VERIFICATION PASSED"
echo "========================================================================"
