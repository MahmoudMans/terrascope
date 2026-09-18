#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step37-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep37Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step37-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step37-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step37-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step37-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step37-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step37-key3-public.b64"

LINEAGE_ENVELOPE="/tmp/terrascope-step37-lineage-envelope.json"
TAMPERED_ENVELOPE="/tmp/terrascope-step37-lineage-tampered.json"

CORRECT_PIN_REQUEST="/tmp/terrascope-step37-correct-pin-request.json"
WRONG_PIN_REQUEST="/tmp/terrascope-step37-wrong-pin-request.json"
TAMPER_PIN_REQUEST="/tmp/terrascope-step37-tamper-pin-request.json"

KEY1_ID="verification-step37-ed25519-v1"
KEY2_ID="verification-step37-ed25519-v2"
KEY3_ID="verification-step37-ed25519-v3"

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
    "${LINEAGE_ENVELOPE}" \
    "${TAMPERED_ENVELOPE}" \
    "${CORRECT_PIN_REQUEST}" \
    "${WRONG_PIN_REQUEST}" \
    "${TAMPER_PIN_REQUEST}"
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
echo " TERRASCOPE — STEP 37 VERIFICATION"
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
echo "===== GENERATE THREE ED25519 KEYPAIRS ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class TerraScopeStep37Keygen {

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
  >/tmp/terrascope-step37-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step37-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.37.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 37); do
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

echo "[PASS] FLYWAY_V37_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_trust_lineage_pin_receipts;

      DELETE
      FROM resilience_governance_trust_lineage_packages;

      DELETE
      FROM resilience_governance_trust_anchor_transitions
      WHERE outgoing_key_id IN (
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
    " \
  >/dev/null

BASE_TRANSITION_COUNT="$(
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

BASE_PACKAGE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_lineage_packages;
      " \
    | tr -d '[:space:]'
)"

BASE_PIN_RECEIPT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_lineage_pin_receipts;
      " \
    | tr -d '[:space:]'
)"

echo "BASE_TRANSITION_COUNT=${BASE_TRANSITION_COUNT}"
echo "BASE_LINEAGE_PACKAGE_COUNT=${BASE_PACKAGE_COUNT}"
echo "BASE_PIN_RECEIPT_COUNT=${BASE_PIN_RECEIPT_COUNT}"

[[ "${BASE_TRANSITION_COUNT}" == "0" ]]
[[ "${BASE_PACKAGE_COUNT}" == "0" ]]
[[ "${BASE_PIN_RECEIPT_COUNT}" == "0" ]]

echo "[PASS] STEP37_PRE_CLEAN"

echo
echo "===== REGISTER ROOT KEY ====="

cat > /tmp/terrascope-step37-register-key1.json <<JSON
{
  "keyId": "${KEY1_ID}",
  "publicKeyX509Base64": "${KEY1_PUBLIC}"
}
JSON

REGISTER_ROOT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step37-root-anchor.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step37-register-key1.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors"
)"

echo "REGISTER_ROOT_HTTP=${REGISTER_ROOT_HTTP}"

[[ "${REGISTER_ROOT_HTTP}" == "200" ]]

echo "[PASS] ROOT_TRUST_ANCHOR_REGISTERED"

echo
echo "===== ROTATE ROOT -> KEY2 ====="

cat > /tmp/terrascope-step37-transition1-request.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 37 externally pinned trust-root transition one."
}
JSON

TRANSITION1_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step37-transition1.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step37-transition1-request.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate"
)"

echo "TRANSITION1_HTTP=${TRANSITION1_HTTP}"

[[ "${TRANSITION1_HTTP}" == "200" ]]

echo "[PASS] ROOT_TO_KEY2_SIGNED_TRANSITION"

echo
echo "===== RESTART WITH KEY2 ====="

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

echo
echo "===== ROTATE KEY2 -> KEY3 ====="

cat > /tmp/terrascope-step37-transition2-request.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 37 externally pinned trust-root transition two."
}
JSON

TRANSITION2_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step37-transition2.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step37-transition2-request.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate"
)"

echo "TRANSITION2_HTTP=${TRANSITION2_HTTP}"

[[ "${TRANSITION2_HTTP}" == "200" ]]

echo "[PASS] KEY2_TO_KEY3_SIGNED_TRANSITION"

echo
echo "===== CREATE PORTABLE LINEAGE PACKAGE ====="

PACKAGE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step37-package.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages"
)"

echo "PACKAGE_HTTP=${PACKAGE_HTTP}"

[[ "${PACKAGE_HTTP}" == "200" ]]

PACKAGE_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step37-package.json",
        encoding="utf-8",
    )
)

assert payload["transitionCount"] == 2
assert payload["trustAnchorCount"] == 3

print(
    payload["id"]
)
PY
)"

echo "PACKAGE_ID=${PACKAGE_ID}"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages/export/${PACKAGE_ID}" \
  --output "${LINEAGE_ENVELOPE}"

[[ -s "${LINEAGE_ENVELOPE}" ]]

echo "[PASS] PORTABLE_LINEAGE_EXPORTED"

echo
echo "===== EXTRACT EXTERNAL ROOT PIN ====="

ROOT_PIN="$(
  python3 \
    - "${LINEAGE_ENVELOPE}" \
    "${KEY1_ID}" \
<<'PY'
import json
import re
import sys

file_name = sys.argv[1]
expected_root_key = sys.argv[2]

envelope = json.load(
    open(
        file_name,
        encoding="utf-8",
    )
)

payload = json.loads(
    envelope["payloadJson"]
)

assert payload["rootKeyId"] == expected_root_key

fingerprint = payload[
    "rootPublicKeyFingerprint"
]

assert re.fullmatch(
    r"[0-9a-f]{64}",
    fingerprint,
)

print(
    fingerprint
)
PY
)"

WRONG_PIN="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_PIN}" == "${ROOT_PIN}" ]]; then
  WRONG_PIN="0000000000000000000000000000000000000000000000000000000000000000"
fi

[[ "${WRONG_PIN}" != "${ROOT_PIN}" ]]

echo "EXTERNAL_ROOT_PIN=${ROOT_PIN}"
echo "WRONG_ROOT_PIN=${WRONG_PIN}"

echo "[PASS] EXTERNAL_ROOT_PIN_CAPTURED"

echo
echo "===== BUILD PIN VERIFICATION REQUESTS ====="

python3 \
  - \
  "${LINEAGE_ENVELOPE}" \
  "${ROOT_PIN}" \
  "${WRONG_PIN}" \
  "${CORRECT_PIN_REQUEST}" \
  "${WRONG_PIN_REQUEST}" \
<<'PY'
import json
import sys

envelope_file = sys.argv[1]
root_pin = sys.argv[2]
wrong_pin = sys.argv[3]

correct_file = sys.argv[4]
wrong_file = sys.argv[5]

envelope_json = open(
    envelope_file,
    encoding="utf-8",
).read()

with open(
    correct_file,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        {
            "envelopeJson":
                envelope_json,

            "trustedRootFingerprint":
                root_pin,
        },
        handle,
        separators=(",", ":"),
    )

with open(
    wrong_file,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        {
            "envelopeJson":
                envelope_json,

            "trustedRootFingerprint":
                wrong_pin,
        },
        handle,
        separators=(",", ":"),
    )
PY

echo "[PASS] PIN_VERIFICATION_REQUESTS_CREATED"

echo
echo "===== REMOVE ALL LINEAGE SOURCE STATE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_trust_lineage_packages
      WHERE id =
            '${PACKAGE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchor_transitions
      WHERE outgoing_key_id IN (
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
    " \
  >/dev/null

SOURCE_PACKAGE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_lineage_packages
      WHERE id =
            '${PACKAGE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

SOURCE_TRANSITION_COUNT="$(
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

SOURCE_ANCHOR_COUNT="$(
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

echo "SOURCE_PACKAGE_COUNT=${SOURCE_PACKAGE_COUNT}"
echo "SOURCE_TRANSITION_COUNT=${SOURCE_TRANSITION_COUNT}"
echo "SOURCE_ANCHOR_COUNT=${SOURCE_ANCHOR_COUNT}"

[[ "${SOURCE_PACKAGE_COUNT}" == "0" ]]
[[ "${SOURCE_TRANSITION_COUNT}" == "0" ]]
[[ "${SOURCE_ANCHOR_COUNT}" == "0" ]]

echo "[PASS] PINNED_VERIFICATION_SOURCE_STATE_REMOVED"

echo
echo "===== RESTART WITHOUT ANY PRIVATE KEY ====="

stop_backend

start_without_key

echo "[PASS] KEYLESS_PINNED_VERIFICATION_RUNTIME_READY"

echo
echo "===== VERIFY WITH CORRECT EXTERNAL ROOT PIN ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @${CORRECT_PIN_REQUEST} \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/verify" \
  >/tmp/terrascope-step37-correct-pin-response.json

python3 \
  - "${ROOT_PIN}" \
<<'PY'
import json
import sys

root_pin = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step37-correct-pin-response.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["packageHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is True
assert payload["rootPinValid"] is True

assert payload["transitionCount"] == 2
assert payload["verifiedTransitionCount"] == 2
assert payload["trustAnchorCount"] == 3

assert payload["trustedRootFingerprint"] == root_pin
assert payload["presentedRootFingerprint"] == root_pin

print(
    "[PASS] EXTERNALLY_PINNED_ROOT_VERIFICATION"
)

print(
    "[PASS] SOURCE_FREE_PINNED_VERIFICATION"
)

print(
    "[PASS] PRIVATE_KEY_FREE_PINNED_VERIFICATION"
)
PY

echo
echo "===== VERIFY WITH WRONG EXTERNAL ROOT PIN ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @${WRONG_PIN_REQUEST} \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/verify" \
  >/tmp/terrascope-step37-wrong-pin-response.json

python3 \
  - "${ROOT_PIN}" \
  "${WRONG_PIN}" \
<<'PY'
import json
import sys

correct_pin = sys.argv[1]
wrong_pin = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step37-wrong-pin-response.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["packageHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is True
assert payload["rootPinValid"] is False

assert payload["trustedRootFingerprint"] == wrong_pin
assert payload["presentedRootFingerprint"] == correct_pin

assert (
    "does not match"
    in payload["message"].lower()
)

print(
    "[PASS] WRONG_ROOT_PIN_REJECTION"
)
PY

echo
echo "===== RECORD VALID PIN RECEIPT ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @${CORRECT_PIN_REQUEST} \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/receipts" \
  >/tmp/terrascope-step37-valid-receipt.json

VALID_RECEIPT_ID="$(
  python3 \
    - "${ROOT_PIN}" \
<<'PY'
import json
import sys

root_pin = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step37-valid-receipt.json",
        encoding="utf-8",
    )
)

assert payload["receiptNumber"] == 1

assert payload["verificationResult"] == "VALID"

assert payload["rootPinValid"] is True
assert payload["lineageValid"] is True

assert payload["trustedRootFingerprint"] == root_pin
assert payload["presentedRootFingerprint"] == root_pin

print(
    payload["id"]
)
PY
)"

echo "VALID_PIN_RECEIPT_ID=${VALID_RECEIPT_ID}"

echo "[PASS] VALID_PIN_VERIFICATION_RECEIPT"

echo
echo "===== RECORD WRONG-PIN RECEIPT ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @${WRONG_PIN_REQUEST} \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/receipts" \
  >/tmp/terrascope-step37-invalid-receipt.json

INVALID_RECEIPT_ID="$(
  python3 \
    - "${ROOT_PIN}" \
    "${WRONG_PIN}" \
<<'PY'
import json
import sys

correct_pin = sys.argv[1]
wrong_pin = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step37-invalid-receipt.json",
        encoding="utf-8",
    )
)

assert payload["receiptNumber"] == 2

assert payload["verificationResult"] == "INVALID"

assert payload["packageHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is True
assert payload["rootPinValid"] is False

assert payload["trustedRootFingerprint"] == wrong_pin
assert payload["presentedRootFingerprint"] == correct_pin

print(
    payload["id"]
)
PY
)"

echo "INVALID_PIN_RECEIPT_ID=${INVALID_RECEIPT_ID}"

echo "[PASS] INVALID_PIN_VERIFICATION_RECEIPT"

echo
echo "===== PIN RECEIPT HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/receipts" \
  >/tmp/terrascope-step37-pin-history.json

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
        "/tmp/terrascope-step37-pin-history.json",
        encoding="utf-8",
    )
)

receipts = payload[
    "receipts"
]

assert len(receipts) == 2

assert receipts[0]["id"] == invalid_id
assert receipts[0]["verificationResult"] == "INVALID"

assert receipts[1]["id"] == valid_id
assert receipts[1]["verificationResult"] == "VALID"

print(
    "[PASS] PIN_VERIFICATION_RECEIPT_HISTORY"
)
PY

echo
echo "===== BUILD SIGNATURE-TAMPERED ENVELOPE ====="

python3 \
  - \
  "${LINEAGE_ENVELOPE}" \
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
    "packageHash"
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

python3 \
  - \
  "${TAMPERED_ENVELOPE}" \
  "${ROOT_PIN}" \
  "${TAMPER_PIN_REQUEST}" \
<<'PY'
import json
import sys

envelope_file = sys.argv[1]
root_pin = sys.argv[2]
request_file = sys.argv[3]

envelope_json = open(
    envelope_file,
    encoding="utf-8",
).read()

with open(
    request_file,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        {
            "envelopeJson":
                envelope_json,

            "trustedRootFingerprint":
                root_pin,
        },
        handle,
        separators=(",", ":"),
    )
PY

echo "[PASS] PINNED_TAMPER_FIXTURE_CREATED"

echo
echo "===== CORRECT PIN CANNOT OVERRIDE CRYPTOGRAPHIC FAILURE ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @${TAMPER_PIN_REQUEST} \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/pinning/verify" \
  >/tmp/terrascope-step37-tamper-response.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step37-tamper-response.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["packageHashValid"] is True
assert payload["canonicalPayloadValid"] is True

assert payload["lineageValid"] is False
assert payload["rootPinValid"] is True

assert (
    "signature verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] PINNED_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

PIN_RECEIPT_COLUMNS="$(
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
            'resilience_governance_trust_lineage_pin_receipts';
      " \
    | tr -d '[:space:]'
)"

PIN_RECEIPT_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_lineage_pin_receipts
      WHERE id IN (
          '${VALID_RECEIPT_ID}'::uuid,
          '${INVALID_RECEIPT_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

PIN_RECEIPT_FKS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid=
            'resilience_governance_trust_lineage_pin_receipts'::regclass
        AND contype='f';
      " \
    | tr -d '[:space:]'
)"

PIN_RECEIPT_NUMBER_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_trust_lineage_pin_receipt_number';
      " \
    | tr -d '[:space:]'
)"

echo "PIN_RECEIPT_COLUMN_COUNT=${PIN_RECEIPT_COLUMNS}"
echo "PIN_RECEIPT_ROW_COUNT=${PIN_RECEIPT_ROWS}"
echo "PIN_RECEIPT_SOURCE_FK_COUNT=${PIN_RECEIPT_FKS}"
echo "PIN_RECEIPT_NUMBER_UNIQUE_COUNT=${PIN_RECEIPT_NUMBER_UNIQUE}"

[[ "${PIN_RECEIPT_COLUMNS}" == "18" ]]
[[ "${PIN_RECEIPT_ROWS}" == "2" ]]
[[ "${PIN_RECEIPT_FKS}" == "0" ]]
[[ "${PIN_RECEIPT_NUMBER_UNIQUE}" == "1" ]]

echo "[PASS] PIN_RECEIPT_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 37 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_trust_lineage_pin_receipts
      WHERE id IN (
          '${VALID_RECEIPT_ID}'::uuid,
          '${INVALID_RECEIPT_ID}'::uuid
      );
    " \
  >/dev/null

FINAL_PIN_RECEIPT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_lineage_pin_receipts;
      " \
    | tr -d '[:space:]'
)"

FINAL_PACKAGE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_lineage_packages;
      " \
    | tr -d '[:space:]'
)"

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

echo "FINAL_PIN_RECEIPT_COUNT=${FINAL_PIN_RECEIPT_COUNT}"
echo "FINAL_PACKAGE_COUNT=${FINAL_PACKAGE_COUNT}"
echo "FINAL_TRANSITION_COUNT=${FINAL_TRANSITION_COUNT}"

[[ "${FINAL_PIN_RECEIPT_COUNT}" == "0" ]]
[[ "${FINAL_PACKAGE_COUNT}" == "0" ]]
[[ "${FINAL_TRANSITION_COUNT}" == "0" ]]

echo "[PASS] STEP37_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 37 VERIFICATION PASSED"
echo "========================================================================"
