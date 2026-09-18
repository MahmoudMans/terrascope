#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step34-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep34Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step34-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step34-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step34-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step34-key2-public.b64"

ENVELOPE="/tmp/terrascope-step34-envelope.json"
TAMPERED_ENVELOPE="/tmp/terrascope-step34-tampered-envelope.json"

KEY1_ID="verification-step34-ed25519-v1"
KEY2_ID="verification-step34-ed25519-v2"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi

  rm -f \
    "${KEYGEN_SOURCE}" \
    "${KEY1_PRIVATE_FILE}" \
    "${KEY1_PUBLIC_FILE}" \
    "${KEY2_PRIVATE_FILE}" \
    "${KEY2_PUBLIC_FILE}" \
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

start_with_key() {
  local KEY_ID="$1"
  local PRIVATE_KEY="$2"
  local PUBLIC_KEY="$3"

  (
    cd apps/backend

    TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID="${KEY_ID}" \
    TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64="${PRIVATE_KEY}" \
    TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64="${PUBLIC_KEY}" \
      mvn spring-boot:run
  ) >> "${BACKEND_LOG}" 2>&1 &

  BACKEND_PID=$!

  wait_backend
}

start_without_key() {
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
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 34 VERIFICATION"
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

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class TerraScopeStep34Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        KeyPair first =
                generator.generateKeyPair();

        KeyPair second =
                generator.generateKeyPair();

        Files.writeString(
                Path.of(args[0]),
                Base64.getEncoder()
                        .encodeToString(
                                first.getPrivate().getEncoded()
                        )
        );

        Files.writeString(
                Path.of(args[1]),
                Base64.getEncoder()
                        .encodeToString(
                                first.getPublic().getEncoded()
                        )
        );

        Files.writeString(
                Path.of(args[2]),
                Base64.getEncoder()
                        .encodeToString(
                                second.getPrivate().getEncoded()
                        )
        );

        Files.writeString(
                Path.of(args[3]),
                Base64.getEncoder()
                        .encodeToString(
                                second.getPublic().getEncoded()
                        )
        );
    }
}
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${KEY1_PRIVATE_FILE}" \
  "${KEY1_PUBLIC_FILE}" \
  "${KEY2_PRIVATE_FILE}" \
  "${KEY2_PUBLIC_FILE}"

KEY1_PRIVATE="$(cat "${KEY1_PRIVATE_FILE}")"
KEY1_PUBLIC="$(cat "${KEY1_PUBLIC_FILE}")"

KEY2_PRIVATE="$(cat "${KEY2_PRIVATE_FILE}")"
KEY2_PUBLIC="$(cat "${KEY2_PUBLIC_FILE}")"

[[ "${KEY1_PUBLIC}" != "${KEY2_PUBLIC}" ]]

echo "[PASS] TWO_LOCAL_ED25519_KEYPAIRS"

rm -f "${BACKEND_LOG}"

start_with_key \
  "${KEY1_ID}" \
  "${KEY1_PRIVATE}" \
  "${KEY1_PUBLIC}"

echo "[PASS] KEY1_RUNTIME_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step34-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step34-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.34.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 34); do
  COUNT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version='${VERSION}'
          AND success=TRUE;
        " \
      | tr -d '[:space:]'
  )"

  echo "FLYWAY_V${VERSION}_COUNT=${COUNT}"

  [[ "${COUNT}" == "1" ]]
done

echo "[PASS] FLYWAY_V34_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_receipt_checkpoints;

      DELETE
      FROM resilience_governance_assurance_receipt_ledger;

      DELETE
      FROM resilience_governance_assurance_verification_receipts;

      DELETE
      FROM resilience_governance_assurance_bundles;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      )
         OR replacement_key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step34-realized';
    " \
  >/dev/null

echo "[PASS] STEP34_PRE_CLEAN"

echo
echo "===== CREATE PORTABLE GOVERNANCE FIXTURE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step34-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-review.json"))["id"])
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-review.json"))["currentSnapshotId"])
PY
)"

cat > /tmp/terrascope-step34-objective.json <<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Cryptographically checkpoint assurance decisions",
  "description": "Step 34 signed receipt-ledger checkpoint fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Receipt-ledger heads are cryptographically attested through Ed25519 checkpoints."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step34-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step34-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-objective-response.json"))["objectives"][0]["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"completionEvidence":"Signed receipt-ledger checkpoint governance implemented."}' \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

cat > /tmp/terrascope-step34-incident.json <<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step34-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 34 Signed Checkpoint Incident",
  "description": "Signed receipt-ledger checkpoint fixture.",
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
  --data @/tmp/terrascope-step34-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step34-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-incident-response.json"))["incident"]["id"])
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
  >/tmp/terrascope-step34-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-aar.json"))["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/dev/null

cat > /tmp/terrascope-step34-improvement.json <<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "TECHNOLOGY",
  "title": "Exercise signed transparency checkpoints",
  "description": "Validate Ed25519 receipt-ledger checkpoints.",
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
  --data @/tmp/terrascope-step34-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step34-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-improvement-response.json"))["actions"][0]["id"])
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

cat > /tmp/terrascope-step34-exercise.json <<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TECHNICAL",
  "title": "Signed checkpoint assurance exercise",
  "description": "Validate checkpoint signing, rotation, and tamper detection.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step34-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step34-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-exercise-response.json"))["exercises"][0]["id"])
PY
)"

cat > /tmp/terrascope-step34-assessment.json <<'JSON'
{
  "result": "PASSED",
  "readinessScore": 99,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Signed receipt-ledger checkpoint controls successfully exercised.",
  "findings": "No material signed-checkpoint assurance gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step34-assessment.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/assess" \
  >/dev/null

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification" \
  >/dev/null

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages" \
  >/dev/null

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/receipts" \
  >/dev/null

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step34-outcome.json

OUTCOME_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-outcome.json"))["validationSnapshotId"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step34-certificate.json

CERTIFICATE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-certificate.json"))["id"])
PY
)"

BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-certificate.json"))["benefitSnapshotId"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step34-signature.json

SIGNATURE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-signature.json"))["id"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step34-bundle.json

BUNDLE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-bundle.json"))["id"])
PY
)"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/export/${BUNDLE_ID}" \
  --output "${ENVELOPE}"

[[ -s "${ENVELOPE}" ]]

echo "[PASS] CHECKPOINT_PORTABLE_FIXTURE"

echo
echo "===== CREATE VALID + INVALID RECEIPTS ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step34-valid-receipt.json

VALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json
payload=json.load(open("/tmp/terrascope-step34-valid-receipt.json"))
assert payload["verificationResult"] == "VALID"
print(payload["id"])
PY
)"

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

envelope = json.load(
    open(
        source,
        encoding="utf-8",
    )
)

payload_json = envelope["payloadJson"]

zero_signature = base64.b64encode(
    bytes(64)
).decode()

tampered, count = re.subn(
    r'("signatureBase64":")[^"]+(")',
    lambda match:
        match.group(1)
        + zero_signature
        + match.group(2),
    payload_json,
    count=1,
)

assert count == 1

envelope["payloadJson"] = tampered
envelope["bundleHash"] = hashlib.sha256(
    tampered.encode("utf-8")
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

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${TAMPERED_ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step34-invalid-receipt.json

INVALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json
payload=json.load(open("/tmp/terrascope-step34-invalid-receipt.json"))
assert payload["verificationResult"] == "INVALID"
print(payload["id"])
PY
)"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/verify" \
  >/tmp/terrascope-step34-ledger-verify.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step34-ledger-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["entryCount"] == 2
assert payload["verifiedEntryCount"] == 2

print(
    "[PASS] CHECKPOINT_LEDGER_BASE_VALID"
)
PY

echo
echo "===== SIGN CHECKPOINT #1 WITH KEY 1 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints" \
  >/tmp/terrascope-step34-checkpoint1.json

CHECKPOINT1_ID="$(
  python3 \
    - "${KEY1_ID}" \
<<'PY'
import json
import re
import sys

key_id = sys.argv[1]

payload=json.load(
    open(
        "/tmp/terrascope-step34-checkpoint1.json",
        encoding="utf-8",
    )
)

assert payload["checkpointNumber"] == 1
assert payload["coveredLedgerNumber"] == 2
assert payload["entryCount"] == 2
assert payload["validReceiptCount"] == 1
assert payload["invalidReceiptCount"] == 1

assert payload["keyId"] == key_id
assert payload["algorithm"] == "ED25519"

assert payload["previousCheckpointHash"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["checkpointHash"],
)

print(payload["id"])
PY
)"

CHECKPOINT1_HASH="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-checkpoint1.json"))["checkpointHash"])
PY
)"

CHECKPOINT1_SIGNATURE="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-checkpoint1.json"))["signatureBase64"])
PY
)"

echo "CHECKPOINT1_ID=${CHECKPOINT1_ID}"
echo "CHECKPOINT1_HASH=${CHECKPOINT1_HASH}"

echo "[PASS] KEY1_SIGNED_LEDGER_CHECKPOINT"

echo
echo "===== DUPLICATE KEY/HEAD GUARD ====="

DUPLICATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step34-duplicate.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints"
)"

echo "CHECKPOINT_DUPLICATE_HTTP=${DUPLICATE_HTTP}"

[[ "${DUPLICATE_HTTP}" == "409" ]]

echo "[PASS] CHECKPOINT_DUPLICATE_GUARD"

echo
echo "===== VERIFY CHECKPOINT #1 ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step34-checkpoint-verify1.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step34-checkpoint-verify1.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["checkpointCount"] == 1
assert payload["verifiedCheckpointCount"] == 1

print(
    "[PASS] KEY1_CHECKPOINT_VERIFICATION"
)
PY

echo
echo "===== REGISTER REPLACEMENT KEY 2 ====="

cat > /tmp/terrascope-step34-register-key2.json <<JSON
{
  "keyId": "${KEY2_ID}",
  "publicKeyX509Base64": "${KEY2_PUBLIC}"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step34-register-key2.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/tmp/terrascope-step34-key2-anchor.json

echo "[PASS] CHECKPOINT_REPLACEMENT_KEY_REGISTERED"

echo
echo "===== RETIRE KEY 1 ====="

cat > /tmp/terrascope-step34-retire-key1.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "reason": "Step 34 signed checkpoint key rotation."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step34-retire-key1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors/${KEY1_ID}/retire" \
  >/tmp/terrascope-step34-key1-retired.json

python3 \
  - "${KEY1_ID}" \
  "${KEY2_ID}" \
<<'PY'
import json
import sys

key1 = sys.argv[1]
key2 = sys.argv[2]

payload=json.load(
    open(
        "/tmp/terrascope-step34-key1-retired.json",
        encoding="utf-8",
    )
)

assert payload["keyId"] == key1
assert payload["status"] == "RETIRED"
assert payload["supersededByKeyId"] == key2

print(
    "[PASS] CHECKPOINT_KEY1_RETIRED"
)
PY

echo
echo "===== HISTORICAL CHECKPOINT STILL VERIFIES ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step34-retired-checkpoint-verify.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step34-retired-checkpoint-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["verifiedCheckpointCount"] == 1

print(
    "[PASS] RETIRED_KEY_HISTORICAL_CHECKPOINT_VALID"
)
PY

echo
echo "===== RESTART WITH KEY 2 ====="

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

echo
echo "===== EXTEND RECEIPT LEDGER ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step34-third-receipt.json

THIRD_RECEIPT_ID="$(
  python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step34-third-receipt.json",
        encoding="utf-8",
    )
)

assert payload["receiptNumber"] == 3
assert payload["verificationResult"] == "VALID"

print(payload["id"])
PY
)"

echo "[PASS] CHECKPOINT_LEDGER_EXTENDED"

echo
echo "===== SIGN CHECKPOINT #2 WITH KEY 2 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints" \
  >/tmp/terrascope-step34-checkpoint2.json

CHECKPOINT2_ID="$(
  python3 \
    - "${KEY2_ID}" \
    "${CHECKPOINT1_HASH}" \
<<'PY'
import json
import re
import sys

key2 = sys.argv[1]
first_hash = sys.argv[2]

payload=json.load(
    open(
        "/tmp/terrascope-step34-checkpoint2.json",
        encoding="utf-8",
    )
)

assert payload["checkpointNumber"] == 2
assert payload["coveredLedgerNumber"] == 3

assert payload["entryCount"] == 3
assert payload["validReceiptCount"] == 2
assert payload["invalidReceiptCount"] == 1

assert payload["keyId"] == key2

assert payload["previousCheckpointHash"] == first_hash

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["checkpointHash"],
)

print(payload["id"])
PY
)"

CHECKPOINT2_SIGNATURE="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step34-checkpoint2.json"))["signatureBase64"])
PY
)"

echo "CHECKPOINT2_ID=${CHECKPOINT2_ID}"

echo "[PASS] KEY2_SIGNED_LEDGER_CHECKPOINT"

echo
echo "===== VERIFY DUAL-GENERATION CHECKPOINT CHAIN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step34-dual-checkpoint-verify.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step34-dual-checkpoint-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["checkpointCount"] == 2
assert payload["verifiedCheckpointCount"] == 2

print(
    "[PASS] DUAL_GENERATION_CHECKPOINT_VERIFICATION"
)
PY

echo
echo "===== RESTART WITHOUT PRIVATE KEY ====="

stop_backend

start_without_key

echo "[PASS] CHECKPOINT_KEYLESS_RUNTIME_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step34-keyless-checkpoint-verify.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step34-keyless-checkpoint-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["checkpointCount"] == 2
assert payload["verifiedCheckpointCount"] == 2

print(
    "[PASS] PRIVATE_KEY_FREE_CHECKPOINT_VERIFICATION"
)
PY

echo
echo "===== CHECKPOINT HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints" \
  >/tmp/terrascope-step34-checkpoint-history.json

python3 \
  - "${CHECKPOINT1_ID}" \
  "${CHECKPOINT2_ID}" \
  "${KEY1_ID}" \
  "${KEY2_ID}" \
<<'PY'
import json
import sys

checkpoint1 = sys.argv[1]
checkpoint2 = sys.argv[2]
key1 = sys.argv[3]
key2 = sys.argv[4]

payload=json.load(
    open(
        "/tmp/terrascope-step34-checkpoint-history.json",
        encoding="utf-8",
    )
)

entries=payload["checkpoints"]

assert len(entries) == 2

assert entries[0]["id"] == checkpoint2
assert entries[0]["keyId"] == key2

assert entries[1]["id"] == checkpoint1
assert entries[1]["keyId"] == key1

print(
    "[PASS] CHECKPOINT_HISTORY"
)
PY

echo
echo "===== TAMPER CHECKPOINT #2 SIGNATURE ====="

ZERO_SIGNATURE="$(
  python3 - <<'PY'
import base64
print(base64.b64encode(bytes(64)).decode())
PY
)"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_receipt_checkpoints

      SET signature_base64 =
          '${ZERO_SIGNATURE}'

      WHERE id =
            '${CHECKPOINT2_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step34-checkpoint-tamper.json

python3 \
  - "${CHECKPOINT2_ID}" \
<<'PY'
import json
import sys

checkpoint_id = sys.argv[1]

payload=json.load(
    open(
        "/tmp/terrascope-step34-checkpoint-tamper.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["verifiedCheckpointCount"] == 1

assert payload["brokenCheckpointId"] == checkpoint_id
assert payload["brokenCheckpointNumber"] == 2
assert payload["brokenLedgerNumber"] == 3

assert (
    "signature verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] CHECKPOINT_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE CHECKPOINT #2 ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_receipt_checkpoints

      SET signature_base64 =
          '${CHECKPOINT2_SIGNATURE}'

      WHERE id =
            '${CHECKPOINT2_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step34-checkpoint-restored.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step34-checkpoint-restored.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["verifiedCheckpointCount"] == 2

print(
    "[PASS] CHECKPOINT_SIGNATURE_RESTORATION"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

CHECKPOINT_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema='public'
        AND table_name=
            'resilience_governance_assurance_receipt_checkpoints';
      " \
    | tr -d '[:space:]'
)"

CHECKPOINT_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_assurance_receipt_checkpoints
      WHERE id IN (
          '${CHECKPOINT1_ID}'::uuid,
          '${CHECKPOINT2_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

LEDGER_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'fk_receipt_checkpoint_ledger';
      " \
    | tr -d '[:space:]'
)"

CHECKPOINT_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_receipt_checkpoint_number';
      " \
    | tr -d '[:space:]'
)"

LEDGER_KEY_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_receipt_checkpoint_ledger_key';
      " \
    | tr -d '[:space:]'
)"

echo "CHECKPOINT_COLUMN_COUNT=${CHECKPOINT_COLUMNS}"
echo "CHECKPOINT_ROW_COUNT=${CHECKPOINT_ROWS}"
echo "CHECKPOINT_LEDGER_FK_COUNT=${LEDGER_FK}"
echo "CHECKPOINT_NUMBER_UNIQUE_COUNT=${CHECKPOINT_UNIQUE}"
echo "CHECKPOINT_LEDGER_KEY_UNIQUE_COUNT=${LEDGER_KEY_UNIQUE}"

[[ "${CHECKPOINT_COLUMNS}" == "15" ]]
[[ "${CHECKPOINT_ROWS}" == "2" ]]
[[ "${LEDGER_FK}" == "1" ]]
[[ "${CHECKPOINT_UNIQUE}" == "1" ]]
[[ "${LEDGER_KEY_UNIQUE}" == "1" ]]

echo "[PASS] CHECKPOINT_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 34 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_receipt_checkpoints
      WHERE id IN (
          '${CHECKPOINT1_ID}'::uuid,
          '${CHECKPOINT2_ID}'::uuid
      );

      DELETE
      FROM resilience_governance_assurance_receipt_ledger;

      DELETE
      FROM resilience_governance_assurance_verification_receipts
      WHERE id IN (
          '${VALID_RECEIPT_ID}'::uuid,
          '${INVALID_RECEIPT_ID}'::uuid,
          '${THIRD_RECEIPT_ID}'::uuid
      );

      DELETE
      FROM resilience_governance_assurance_bundles
      WHERE id =
            '${BUNDLE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      )
         OR replacement_key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

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
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM resilience_benefit_realization_snapshots
      WHERE id =
            '${BENEFIT_SNAPSHOT_ID}'::uuid;

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step34-realized';

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

FINAL_CHECKPOINT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_assurance_receipt_checkpoints;
      " \
    | tr -d '[:space:]'
)"

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

echo "FINAL_CHECKPOINT_COUNT=${FINAL_CHECKPOINT_COUNT}"
echo "FINAL_RECEIPT_LEDGER_COUNT=${FINAL_LEDGER_COUNT}"
echo "FINAL_RECEIPT_COUNT=${FINAL_RECEIPT_COUNT}"

[[ "${FINAL_CHECKPOINT_COUNT}" == "0" ]]
[[ "${FINAL_LEDGER_COUNT}" == "0" ]]
[[ "${FINAL_RECEIPT_COUNT}" == "0" ]]

echo "[PASS] STEP34_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 34 VERIFICATION PASSED"
echo "========================================================================"
