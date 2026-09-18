#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step35-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep35Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step35-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step35-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step35-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step35-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step35-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step35-key3-public.b64"

ENVELOPE="/tmp/terrascope-step35-envelope.json"

KEY1_ID="verification-step35-ed25519-v1"
KEY2_ID="verification-step35-ed25519-v2"
KEY3_ID="verification-step35-ed25519-v3"

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
    "${KEY3_PRIVATE_FILE}" \
    "${KEY3_PUBLIC_FILE}" \
    "${ENVELOPE}"
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
       && ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then

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
echo " TERRASCOPE — STEP 35 VERIFICATION"
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

public class TerraScopeStep35Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        for (
                int index = 0;
                index < 3;
                index++
        ) {
            KeyPair pair =
                    generator.generateKeyPair();

            Files.writeString(
                    Path.of(
                            args[index * 2]
                    ),
                    Base64
                            .getEncoder()
                            .encodeToString(
                                    pair
                                            .getPrivate()
                                            .getEncoded()
                            )
            );

            Files.writeString(
                    Path.of(
                            args[index * 2 + 1]
                    ),
                    Base64
                            .getEncoder()
                            .encodeToString(
                                    pair
                                            .getPublic()
                                            .getEncoded()
                            )
            );
        }
    }
}
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${KEY1_PRIVATE_FILE}" \
  "${KEY1_PUBLIC_FILE}" \
  "${KEY2_PRIVATE_FILE}" \
  "${KEY2_PUBLIC_FILE}" \
  "${KEY3_PRIVATE_FILE}" \
  "${KEY3_PUBLIC_FILE}"

KEY1_PRIVATE="$(cat "${KEY1_PRIVATE_FILE}")"
KEY1_PUBLIC="$(cat "${KEY1_PUBLIC_FILE}")"

KEY2_PRIVATE="$(cat "${KEY2_PRIVATE_FILE}")"
KEY2_PUBLIC="$(cat "${KEY2_PUBLIC_FILE}")"

KEY3_PRIVATE="$(cat "${KEY3_PRIVATE_FILE}")"
KEY3_PUBLIC="$(cat "${KEY3_PUBLIC_FILE}")"

[[ "${KEY1_PUBLIC}" != "${KEY2_PUBLIC}" ]]
[[ "${KEY2_PUBLIC}" != "${KEY3_PUBLIC}" ]]
[[ "${KEY1_PUBLIC}" != "${KEY3_PUBLIC}" ]]

echo "[PASS] THREE_LOCAL_ED25519_KEYPAIRS"

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
  >/tmp/terrascope-step35-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step35-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.35.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 35); do
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

echo "[PASS] FLYWAY_V35_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_trust_anchor_transitions;

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
          '${KEY2_ID}',
          '${KEY3_ID}'
      )
         OR replacement_key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );

      UPDATE resilience_governance_trust_anchors
      SET superseded_by_key_id = NULL
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step35-realized';
    " \
  >/dev/null

echo "[PASS] STEP35_PRE_CLEAN"

echo
echo "===== CREATE GOVERNANCE FIXTURE ====="

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step35-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-review.json"))["id"])
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-review.json"))["currentSnapshotId"])
PY
)"

cat > /tmp/terrascope-step35-objective.json <<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Cryptographically prove governance key succession",
  "description": "Step 35 signed trust-anchor transition fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Every governance signing-key retirement is cryptographically linked to its successor."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step35-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step35-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-objective-response.json"))["objectives"][0]["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"completionEvidence":"Signed trust-anchor transition governance implemented."}' \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

cat > /tmp/terrascope-step35-incident.json <<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step35-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 35 Trust Lineage Incident",
  "description": "Signed trust-anchor transition verification fixture.",
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
  --data @/tmp/terrascope-step35-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step35-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-incident-response.json"))["incident"]["id"])
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
  >/tmp/terrascope-step35-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-aar.json"))["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/dev/null

cat > /tmp/terrascope-step35-improvement.json <<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "TECHNOLOGY",
  "title": "Validate cryptographic key succession",
  "description": "Exercise signed trust-anchor transition lineage.",
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
  --data @/tmp/terrascope-step35-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step35-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-improvement-response.json"))["actions"][0]["id"])
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

cat > /tmp/terrascope-step35-exercise.json <<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TECHNICAL",
  "title": "Signed key lineage exercise",
  "description": "Validate outgoing-key signatures and chained trust transitions.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step35-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step35-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-exercise-response.json"))["exercises"][0]["id"])
PY
)"

cat > /tmp/terrascope-step35-assessment.json <<'JSON'
{
  "result": "PASSED",
  "readinessScore": 99,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Signed trust-anchor transition controls successfully exercised.",
  "findings": "No material governance key-lineage assurance gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step35-assessment.json \
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
  >/tmp/terrascope-step35-outcome.json

OUTCOME_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-outcome.json"))["validationSnapshotId"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step35-certificate.json

CERTIFICATE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-certificate.json"))["id"])
PY
)"

BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-certificate.json"))["benefitSnapshotId"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step35-signature.json

SIGNATURE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-signature.json"))["id"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step35-bundle.json

BUNDLE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-bundle.json"))["id"])
PY
)"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/export/${BUNDLE_ID}" \
  --output "${ENVELOPE}"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step35-receipt1.json

RECEIPT1_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-receipt1.json"))["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints" \
  >/tmp/terrascope-step35-checkpoint1.json

CHECKPOINT1_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-checkpoint1.json"))["id"])
PY
)"

echo "[PASS] KEY1_GOVERNANCE_FIXTURE"

echo
echo "===== SIGN TRANSITION #1 — KEY1 -> KEY2 ====="

cat > /tmp/terrascope-step35-transition1-request.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 35 cryptographic rotation from generation one to generation two."
}
JSON

TRANSITION1_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step35-transition1.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step35-transition1-request.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate"
)"

echo "TRANSITION1_HTTP=${TRANSITION1_HTTP}"

if [[ "${TRANSITION1_HTTP}" != "200" ]]; then
  echo "TRANSITION1_RESPONSE_BEGIN"

  cat /tmp/terrascope-step35-transition1.json || true

  echo
  echo "TRANSITION1_RESPONSE_END"

  exit 1
fi

TRANSITION1_ID="$(
  python3 \
    - "${KEY1_ID}" \
    "${KEY2_ID}" \
<<'PY'
import json
import re
import sys

key1 = sys.argv[1]
key2 = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step35-transition1.json",
        encoding="utf-8",
    )
)

assert payload["transitionNumber"] == 1

assert payload["outgoingKeyId"] == key1
assert payload["replacementKeyId"] == key2

assert payload["algorithm"] == "ED25519"

assert payload["previousTransitionHash"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["transitionHash"],
)

print(payload["id"])
PY
)"

TRANSITION1_HASH="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-transition1.json"))["transitionHash"])
PY
)"

echo "TRANSITION1_ID=${TRANSITION1_ID}"
echo "TRANSITION1_HASH=${TRANSITION1_HASH}"

echo "[PASS] KEY1_TO_KEY2_SIGNED_TRANSITION"

echo
echo "===== KEY1 DUPLICATE ROTATION GUARD ====="

DUPLICATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step35-duplicate.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step35-transition1-request.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate"
)"

echo "KEY1_DUPLICATE_ROTATION_HTTP=${DUPLICATE_HTTP}"

[[ "${DUPLICATE_HTTP}" == "409" ]]

echo "[PASS] SIGNED_TRANSITION_DUPLICATE_GUARD"

echo
echo "===== VERIFY FIRST TRANSITION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/verify" \
  >/tmp/terrascope-step35-transition-verify1.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step35-transition-verify1.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["transitionCount"] == 1
assert payload["verifiedTransitionCount"] == 1

print(
    "[PASS] FIRST_SIGNED_TRANSITION_VERIFIED"
)
PY

echo
echo "===== HISTORICAL KEY1 CHECKPOINT AFTER RETIREMENT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step35-checkpoint-after-rotation1.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step35-checkpoint-after-rotation1.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["checkpointCount"] == 1
assert payload["verifiedCheckpointCount"] == 1

print(
    "[PASS] KEY1_HISTORICAL_CHECKPOINT_AFTER_SIGNED_ROTATION"
)
PY

echo
echo "===== RESTART WITH KEY2 ====="

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step35-receipt2.json

RECEIPT2_ID="$(
  python3 - <<'PY'
import json
payload=json.load(open("/tmp/terrascope-step35-receipt2.json"))
assert payload["receiptNumber"] == 2
print(payload["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints" \
  >/tmp/terrascope-step35-checkpoint2.json

CHECKPOINT2_ID="$(
  python3 \
    - "${KEY2_ID}" \
<<'PY'
import json
import sys

key2 = sys.argv[1]

payload=json.load(
    open(
        "/tmp/terrascope-step35-checkpoint2.json",
        encoding="utf-8",
    )
)

assert payload["checkpointNumber"] == 2
assert payload["coveredLedgerNumber"] == 2
assert payload["keyId"] == key2

print(payload["id"])
PY
)"

echo "[PASS] KEY2_LEDGER_CHECKPOINT"

echo
echo "===== SIGN TRANSITION #2 — KEY2 -> KEY3 ====="

cat > /tmp/terrascope-step35-transition2-request.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 35 cryptographic rotation from generation two to generation three."
}
JSON

TRANSITION2_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step35-transition2.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step35-transition2-request.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate"
)"

echo "TRANSITION2_HTTP=${TRANSITION2_HTTP}"

if [[ "${TRANSITION2_HTTP}" != "200" ]]; then
  echo "TRANSITION2_RESPONSE_BEGIN"

  cat /tmp/terrascope-step35-transition2.json || true

  echo
  echo "TRANSITION2_RESPONSE_END"

  exit 1
fi

TRANSITION2_ID="$(
  python3 \
    - "${KEY2_ID}" \
    "${KEY3_ID}" \
    "${TRANSITION1_HASH}" \
<<'PY'
import json
import re
import sys

key2 = sys.argv[1]
key3 = sys.argv[2]
first_hash = sys.argv[3]

payload = json.load(
    open(
        "/tmp/terrascope-step35-transition2.json",
        encoding="utf-8",
    )
)

assert payload["transitionNumber"] == 2

assert payload["outgoingKeyId"] == key2
assert payload["replacementKeyId"] == key3

assert payload["previousTransitionHash"] == first_hash

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["transitionHash"],
)

print(payload["id"])
PY
)"

TRANSITION2_SIGNATURE="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step35-transition2.json"))["signatureBase64"])
PY
)"

echo "TRANSITION2_ID=${TRANSITION2_ID}"

echo "[PASS] KEY2_TO_KEY3_SIGNED_TRANSITION"

echo
echo "===== VERIFY TWO-GENERATION TRANSITION CHAIN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/verify" \
  >/tmp/terrascope-step35-transition-verify2.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step35-transition-verify2.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["transitionCount"] == 2
assert payload["verifiedTransitionCount"] == 2

print(
    "[PASS] MULTI_GENERATION_TRUST_LINEAGE_VERIFIED"
)
PY

echo
echo "===== VERIFY BOTH RETIRED-KEY CHECKPOINTS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step35-checkpoint-after-rotation2.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step35-checkpoint-after-rotation2.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["checkpointCount"] == 2
assert payload["verifiedCheckpointCount"] == 2

print(
    "[PASS] MULTI_GENERATION_HISTORICAL_CHECKPOINTS_VALID"
)
PY

echo
echo "===== RESTART WITHOUT ANY PRIVATE KEY ====="

stop_backend
start_without_key

echo "[PASS] KEYLESS_TRUST_LINEAGE_RUNTIME_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/verify" \
  >/tmp/terrascope-step35-keyless-transition-verify.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step35-keyless-transition-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["transitionCount"] == 2
assert payload["verifiedTransitionCount"] == 2

print(
    "[PASS] PRIVATE_KEY_FREE_TRUST_LINEAGE_VERIFICATION"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/receipt-ledger/checkpoints/verify" \
  >/tmp/terrascope-step35-keyless-checkpoint-verify.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step35-keyless-checkpoint-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["verifiedCheckpointCount"] == 2

print(
    "[PASS] PRIVATE_KEY_FREE_HISTORICAL_CHECKPOINT_VERIFICATION"
)
PY

echo
echo "===== TRANSITION HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions" \
  >/tmp/terrascope-step35-transition-history.json

python3 \
  - "${TRANSITION1_ID}" \
  "${TRANSITION2_ID}" \
  "${KEY1_ID}" \
  "${KEY2_ID}" \
  "${KEY3_ID}" \
<<'PY'
import json
import sys

transition1 = sys.argv[1]
transition2 = sys.argv[2]

key1 = sys.argv[3]
key2 = sys.argv[4]
key3 = sys.argv[5]

payload=json.load(
    open(
        "/tmp/terrascope-step35-transition-history.json",
        encoding="utf-8",
    )
)

transitions = payload["transitions"]

assert len(transitions) == 2

assert transitions[0]["id"] == transition2
assert transitions[0]["outgoingKeyId"] == key2
assert transitions[0]["replacementKeyId"] == key3

assert transitions[1]["id"] == transition1
assert transitions[1]["outgoingKeyId"] == key1
assert transitions[1]["replacementKeyId"] == key2

print(
    "[PASS] SIGNED_TRUST_TRANSITION_HISTORY"
)
PY

echo
echo "===== TAMPER TRANSITION #2 SIGNATURE ====="

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
      UPDATE resilience_governance_trust_anchor_transitions

      SET signature_base64 =
          '${ZERO_SIGNATURE}'

      WHERE id =
            '${TRANSITION2_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/verify" \
  >/tmp/terrascope-step35-transition-tamper.json

python3 \
  - "${TRANSITION2_ID}" \
  "${KEY2_ID}" \
  "${KEY3_ID}" \
<<'PY'
import json
import sys

transition_id = sys.argv[1]
key2 = sys.argv[2]
key3 = sys.argv[3]

payload=json.load(
    open(
        "/tmp/terrascope-step35-transition-tamper.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["verifiedTransitionCount"] == 1

assert payload["brokenTransitionId"] == transition_id
assert payload["brokenTransitionNumber"] == 2

assert payload["brokenOutgoingKeyId"] == key2
assert payload["brokenReplacementKeyId"] == key3

assert (
    "signature verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] TRUST_TRANSITION_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE TRANSITION #2 ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_trust_anchor_transitions

      SET signature_base64 =
          '${TRANSITION2_SIGNATURE}'

      WHERE id =
            '${TRANSITION2_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/verify" \
  >/tmp/terrascope-step35-transition-restored.json

python3 - <<'PY'
import json

payload=json.load(
    open(
        "/tmp/terrascope-step35-transition-restored.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["verifiedTransitionCount"] == 2

print(
    "[PASS] TRUST_TRANSITION_SIGNATURE_RESTORATION"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

TRANSITION_COLUMNS="$(
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
            'resilience_governance_trust_anchor_transitions';
      " \
    | tr -d '[:space:]'
)"

TRANSITION_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_anchor_transitions
      WHERE id IN (
          '${TRANSITION1_ID}'::uuid,
          '${TRANSITION2_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

ANCHOR_FKS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid=
            'resilience_governance_trust_anchor_transitions'::regclass
        AND contype='f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_governance_trust_anchors%';
      " \
    | tr -d '[:space:]'
)"

NUMBER_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_governance_trust_transition_number';
      " \
    | tr -d '[:space:]'
)"

OUTGOING_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_governance_trust_transition_outgoing_key';
      " \
    | tr -d '[:space:]'
)"

echo "TRUST_TRANSITION_COLUMN_COUNT=${TRANSITION_COLUMNS}"
echo "TRUST_TRANSITION_ROW_COUNT=${TRANSITION_ROWS}"
echo "TRUST_TRANSITION_ANCHOR_FK_COUNT=${ANCHOR_FKS}"
echo "TRUST_TRANSITION_NUMBER_UNIQUE_COUNT=${NUMBER_UNIQUE}"
echo "TRUST_TRANSITION_OUTGOING_UNIQUE_COUNT=${OUTGOING_UNIQUE}"

[[ "${TRANSITION_COLUMNS}" == "13" ]]
[[ "${TRANSITION_ROWS}" == "2" ]]
[[ "${ANCHOR_FKS}" == "2" ]]
[[ "${NUMBER_UNIQUE}" == "1" ]]
[[ "${OUTGOING_UNIQUE}" == "1" ]]

echo "[PASS] TRUST_TRANSITION_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 35 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_trust_anchor_transitions
      WHERE id IN (
          '${TRANSITION1_ID}'::uuid,
          '${TRANSITION2_ID}'::uuid
      );

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
          '${RECEIPT1_ID}'::uuid,
          '${RECEIPT2_ID}'::uuid
      );

      DELETE
      FROM resilience_governance_assurance_bundles
      WHERE id =
            '${BUNDLE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      )
         OR replacement_key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE id =
            '${SIGNATURE_ID}'::uuid;

      DELETE
      FROM resilience_governance_closure_certificates
      WHERE id =
            '${CERTIFICATE_ID}'::uuid;

      UPDATE resilience_governance_trust_anchors

      SET superseded_by_key_id =
          NULL

      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );

      DELETE
      FROM resilience_benefit_realization_snapshots
      WHERE id =
            '${BENEFIT_SNAPSHOT_ID}'::uuid;

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step35-realized';

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

FINAL_TRANSITION_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_anchor_transitions;
      " \
    | tr -d '[:space:]'
)"

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

FINAL_ANCHOR_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_anchors
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}',
          '${KEY3_ID}'
      );
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_TRANSITION_COUNT=${FINAL_TRANSITION_COUNT}"
echo "FINAL_CHECKPOINT_COUNT=${FINAL_CHECKPOINT_COUNT}"
echo "FINAL_RECEIPT_COUNT=${FINAL_RECEIPT_COUNT}"
echo "FINAL_TRUST_ANCHOR_COUNT=${FINAL_ANCHOR_COUNT}"

[[ "${FINAL_TRANSITION_COUNT}" == "0" ]]
[[ "${FINAL_CHECKPOINT_COUNT}" == "0" ]]
[[ "${FINAL_RECEIPT_COUNT}" == "0" ]]
[[ "${FINAL_ANCHOR_COUNT}" == "0" ]]

echo "[PASS] STEP35_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 35 VERIFICATION PASSED"
echo "========================================================================"
