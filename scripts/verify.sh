#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step38-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep38Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step38-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step38-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step38-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step38-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step38-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step38-key3-public.b64"

ENVELOPE="/tmp/terrascope-step38-lineage-envelope.json"
TAMPERED_ENVELOPE="/tmp/terrascope-step38-lineage-tampered.json"

ROOT_PIN_FILE="/tmp/terrascope-step38-root.sha256"
WRONG_PIN_FILE="/tmp/terrascope-step38-wrong-root.sha256"

VALID_RECEIPT="/tmp/terrascope-step38-offline-valid-receipt.json"
WRONG_PIN_RECEIPT="/tmp/terrascope-step38-offline-wrong-pin-receipt.json"
TAMPER_RECEIPT="/tmp/terrascope-step38-offline-tamper-receipt.json"

VALID_IMPORT_REQUEST="/tmp/terrascope-step38-valid-import-request.json"
INVALID_IMPORT_REQUEST="/tmp/terrascope-step38-invalid-import-request.json"
TAMPERED_RECEIPT_IMPORT_REQUEST="/tmp/terrascope-step38-tampered-receipt-import-request.json"

STANDALONE_JAR="/tmp/terrascope-trust-verifier.jar"

KEY1_ID="verification-step38-ed25519-v1"
KEY2_ID="verification-step38-ed25519-v2"
KEY3_ID="verification-step38-ed25519-v3"

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
    "${ENVELOPE}" \
    "${TAMPERED_ENVELOPE}" \
    "${ROOT_PIN_FILE}" \
    "${WRONG_PIN_FILE}" \
    "${VALID_RECEIPT}" \
    "${WRONG_PIN_RECEIPT}" \
    "${TAMPER_RECEIPT}" \
    "${VALID_IMPORT_REQUEST}" \
    "${INVALID_IMPORT_REQUEST}" \
    "${TAMPERED_RECEIPT_IMPORT_REQUEST}" \
    "${STANDALONE_JAR}"
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
echo " TERRASCOPE — STEP 38 VERIFICATION"
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
echo "===== BUILD STANDALONE VERIFIER ====="

bash \
  tools/trust-verifier/build.sh \
  "${STANDALONE_JAR}"

[[ -s "${STANDALONE_JAR}" ]]

echo "[PASS] STANDALONE_VERIFIER_JAR_READY"

echo
echo "===== GENERATE THREE ED25519 KEYPAIRS ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class TerraScopeStep38Keygen {

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
  >/tmp/terrascope-step38-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step38-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.38.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 38); do
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

echo "[PASS] FLYWAY_V38_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_offline_trust_verification_receipts;

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

echo "[PASS] STEP38_PRE_CLEAN"

echo
echo "===== REGISTER ROOT KEY ====="

cat > /tmp/terrascope-step38-register-key1.json <<JSON
{
  "keyId": "${KEY1_ID}",
  "publicKeyX509Base64": "${KEY1_PUBLIC}"
}
JSON

REGISTER_ROOT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step38-root-anchor.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step38-register-key1.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors"
)"

echo "REGISTER_ROOT_HTTP=${REGISTER_ROOT_HTTP}"

[[ "${REGISTER_ROOT_HTTP}" == "200" ]]

echo "[PASS] ROOT_TRUST_ANCHOR_REGISTERED"

echo
echo "===== ROTATE ROOT -> KEY2 ====="

cat > /tmp/terrascope-step38-transition1-request.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 38 offline verifier transition one."
}
JSON

TRANSITION1_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step38-transition1.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step38-transition1-request.json \
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

cat > /tmp/terrascope-step38-transition2-request.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 38 offline verifier transition two."
}
JSON

TRANSITION2_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step38-transition2.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step38-transition2-request.json \
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
    --output /tmp/terrascope-step38-package.json \
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
        "/tmp/terrascope-step38-package.json",
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
  --output "${ENVELOPE}"

[[ -s "${ENVELOPE}" ]]

echo "[PASS] OFFLINE_TEST_LINEAGE_EXPORTED"

echo
echo "===== CAPTURE INDEPENDENT ROOT PIN FILE ====="

ROOT_PIN="$(
  python3 \
    - "${ENVELOPE}" \
    "${KEY1_ID}" \
<<'PY'
import json
import re
import sys

file_name = sys.argv[1]
root_key = sys.argv[2]

envelope = json.load(
    open(
        file_name,
        encoding="utf-8",
    )
)

payload = json.loads(
    envelope["payloadJson"]
)

assert payload["rootKeyId"] == root_key

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

printf '%s\n' \
  "${ROOT_PIN}" \
  > "${ROOT_PIN_FILE}"

printf '%s\n' \
  "${WRONG_PIN}" \
  > "${WRONG_PIN_FILE}"

echo "ROOT_PIN=${ROOT_PIN}"
echo "WRONG_PIN=${WRONG_PIN}"

echo "[PASS] INDEPENDENT_ROOT_PIN_FILES_READY"

echo
echo "===== REMOVE ALL SOURCE STATE BEFORE OFFLINE VERIFICATION ====="

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

echo "[PASS] OFFLINE_SOURCE_STATE_REMOVED"

echo
echo "===== SHUT DOWN TERRASCOPE + POSTGRES ====="

stop_backend

docker compose stop postgres >/dev/null

sleep 1

if curl \
    --silent \
    --fail \
    "http://localhost:8087/actuator/health" \
    >/dev/null 2>&1; then

  echo "[FAIL] Backend is unexpectedly reachable"
  exit 1
fi

RUNNING_POSTGRES="$(
  docker compose ps \
    --status running \
    --services \
    | grep -c '^postgres$' \
    || true
)"

echo "RUNNING_POSTGRES_COUNT=${RUNNING_POSTGRES}"

[[ "${RUNNING_POSTGRES}" == "0" ]]

echo "[PASS] BACKEND_OFFLINE"
echo "[PASS] POSTGRES_OFFLINE"

echo
echo "===== PURE-JDK VALID OFFLINE VERIFICATION ====="

set +e

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${VALID_RECEIPT}" \
  >/tmp/terrascope-step38-offline-valid.log

VALID_VERIFIER_EXIT=$?

set -e

cat /tmp/terrascope-step38-offline-valid.log

echo "VALID_VERIFIER_EXIT=${VALID_VERIFIER_EXIT}"

[[ "${VALID_VERIFIER_EXIT}" == "0" ]]

python3 \
  - "${VALID_RECEIPT}" \
  "${ROOT_PIN}" \
  "${KEY1_ID}" \
  "${KEY3_ID}" \
<<'PY'
import hashlib
import json
import re
import sys

receipt_file = sys.argv[1]
root_pin = sys.argv[2]
key1 = sys.argv[3]
key3 = sys.argv[4]

text = open(
    receipt_file,
    encoding="utf-8",
).read()

payload = json.loads(
    text
)

expected_document_keys = [
    "verifierVersion",

    "sourcePackageId",
    "sourcePackageNumber",

    "envelopeJson",
    "envelopeHash",
    "packageHash",

    "trustedRootFingerprint",
    "presentedRootFingerprint",

    "valid",

    "packageHashValid",
    "canonicalPayloadValid",
    "lineageValid",
    "rootPinValid",

    "transitionCount",
    "verifiedTransitionCount",
    "trustAnchorCount",

    "rootKeyId",
    "terminalKeyId",

    "message",
    "verifiedAt",

    "receiptHash",
]

assert list(
    payload.keys()
) == expected_document_keys

assert (
    payload["verifierVersion"]
    == "TS-STANDALONE-TRUST-VERIFIER-V1"
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

assert payload["rootKeyId"] == key1
assert payload["terminalKeyId"] == key3

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["receiptHash"],
)

material = dict(
    payload
)

receipt_hash = material.pop(
    "receiptHash"
)

expected_material_keys = (
    expected_document_keys[
        :-1
    ]
)

assert list(
    material.keys()
) == expected_material_keys

# Python dictionaries preserve insertion order.
#
# This intentionally mirrors the declaration/creator
# order used by:
#
# GovernanceOfflineTrustVerificationReceiptMaterial
#
# and by the standalone verifier's LinkedHashMap.
canonical_material = json.dumps(
    material,
    separators=(",", ":"),
    ensure_ascii=False,
)

calculated = hashlib.sha256(
    canonical_material.encode(
        "utf-8"
    )
).hexdigest()

print(
    f"OFFLINE_RECEIPT_EXPECTED_HASH={receipt_hash}"
)

print(
    f"OFFLINE_RECEIPT_CALCULATED_HASH={calculated}"
)

assert calculated == receipt_hash

canonical_document = json.dumps(
    payload,
    separators=(",", ":"),
    ensure_ascii=False,
)

assert text == canonical_document

print(
    "[PASS] STANDALONE_RECEIPT_DECLARATION_ORDER"
)

print(
    "[PASS] STANDALONE_VALID_RECEIPT_AUTHORITY"
)

print(
    "[PASS] STANDALONE_RECEIPT_HASH_AUTHORITY"
)
PY

echo "[PASS] SERVER_FREE_VALID_VERIFICATION"
echo "[PASS] DATABASE_FREE_VALID_VERIFICATION"
echo "[PASS] PRIVATE_KEY_FREE_VALID_VERIFICATION"

echo
echo "===== PURE-JDK WRONG-PIN OFFLINE VERIFICATION ====="

set +e

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${ENVELOPE}" \
  --root-pin "${WRONG_PIN_FILE}" \
  --receipt "${WRONG_PIN_RECEIPT}" \
  >/tmp/terrascope-step38-offline-wrong-pin.log

WRONG_PIN_EXIT=$?

set -e

cat /tmp/terrascope-step38-offline-wrong-pin.log

echo "WRONG_PIN_VERIFIER_EXIT=${WRONG_PIN_EXIT}"

[[ "${WRONG_PIN_EXIT}" == "2" ]]

python3 \
  - "${WRONG_PIN_RECEIPT}" \
  "${ROOT_PIN}" \
  "${WRONG_PIN}" \
<<'PY'
import json
import sys

file_name = sys.argv[1]
correct_pin = sys.argv[2]
wrong_pin = sys.argv[3]

payload = json.load(
    open(
        file_name,
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
    "[PASS] STANDALONE_WRONG_ROOT_PIN_REJECTION"
)
PY

echo
echo "===== BUILD SIGNATURE-TAMPERED ENVELOPE ====="

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

payload_json = envelope[
    "payloadJson"
]

zero_signature = base64.b64encode(
    bytes(64)
).decode()

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

echo "[PASS] OFFLINE_TAMPERED_ENVELOPE_CREATED"

echo
echo "===== PURE-JDK SIGNATURE-TAMPER OFFLINE VERIFICATION ====="

set +e

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${TAMPERED_ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${TAMPER_RECEIPT}" \
  >/tmp/terrascope-step38-offline-tamper.log

TAMPER_EXIT=$?

set -e

cat /tmp/terrascope-step38-offline-tamper.log

echo "TAMPER_VERIFIER_EXIT=${TAMPER_EXIT}"

[[ "${TAMPER_EXIT}" == "2" ]]

python3 \
  - "${TAMPER_RECEIPT}" \
  "${ROOT_PIN}" \
<<'PY'
import json
import sys

file_name = sys.argv[1]
root_pin = sys.argv[2]

payload = json.load(
    open(
        file_name,
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["packageHashValid"] is True
assert payload["canonicalPayloadValid"] is True

assert payload["lineageValid"] is False
assert payload["rootPinValid"] is True

assert payload["trustedRootFingerprint"] == root_pin
assert payload["presentedRootFingerprint"] == root_pin

assert (
    "signature verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] STANDALONE_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== PROVE VERIFICATION OCCURRED WITH PLATFORM OFFLINE ====="

if curl \
    --silent \
    --fail \
    "http://localhost:8087/actuator/health" \
    >/dev/null 2>&1; then

  echo "[FAIL] Backend became reachable during offline verification"
  exit 1
fi

RUNNING_POSTGRES="$(
  docker compose ps \
    --status running \
    --services \
    | grep -c '^postgres$' \
    || true
)"

echo "RUNNING_POSTGRES_COUNT_AFTER_OFFLINE_VERIFY=${RUNNING_POSTGRES}"

[[ "${RUNNING_POSTGRES}" == "0" ]]

echo "[PASS] TRUE_AIR_GAPPED_RUNTIME_PROOF"

echo
echo "===== RESTART DATABASE + BACKEND WITHOUT PRIVATE KEY ====="

docker compose up -d postgres

for attempt in $(seq 1 30); do
  if docker compose exec -T postgres \
      pg_isready \
      -U terrascope \
      -d terrascope \
      >/dev/null 2>&1; then

    break
  fi

  [[ "${attempt}" -lt 30 ]]

  sleep 1
done

start_without_key

echo "[PASS] KEYLESS_IMPORT_RUNTIME_READY"

echo
echo "===== BUILD IMPORT REQUESTS ====="

python3 \
  - \
  "${VALID_RECEIPT}" \
  "${WRONG_PIN_RECEIPT}" \
  "${VALID_IMPORT_REQUEST}" \
  "${INVALID_IMPORT_REQUEST}" \
<<'PY'
import json
import sys

valid_receipt_file = sys.argv[1]
invalid_receipt_file = sys.argv[2]

valid_request_file = sys.argv[3]
invalid_request_file = sys.argv[4]

valid_json = open(
    valid_receipt_file,
    encoding="utf-8",
).read()

invalid_json = open(
    invalid_receipt_file,
    encoding="utf-8",
).read()

with open(
    valid_request_file,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        {
            "receiptJson":
                valid_json,
        },
        handle,
        separators=(",", ":"),
    )

with open(
    invalid_request_file,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        {
            "receiptJson":
                invalid_json,
        },
        handle,
        separators=(",", ":"),
    )
PY

echo "[PASS] OFFLINE_IMPORT_REQUESTS_READY"

echo
echo "===== IMPORT VALID OFFLINE RECEIPT ====="

VALID_IMPORT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step38-valid-import-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @"${VALID_IMPORT_REQUEST}" \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports"
)"

echo "VALID_OFFLINE_IMPORT_HTTP=${VALID_IMPORT_HTTP}"

if [[ "${VALID_IMPORT_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step38-valid-import-response.json
  exit 1
fi

VALID_IMPORT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step38-valid-import-response.json",
        encoding="utf-8",
    )
)

assert payload["importNumber"] == 1
assert payload["verificationResult"] == "VALID"

assert payload["packageHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is True
assert payload["rootPinValid"] is True

print(
    payload["id"]
)
PY
)"

echo "VALID_OFFLINE_IMPORT_ID=${VALID_IMPORT_ID}"

echo "[PASS] VALID_OFFLINE_RECEIPT_REVERIFIED_AND_IMPORTED"

echo
echo "===== IMPORT WRONG-PIN OFFLINE RECEIPT ====="

INVALID_IMPORT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step38-invalid-import-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @"${INVALID_IMPORT_REQUEST}" \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports"
)"

echo "INVALID_OFFLINE_IMPORT_HTTP=${INVALID_IMPORT_HTTP}"

if [[ "${INVALID_IMPORT_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step38-invalid-import-response.json
  exit 1
fi

INVALID_IMPORT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step38-invalid-import-response.json",
        encoding="utf-8",
    )
)

assert payload["importNumber"] == 2
assert payload["verificationResult"] == "INVALID"

assert payload["packageHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is True
assert payload["rootPinValid"] is False

print(
    payload["id"]
)
PY
)"

echo "INVALID_OFFLINE_IMPORT_ID=${INVALID_IMPORT_ID}"

echo "[PASS] INVALID_OFFLINE_RECEIPT_REVERIFIED_AND_IMPORTED"

echo
echo "===== IDEMPOTENT OFFLINE RECEIPT IMPORT ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${VALID_IMPORT_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step38-valid-import-repeat.json

python3 \
  - "${VALID_IMPORT_ID}" \
<<'PY'
import json
import sys

expected_id = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step38-valid-import-repeat.json",
        encoding="utf-8",
    )
)

assert payload["id"] == expected_id
assert payload["importNumber"] == 1

print(
    "[PASS] OFFLINE_RECEIPT_IMPORT_IDEMPOTENCY"
)
PY

echo
echo "===== TAMPER OFFLINE RECEIPT DOCUMENT ====="

python3 \
  - \
  "${VALID_RECEIPT}" \
  "${TAMPERED_RECEIPT_IMPORT_REQUEST}" \
<<'PY'
import json
import sys

receipt_file = sys.argv[1]
request_file = sys.argv[2]

receipt = json.load(
    open(
        receipt_file,
        encoding="utf-8",
    )
)

receipt[
    "valid"
] = False

tampered_json = json.dumps(
    receipt,
    sort_keys=True,
    separators=(",", ":"),
    ensure_ascii=False,
)

with open(
    request_file,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        {
            "receiptJson":
                tampered_json,
        },
        handle,
        separators=(",", ":"),
    )
PY

TAMPERED_RECEIPT_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step38-tampered-import-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @"${TAMPERED_RECEIPT_IMPORT_REQUEST}" \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports"
)"

echo "TAMPERED_OFFLINE_RECEIPT_IMPORT_HTTP=${TAMPERED_RECEIPT_HTTP}"

[[ "${TAMPERED_RECEIPT_HTTP}" == "409" ]]

echo "[PASS] OFFLINE_RECEIPT_TAMPER_REJECTION"

echo
echo "===== IMPORT HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step38-import-history.json

python3 \
  - "${VALID_IMPORT_ID}" \
  "${INVALID_IMPORT_ID}" \
<<'PY'
import json
import sys

valid_id = sys.argv[1]
invalid_id = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step38-import-history.json",
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
    "[PASS] OFFLINE_RECEIPT_IMPORT_HISTORY"
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
            'resilience_governance_offline_trust_verification_receipts';
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
      FROM resilience_governance_offline_trust_verification_receipts
      WHERE id IN (
          '${VALID_IMPORT_ID}'::uuid,
          '${INVALID_IMPORT_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

FK_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid=
            'resilience_governance_offline_trust_verification_receipts'::regclass
        AND contype='f';
      " \
    | tr -d '[:space:]'
)"

RECEIPT_HASH_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_offline_trust_receipt_hash';
      " \
    | tr -d '[:space:]'
)"

IMPORT_NUMBER_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_offline_trust_receipt_import_number';
      " \
    | tr -d '[:space:]'
)"

echo "OFFLINE_RECEIPT_COLUMN_COUNT=${COLUMN_COUNT}"
echo "OFFLINE_RECEIPT_ROW_COUNT=${ROW_COUNT}"
echo "OFFLINE_RECEIPT_SOURCE_FK_COUNT=${FK_COUNT}"
echo "OFFLINE_RECEIPT_HASH_UNIQUE_COUNT=${RECEIPT_HASH_UNIQUE}"
echo "OFFLINE_RECEIPT_IMPORT_NUMBER_UNIQUE_COUNT=${IMPORT_NUMBER_UNIQUE}"

[[ "${COLUMN_COUNT}" == "22" ]]
[[ "${ROW_COUNT}" == "2" ]]
[[ "${FK_COUNT}" == "0" ]]
[[ "${RECEIPT_HASH_UNIQUE}" == "1" ]]
[[ "${IMPORT_NUMBER_UNIQUE}" == "1" ]]

echo "[PASS] OFFLINE_RECEIPT_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 38 FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_offline_trust_verification_receipts
      WHERE id IN (
          '${VALID_IMPORT_ID}'::uuid,
          '${INVALID_IMPORT_ID}'::uuid
      );
    " \
  >/dev/null

FINAL_OFFLINE_RECEIPT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_offline_trust_verification_receipts;
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

echo "FINAL_OFFLINE_RECEIPT_COUNT=${FINAL_OFFLINE_RECEIPT_COUNT}"
echo "FINAL_PACKAGE_COUNT=${FINAL_PACKAGE_COUNT}"
echo "FINAL_TRANSITION_COUNT=${FINAL_TRANSITION_COUNT}"

[[ "${FINAL_OFFLINE_RECEIPT_COUNT}" == "0" ]]
[[ "${FINAL_PACKAGE_COUNT}" == "0" ]]
[[ "${FINAL_TRANSITION_COUNT}" == "0" ]]

echo "[PASS] STEP38_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 38 VERIFICATION PASSED"
echo "========================================================================"
