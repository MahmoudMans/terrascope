#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step21-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 21 VERIFICATION"
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
  >/tmp/terrascope-step21-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.21.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 21
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

echo "[PASS] FLYWAY_V21_RUNTIME"

echo
echo "===== CLEAN STEP 21 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step21-incident';
    " \
  >/dev/null

echo "[PASS] STEP21_PRE_CLEAN"

echo
echo "===== CREATE EVIDENCE INCIDENT ====="

cat > \
  /tmp/terrascope-step21-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step21-incident",
  "hazardType": "earthquakes",
  "title": "Verification Evidence Incident",
  "description": "Step 21 resilience evidence manifest fixture.",
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
  --data @/tmp/terrascope-step21-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step21-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] EVIDENCE_INCIDENT_CREATE"

echo
echo "===== UNCERTIFIED EVIDENCE GUARD ====="

UNCERTIFIED_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step21-uncertified.json \
    --write-out '%{http_code}' \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/preview"
)"

echo "UNCERTIFIED_EVIDENCE_HTTP=${UNCERTIFIED_HTTP}"

[[ "${UNCERTIFIED_HTTP}" == "409" ]]

echo "[PASS] UNCERTIFIED_EVIDENCE_GUARD"

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step21-close.json \
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
  --data @/tmp/terrascope-step21-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

echo "[PASS] EVIDENCE_INCIDENT_CLOSED"

echo
echo "===== CREATE AAR ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step21-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

echo "AAR_ID=${AAR_ID}"

echo "[PASS] EVIDENCE_AAR_SOURCE"

echo
echo "===== CREATE SITREP ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/tmp/terrascope-step21-sitrep.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-sitrep.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportNumber"] == 1

print(
    "[PASS] EVIDENCE_SITREP_SOURCE"
)
PY

echo
echo "===== CREATE COMPLETED IMPROVEMENT ====="

cat > \
  /tmp/terrascope-step21-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Formalize evidence-ready handoff checklist",
  "description": "Validation source for evidence packaging.",
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
  --data @/tmp/terrascope-step21-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step21-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step21-improvement-complete.json \
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
  --data @/tmp/terrascope-step21-improvement-complete.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

echo "[PASS] EVIDENCE_IMPROVEMENT_SOURCE"

echo
echo "===== CREATE PASSED PREPAREDNESS EXERCISE ====="

cat > \
  /tmp/terrascope-step21-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Evidence package validation exercise",
  "description": "Validate the completed improvement.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step21-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step21-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step21-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 94,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "All evidence controls executed successfully.",
  "findings": "No material resilience gaps remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step21-assessment.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/assess" \
  >/dev/null

echo "[PASS] EVIDENCE_EXERCISE_SOURCE"

echo
echo "===== CREATE RESILIENCE CERTIFICATION ====="

CERT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step21-certification.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification"
)"

echo "RESILIENCE_CERTIFICATION_HTTP=${CERT_HTTP}"

[[ "${CERT_HTTP}" == "200" ]]

CERTIFICATION_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-certification.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificationNumber"] == 1
assert payload["status"] == "CERTIFIED"
assert payload["averageLatestReadinessScore"] == 94.0

print(
    payload["id"]
)
PY
)"

echo "CERTIFICATION_ID=${CERTIFICATION_ID}"

echo "[PASS] EVIDENCE_CERTIFICATION_SOURCE"

echo
echo "===== EVIDENCE PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/preview" \
  >/tmp/terrascope-step21-preview.json

python3 - <<'PY'
import json
import re

with open(
    "/tmp/terrascope-step21-preview.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificationNumber"] == 1
assert payload["certificationReadinessScore"] == 94.0
assert payload["certificationCount"] == 1

assert payload["afterActionReviewCount"] == 1
assert payload["sitrepCount"] == 1
assert payload["improvementCount"] == 1
assert payload["exerciseCount"] == 1
assert payload["correctiveActionCount"] == 0

assert payload["timelineEventCount"] > 0

assert (
    payload["manifestVersion"]
    == "TS-RESILIENCE-EVIDENCE-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["manifestHash"],
)

assert (
    "generated locally with SHA-256"
    in payload["executiveSummary"]
)

print(
    "[PASS] EVIDENCE_PREVIEW"
)

print(
    "[PASS] EVIDENCE_CANONICAL_HASH"
)
PY

PREVIEW_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-preview.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["manifestHash"]
)
PY
)"

echo "PREVIEW_HASH=${PREVIEW_HASH}"

echo
echo "===== CREATE EVIDENCE PACKAGE #1 ====="

PACKAGE_ONE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step21-package-one.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages"
)"

echo "EVIDENCE_PACKAGE_ONE_HTTP=${PACKAGE_ONE_HTTP}"

[[ "${PACKAGE_ONE_HTTP}" == "200" ]]

PACKAGE_ONE_ID="$(
  python3 - "${PREVIEW_HASH}" <<'PY'
import json
import sys

expected_hash = sys.argv[1]

with open(
    "/tmp/terrascope-step21-package-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["packageNumber"] == 1

assert payload["certificationNumber"] == 1
assert payload["certificationReadinessScore"] == 94.0

assert payload["certificationCount"] == 1
assert payload["afterActionReviewCount"] == 1
assert payload["sitrepCount"] == 1
assert payload["improvementCount"] == 1
assert payload["exerciseCount"] == 1
assert payload["correctiveActionCount"] == 0

assert (
    payload["manifestVersion"]
    == "TS-RESILIENCE-EVIDENCE-V1"
)

assert payload["manifestHash"] == expected_hash

print(
    payload["id"]
)
PY
)"

echo "PACKAGE_ONE_ID=${PACKAGE_ONE_ID}"

echo "[PASS] EVIDENCE_PACKAGE_ONE_CREATE"

echo
echo "===== VERIFY EVIDENCE PACKAGE #1 ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages/${PACKAGE_ONE_ID}/verify" \
  >/tmp/terrascope-step21-verify-one.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-verify-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert (
    payload["storedHash"]
    == payload["recomputedHash"]
)

assert (
    payload["manifestVersion"]
    == "TS-RESILIENCE-EVIDENCE-V1"
)

print(
    "[PASS] EVIDENCE_PACKAGE_INTEGRITY"
)
PY

echo
echo "===== CREATE EVIDENCE PACKAGE #2 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages" \
  >/tmp/terrascope-step21-package-two.json

PACKAGE_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-package-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["packageNumber"] == 2
assert payload["certificationNumber"] == 1

print(
    payload["id"]
)
PY
)"

PACKAGE_TWO_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-package-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["manifestHash"]
)
PY
)"

echo "PACKAGE_TWO_ID=${PACKAGE_TWO_ID}"
echo "PACKAGE_TWO_HASH=${PACKAGE_TWO_HASH}"

[[ "${PACKAGE_TWO_HASH}" != "${PREVIEW_HASH}" ]]

echo "[PASS] EVIDENCE_PACKAGE_SEQUENCE"
echo "[PASS] EVIDENCE_TIMELINE_SENSITIVE_MANIFEST"

echo
echo "===== EVIDENCE PACKAGE HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages" \
  >/tmp/terrascope-step21-history.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

packages = payload["packages"]

assert len(packages) == 2

assert packages[0]["packageNumber"] == 2
assert packages[1]["packageNumber"] == 1

assert packages[0]["manifestHash"] != packages[1]["manifestHash"]

print(
    "[PASS] EVIDENCE_PACKAGE_HISTORY"
)
PY

echo
echo "===== EVIDENCE TIMELINE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step21-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step21-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert (
    types.count(
        "RESILIENCE_EVIDENCE_PACKAGE_CREATED"
    )
    == 2
)

assert "RESILIENCE_CERTIFICATION_CREATED" in types
assert "AFTER_ACTION_REVIEW_CREATED" in types
assert "SITREP_CREATED" in types
assert "IMPROVEMENT_STATUS_CHANGED" in types
assert "PREPAREDNESS_EXERCISE_ASSESSED" in types

print(
    "[PASS] EVIDENCE_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

EVIDENCE_COLUMNS="$(
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
            'incident_resilience_evidence_packages';
      " \
    | tr -d '[:space:]'
)"

EVIDENCE_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_resilience_evidence_packages
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

CERT_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'incident_resilience_evidence_packages'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%incident_resilience_certifications%';
      " \
    | tr -d '[:space:]'
)"

UNIQUE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_resilience_evidence_package_number';
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
            LIKE '%RESILIENCE_EVIDENCE_PACKAGE_CREATED%';
      " \
    | tr -d '[:space:]'
)"

echo "EVIDENCE_PACKAGE_COLUMN_COUNT=${EVIDENCE_COLUMNS}"
echo "EVIDENCE_PACKAGE_ROW_COUNT=${EVIDENCE_ROWS}"
echo "EVIDENCE_CERTIFICATION_FK_COUNT=${CERT_FK}"
echo "EVIDENCE_PACKAGE_UNIQUE_COUNT=${UNIQUE_COUNT}"
echo "EVIDENCE_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT}"

[[ "${EVIDENCE_COLUMNS}" == "17" ]]
[[ "${EVIDENCE_ROWS}" == "2" ]]
[[ "${CERT_FK}" == "1" ]]
[[ "${UNIQUE_COUNT}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT}" == "1" ]]

echo "[PASS] EVIDENCE_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 21 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step21-incident';
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
            'verification-step21-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_EVIDENCE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_resilience_evidence_packages evidence
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                evidence.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_EVIDENCE_PACKAGE_COUNT=${ORPHAN_EVIDENCE_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_EVIDENCE_COUNT}" == "0" ]]

echo "[PASS] EVIDENCE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 21 VERIFICATION PASSED"
echo "========================================================================"
