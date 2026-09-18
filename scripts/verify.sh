#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step43-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep43Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step43-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step43-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step43-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step43-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step43-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step43-key3-public.b64"

LINEAGE_ENVELOPE="/tmp/terrascope-step43-lineage-envelope.json"
ROOT_PIN_FILE="/tmp/terrascope-step43-root.sha256"

RECEIPT_ONE="/tmp/terrascope-step43-receipt-one.json"
RECEIPT_TWO="/tmp/terrascope-step43-receipt-two.json"

REQUEST_ONE="/tmp/terrascope-step43-request-one.json"
REQUEST_TWO="/tmp/terrascope-step43-request-two.json"

BUNDLE_ENVELOPE="/tmp/terrascope-step43-checkpoint-bundle.json"

BUNDLE_VERIFY_REQUEST="/tmp/terrascope-step43-bundle-verify-request.json"
BUNDLE_WRONG_PIN_REQUEST="/tmp/terrascope-step43-bundle-wrong-pin-request.json"

TAMPERED_LEDGER_ENVELOPE="/tmp/terrascope-step43-tampered-ledger-envelope.json"
TAMPERED_LEDGER_REQUEST="/tmp/terrascope-step43-tampered-ledger-request.json"

TAMPERED_SIGNATURE_ENVELOPE="/tmp/terrascope-step43-tampered-signature-envelope.json"
TAMPERED_SIGNATURE_REQUEST="/tmp/terrascope-step43-tampered-signature-request.json"

STANDALONE_JAR="/tmp/terrascope-step43-trust-verifier.jar"

KEY1_ID="verification-step43-ed25519-v1"
KEY2_ID="verification-step43-ed25519-v2"
KEY3_ID="verification-step43-ed25519-v3"

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
    "${ROOT_PIN_FILE}" \
    "${RECEIPT_ONE}" \
    "${RECEIPT_TWO}" \
    "${REQUEST_ONE}" \
    "${REQUEST_TWO}" \
    "${BUNDLE_ENVELOPE}" \
    "${BUNDLE_VERIFY_REQUEST}" \
    "${BUNDLE_WRONG_PIN_REQUEST}" \
    "${TAMPERED_LEDGER_ENVELOPE}" \
    "${TAMPERED_LEDGER_REQUEST}" \
    "${TAMPERED_SIGNATURE_ENVELOPE}" \
    "${TAMPERED_SIGNATURE_REQUEST}" \
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

build_verify_request() {
  local ENVELOPE_FILE="$1"
  local FINGERPRINT="$2"
  local OUTPUT_FILE="$3"

  python3 \
    - \
    "${ENVELOPE_FILE}" \
    "${FINGERPRINT}" \
    "${OUTPUT_FILE}" \
<<'PY'
import json
import sys

envelope_file = sys.argv[1]
fingerprint = sys.argv[2]
output_file = sys.argv[3]

envelope_json = open(
    envelope_file,
    encoding="utf-8",
).read()

with open(
    output_file,
    "w",
    encoding="utf-8",
) as handle:
    json.dump(
        {
            "envelopeJson":
                envelope_json,

            "trustedSigningKeyFingerprint":
                fingerprint,
        },
        handle,
        separators=(",", ":"),
    )
PY
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 43 VERIFICATION"
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

bash \
  tools/trust-verifier/build.sh \
  "${STANDALONE_JAR}"

[[ -s "${STANDALONE_JAR}" ]]

echo "[PASS] STANDALONE_VERIFIER_JAR_READY"

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.util.Base64;

public class TerraScopeStep43Keygen {

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
  >/tmp/terrascope-step43-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.43.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 43); do
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

echo "[PASS] FLYWAY_V43_RUNTIME"

echo
echo "===== STEP 43 PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_offline_trust_checkpoint_bundles;

      DELETE
      FROM resilience_governance_offline_trust_receipt_checkpoints;

      DELETE
      FROM resilience_governance_offline_trust_receipt_ledger;

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

      UPDATE resilience_governance_sequence_allocators

      SET
          next_value = 1,
          updated_at = NOW()

      WHERE allocator_key IN (
          'OFFLINE_TRUST_IMPORT_NUMBER_V1',
          'OFFLINE_TRUST_LEDGER_NUMBER_V1',
          'OFFLINE_TRUST_RECEIPT_CHECKPOINT_NUMBER_V1',
          'OFFLINE_TRUST_CHECKPOINT_BUNDLE_NUMBER_V1',
          'GOV_TRUST_ANCHOR_TRANSITIONS_TRANSITION_NUMBER_V1',
          'GOV_TRUST_LINEAGE_PACKAGES_PACKAGE_NUMBER_V1',
          'GOV_TRUST_LINEAGE_PIN_RECEIPTS_RECEIPT_NUMBER_V1'
      );
    " \
  >/dev/null

echo "[PASS] STEP43_PRE_CLEAN"

echo
echo "===== CREATE TRUST LINEAGE ====="

cat > /tmp/terrascope-step43-root.json <<JSON
{
  "keyId": "${KEY1_ID}",
  "publicKeyX509Base64": "${KEY1_PUBLIC}"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step43-root.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/tmp/terrascope-step43-root-response.json

echo "[PASS] ROOT_ANCHOR_REGISTERED"

cat > /tmp/terrascope-step43-transition1.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 43 portable checkpoint bundle transition one."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step43-transition1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step43-transition1-response.json

echo "[PASS] ROOT_TO_KEY2_TRANSITION"

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

cat > /tmp/terrascope-step43-transition2.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 43 portable checkpoint bundle transition two."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step43-transition2.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step43-transition2-response.json

echo "[PASS] KEY2_TO_KEY3_TRANSITION"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages" \
  >/tmp/terrascope-step43-lineage-package.json

LINEAGE_PACKAGE_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-lineage-package.json",
        encoding="utf-8",
    )
)

assert payload["transitionCount"] == 2

print(
    payload["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages/export/${LINEAGE_PACKAGE_ID}" \
  --output "${LINEAGE_ENVELOPE}"

ROOT_PIN="$(
  python3 \
    - "${LINEAGE_ENVELOPE}" \
<<'PY'
import json
import sys

envelope = json.load(
    open(
        sys.argv[1],
        encoding="utf-8",
    )
)

payload = json.loads(
    envelope["payloadJson"]
)

print(
    payload["rootPublicKeyFingerprint"]
)
PY
)"

printf '%s\n' \
  "${ROOT_PIN}" \
  > "${ROOT_PIN_FILE}"

echo "[PASS] TRUST_LINEAGE_FIXTURE_READY"

echo
echo "===== CREATE TWO OFFLINE RECEIPTS ====="

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${LINEAGE_ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${RECEIPT_ONE}" \
  >/tmp/terrascope-step43-receipt-one.log

sleep 1

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${LINEAGE_ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${RECEIPT_TWO}" \
  >/tmp/terrascope-step43-receipt-two.log

grep \
  -q \
  'VERIFICATION_RESULT=VALID' \
  /tmp/terrascope-step43-receipt-one.log

grep \
  -q \
  'VERIFICATION_RESULT=VALID' \
  /tmp/terrascope-step43-receipt-two.log

python3 \
  - \
  "${RECEIPT_ONE}" \
  "${RECEIPT_TWO}" \
  "${REQUEST_ONE}" \
  "${REQUEST_TWO}" \
<<'PY'
import json
import sys

hashes = []

for source, target in (
    (
        sys.argv[1],
        sys.argv[3],
    ),
    (
        sys.argv[2],
        sys.argv[4],
    ),
):
    receipt_text = open(
        source,
        encoding="utf-8",
    ).read()

    receipt = json.loads(
        receipt_text
    )

    hashes.append(
        receipt["receiptHash"]
    )

    with open(
        target,
        "w",
        encoding="utf-8",
    ) as handle:
        json.dump(
            {
                "receiptJson":
                    receipt_text,
            },
            handle,
            separators=(",", ":"),
        )

assert hashes[0] != hashes[1]

print(
    "[PASS] TWO_DISTINCT_RECEIPTS_READY"
)
PY

echo
echo "===== IMPORT RECEIPTS KEYLESS ====="

stop_backend

start_without_key

echo "[PASS] KEYLESS_IMPORT_RUNTIME_READY"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${REQUEST_ONE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step43-import-one.json

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${REQUEST_TWO}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step43-import-two.json

echo "[PASS] TWO_RECEIPTS_IMPORTED"

echo
echo "===== SIGN CHECKPOINT WITH KEY3 ====="

stop_backend

start_with_key \
  "${KEY3_ID}" \
  "${KEY3_PRIVATE}" \
  "${KEY3_PUBLIC}"

echo "[PASS] KEY3_RUNTIME_READY"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints" \
  >/tmp/terrascope-step43-checkpoint.json

CHECKPOINT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-checkpoint.json",
        encoding="utf-8",
    )
)

assert payload["coveredLedgerNumber"] == 2
assert payload["entryCount"] == 2
assert payload["validReceiptCount"] == 2
assert payload["invalidReceiptCount"] == 0

print(
    payload["id"]
)
PY
)"

echo "CHECKPOINT_ID=${CHECKPOINT_ID}"

echo "[PASS] SIGNED_CHECKPOINT_READY"

echo
echo "===== CREATE PORTABLE CHECKPOINT BUNDLE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles" \
  >/tmp/terrascope-step43-bundle.json

BUNDLE_ID="$(
  python3 - <<'PY'
import json
import re

payload = json.load(
    open(
        "/tmp/terrascope-step43-bundle.json",
        encoding="utf-8",
    )
)

assert payload["bundleNumber"] == 1
assert payload["sourceCheckpointNumber"] == 1
assert payload["coveredLedgerNumber"] == 2

assert (
    payload["bundleVersion"]
    == "TS-OFFLINE-TRUST-CHECKPOINT-BUNDLE-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["checkpointHash"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["signingPublicKeyFingerprint"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["bundleHash"],
)

print(
    payload["id"]
)
PY
)"

echo "BUNDLE_ID=${BUNDLE_ID}"

echo "[PASS] PORTABLE_CHECKPOINT_BUNDLE_CREATED"

echo
echo "===== BUNDLE IDEMPOTENCY ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles" \
  >/tmp/terrascope-step43-bundle-repeat.json

python3 \
  - "${BUNDLE_ID}" \
<<'PY'
import json
import sys

expected = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step43-bundle-repeat.json",
        encoding="utf-8",
    )
)

assert payload["id"] == expected
assert payload["bundleNumber"] == 1

print(
    "[PASS] PORTABLE_BUNDLE_IDEMPOTENCY"
)
PY

echo
echo "===== EXPORT PORTABLE BUNDLE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles/export/${BUNDLE_ID}" \
  --output "${BUNDLE_ENVELOPE}"

SIGNING_KEY_PIN="$(
  python3 \
    - "${BUNDLE_ENVELOPE}" \
<<'PY'
import hashlib
import json
import re
import sys

envelope = json.load(
    open(
        sys.argv[1],
        encoding="utf-8",
    )
)

assert (
    envelope["envelopeVersion"]
    == "TS-OFFLINE-TRUST-CHECKPOINT-ENVELOPE-V1"
)

payload_json = envelope["payloadJson"]

assert (
    hashlib.sha256(
        payload_json.encode(
            "utf-8"
        )
    ).hexdigest()
    == envelope["bundleHash"]
)

payload = json.loads(
    payload_json
)

assert (
    payload["bundleVersion"]
    == "TS-OFFLINE-TRUST-CHECKPOINT-BUNDLE-V1"
)

assert len(
    payload["ledgerEntries"]
) == 2

assert payload["checkpoint"]["coveredLedgerNumber"] == 2

fingerprint = payload["signingPublicKeyFingerprint"]

assert re.fullmatch(
    r"[0-9a-f]{64}",
    fingerprint,
)

print(
    fingerprint
)
PY
)"

echo "SIGNING_KEY_PIN=${SIGNING_KEY_PIN}"

echo "[PASS] PORTABLE_BUNDLE_EXPORT"

echo
echo "===== LIVE ROOT-INDEPENDENT PINNED VERIFICATION ====="

build_verify_request \
  "${BUNDLE_ENVELOPE}" \
  "${SIGNING_KEY_PIN}" \
  "${BUNDLE_VERIFY_REQUEST}"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${BUNDLE_VERIFY_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles/verify" \
  >/tmp/terrascope-step43-bundle-valid.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-bundle-valid.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["bundleHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["ledgerValid"] is True
assert payload["checkpointValid"] is True
assert payload["signingKeyPinValid"] is True

assert payload["ledgerEntryCount"] == 2
assert payload["verifiedLedgerEntryCount"] == 2

assert payload["checkpointNumber"] == 1
assert payload["coveredLedgerNumber"] == 2

print(
    "[PASS] PINNED_PORTABLE_BUNDLE_VERIFICATION"
)
PY

echo
echo "===== WRONG SIGNING KEY PIN ====="

WRONG_PIN="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_PIN}" == "${SIGNING_KEY_PIN}" ]]; then
  WRONG_PIN="0000000000000000000000000000000000000000000000000000000000000000"
fi

build_verify_request \
  "${BUNDLE_ENVELOPE}" \
  "${WRONG_PIN}" \
  "${BUNDLE_WRONG_PIN_REQUEST}"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${BUNDLE_WRONG_PIN_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles/verify" \
  >/tmp/terrascope-step43-bundle-wrong-pin.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-bundle-wrong-pin.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["bundleHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["ledgerValid"] is True
assert payload["checkpointValid"] is True
assert payload["signingKeyPinValid"] is False

print(
    "[PASS] WRONG_SIGNING_KEY_PIN_REJECTION"
)
PY

echo
echo "===== PORTABLE LEDGER TAMPER TEST ====="

python3 \
  - \
  "${BUNDLE_ENVELOPE}" \
  "${TAMPERED_LEDGER_ENVELOPE}" \
<<'PY'
import hashlib
import json
import sys

source = sys.argv[1]
target = sys.argv[2]

envelope = json.load(
    open(
        source,
        encoding="utf-8",
    )
)

payload = json.loads(
    envelope["payloadJson"]
)

payload["ledgerEntries"][0]["receiptHash"] = (
    "0" * 64
)

payload_json = json.dumps(
    payload,
    separators=(",", ":"),
    ensure_ascii=False,
)

envelope["payloadJson"] = payload_json

envelope["bundleHash"] = hashlib.sha256(
    payload_json.encode(
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
        separators=(",", ":"),
        ensure_ascii=False,
    )
PY

build_verify_request \
  "${TAMPERED_LEDGER_ENVELOPE}" \
  "${SIGNING_KEY_PIN}" \
  "${TAMPERED_LEDGER_REQUEST}"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${TAMPERED_LEDGER_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles/verify" \
  >/tmp/terrascope-step43-tampered-ledger.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-tampered-ledger.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["bundleHashValid"] is True
assert payload["canonicalPayloadValid"] is True

assert payload["ledgerValid"] is False
assert payload["checkpointValid"] is False

print(
    "[PASS] PORTABLE_LEDGER_TAMPER_DETECTION"
)
PY

echo
echo "===== PORTABLE CHECKPOINT SIGNATURE TAMPER TEST ====="

python3 \
  - \
  "${BUNDLE_ENVELOPE}" \
  "${TAMPERED_SIGNATURE_ENVELOPE}" \
<<'PY'
import base64
import hashlib
import json
import sys

source = sys.argv[1]
target = sys.argv[2]

envelope = json.load(
    open(
        source,
        encoding="utf-8",
    )
)

payload = json.loads(
    envelope["payloadJson"]
)

payload["checkpoint"]["signatureBase64"] = (
    base64.b64encode(
        bytes(64)
    ).decode()
)

payload_json = json.dumps(
    payload,
    separators=(",", ":"),
    ensure_ascii=False,
)

envelope["payloadJson"] = payload_json

envelope["bundleHash"] = hashlib.sha256(
    payload_json.encode(
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
        separators=(",", ":"),
        ensure_ascii=False,
    )
PY

build_verify_request \
  "${TAMPERED_SIGNATURE_ENVELOPE}" \
  "${SIGNING_KEY_PIN}" \
  "${TAMPERED_SIGNATURE_REQUEST}"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${TAMPERED_SIGNATURE_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles/verify" \
  >/tmp/terrascope-step43-tampered-signature.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-tampered-signature.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["bundleHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["ledgerValid"] is True

assert payload["checkpointValid"] is False

print(
    "[PASS] PORTABLE_CHECKPOINT_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== REMOVE ALL BUNDLE SOURCE STATE ====="

stop_backend

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_offline_trust_receipt_checkpoints;

      DELETE
      FROM resilience_governance_offline_trust_receipt_ledger;

      DELETE
      FROM resilience_governance_offline_trust_verification_receipts;

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

SOURCE_CHECKPOINT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_offline_trust_receipt_checkpoints;
      " \
    | tr -d '[:space:]'
)"

SOURCE_LEDGER_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_offline_trust_receipt_ledger;
      " \
    | tr -d '[:space:]'
)"

SOURCE_RECEIPT_COUNT="$(
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

BUNDLE_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_offline_trust_checkpoint_bundles;
      " \
    | tr -d '[:space:]'
)"

echo "SOURCE_CHECKPOINT_COUNT=${SOURCE_CHECKPOINT_COUNT}"
echo "SOURCE_LEDGER_COUNT=${SOURCE_LEDGER_COUNT}"
echo "SOURCE_RECEIPT_COUNT=${SOURCE_RECEIPT_COUNT}"
echo "SOURCE_ANCHOR_COUNT=${SOURCE_ANCHOR_COUNT}"
echo "BUNDLE_ROW_COUNT=${BUNDLE_ROW_COUNT}"

[[ "${SOURCE_CHECKPOINT_COUNT}" == "0" ]]
[[ "${SOURCE_LEDGER_COUNT}" == "0" ]]
[[ "${SOURCE_RECEIPT_COUNT}" == "0" ]]
[[ "${SOURCE_ANCHOR_COUNT}" == "0" ]]
[[ "${BUNDLE_ROW_COUNT}" == "1" ]]

echo "[PASS] BUNDLE_SOURCE_STATE_REMOVED"

echo
echo "===== SOURCE-FREE + PRIVATE-KEY-FREE VERIFICATION ====="

start_without_key

echo "[PASS] KEYLESS_SOURCE_FREE_RUNTIME_READY"

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${BUNDLE_VERIFY_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles/verify" \
  >/tmp/terrascope-step43-source-free-valid.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step43-source-free-valid.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["bundleHashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["ledgerValid"] is True
assert payload["checkpointValid"] is True
assert payload["signingKeyPinValid"] is True

assert payload["verifiedLedgerEntryCount"] == 2

print(
    "[PASS] SOURCE_FREE_PORTABLE_BUNDLE_VERIFICATION"
)

print(
    "[PASS] PRIVATE_KEY_FREE_PORTABLE_BUNDLE_VERIFICATION"
)
PY

echo
echo "===== BUNDLE HISTORY AFTER SOURCE DELETION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles" \
  >/tmp/terrascope-step43-history.json

python3 \
  - "${BUNDLE_ID}" \
<<'PY'
import json
import sys

expected = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step43-history.json",
        encoding="utf-8",
    )
)

assert len(
    payload["bundles"]
) == 1

assert payload["bundles"][0]["id"] == expected

print(
    "[PASS] PORTABLE_BUNDLE_HISTORY_SURVIVES_SOURCE_DELETION"
)
PY

echo
echo "===== V43 DATABASE CONTRACT ====="

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
            'resilience_governance_offline_trust_checkpoint_bundles';
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
            'resilience_governance_offline_trust_checkpoint_bundles'::regclass
        AND contype='f';
      " \
    | tr -d '[:space:]'
)"

ALLOCATOR_NEXT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            'OFFLINE_TRUST_CHECKPOINT_BUNDLE_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

echo "BUNDLE_COLUMN_COUNT=${COLUMN_COUNT}"
echo "BUNDLE_SOURCE_FK_COUNT=${FK_COUNT}"
echo "BUNDLE_ALLOCATOR_NEXT=${ALLOCATOR_NEXT}"

[[ "${COLUMN_COUNT}" == "12" ]]
[[ "${FK_COUNT}" == "0" ]]
[[ "${ALLOCATOR_NEXT}" == "2" ]]

echo "[PASS] V43_DATABASE_CONTRACT"

echo
echo "===== STEP 43 CLEANUP ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_offline_trust_checkpoint_bundles;

      UPDATE resilience_governance_sequence_allocators

      SET
          next_value = 1,
          updated_at = NOW()

      WHERE allocator_key IN (
          'OFFLINE_TRUST_IMPORT_NUMBER_V1',
          'OFFLINE_TRUST_LEDGER_NUMBER_V1',
          'OFFLINE_TRUST_RECEIPT_CHECKPOINT_NUMBER_V1',
          'OFFLINE_TRUST_CHECKPOINT_BUNDLE_NUMBER_V1',
          'GOV_TRUST_ANCHOR_TRANSITIONS_TRANSITION_NUMBER_V1',
          'GOV_TRUST_LINEAGE_PACKAGES_PACKAGE_NUMBER_V1',
          'GOV_TRUST_LINEAGE_PIN_RECEIPTS_RECEIPT_NUMBER_V1'
      );
    " \
  >/dev/null

FINAL_BUNDLE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_offline_trust_checkpoint_bundles;
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_BUNDLE_COUNT=${FINAL_BUNDLE_COUNT}"

[[ "${FINAL_BUNDLE_COUNT}" == "0" ]]

echo "[PASS] STEP43_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 43 VERIFICATION PASSED"
echo "========================================================================"
