#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step44-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep44Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step44-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step44-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step44-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step44-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step44-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step44-key3-public.b64"

LINEAGE_ENVELOPE="/tmp/terrascope-step44-lineage-envelope.json"
ROOT_PIN_FILE="/tmp/terrascope-step44-root.sha256"

RECEIPT_ONE="/tmp/terrascope-step44-receipt-one.json"
RECEIPT_TWO="/tmp/terrascope-step44-receipt-two.json"

REQUEST_ONE="/tmp/terrascope-step44-request-one.json"
REQUEST_TWO="/tmp/terrascope-step44-request-two.json"

CHECKPOINT_BUNDLE="/tmp/terrascope-step44-checkpoint-bundle.json"
SIGNING_KEY_PIN_FILE="/tmp/terrascope-step44-signing-key.sha256"
WRONG_PIN_FILE="/tmp/terrascope-step44-wrong-signing-key.sha256"

TAMPERED_BUNDLE_HASH="/tmp/terrascope-step44-bundle-hash-tampered.json"
TAMPERED_LEDGER="/tmp/terrascope-step44-ledger-tampered.json"
TAMPERED_SIGNATURE="/tmp/terrascope-step44-signature-tampered.json"

TRUST_VERIFIER_JAR="/tmp/terrascope-step44-trust-verifier.jar"
CHECKPOINT_VERIFIER_JAR="/tmp/terrascope-step44-checkpoint-bundle-verifier.jar"

KEY1_ID="verification-step44-ed25519-v1"
KEY2_ID="verification-step44-ed25519-v2"
KEY3_ID="verification-step44-ed25519-v3"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
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

wait_postgres() {
  for attempt in $(seq 1 30); do
    if docker compose exec -T postgres \
        pg_isready \
        -U terrascope \
        -d terrascope \
        >/dev/null 2>&1; then

      return 0
    fi

    [[ "${attempt}" -lt 30 ]]

    sleep 1
  done

  return 1
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 44 VERIFICATION"
echo "========================================================================"

docker compose config >/dev/null

docker compose up -d postgres

wait_postgres

echo "[PASS] POSTGRES_READY"

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
  "${TRUST_VERIFIER_JAR}"

[[ -s "${TRUST_VERIFIER_JAR}" ]]

bash \
  tools/checkpoint-bundle-verifier/build.sh \
  "${CHECKPOINT_VERIFIER_JAR}"

[[ -s "${CHECKPOINT_VERIFIER_JAR}" ]]

echo "[PASS] BOTH_STANDALONE_VERIFIERS_READY"

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.util.Base64;

public class TerraScopeStep44Keygen {

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
  >/tmp/terrascope-step44-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step44-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.44.0"

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

echo "[PASS] FLYWAY_V1_TO_V43_RUNTIME"

echo
echo "===== STEP 44 PRE-CLEAN ====="

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

echo "[PASS] STEP44_PRE_CLEAN"

echo
echo "===== BUILD TRUST LINEAGE ====="

cat > /tmp/terrascope-step44-root.json <<JSON
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
  --data @/tmp/terrascope-step44-root.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/tmp/terrascope-step44-root-response.json

echo "[PASS] ROOT_ANCHOR_REGISTERED"

cat > /tmp/terrascope-step44-transition1.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 44 standalone checkpoint verifier transition one."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step44-transition1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step44-transition1-response.json

echo "[PASS] ROOT_TO_KEY2_TRANSITION"

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

cat > /tmp/terrascope-step44-transition2.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 44 standalone checkpoint verifier transition two."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step44-transition2.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step44-transition2-response.json

echo "[PASS] KEY2_TO_KEY3_TRANSITION"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages" \
  >/tmp/terrascope-step44-lineage-package.json

LINEAGE_PACKAGE_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step44-lineage-package.json",
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

echo "[PASS] TRUST_LINEAGE_EXPORTED"

echo
echo "===== GENERATE TWO OFFLINE RECEIPTS ====="

java \
  -jar "${TRUST_VERIFIER_JAR}" \
  --envelope "${LINEAGE_ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${RECEIPT_ONE}" \
  >/tmp/terrascope-step44-receipt-one.log

sleep 1

java \
  -jar "${TRUST_VERIFIER_JAR}" \
  --envelope "${LINEAGE_ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${RECEIPT_TWO}" \
  >/tmp/terrascope-step44-receipt-two.log

grep \
  -q \
  'VERIFICATION_RESULT=VALID' \
  /tmp/terrascope-step44-receipt-one.log

grep \
  -q \
  'VERIFICATION_RESULT=VALID' \
  /tmp/terrascope-step44-receipt-two.log

python3 \
  - \
  "${RECEIPT_ONE}" \
  "${RECEIPT_TWO}" \
  "${REQUEST_ONE}" \
  "${REQUEST_TWO}" \
<<'PY'
import json
import sys

for source, target in (
    (sys.argv[1], sys.argv[3]),
    (sys.argv[2], sys.argv[4]),
):
    receipt_text = open(
        source,
        encoding="utf-8",
    ).read()

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
PY

echo "[PASS] TWO_OFFLINE_RECEIPTS_READY"

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
  >/tmp/terrascope-step44-import-one.json

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${REQUEST_TWO}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step44-import-two.json

echo "[PASS] TWO_RECEIPTS_IMPORTED"

echo
echo "===== CREATE SIGNED CHECKPOINT + PORTABLE BUNDLE ====="

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
  >/tmp/terrascope-step44-checkpoint.json

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles" \
  >/tmp/terrascope-step44-bundle.json

BUNDLE_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step44-bundle.json",
        encoding="utf-8",
    )
)

assert payload["sourceCheckpointNumber"] == 1
assert payload["coveredLedgerNumber"] == 2

print(
    payload["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoint-bundles/export/${BUNDLE_ID}" \
  --output "${CHECKPOINT_BUNDLE}"

SIGNING_KEY_PIN="$(
  python3 \
    - "${CHECKPOINT_BUNDLE}" \
<<'PY'
import json
import re
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

printf '%s\n' \
  "${SIGNING_KEY_PIN}" \
  > "${SIGNING_KEY_PIN_FILE}"

WRONG_PIN="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_PIN}" == "${SIGNING_KEY_PIN}" ]]; then
  WRONG_PIN="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_PIN}" \
  > "${WRONG_PIN_FILE}"

echo "SIGNING_KEY_PIN=${SIGNING_KEY_PIN}"

echo "[PASS] PORTABLE_CHECKPOINT_BUNDLE_EXPORTED"

echo
echo "===== DELETE ALL DATABASE EVIDENCE ====="

stop_backend

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

SOURCE_TOTAL="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT
          (SELECT COUNT(*) FROM resilience_governance_offline_trust_checkpoint_bundles)
        + (SELECT COUNT(*) FROM resilience_governance_offline_trust_receipt_checkpoints)
        + (SELECT COUNT(*) FROM resilience_governance_offline_trust_receipt_ledger)
        + (SELECT COUNT(*) FROM resilience_governance_offline_trust_verification_receipts)
        + (SELECT COUNT(*) FROM resilience_governance_trust_lineage_packages)
        + (
            SELECT COUNT(*)
            FROM resilience_governance_trust_anchors
            WHERE key_id IN (
                '${KEY1_ID}',
                '${KEY2_ID}',
                '${KEY3_ID}'
            )
          );
      " \
    | tr -d '[:space:]'
)"

echo "SOURCE_DATABASE_EVIDENCE_COUNT=${SOURCE_TOTAL}"

[[ "${SOURCE_TOTAL}" == "0" ]]

echo "[PASS] ALL_DATABASE_EVIDENCE_REMOVED"

echo
echo "===== SHUT DOWN POSTGRES ====="

docker compose stop postgres

RUNNING_POSTGRES_COUNT="$(
  docker compose ps \
    --status running \
    -q postgres \
    | awk 'END { print NR + 0 }'
)"

echo "RUNNING_POSTGRES_COUNT=${RUNNING_POSTGRES_COUNT}"

[[ "${RUNNING_POSTGRES_COUNT}" == "0" ]]

if curl \
    --silent \
    --max-time 1 \
    "http://localhost:8087/actuator/health" \
    >/dev/null 2>&1; then

  echo "[FAIL] TerraScope backend is unexpectedly reachable."
  exit 1
fi

echo "[PASS] TERRASCOPE_BACKEND_OFFLINE"
echo "[PASS] POSTGRES_OFFLINE"

echo
echo "===== PURE-JDK VALID CHECKPOINT BUNDLE ====="

env \
  -u TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID \
  -u TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64 \
  -u TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64 \
  java \
    -jar "${CHECKPOINT_VERIFIER_JAR}" \
    --bundle "${CHECKPOINT_BUNDLE}" \
    --signing-key-pin "${SIGNING_KEY_PIN_FILE}" \
  | tee /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^ENVELOPE_VERSION_VALID=true$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^BUNDLE_HASH_VALID=true$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^CANONICAL_PAYLOAD_VALID=true$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^LEDGER_VALID=true$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^CHECKPOINT_VALID=true$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^SIGNING_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^LEDGER_ENTRY_COUNT=2$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^VERIFIED_LEDGER_ENTRY_COUNT=2$' \
  /tmp/terrascope-step44-valid.log

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step44-valid.log

echo "[PASS] TRUE_AIR_GAPPED_CHECKPOINT_BUNDLE_VERIFICATION"

echo
echo "===== PURE-JDK WRONG PIN ====="

set +e

env \
  -u TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID \
  -u TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64 \
  -u TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64 \
  java \
    -jar "${CHECKPOINT_VERIFIER_JAR}" \
    --bundle "${CHECKPOINT_BUNDLE}" \
    --signing-key-pin "${WRONG_PIN_FILE}" \
  > /tmp/terrascope-step44-wrong-pin.log \
  2>&1

WRONG_PIN_EXIT=$?

set -e

cat /tmp/terrascope-step44-wrong-pin.log

echo "WRONG_PIN_EXIT=${WRONG_PIN_EXIT}"

[[ "${WRONG_PIN_EXIT}" == "2" ]]

grep \
  -q \
  '^LEDGER_VALID=true$' \
  /tmp/terrascope-step44-wrong-pin.log

grep \
  -q \
  '^CHECKPOINT_VALID=true$' \
  /tmp/terrascope-step44-wrong-pin.log

grep \
  -q \
  '^SIGNING_KEY_PIN_VALID=false$' \
  /tmp/terrascope-step44-wrong-pin.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step44-wrong-pin.log

echo "[PASS] AIR_GAPPED_WRONG_PIN_REJECTION"

echo
echo "===== OUTER BUNDLE HASH TAMPER ====="

python3 \
  - \
  "${CHECKPOINT_BUNDLE}" \
  "${TAMPERED_BUNDLE_HASH}" \
<<'PY'
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

envelope["bundleHash"] = (
    "0" * 64
)

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

set +e

java \
  -jar "${CHECKPOINT_VERIFIER_JAR}" \
  --bundle "${TAMPERED_BUNDLE_HASH}" \
  --signing-key-pin "${SIGNING_KEY_PIN_FILE}" \
  > /tmp/terrascope-step44-bundle-hash-tamper.log \
  2>&1

BUNDLE_HASH_TAMPER_EXIT=$?

set -e

cat /tmp/terrascope-step44-bundle-hash-tamper.log

[[ "${BUNDLE_HASH_TAMPER_EXIT}" == "2" ]]

grep \
  -q \
  '^BUNDLE_HASH_VALID=false$' \
  /tmp/terrascope-step44-bundle-hash-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step44-bundle-hash-tamper.log

echo "[PASS] AIR_GAPPED_BUNDLE_HASH_TAMPER_DETECTION"

echo
echo "===== LEDGER TAMPER WITH RECOMPUTED OUTER HASH ====="

python3 \
  - \
  "${CHECKPOINT_BUNDLE}" \
  "${TAMPERED_LEDGER}" \
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

set +e

java \
  -jar "${CHECKPOINT_VERIFIER_JAR}" \
  --bundle "${TAMPERED_LEDGER}" \
  --signing-key-pin "${SIGNING_KEY_PIN_FILE}" \
  > /tmp/terrascope-step44-ledger-tamper.log \
  2>&1

LEDGER_TAMPER_EXIT=$?

set -e

cat /tmp/terrascope-step44-ledger-tamper.log

[[ "${LEDGER_TAMPER_EXIT}" == "2" ]]

grep \
  -q \
  '^BUNDLE_HASH_VALID=true$' \
  /tmp/terrascope-step44-ledger-tamper.log

grep \
  -q \
  '^CANONICAL_PAYLOAD_VALID=true$' \
  /tmp/terrascope-step44-ledger-tamper.log

grep \
  -q \
  '^LEDGER_VALID=false$' \
  /tmp/terrascope-step44-ledger-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step44-ledger-tamper.log

echo "[PASS] AIR_GAPPED_LEDGER_TAMPER_DETECTION"

echo
echo "===== CHECKPOINT SIGNATURE TAMPER WITH RECOMPUTED OUTER HASH ====="

python3 \
  - \
  "${CHECKPOINT_BUNDLE}" \
  "${TAMPERED_SIGNATURE}" \
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

set +e

java \
  -jar "${CHECKPOINT_VERIFIER_JAR}" \
  --bundle "${TAMPERED_SIGNATURE}" \
  --signing-key-pin "${SIGNING_KEY_PIN_FILE}" \
  > /tmp/terrascope-step44-signature-tamper.log \
  2>&1

SIGNATURE_TAMPER_EXIT=$?

set -e

cat /tmp/terrascope-step44-signature-tamper.log

[[ "${SIGNATURE_TAMPER_EXIT}" == "2" ]]

grep \
  -q \
  '^BUNDLE_HASH_VALID=true$' \
  /tmp/terrascope-step44-signature-tamper.log

grep \
  -q \
  '^CANONICAL_PAYLOAD_VALID=true$' \
  /tmp/terrascope-step44-signature-tamper.log

grep \
  -q \
  '^LEDGER_VALID=true$' \
  /tmp/terrascope-step44-signature-tamper.log

grep \
  -q \
  '^CHECKPOINT_VALID=false$' \
  /tmp/terrascope-step44-signature-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step44-signature-tamper.log

echo "[PASS] AIR_GAPPED_CHECKPOINT_SIGNATURE_TAMPER_DETECTION"

echo
echo "===== PROVE PLATFORM REMAINED OFFLINE ====="

RUNNING_POSTGRES_COUNT_AFTER_TESTS="$(
  docker compose ps \
    --status running \
    -q postgres \
    | awk 'END { print NR + 0 }'
)"

echo "RUNNING_POSTGRES_COUNT_AFTER_TESTS=${RUNNING_POSTGRES_COUNT_AFTER_TESTS}"

[[ "${RUNNING_POSTGRES_COUNT_AFTER_TESTS}" == "0" ]]

if curl \
    --silent \
    --max-time 1 \
    "http://localhost:8087/actuator/health" \
    >/dev/null 2>&1; then

  echo "[FAIL] Backend became reachable during offline verification."
  exit 1
fi

echo "[PASS] TRUE_AIR_GAP_MAINTAINED"

echo
echo "===== RESTORE POSTGRES FOR FINAL AUTHORITY ====="

docker compose start postgres

wait_postgres

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

  [[ "${COUNT}" == "1" ]]
done

FINAL_SOURCE_TOTAL="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT
          (SELECT COUNT(*) FROM resilience_governance_offline_trust_checkpoint_bundles)
        + (SELECT COUNT(*) FROM resilience_governance_offline_trust_receipt_checkpoints)
        + (SELECT COUNT(*) FROM resilience_governance_offline_trust_receipt_ledger)
        + (SELECT COUNT(*) FROM resilience_governance_offline_trust_verification_receipts);
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_STEP44_SOURCE_ROW_COUNT=${FINAL_SOURCE_TOTAL}"

[[ "${FINAL_SOURCE_TOTAL}" == "0" ]]

echo "[PASS] STEP44_DATABASE_CLEAN"
echo "[PASS] FLYWAY_V1_TO_V43_PRESERVED"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 44 VERIFICATION PASSED"
echo "========================================================================"
