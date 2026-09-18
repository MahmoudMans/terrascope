#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step28-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 28 VERIFICATION"
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
  >/tmp/terrascope-step28-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.28.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 \
  21 22 23 24 25 26 27 28
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

echo "[PASS] FLYWAY_V28_RUNTIME"

echo
echo "===== CLEAN STEP 28 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step28-realized';
    " \
  >/dev/null

echo "[PASS] STEP28_PRE_CLEAN"

echo
echo "===== BLOCKED INITIAL READINESS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/readiness" \
  >/tmp/terrascope-step28-readiness-blocked.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-readiness-blocked.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False

assert "NO_STRATEGIC_OBJECTIVES" in payload["blockers"]

assert "REALIZATION_BAND_NOT_REALIZED" in payload["blockers"]

print(
    "[PASS] GOVERNANCE_CLOSURE_INITIAL_BLOCKERS"
)
PY

BLOCKED_CERT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step28-blocked-cert.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates"
)"

echo "BLOCKED_CLOSURE_CERTIFICATE_HTTP=${BLOCKED_CERT_HTTP}"

[[ "${BLOCKED_CERT_HTTP}" == "409" ]]

echo "[PASS] GOVERNANCE_CLOSURE_CERTIFICATION_GUARD"

echo
echo "===== CREATE EXECUTIVE BASELINE REVIEW ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step28-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-review.json",
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
    "/tmp/terrascope-step28-review.json",
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

echo "[PASS] GOVERNANCE_CLOSURE_REVIEW_FIXTURE"

echo
echo "===== CREATE + COMPLETE OBJECTIVE ====="

cat > \
  /tmp/terrascope-step28-objective.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Close enterprise resilience governance assurance",
  "description": "Step 28 governance closure certificate fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Enterprise benefit realization reaches REALIZED with complete validated assurance."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step28-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step28-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-objective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["objectives"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step28-complete.json \
<<'JSON'
{
  "completionEvidence": "Executive Resilience Council approved objective completion for independent benefit realization and governance closure certification."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step28-complete.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

echo "OBJECTIVE_ID=${OBJECTIVE_ID}"

echo "[PASS] GOVERNANCE_CLOSURE_OBJECTIVE_FIXTURE"

echo
echo "===== PARTIAL OUTCOME REMAINS BLOCKED ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step28-partial-validation.json

PARTIAL_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-partial-validation.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["outcome"] == "PARTIAL"

print(
    payload["validationSnapshotId"]
)
PY
)"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/readiness" \
  >/tmp/terrascope-step28-partial-readiness.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-partial-readiness.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False

assert "PARTIAL_OUTCOMES" in payload["blockers"]

assert "REALIZED_BENEFIT_RATE_BELOW_100" in payload["blockers"]

print(
    "[PASS] GOVERNANCE_CLOSURE_PARTIAL_BLOCKER"
)
PY

echo
echo "===== CREATE REALIZED BENEFIT FIXTURE ====="

cat > \
  /tmp/terrascope-step28-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step28-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 28 Realized Governance Incident",
  "description": "Governance closure certificate realized-benefit fixture.",
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
  --data @/tmp/terrascope-step28-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step28-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step28-close.json \
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
  --data @/tmp/terrascope-step28-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step28-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-aar.json",
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
  /tmp/terrascope-step28-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Prove enterprise governance assurance closure",
  "description": "Step 28 governance closure validation improvement.",
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
  --data @/tmp/terrascope-step28-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step28-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step28-complete-improvement.json \
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
  --data @/tmp/terrascope-step28-complete-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step28-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Governance closure assurance exercise",
  "description": "Validate enterprise governance closure assurance.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step28-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step28-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step28-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 98,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Enterprise closure assurance controls successfully validated.",
  "findings": "No material enterprise assurance gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step28-assessment.json \
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

echo "[PASS] GOVERNANCE_CLOSURE_REALIZED_FIXTURE"

echo
echo "===== ACHIEVED OBJECTIVE OUTCOME ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step28-achieved-validation.json

ACHIEVED_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-achieved-validation.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["outcome"] == "ACHIEVED"
assert payload["scoreDelta"] >= 5

print(
    payload["validationSnapshotId"]
)
PY
)"

echo "ACHIEVED_SNAPSHOT_ID=${ACHIEVED_SNAPSHOT_ID}"

echo "[PASS] GOVERNANCE_CLOSURE_ACHIEVED_OUTCOME"

echo
echo "===== READY GOVERNANCE CLOSURE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/readiness" \
  >/tmp/terrascope-step28-ready.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-ready.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is True

assert payload["band"] == "REALIZED"

assert payload["totalObjectiveCount"] == 1
assert payload["completedObjectiveCount"] == 1
assert payload["validatedObjectiveCount"] == 1
assert payload["achievedCount"] == 1

assert payload["validationCoveragePct"] == 100.0
assert payload["realizedBenefitRatePct"] == 100.0

assert payload["blockers"] == []

print(
    "[PASS] GOVERNANCE_CLOSURE_READINESS"
)
PY

echo
echo "===== ISSUE CERTIFICATE #1 ====="

CERT_ONE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step28-cert-one.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates"
)"

echo "GOVERNANCE_CERTIFICATE_ONE_HTTP=${CERT_ONE_HTTP}"

[[ "${CERT_ONE_HTTP}" == "200" ]]

CERT_ONE_ID="$(
  python3 - <<'PY'
import json
import re

with open(
    "/tmp/terrascope-step28-cert-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificateNumber"] == 1

assert payload["band"] == "REALIZED"
assert payload["closureReady"] is True

assert payload["previousCertificateHash"] is None

assert payload["validationCoveragePct"] == 100.0
assert payload["realizedBenefitRatePct"] == 100.0

assert (
    payload["dossierVersion"]
    == "TS-GOVERNANCE-CLOSURE-DOSSIER-V1"
)

assert (
    payload["certificateVersion"]
    == "TS-GOVERNANCE-CLOSURE-CERTIFICATE-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["dossierHash"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["certificateHash"],
)

print(
    payload["id"]
)
PY
)"

CERT_ONE_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-cert-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["certificateHash"]
)
PY
)"

CERT_ONE_BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-cert-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["benefitSnapshotId"]
)
PY
)"

echo "CERT_ONE_ID=${CERT_ONE_ID}"
echo "CERT_ONE_HASH=${CERT_ONE_HASH}"
echo "CERT_ONE_BENEFIT_SNAPSHOT_ID=${CERT_ONE_BENEFIT_SNAPSHOT_ID}"

echo "[PASS] GOVERNANCE_CLOSURE_CERTIFICATE_ONE"

echo
echo "===== VERIFY SINGLE CERTIFICATE CHAIN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify" \
  >/tmp/terrascope-step28-verify-one.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-verify-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["certificateCount"] == 1
assert payload["verifiedCertificateCount"] == 1

assert payload["brokenCertificateId"] is None
assert payload["brokenCertificateNumber"] is None

print(
    "[PASS] GOVERNANCE_CLOSURE_SINGLE_CERTIFICATE_INTEGRITY"
)
PY

echo
echo "===== ISSUE CERTIFICATE #2 ====="

CERT_TWO_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step28-cert-two.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates"
)"

echo "GOVERNANCE_CERTIFICATE_TWO_HTTP=${CERT_TWO_HTTP}"

[[ "${CERT_TWO_HTTP}" == "200" ]]

CERT_TWO_ID="$(
  python3 - "${CERT_ONE_HASH}" <<'PY'
import json
import re
import sys

first_hash = sys.argv[1]

with open(
    "/tmp/terrascope-step28-cert-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificateNumber"] == 2

assert payload["band"] == "REALIZED"
assert payload["closureReady"] is True

assert (
    payload["previousCertificateHash"]
    == first_hash
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["certificateHash"],
)

print(
    payload["id"]
)
PY
)"

CERT_TWO_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-cert-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["certificateHash"]
)
PY
)"

CERT_TWO_BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-cert-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["benefitSnapshotId"]
)
PY
)"

echo "CERT_TWO_ID=${CERT_TWO_ID}"
echo "CERT_TWO_HASH=${CERT_TWO_HASH}"
echo "CERT_TWO_BENEFIT_SNAPSHOT_ID=${CERT_TWO_BENEFIT_SNAPSHOT_ID}"

[[ "${CERT_TWO_HASH}" != "${CERT_ONE_HASH}" ]]

echo "[PASS] GOVERNANCE_CLOSURE_CERTIFICATE_TWO"

echo
echo "===== VERIFY TWO-CERTIFICATE CHAIN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify" \
  >/tmp/terrascope-step28-verify-two.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-verify-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["certificateCount"] == 2
assert payload["verifiedCertificateCount"] == 2

print(
    "[PASS] GOVERNANCE_CLOSURE_CHAIN_INTEGRITY"
)
PY

echo
echo "===== CERTIFICATE HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step28-cert-history.json

python3 \
  - "${CERT_ONE_HASH}" \
  "${CERT_TWO_HASH}" \
<<'PY'
import json
import sys

first_hash = sys.argv[1]
second_hash = sys.argv[2]

with open(
    "/tmp/terrascope-step28-cert-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

certificates = payload["certificates"]

assert len(certificates) == 2

assert certificates[0]["certificateNumber"] == 2
assert certificates[0]["certificateHash"] == second_hash
assert certificates[0]["previousCertificateHash"] == first_hash

assert certificates[1]["certificateNumber"] == 1
assert certificates[1]["certificateHash"] == first_hash
assert certificates[1]["previousCertificateHash"] is None

print(
    "[PASS] GOVERNANCE_CLOSURE_CERTIFICATE_HISTORY"
)
PY

echo
echo "===== TAMPER DETECTION ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_closure_certificates

      SET previous_certificate_hash =
          '0000000000000000000000000000000000000000000000000000000000000000'

      WHERE id =
            '${CERT_TWO_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify" \
  >/tmp/terrascope-step28-tampered.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-tampered.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is False

assert payload["certificateCount"] == 2
assert payload["verifiedCertificateCount"] == 1

assert payload["brokenCertificateNumber"] == 2

assert (
    "Previous certificate hash"
    in payload["message"]
)

print(
    "[PASS] GOVERNANCE_CLOSURE_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE CERTIFICATE CHAIN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_closure_certificates

      SET previous_certificate_hash =
          '${CERT_ONE_HASH}'

      WHERE id =
            '${CERT_TWO_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify" \
  >/tmp/terrascope-step28-restored.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step28-restored.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True
assert payload["verifiedCertificateCount"] == 2

print(
    "[PASS] GOVERNANCE_CLOSURE_RESTORATION_VERIFIED"
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
            'resilience_governance_closure_certificates';
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
      FROM resilience_governance_closure_certificates
      WHERE id IN (
          '${CERT_ONE_ID}'::uuid,
          '${CERT_TWO_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

SNAPSHOT_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_governance_closure_certificates'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_benefit_realization_snapshots%';
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
            'uq_governance_closure_certificate_number';
      " \
    | tr -d '[:space:]'
)"

SNAPSHOT_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_governance_closure_benefit_snapshot';
      " \
    | tr -d '[:space:]'
)"

DOSSIER_HASH_CHECK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'ck_governance_closure_dossier_hash';
      " \
    | tr -d '[:space:]'
)"

CERT_HASH_CHECK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'ck_governance_closure_certificate_hash';
      " \
    | tr -d '[:space:]'
)"

echo "GOVERNANCE_CLOSURE_COLUMN_COUNT=${CERT_COLUMNS}"
echo "GOVERNANCE_CLOSURE_ROW_COUNT=${CERT_ROWS}"
echo "GOVERNANCE_CLOSURE_SNAPSHOT_FK_COUNT=${SNAPSHOT_FK}"
echo "GOVERNANCE_CLOSURE_CERTIFICATE_UNIQUE_COUNT=${CERT_UNIQUE}"
echo "GOVERNANCE_CLOSURE_SNAPSHOT_UNIQUE_COUNT=${SNAPSHOT_UNIQUE}"
echo "GOVERNANCE_CLOSURE_DOSSIER_HASH_CHECK_COUNT=${DOSSIER_HASH_CHECK}"
echo "GOVERNANCE_CLOSURE_CERT_HASH_CHECK_COUNT=${CERT_HASH_CHECK}"

[[ "${CERT_COLUMNS}" == "21" ]]
[[ "${CERT_ROWS}" == "2" ]]
[[ "${SNAPSHOT_FK}" == "1" ]]
[[ "${CERT_UNIQUE}" == "1" ]]
[[ "${SNAPSHOT_UNIQUE}" == "1" ]]
[[ "${DOSSIER_HASH_CHECK}" == "1" ]]
[[ "${CERT_HASH_CHECK}" == "1" ]]

echo "[PASS] GOVERNANCE_CLOSURE_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 28 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_closure_certificates
      WHERE id IN (
          '${CERT_ONE_ID}'::uuid,
          '${CERT_TWO_ID}'::uuid
      );

      DELETE
      FROM resilience_benefit_realization_snapshots
      WHERE id IN (
          '${CERT_ONE_BENEFIT_SNAPSHOT_ID}'::uuid,
          '${CERT_TWO_BENEFIT_SNAPSHOT_ID}'::uuid
      );

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step28-realized';

      DELETE
      FROM resilience_portfolio_reviews
      WHERE id =
            '${REVIEW_ID}'::uuid;

      DELETE
      FROM resilience_portfolio_snapshots
      WHERE id IN (
          '${BASELINE_SNAPSHOT_ID}'::uuid,
          '${PARTIAL_SNAPSHOT_ID}'::uuid,
          '${ACHIEVED_SNAPSHOT_ID}'::uuid
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
            'verification-step28-realized';
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

VERIFY_CERT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_closure_certificates
      WHERE id IN (
          '${CERT_ONE_ID}'::uuid,
          '${CERT_TWO_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

VERIFY_BENEFIT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_benefit_realization_snapshots
      WHERE id IN (
          '${CERT_ONE_BENEFIT_SNAPSHOT_ID}'::uuid,
          '${CERT_TWO_BENEFIT_SNAPSHOT_ID}'::uuid
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
echo "VERIFICATION_CLOSURE_CERTIFICATE_COUNT=${VERIFY_CERT_COUNT}"
echo "VERIFICATION_BENEFIT_SNAPSHOT_COUNT=${VERIFY_BENEFIT_COUNT}"
echo "VERIFICATION_REVIEW_COUNT=${VERIFY_REVIEW_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_OBJECTIVE_COUNT}" == "0" ]]
[[ "${VERIFY_CERT_COUNT}" == "0" ]]
[[ "${VERIFY_BENEFIT_COUNT}" == "0" ]]
[[ "${VERIFY_REVIEW_COUNT}" == "0" ]]

echo "[PASS] GOVERNANCE_CLOSURE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 28 VERIFICATION PASSED"
echo "========================================================================"
