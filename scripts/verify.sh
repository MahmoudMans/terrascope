#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step33-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep33Keygen.java"

PRIVATE_FILE="/tmp/terrascope-step33-private.b64"
PUBLIC_FILE="/tmp/terrascope-step33-public.b64"

ENVELOPE="/tmp/terrascope-step33-envelope.json"
TAMPERED_ENVELOPE="/tmp/terrascope-step33-tampered-envelope.json"

KEY_ID="verification-step33-ed25519"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi

  rm -f \
    "${KEYGEN_SOURCE}" \
    "${PRIVATE_FILE}" \
    "${PUBLIC_FILE}" \
    "${ENVELOPE}" \
    "${TAMPERED_ENVELOPE}"
}

trap cleanup EXIT

stop_backend() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true

    BACKEND_PID=""
  fi

  sleep 1
}

wait_backend() {
  for attempt in $(seq 1 45); do
    if curl \
        --fail \
        --silent \
        "http://localhost:8087/actuator/health" \
        >/dev/null 2>&1; then

      return 0
    fi

    if [[ -n "${BACKEND_PID}" ]] \
       && ! kill -0 \
          "${BACKEND_PID}" \
          >/dev/null 2>&1; then

      cat "${BACKEND_LOG}"

      return 1
    fi

    sleep 1
  done

  cat "${BACKEND_LOG}"

  return 1
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 33 VERIFICATION"
echo "========================================================================"

docker compose config >/dev/null

docker compose up -d postgres

echo "[PASS] COMPOSE_CONFIGURATION"

(
  cd apps/backend
  mvn test
)

echo "[PASS] BACKEND_TESTS"

(
  cd apps/backend
  mvn -DskipTests package
)

(
  cd apps/web

  npx eslint . \
    --max-warnings=0

  npm run typecheck

  npm run build
)

echo "[PASS] BUILD_QUALITY"

echo
echo "===== GENERATE LOCAL ED25519 KEY ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class TerraScopeStep33Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        KeyPair pair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        Files.writeString(
                Path.of(
                        args[0]
                ),

                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPrivate()
                                        .getEncoded()
                        )
        );

        Files.writeString(
                Path.of(
                        args[1]
                ),

                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPublic()
                                        .getEncoded()
                        )
        );
    }
}
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${PRIVATE_FILE}" \
  "${PUBLIC_FILE}"

PRIVATE_KEY="$(
  cat "${PRIVATE_FILE}"
)"

PUBLIC_KEY="$(
  cat "${PUBLIC_FILE}"
)"

echo "[PASS] LOCAL_ED25519_KEYPAIR"

rm -f "${BACKEND_LOG}"

(
  cd apps/backend

  TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID="${KEY_ID}" \
  TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64="${PRIVATE_KEY}" \
  TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64="${PUBLIC_KEY}" \
    mvn spring-boot:run
) >> "${BACKEND_LOG}" 2>&1 &

BACKEND_PID=$!

wait_backend

echo "[PASS] SIGNING_RUNTIME_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step33-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.33.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 33); do
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

echo "[PASS] FLYWAY_V33_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_receipt_ledger;

      DELETE
      FROM resilience_governance_assurance_verification_receipts;

      DELETE
      FROM resilience_governance_assurance_bundles;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id =
            '${KEY_ID}'
         OR replacement_key_id =
            '${KEY_ID}';

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE key_id =
            '${KEY_ID}';

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id =
            '${KEY_ID}';

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step33-realized';
    " \
  >/dev/null

echo "[PASS] STEP33_PRE_CLEAN"

echo
echo "===== CREATE PORTABLE GOVERNANCE FIXTURE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step33-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-review.json",
            encoding="utf-8",
        )
    )["id"]
)
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-review.json",
            encoding="utf-8",
        )
    )["currentSnapshotId"]
)
PY
)"

cat > \
  /tmp/terrascope-step33-objective.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Anchor external verification decisions",
  "description": "Step 33 receipt transparency ledger fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Every air-gapped verification decision is chained into a tamper-evident receipt ledger."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step33-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step33-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-objective-response.json",
            encoding="utf-8",
        )
    )["objectives"][0]["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"completionEvidence":"Verification receipt transparency ledger controls implemented."}' \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

cat > \
  /tmp/terrascope-step33-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step33-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 33 Receipt Ledger Incident",
  "description": "Receipt transparency ledger verification fixture.",
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
  --data @/tmp/terrascope-step33-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step33-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-incident-response.json",
            encoding="utf-8",
        )
    )["incident"]["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data '{"status":"CLOSED"}' \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step33-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-aar.json",
            encoding="utf-8",
        )
    )["id"]
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
  /tmp/terrascope-step33-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "TECHNOLOGY",
  "title": "Exercise receipt transparency controls",
  "description": "Step 33 receipt-ledger validation improvement.",
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
  --data @/tmp/terrascope-step33-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step33-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-improvement-response.json",
            encoding="utf-8",
        )
    )["actions"][0]["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data '{"status":"COMPLETED"}' \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step33-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TECHNICAL",
  "title": "Receipt transparency verification exercise",
  "description": "Validate receipt payload and chain tamper detection.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step33-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step33-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-exercise-response.json",
            encoding="utf-8",
        )
    )["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step33-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 99,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Verification receipt transparency controls successfully exercised.",
  "findings": "No material receipt-chain assurance gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step33-assessment.json \
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

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step33-outcome.json

OUTCOME_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-outcome.json",
            encoding="utf-8",
        )
    )["validationSnapshotId"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step33-certificate.json

CERTIFICATE_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-certificate.json",
            encoding="utf-8",
        )
    )["id"]
)
PY
)"

BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-certificate.json",
            encoding="utf-8",
        )
    )["benefitSnapshotId"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step33-signature.json

SIGNATURE_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-signature.json",
            encoding="utf-8",
        )
    )["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step33-bundle.json

BUNDLE_ID="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step33-bundle.json",
            encoding="utf-8",
        )
    )["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/export/${BUNDLE_ID}" \
  --output "${ENVELOPE}"

[[ -s "${ENVELOPE}" ]]

echo "[PASS] RECEIPT_LEDGER_PORTABLE_FIXTURE"

echo
echo "===== REMOVE LIVE SOURCE EVIDENCE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_bundles
      WHERE id =
            '${BUNDLE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id =
            '${KEY_ID}'
         OR replacement_key_id =
            '${KEY_ID}';

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE id =
            '${SIGNATURE_ID}'::uuid;

      DELETE
      FROM resilience_governance_closure_certificates
      WHERE id =
            '${CERTIFICATE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id =
            '${KEY_ID}';

      DELETE
      FROM resilience_benefit_realization_snapshots
      WHERE id =
            '${BENEFIT_SNAPSHOT_ID}'::uuid;

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step33-realized';

      DELETE
      FROM resilience_portfolio_reviews
      WHERE id =
            '${REVIEW_ID}'::uuid;

      DELETE
      FROM resilience_portfolio_snapshots
      WHERE id IN (
          '${BASELINE_SNAPSHOT_ID}'::uuid,
          '${OUTCOME_SNAPSHOT_ID}'::uuid
      );
    " \
  >/dev/null

echo "[PASS] RECEIPT_LEDGER_SOURCE_STATE_REMOVED"

echo
echo "===== RESTART WITHOUT SIGNING KEY ====="

stop_backend

(
  cd apps/backend

  env \
    -u TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID \
    -u TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64 \
    -u TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64 \
    mvn spring-boot:run
) >> "${BACKEND_LOG}" 2>&1 &

BACKEND_PID=$!

wait_backend

echo "[PASS] RECEIPT_LEDGER_KEYLESS_RUNTIME_READY"

echo
echo "===== CREATE VALID RECEIPT + LEDGER ENTRY ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step33-valid-receipt.json

VALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-valid-receipt.json",
        encoding="utf-8",
    )
)

assert payload["receiptNumber"] == 1
assert payload["verificationResult"] == "VALID"

print(
    payload["id"]
)
PY
)"

echo "VALID_RECEIPT_ID=${VALID_RECEIPT_ID}"

echo "[PASS] VALID_RECEIPT_LEDGER_ANCHOR"

echo
echo "===== BUILD CANONICAL SIGNATURE TAMPER ====="

python3 \
  - \
  "${ENVELOPE}" \
  "${TAMPERED_ENVELOPE}" \
<<'PY'
import base64
import hashlib
import json
import re
import sys

source = sys.argv[1]
target = sys.argv[2]

with open(
    source,
    encoding="utf-8",
) as handle:
    envelope = json.load(
        handle
    )

payload_json = envelope[
    "payloadJson"
]

zero_signature = (
    base64.b64encode(
        bytes(64)
    ).decode()
)

tampered_payload_json, count = re.subn(
    r'("signatureBase64":")[^"]+(")',

    lambda match:
        match.group(1)
        + zero_signature
        + match.group(2),

    payload_json,

    count=1,
)

assert count == 1

envelope[
    "payloadJson"
] = tampered_payload_json

envelope[
    "bundleHash"
] = hashlib.sha256(
    tampered_payload_json.encode(
        "utf-8"
    )
).hexdigest()

with open(
    target,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        envelope,
        handle,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )
PY

[[ -s "${TAMPERED_ENVELOPE}" ]]

echo "[PASS] RECEIPT_LEDGER_TAMPERED_ENVELOPE"

echo
echo "===== CREATE INVALID RECEIPT + LEDGER ENTRY ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${TAMPERED_ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step33-invalid-receipt.json

INVALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-invalid-receipt.json",
        encoding="utf-8",
    )
)

assert payload["receiptNumber"] == 2
assert payload["verificationResult"] == "INVALID"

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["portableVerificationValid"] is False

print(
    payload["id"]
)
PY
)"

echo "INVALID_RECEIPT_ID=${INVALID_RECEIPT_ID}"

echo "[PASS] INVALID_RECEIPT_LEDGER_ANCHOR"

echo
echo "===== RECEIPT LEDGER HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger" \
  >/tmp/terrascope-step33-ledger-history.json

LEDGER_ONE_ID="$(
  python3 \
    - "${VALID_RECEIPT_ID}" \
    "${INVALID_RECEIPT_ID}" \
<<'PY'
import json
import re
import sys

valid_receipt_id = sys.argv[1]
invalid_receipt_id = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step33-ledger-history.json",
        encoding="utf-8",
    )
)

entries = payload["entries"]

assert len(entries) == 2

second = entries[0]
first = entries[1]

assert first["ledgerNumber"] == 1
assert first["receiptId"] == valid_receipt_id
assert first["receiptNumber"] == 1
assert first["verificationResult"] == "VALID"
assert first["previousLedgerHash"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    first["receiptPayloadHash"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    first["ledgerHash"],
)

assert second["ledgerNumber"] == 2
assert second["receiptId"] == invalid_receipt_id
assert second["receiptNumber"] == 2
assert second["verificationResult"] == "INVALID"

assert (
    second["previousLedgerHash"]
    == first["ledgerHash"]
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    second["receiptPayloadHash"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    second["ledgerHash"],
)

print(
    first["id"]
)
PY
)"

LEDGER_ONE_HASH="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-ledger-history.json",
        encoding="utf-8",
    )
)

print(
    payload["entries"][1]["ledgerHash"]
)
PY
)"

LEDGER_TWO_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-ledger-history.json",
        encoding="utf-8",
    )
)

print(
    payload["entries"][0]["id"]
)
PY
)"

echo "LEDGER_ONE_ID=${LEDGER_ONE_ID}"
echo "LEDGER_TWO_ID=${LEDGER_TWO_ID}"
echo "LEDGER_ONE_HASH=${LEDGER_ONE_HASH}"

echo "[PASS] RECEIPT_LEDGER_HISTORY"

echo
echo "===== VERIFY RECEIPT LEDGER ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/verify" \
  >/tmp/terrascope-step33-ledger-verify.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-ledger-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["entryCount"] == 2
assert payload["verifiedEntryCount"] == 2

assert payload["brokenLedgerEntryId"] is None
assert payload["brokenReceiptId"] is None

print(
    "[PASS] RECEIPT_LEDGER_CHAIN_VERIFICATION"
)
PY

echo
echo "===== TAMPER VERIFICATION RECEIPT ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_verification_receipts

      SET message =
          message || ' [tampered]'

      WHERE id =
            '${VALID_RECEIPT_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/verify" \
  >/tmp/terrascope-step33-receipt-tamper.json

python3 \
  - "${VALID_RECEIPT_ID}" \
<<'PY'
import json
import sys

receipt_id = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step33-receipt-tamper.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["brokenLedgerNumber"] == 1
assert payload["brokenReceiptNumber"] == 1
assert payload["brokenReceiptId"] == receipt_id

assert (
    "payload has changed"
    in payload["message"].lower()
)

print(
    "[PASS] RECEIPT_ROW_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE VERIFICATION RECEIPT ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_verification_receipts

      SET message =
          regexp_replace(
              message,
              ' \\[tampered\\]$',
              ''
          )

      WHERE id =
            '${VALID_RECEIPT_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/verify" \
  >/tmp/terrascope-step33-receipt-restored.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-receipt-restored.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["verifiedEntryCount"] == 2

print(
    "[PASS] RECEIPT_ROW_RESTORATION_VERIFIED"
)
PY

echo
echo "===== TAMPER LEDGER CHAIN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_receipt_ledger

      SET previous_ledger_hash =
          '0000000000000000000000000000000000000000000000000000000000000000'

      WHERE id =
            '${LEDGER_TWO_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/verify" \
  >/tmp/terrascope-step33-chain-tamper.json

python3 \
  - "${LEDGER_TWO_ID}" \
  "${INVALID_RECEIPT_ID}" \
<<'PY'
import json
import sys

ledger_id = sys.argv[1]
receipt_id = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step33-chain-tamper.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["verifiedEntryCount"] == 1

assert payload["brokenLedgerEntryId"] == ledger_id
assert payload["brokenLedgerNumber"] == 2

assert payload["brokenReceiptId"] == receipt_id
assert payload["brokenReceiptNumber"] == 2

assert (
    "previous hash"
    in payload["message"].lower()
)

print(
    "[PASS] RECEIPT_LEDGER_CHAIN_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE LEDGER CHAIN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_receipt_ledger

      SET previous_ledger_hash =
          '${LEDGER_ONE_HASH}'

      WHERE id =
            '${LEDGER_TWO_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/verify" \
  >/tmp/terrascope-step33-chain-restored.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step33-chain-restored.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["verifiedEntryCount"] == 2

print(
    "[PASS] RECEIPT_LEDGER_CHAIN_RESTORATION"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

LEDGER_COLUMN_COUNT="$(
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
            'resilience_governance_assurance_receipt_ledger';
      " \
    | tr -d '[:space:]'
)"

LEDGER_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_assurance_receipt_ledger
      WHERE id IN (
          '${LEDGER_ONE_ID}'::uuid,
          '${LEDGER_TWO_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

RECEIPT_FK_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_governance_assurance_receipt_ledger'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_governance_assurance_verification_receipts%';
      " \
    | tr -d '[:space:]'
)"

LEDGER_NUMBER_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_assurance_receipt_ledger_number';
      " \
    | tr -d '[:space:]'
)"

LEDGER_RECEIPT_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_assurance_receipt_ledger_receipt';
      " \
    | tr -d '[:space:]'
)"

echo "RECEIPT_LEDGER_COLUMN_COUNT=${LEDGER_COLUMN_COUNT}"
echo "RECEIPT_LEDGER_ROW_COUNT=${LEDGER_ROW_COUNT}"
echo "RECEIPT_LEDGER_RECEIPT_FK_COUNT=${RECEIPT_FK_COUNT}"
echo "RECEIPT_LEDGER_NUMBER_UNIQUE_COUNT=${LEDGER_NUMBER_UNIQUE}"
echo "RECEIPT_LEDGER_RECEIPT_UNIQUE_COUNT=${LEDGER_RECEIPT_UNIQUE}"

[[ "${LEDGER_COLUMN_COUNT}" == "12" ]]
[[ "${LEDGER_ROW_COUNT}" == "2" ]]
[[ "${RECEIPT_FK_COUNT}" == "1" ]]
[[ "${LEDGER_NUMBER_UNIQUE}" == "1" ]]
[[ "${LEDGER_RECEIPT_UNIQUE}" == "1" ]]

echo "[PASS] RECEIPT_LEDGER_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 33 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_receipt_ledger
      WHERE id IN (
          '${LEDGER_ONE_ID}'::uuid,
          '${LEDGER_TWO_ID}'::uuid
      );

      DELETE
      FROM resilience_governance_assurance_verification_receipts
      WHERE id IN (
          '${VALID_RECEIPT_ID}'::uuid,
          '${INVALID_RECEIPT_ID}'::uuid
      );
    " \
  >/dev/null

FINAL_LEDGER_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_assurance_receipt_ledger;
      " \
    | tr -d '[:space:]'
)"

FINAL_RECEIPT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_assurance_verification_receipts;
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_RECEIPT_LEDGER_COUNT=${FINAL_LEDGER_COUNT}"
echo "FINAL_VERIFICATION_RECEIPT_COUNT=${FINAL_RECEIPT_COUNT}"

[[ "${FINAL_LEDGER_COUNT}" == "0" ]]
[[ "${FINAL_RECEIPT_COUNT}" == "0" ]]

echo "[PASS] STEP33_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 33 VERIFICATION PASSED"
echo "========================================================================"
