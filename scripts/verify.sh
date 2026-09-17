#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step22-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 22 VERIFICATION"
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
  >/tmp/terrascope-step22-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.22.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 21 22
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

echo "[PASS] FLYWAY_V22_RUNTIME"

echo
echo "===== CLEAN STEP 22 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step22-incident';
    " \
  >/dev/null

echo "[PASS] STEP22_PRE_CLEAN"

echo
echo "===== CREATE AUDIT INCIDENT ====="

cat > \
  /tmp/terrascope-step22-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step22-incident",
  "hazardType": "earthquakes",
  "title": "Verification Audit Ledger Incident",
  "description": "Step 22 tamper-evident ledger fixture.",
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
  --data @/tmp/terrascope-step22-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step22-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"

echo "[PASS] AUDIT_LEDGER_INCIDENT_CREATE"

echo
echo "===== PRE-PACKAGE LEDGER GUARD ====="

PREPACKAGE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step22-prepackage.json \
    --write-out '%{http_code}' \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/preview"
)"

echo "PREPACKAGE_LEDGER_HTTP=${PREPACKAGE_HTTP}"

[[ "${PREPACKAGE_HTTP}" == "409" ]]

echo "[PASS] AUDIT_LEDGER_PACKAGE_GUARD"

echo
echo "===== CLOSE INCIDENT ====="

cat > \
  /tmp/terrascope-step22-close.json \
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
  --data @/tmp/terrascope-step22-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

echo "[PASS] AUDIT_LEDGER_INCIDENT_CLOSED"

echo
echo "===== CREATE AAR ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step22-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

echo "AAR_ID=${AAR_ID}"

echo "[PASS] AUDIT_LEDGER_AAR"

echo
echo "===== CREATE SITREP ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/dev/null

echo "[PASS] AUDIT_LEDGER_SITREP"

echo
echo "===== CREATE COMPLETED IMPROVEMENT ====="

cat > \
  /tmp/terrascope-step22-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "PROCESS",
  "title": "Formalize tamper-evident assurance workflow",
  "description": "Improvement fixture for audit-ledger verification.",
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
  --data @/tmp/terrascope-step22-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step22-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step22-improvement-completed.json \
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
  --data @/tmp/terrascope-step22-improvement-completed.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

echo "[PASS] AUDIT_LEDGER_IMPROVEMENT"

echo
echo "===== CREATE PASSED EXERCISE ====="

cat > \
  /tmp/terrascope-step22-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "FUNCTIONAL",
  "title": "Audit assurance validation exercise",
  "description": "Validate the audit assurance workflow.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step22-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step22-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step22-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 96,
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
  --data @/tmp/terrascope-step22-assessment.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/assess" \
  >/dev/null

echo "[PASS] AUDIT_LEDGER_EXERCISE"

echo
echo "===== CERTIFY INCIDENT ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification" \
  >/tmp/terrascope-step22-certification.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-certification.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["status"] == "CERTIFIED"
assert payload["certificationNumber"] == 1
assert payload["averageLatestReadinessScore"] == 96.0

print(
    "[PASS] AUDIT_LEDGER_CERTIFICATION"
)
PY

echo
echo "===== CREATE EVIDENCE PACKAGE #1 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages" \
  >/tmp/terrascope-step22-package-one.json

PACKAGE_ONE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-package-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["packageNumber"] == 1

print(
    payload["id"]
)
PY
)"

PACKAGE_ONE_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-package-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["manifestHash"]
)
PY
)"

echo "PACKAGE_ONE_ID=${PACKAGE_ONE_ID}"
echo "PACKAGE_ONE_HASH=${PACKAGE_ONE_HASH}"

echo "[PASS] AUDIT_LEDGER_EVIDENCE_PACKAGE_ONE"

echo
echo "===== GENESIS RECEIPT PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/preview" \
  >/tmp/terrascope-step22-preview-one.json

python3 - "${PACKAGE_ONE_ID}" "${PACKAGE_ONE_HASH}" <<'PY'
import json
import re
import sys

package_id = sys.argv[1]
package_hash = sys.argv[2]

with open(
    "/tmp/terrascope-step22-preview-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["nextReceiptNumber"] == 1

assert payload["evidencePackageId"] == package_id
assert payload["evidencePackageNumber"] == 1

assert payload["packageManifestHash"] == package_hash

assert payload["latestPackageReceipted"] is False

assert payload["previousReceiptHash"] is None

assert (
    payload["ledgerVersion"]
    == "TS-RESILIENCE-AUDIT-LEDGER-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["ledgerHash"],
)

print(
    "[PASS] AUDIT_LEDGER_GENESIS_PREVIEW"
)
PY

echo
echo "===== CREATE AUDIT RECEIPT #1 ====="

RECEIPT_ONE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step22-receipt-one.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/receipts"
)"

echo "AUDIT_RECEIPT_ONE_HTTP=${RECEIPT_ONE_HTTP}"

[[ "${RECEIPT_ONE_HTTP}" == "200" ]]

RECEIPT_ONE_ID="$(
  python3 - "${PACKAGE_ONE_ID}" "${PACKAGE_ONE_HASH}" <<'PY'
import json
import re
import sys

package_id = sys.argv[1]
package_hash = sys.argv[2]

with open(
    "/tmp/terrascope-step22-receipt-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["receiptNumber"] == 1

assert payload["evidencePackageId"] == package_id
assert payload["evidencePackageNumber"] == 1

assert payload["packageManifestHash"] == package_hash

assert payload["previousReceiptHash"] is None

assert (
    payload["ledgerVersion"]
    == "TS-RESILIENCE-AUDIT-LEDGER-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["ledgerHash"],
)

print(
    payload["id"]
)
PY
)"

RECEIPT_ONE_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-receipt-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["ledgerHash"]
)
PY
)"

echo "RECEIPT_ONE_ID=${RECEIPT_ONE_ID}"
echo "RECEIPT_ONE_HASH=${RECEIPT_ONE_HASH}"

echo "[PASS] AUDIT_LEDGER_GENESIS_RECEIPT"

echo
echo "===== VERIFY SINGLE-RECEIPT LEDGER ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/verify" \
  >/tmp/terrascope-step22-verify-one.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-verify-one.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True
assert payload["receiptCount"] == 1
assert payload["verifiedReceiptCount"] == 1
assert payload["brokenReceiptId"] is None
assert payload["brokenReceiptNumber"] is None

print(
    "[PASS] AUDIT_LEDGER_SINGLE_RECEIPT_INTEGRITY"
)
PY

echo
echo "===== DUPLICATE PACKAGE RECEIPT GUARD ====="

DUPLICATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step22-duplicate.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/receipts"
)"

echo "DUPLICATE_PACKAGE_RECEIPT_HTTP=${DUPLICATE_HTTP}"

[[ "${DUPLICATE_HTTP}" == "409" ]]

echo "[PASS] AUDIT_LEDGER_DUPLICATE_PACKAGE_GUARD"

echo
echo "===== CREATE EVIDENCE PACKAGE #2 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages" \
  >/tmp/terrascope-step22-package-two.json

PACKAGE_TWO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-package-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["packageNumber"] == 2

print(
    payload["id"]
)
PY
)"

PACKAGE_TWO_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-package-two.json",
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

[[ "${PACKAGE_TWO_HASH}" != "${PACKAGE_ONE_HASH}" ]]

echo "[PASS] AUDIT_LEDGER_EVIDENCE_PACKAGE_TWO"

echo
echo "===== CHAINED RECEIPT PREVIEW ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/preview" \
  >/tmp/terrascope-step22-preview-two.json

python3 \
  - "${PACKAGE_TWO_ID}" \
  "${PACKAGE_TWO_HASH}" \
  "${RECEIPT_ONE_HASH}" \
<<'PY'
import json
import re
import sys

package_id = sys.argv[1]
package_hash = sys.argv[2]
previous_hash = sys.argv[3]

with open(
    "/tmp/terrascope-step22-preview-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["nextReceiptNumber"] == 2

assert payload["evidencePackageId"] == package_id
assert payload["evidencePackageNumber"] == 2

assert payload["packageManifestHash"] == package_hash

assert payload["latestPackageReceipted"] is False

assert payload["previousReceiptHash"] == previous_hash

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["ledgerHash"],
)

print(
    "[PASS] AUDIT_LEDGER_CHAINED_PREVIEW"
)
PY

echo
echo "===== CREATE AUDIT RECEIPT #2 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/receipts" \
  >/tmp/terrascope-step22-receipt-two.json

RECEIPT_TWO_ID="$(
  python3 \
    - "${PACKAGE_TWO_ID}" \
    "${PACKAGE_TWO_HASH}" \
    "${RECEIPT_ONE_HASH}" \
  <<'PY'
import json
import re
import sys

package_id = sys.argv[1]
package_hash = sys.argv[2]
previous_hash = sys.argv[3]

with open(
    "/tmp/terrascope-step22-receipt-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["receiptNumber"] == 2

assert payload["evidencePackageId"] == package_id
assert payload["evidencePackageNumber"] == 2

assert payload["packageManifestHash"] == package_hash

assert payload["previousReceiptHash"] == previous_hash

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["ledgerHash"],
)

print(
    payload["id"]
)
PY
)"

RECEIPT_TWO_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-receipt-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["ledgerHash"]
)
PY
)"

echo "RECEIPT_TWO_ID=${RECEIPT_TWO_ID}"
echo "RECEIPT_TWO_HASH=${RECEIPT_TWO_HASH}"

[[ "${RECEIPT_TWO_HASH}" != "${RECEIPT_ONE_HASH}" ]]

echo "[PASS] AUDIT_LEDGER_CHAINED_RECEIPT"

echo
echo "===== VERIFY TWO-RECEIPT CHAIN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/verify" \
  >/tmp/terrascope-step22-verify-two.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-verify-two.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True
assert payload["receiptCount"] == 2
assert payload["verifiedReceiptCount"] == 2

assert payload["brokenReceiptId"] is None
assert payload["brokenReceiptNumber"] is None

print(
    "[PASS] AUDIT_LEDGER_CHAIN_INTEGRITY"
)
PY

echo
echo "===== LEDGER HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/receipts" \
  >/tmp/terrascope-step22-history.json

python3 \
  - "${RECEIPT_ONE_HASH}" \
  "${RECEIPT_TWO_HASH}" \
<<'PY'
import json
import sys

first_hash = sys.argv[1]
second_hash = sys.argv[2]

with open(
    "/tmp/terrascope-step22-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

receipts = payload["receipts"]

assert len(receipts) == 2

assert receipts[0]["receiptNumber"] == 2
assert receipts[1]["receiptNumber"] == 1

assert receipts[0]["previousReceiptHash"] == first_hash
assert receipts[0]["ledgerHash"] == second_hash

assert receipts[1]["previousReceiptHash"] is None
assert receipts[1]["ledgerHash"] == first_hash

print(
    "[PASS] AUDIT_LEDGER_HISTORY"
)
PY

echo
echo "===== TAMPER DETECTION ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE incident_resilience_audit_receipts
      SET previous_receipt_hash =
          '0000000000000000000000000000000000000000000000000000000000000000'
      WHERE id =
            '${RECEIPT_TWO_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/verify" \
  >/tmp/terrascope-step22-tampered.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-tampered.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is False

assert payload["receiptCount"] == 2
assert payload["verifiedReceiptCount"] == 1

assert payload["brokenReceiptNumber"] == 2

assert (
    "Previous receipt hash"
    in payload["message"]
)

print(
    "[PASS] AUDIT_LEDGER_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE CHAIN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE incident_resilience_audit_receipts
      SET previous_receipt_hash =
          '${RECEIPT_ONE_HASH}'
      WHERE id =
            '${RECEIPT_TWO_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/verify" \
  >/tmp/terrascope-step22-restored.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-restored.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True
assert payload["verifiedReceiptCount"] == 2

print(
    "[PASS] AUDIT_LEDGER_RESTORATION_VERIFIED"
)
PY

echo
echo "===== TIMELINE EVIDENCE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step22-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step22-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert (
    types.count(
        "RESILIENCE_AUDIT_RECEIPT_CREATED"
    )
    == 2
)

assert (
    types.count(
        "RESILIENCE_EVIDENCE_PACKAGE_CREATED"
    )
    == 2
)

assert "RESILIENCE_CERTIFICATION_CREATED" in types

print(
    "[PASS] AUDIT_LEDGER_TIMELINE_CONTRACT"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

LEDGER_COLUMNS="$(
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
            'incident_resilience_audit_receipts';
      " \
    | tr -d '[:space:]'
)"

LEDGER_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_resilience_audit_receipts
      WHERE incident_id =
            '${INCIDENT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

PACKAGE_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'incident_resilience_audit_receipts'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%incident_resilience_evidence_packages%';
      " \
    | tr -d '[:space:]'
)"

RECEIPT_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_resilience_audit_receipt_number';
      " \
    | tr -d '[:space:]'
)"

PACKAGE_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_resilience_audit_evidence_package';
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
            LIKE '%RESILIENCE_AUDIT_RECEIPT_CREATED%';
      " \
    | tr -d '[:space:]'
)"

echo "AUDIT_LEDGER_COLUMN_COUNT=${LEDGER_COLUMNS}"
echo "AUDIT_LEDGER_ROW_COUNT=${LEDGER_ROWS}"
echo "AUDIT_LEDGER_PACKAGE_FK_COUNT=${PACKAGE_FK}"
echo "AUDIT_LEDGER_RECEIPT_UNIQUE_COUNT=${RECEIPT_UNIQUE}"
echo "AUDIT_LEDGER_PACKAGE_UNIQUE_COUNT=${PACKAGE_UNIQUE}"
echo "AUDIT_LEDGER_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT}"

[[ "${LEDGER_COLUMNS}" == "11" ]]
[[ "${LEDGER_ROWS}" == "2" ]]
[[ "${PACKAGE_FK}" == "1" ]]
[[ "${RECEIPT_UNIQUE}" == "1" ]]
[[ "${PACKAGE_UNIQUE}" == "1" ]]
[[ "${TIMELINE_CONSTRAINT}" == "1" ]]

echo "[PASS] AUDIT_LEDGER_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 22 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step22-incident';
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
            'verification-step22-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_LEDGER_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_resilience_audit_receipts receipt
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                receipt.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_AUDIT_LEDGER_COUNT=${ORPHAN_LEDGER_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_LEDGER_COUNT}" == "0" ]]

echo "[PASS] AUDIT_LEDGER_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 22 VERIFICATION PASSED"
echo "========================================================================"
