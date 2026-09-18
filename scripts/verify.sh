#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step32-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep32Keygen.java"

PRIVATE_FILE="/tmp/terrascope-step32-private.b64"
PUBLIC_FILE="/tmp/terrascope-step32-public.b64"

ENVELOPE_ONE="/tmp/terrascope-step32-envelope-one.json"
ENVELOPE_TWO="/tmp/terrascope-step32-envelope-two.json"
TAMPERED_ENVELOPE="/tmp/terrascope-step32-envelope-tampered.json"

KEY_ID="verification-step32-ed25519"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi

  rm -f \
    "${KEYGEN_SOURCE}" \
    "${PRIVATE_FILE}" \
    "${PUBLIC_FILE}" \
    "${ENVELOPE_ONE}" \
    "${ENVELOPE_TWO}" \
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
       && ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
      cat "${BACKEND_LOG}"
      return 1
    fi

    sleep 1
  done

  return 1
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 32 VERIFICATION"
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
  npx eslint . --max-warnings=0
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

public class TerraScopeStep32Keygen {
    public static void main(String[] args) throws Exception {
        KeyPair pair =
                KeyPairGenerator
                        .getInstance("Ed25519")
                        .generateKeyPair();

        Files.writeString(
                Path.of(args[0]),
                Base64.getEncoder()
                        .encodeToString(
                                pair.getPrivate().getEncoded()
                        )
        );

        Files.writeString(
                Path.of(args[1]),
                Base64.getEncoder()
                        .encodeToString(
                                pair.getPublic().getEncoded()
                        )
        );
    }
}
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${PRIVATE_FILE}" \
  "${PUBLIC_FILE}"

PRIVATE_KEY="$(cat "${PRIVATE_FILE}")"
PUBLIC_KEY="$(cat "${PUBLIC_FILE}")"

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
  >/tmp/terrascope-step32-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step32-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.32.0"

print("[PASS] STATUS_ENDPOINT")
PY

for VERSION in $(seq 1 32); do
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

echo "[PASS] FLYWAY_V32_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_verification_receipts;

      DELETE
      FROM resilience_governance_assurance_bundles;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id = '${KEY_ID}'
         OR replacement_key_id = '${KEY_ID}';

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE key_id = '${KEY_ID}';

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id = '${KEY_ID}';

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step32-realized';
    " \
  >/dev/null

echo "[PASS] STEP32_PRE_CLEAN"

echo
echo "===== CREATE SIGNED PORTABLE BUNDLE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step32-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-review.json"))["id"])
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-review.json"))["currentSnapshotId"])
PY
)"

cat > /tmp/terrascope-step32-objective.json <<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Prove air-gapped governance assurance",
  "description": "Step 32 stateless assurance verification fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Portable assurance verifies after all mutable source evidence is removed."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step32-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step32-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-objective-response.json"))["objectives"][0]["id"])
PY
)"

cat > /tmp/terrascope-step32-complete.json <<'JSON'
{
  "completionEvidence": "Air-gapped assurance verification controls completed."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step32-complete.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

cat > /tmp/terrascope-step32-incident.json <<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step32-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 32 Air-Gapped Assurance Incident",
  "description": "Stateless verification fixture.",
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
  --data @/tmp/terrascope-step32-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step32-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-incident-response.json"))["incident"]["id"])
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
  >/tmp/terrascope-step32-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-aar.json"))["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/dev/null

cat > /tmp/terrascope-step32-improvement.json <<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "TECHNOLOGY",
  "title": "Validate air-gapped assurance exchange",
  "description": "Step 32 evidence portability improvement.",
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
  --data @/tmp/terrascope-step32-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step32-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-improvement-response.json"))["actions"][0]["id"])
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

cat > /tmp/terrascope-step32-exercise.json <<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TECHNICAL",
  "title": "Air-gapped assurance exercise",
  "description": "Validate source-independent verification.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step32-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step32-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-exercise-response.json"))["exercises"][0]["id"])
PY
)"

cat > /tmp/terrascope-step32-assessment.json <<'JSON'
{
  "result": "PASSED",
  "readinessScore": 99,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Air-gapped assurance controls validated.",
  "findings": "No material portability gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step32-assessment.json \
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
  >/tmp/terrascope-step32-outcome.json

OUTCOME_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-outcome.json"))["validationSnapshotId"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step32-certificate.json

CERTIFICATE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-certificate.json"))["id"])
PY
)"

BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-certificate.json"))["benefitSnapshotId"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step32-signature.json

SIGNATURE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-signature.json"))["id"])
PY
)"

curl --fail --silent --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step32-bundle.json

BUNDLE_ID="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-bundle.json"))["id"])
PY
)"

BUNDLE_NUMBER="$(
  python3 - <<'PY'
import json
print(json.load(open("/tmp/terrascope-step32-bundle.json"))["bundleNumber"])
PY
)"

echo "BUNDLE_ID=${BUNDLE_ID}"
echo "BUNDLE_NUMBER=${BUNDLE_NUMBER}"

echo "[PASS] AIRGAP_SOURCE_BUNDLE_CREATED"

echo
echo "===== DETERMINISTIC EXPORT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/export/${BUNDLE_ID}" \
  --output "${ENVELOPE_ONE}"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/export/${BUNDLE_ID}" \
  --output "${ENVELOPE_TWO}"

cmp \
  "${ENVELOPE_ONE}" \
  "${ENVELOPE_TWO}"

ENVELOPE_SHA="$(
  shasum -a 256 \
    "${ENVELOPE_ONE}" \
    | awk '{print $1}'
)"

echo "EXPORTED_ENVELOPE_SHA256=${ENVELOPE_SHA}"

python3 \
  - "${ENVELOPE_ONE}" \
  "${BUNDLE_ID}" \
  "${BUNDLE_NUMBER}" \
<<'PY'
import json
import re
import sys

path = sys.argv[1]
bundle_id = sys.argv[2]
bundle_number = int(sys.argv[3])

payload = json.load(
    open(
        path,
        encoding="utf-8",
    )
)

assert (
    payload["envelopeVersion"]
    == "TS-GOVERNANCE-AIRGAP-ENVELOPE-V1"
)

assert payload["sourceBundleId"] == bundle_id
assert payload["sourceBundleNumber"] == bundle_number

assert (
    payload["bundleVersion"]
    == "TS-GOVERNANCE-ASSURANCE-BUNDLE-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["bundleHash"],
)

assert isinstance(
    payload["payloadJson"],
    str,
)

print(
    "[PASS] DETERMINISTIC_AIRGAP_EXPORT"
)
PY

echo
echo "===== DELETE ALL MUTABLE SOURCE EVIDENCE ====="

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
            'verification-step32-realized';

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

SOURCE_BUNDLE_COUNT="$(
  docker compose exec -T postgres \
    psql -U terrascope -d terrascope -tAc \
    "
    SELECT COUNT(*)
    FROM resilience_governance_assurance_bundles
    WHERE id='${BUNDLE_ID}'::uuid;
    " \
    | tr -d '[:space:]'
)"

SOURCE_CERT_COUNT="$(
  docker compose exec -T postgres \
    psql -U terrascope -d terrascope -tAc \
    "
    SELECT COUNT(*)
    FROM resilience_governance_closure_certificates
    WHERE id='${CERTIFICATE_ID}'::uuid;
    " \
    | tr -d '[:space:]'
)"

SOURCE_SIGNATURE_COUNT="$(
  docker compose exec -T postgres \
    psql -U terrascope -d terrascope -tAc \
    "
    SELECT COUNT(*)
    FROM resilience_governance_closure_signatures
    WHERE id='${SIGNATURE_ID}'::uuid;
    " \
    | tr -d '[:space:]'
)"

SOURCE_ANCHOR_COUNT="$(
  docker compose exec -T postgres \
    psql -U terrascope -d terrascope -tAc \
    "
    SELECT COUNT(*)
    FROM resilience_governance_trust_anchors
    WHERE key_id='${KEY_ID}';
    " \
    | tr -d '[:space:]'
)"

echo "SOURCE_BUNDLE_COUNT=${SOURCE_BUNDLE_COUNT}"
echo "SOURCE_CERTIFICATE_COUNT=${SOURCE_CERT_COUNT}"
echo "SOURCE_SIGNATURE_COUNT=${SOURCE_SIGNATURE_COUNT}"
echo "SOURCE_TRUST_ANCHOR_COUNT=${SOURCE_ANCHOR_COUNT}"

[[ "${SOURCE_BUNDLE_COUNT}" == "0" ]]
[[ "${SOURCE_CERT_COUNT}" == "0" ]]
[[ "${SOURCE_SIGNATURE_COUNT}" == "0" ]]
[[ "${SOURCE_ANCHOR_COUNT}" == "0" ]]

echo "[PASS] LIVE_SOURCE_STATE_REMOVED"

echo
echo "===== RESTART WITHOUT PRIVATE KEY ====="

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

echo "[PASS] KEYLESS_STATELESS_RUNTIME_READY"

echo
echo "===== VERIFY EXPORTED ENVELOPE ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE_ONE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verify-envelope" \
  >/tmp/terrascope-step32-valid-verification.json

python3 \
  - "${BUNDLE_ID}" \
  "${BUNDLE_NUMBER}" \
  "${ENVELOPE_SHA}" \
<<'PY'
import json
import sys

bundle_id = sys.argv[1]
bundle_number = int(sys.argv[2])
envelope_hash = sys.argv[3]

payload = json.load(
    open(
        "/tmp/terrascope-step32-valid-verification.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["sourceBundleId"] == bundle_id
assert payload["sourceBundleNumber"] == bundle_number

assert payload["envelopeHash"] == envelope_hash

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["portableVerificationValid"] is True

assert payload["signatureCount"] == 1
assert payload["verifiedSignatureCount"] == 1

assert payload["trustAnchorCount"] == 1

print(
    "[PASS] STATELESS_SOURCE_FREE_VERIFICATION"
)

print(
    "[PASS] STATELESS_PRIVATE_KEY_FREE_VERIFICATION"
)
PY

echo
echo "===== RECORD VALID RECEIPT ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE_ONE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step32-valid-receipt.json

VALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step32-valid-receipt.json",
        encoding="utf-8",
    )
)

assert payload["receiptNumber"] == 1
assert payload["verificationResult"] == "VALID"

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["portableVerificationValid"] is True

assert payload["signatureCount"] == 1
assert payload["verifiedSignatureCount"] == 1

print(payload["id"])
PY
)"

echo "VALID_RECEIPT_ID=${VALID_RECEIPT_ID}"

echo "[PASS] VALID_STATELESS_VERIFICATION_RECEIPT"

echo
echo "===== BUILD CRYPTOGRAPHICALLY TAMPERED ENVELOPE ====="

python3 \
  - \
  "${ENVELOPE_ONE}" \
  "${TAMPERED_ENVELOPE}" \
<<'PY_TAMPER_BUILD'
import base64
import hashlib
import json
import os
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

pattern = (
    r'("signatureBase64":")[^"]+(")'
)

tampered_payload_json, replacements = (
    re.subn(
        pattern,

        lambda match:
            match.group(1)
            + zero_signature
            + match.group(2),

        payload_json,

        count=1,
    )
)

assert replacements == 1

assert (
    tampered_payload_json
    != payload_json
)

# Critical Step 32 recovery rule:
# DO NOT parse/re-serialize payloadJson.
#
# payloadJson was produced by TerraScope's Jackson 3
# canonical encoder. Replacing only one JSON scalar
# preserves the exact property ordering and formatting
# required by canonicalPayloadValid.

parsed_payload = json.loads(
    tampered_payload_json
)

assert (
    parsed_payload[
        "signatures"
    ][0][
        "signatureBase64"
    ]
    == zero_signature
)

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

assert os.path.isfile(
    target
)

assert os.path.getsize(
    target
) > 0
PY_TAMPER_BUILD

if [[ ! -s "${TAMPERED_ENVELOPE}" ]]; then
  echo "[FAIL] TAMPERED_ENVELOPE_NOT_CREATED"
  exit 1
fi

python3 \
  - \
  "${ENVELOPE_ONE}" \
  "${TAMPERED_ENVELOPE}" \
<<'PY_TAMPER_AUTHORITY'
import base64
import hashlib
import json
import os
import sys

source = sys.argv[1]
target = sys.argv[2]

with open(
    source,
    encoding="utf-8",
) as handle:
    original = json.load(
        handle
    )

with open(
    target,
    encoding="utf-8",
) as handle:
    tampered = json.load(
        handle
    )

original_payload_json = original[
    "payloadJson"
]

tampered_payload_json = tampered[
    "payloadJson"
]

original_payload = json.loads(
    original_payload_json
)

tampered_payload = json.loads(
    tampered_payload_json
)

zero_signature = (
    base64.b64encode(
        bytes(64)
    ).decode()
)

assert (
    original_payload[
        "signatures"
    ][0][
        "signatureBase64"
    ]
    != zero_signature
)

assert (
    tampered_payload[
        "signatures"
    ][0][
        "signatureBase64"
    ]
    == zero_signature
)

original_payload[
    "signatures"
][0][
    "signatureBase64"
] = zero_signature

# Semantic payload equality proves that only the
# signature scalar changed.
assert (
    original_payload
    == tampered_payload
)

expected_bundle_hash = hashlib.sha256(
    tampered_payload_json.encode(
        "utf-8"
    )
).hexdigest()

assert (
    tampered[
        "bundleHash"
    ]
    == expected_bundle_hash
)

assert (
    tampered[
        "bundleHash"
    ]
    != original[
        "bundleHash"
    ]
)

size = os.path.getsize(
    target
)

print(
    f"TAMPERED_ENVELOPE_SIZE={size}"
)

print(
    "[PASS] TAMPERED_ENVELOPE_FILE_AUTHORITY"
)

print(
    "[PASS] TAMPERED_PAYLOAD_SCALAR_ONLY_MUTATION"
)

print(
    "[PASS] TAMPERED_BUNDLE_HASH_RECALCULATED"
)
PY_TAMPER_AUTHORITY

echo "[PASS] TAMPERED_ENVELOPE_CREATED"

echo
echo "===== STATELESS SIGNATURE TAMPER DETECTION ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${TAMPERED_ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verify-envelope" \
  >/tmp/terrascope-step32-invalid-verification.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step32-invalid-verification.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True

assert payload["portableVerificationValid"] is False

assert payload["signatureCount"] == 1
assert payload["verifiedSignatureCount"] == 0

assert (
    "verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] STATELESS_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== RECORD INVALID RECEIPT ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${TAMPERED_ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step32-invalid-receipt.json

INVALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step32-invalid-receipt.json",
        encoding="utf-8",
    )
)

assert payload["receiptNumber"] == 2
assert payload["verificationResult"] == "INVALID"

assert payload["hashValid"] is True
assert payload["portableVerificationValid"] is False

print(payload["id"])
PY
)"

echo "INVALID_RECEIPT_ID=${INVALID_RECEIPT_ID}"

echo "[PASS] INVALID_STATELESS_VERIFICATION_RECEIPT"

echo
echo "===== RECEIPT HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/airgap/verification-receipts" \
  >/tmp/terrascope-step32-receipt-history.json

python3 \
  - "${VALID_RECEIPT_ID}" \
  "${INVALID_RECEIPT_ID}" \
<<'PY'
import json
import sys

valid_id = sys.argv[1]
invalid_id = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step32-receipt-history.json",
        encoding="utf-8",
    )
)

receipts = payload["receipts"]

assert len(receipts) == 2

assert receipts[0]["id"] == invalid_id
assert receipts[0]["verificationResult"] == "INVALID"

assert receipts[1]["id"] == valid_id
assert receipts[1]["verificationResult"] == "VALID"

print(
    "[PASS] STATELESS_VERIFICATION_RECEIPT_HISTORY"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

COLUMN_COUNT="$(
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
            'resilience_governance_assurance_verification_receipts';
      " \
    | tr -d '[:space:]'
)"

ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_assurance_verification_receipts
      WHERE id IN (
          '${VALID_RECEIPT_ID}'::uuid,
          '${INVALID_RECEIPT_ID}'::uuid
      );
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
      WHERE conname=
            'uq_governance_assurance_verification_receipt_number';
      " \
    | tr -d '[:space:]'
)"

echo "STATELESS_RECEIPT_COLUMN_COUNT=${COLUMN_COUNT}"
echo "STATELESS_RECEIPT_ROW_COUNT=${ROW_COUNT}"
echo "STATELESS_RECEIPT_UNIQUE_COUNT=${UNIQUE_COUNT}"

[[ "${COLUMN_COUNT}" == "19" ]]
[[ "${ROW_COUNT}" == "2" ]]
[[ "${UNIQUE_COUNT}" == "1" ]]

echo "[PASS] STATELESS_VERIFICATION_DATABASE_CONTRACT"

echo
echo "===== CLEAN RECEIPTS ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_verification_receipts
      WHERE id IN (
          '${VALID_RECEIPT_ID}'::uuid,
          '${INVALID_RECEIPT_ID}'::uuid
      );
    " \
  >/dev/null

RECEIPT_REMAINDER="$(
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

[[ "${RECEIPT_REMAINDER}" == "0" ]]

echo "[PASS] STEP32_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 32 VERIFICATION PASSED"
echo "========================================================================"
