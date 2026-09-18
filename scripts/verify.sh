#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step42-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep42Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step42-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step42-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step42-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step42-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step42-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step42-key3-public.b64"

ENVELOPE="/tmp/terrascope-step42-lineage-envelope.json"
ROOT_PIN_FILE="/tmp/terrascope-step42-root.sha256"

RECEIPT_ONE="/tmp/terrascope-step42-receipt-one.json"
RECEIPT_TWO="/tmp/terrascope-step42-receipt-two.json"

REQUEST_ONE="/tmp/terrascope-step42-request-one.json"
REQUEST_TWO="/tmp/terrascope-step42-request-two.json"

STANDALONE_JAR="/tmp/terrascope-step42-trust-verifier.jar"

KEY1_ID="verification-step42-ed25519-v1"
KEY2_ID="verification-step42-ed25519-v2"
KEY3_ID="verification-step42-ed25519-v3"

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
    "${ROOT_PIN_FILE}" \
    "${RECEIPT_ONE}" \
    "${RECEIPT_TWO}" \
    "${REQUEST_ONE}" \
    "${REQUEST_TWO}" \
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

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 42 VERIFICATION"
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

public class TerraScopeStep42Keygen {

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
  >/tmp/terrascope-step42-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step42-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.42.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 42); do
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

echo "[PASS] FLYWAY_V42_RUNTIME"

echo
echo "===== STEP 42 PRE-CLEAN ====="

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
          'OFFLINE_TRUST_RECEIPT_CHECKPOINT_NUMBER_V1'
      );
    " \
  >/dev/null

echo "[PASS] STEP42_PRE_CLEAN"

echo
echo "===== CREATE TRUST LINEAGE ====="

cat > /tmp/terrascope-step42-root.json <<JSON
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
  --data @/tmp/terrascope-step42-root.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/tmp/terrascope-step42-root-response.json

echo "[PASS] ROOT_ANCHOR_REGISTERED"

cat > /tmp/terrascope-step42-transition1.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 42 signed offline receipt checkpoint transition one."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step42-transition1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step42-transition1-response.json

echo "[PASS] ROOT_TO_KEY2_TRANSITION"

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

cat > /tmp/terrascope-step42-transition2.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 42 signed offline receipt checkpoint transition two."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step42-transition2.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step42-transition2-response.json

echo "[PASS] KEY2_TO_KEY3_TRANSITION"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages" \
  >/tmp/terrascope-step42-package.json

PACKAGE_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step42-package.json",
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
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages/export/${PACKAGE_ID}" \
  --output "${ENVELOPE}"

ROOT_PIN="$(
  python3 \
    - "${ENVELOPE}" \
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

echo "[PASS] PORTABLE_LINEAGE_FIXTURE_READY"

echo
echo "===== GENERATE TWO DISTINCT OFFLINE RECEIPTS ====="

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${RECEIPT_ONE}" \
  >/tmp/terrascope-step42-receipt-one.log

sleep 1

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${RECEIPT_TWO}" \
  >/tmp/terrascope-step42-receipt-two.log

grep \
  -q \
  'VERIFICATION_RESULT=VALID' \
  /tmp/terrascope-step42-receipt-one.log

grep \
  -q \
  'VERIFICATION_RESULT=VALID' \
  /tmp/terrascope-step42-receipt-two.log

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
    "[PASS] TWO_DISTINCT_OFFLINE_RECEIPTS"
)
PY

echo
echo "===== KEYLESS RECEIPT IMPORT ====="

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
  >/tmp/terrascope-step42-import-one.json

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${REQUEST_TWO}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step42-import-two.json

python3 - <<'PY'
import json

one = json.load(
    open(
        "/tmp/terrascope-step42-import-one.json",
        encoding="utf-8",
    )
)

two = json.load(
    open(
        "/tmp/terrascope-step42-import-two.json",
        encoding="utf-8",
    )
)

assert one["verificationResult"] == "VALID"
assert two["verificationResult"] == "VALID"

assert {
    one["importNumber"],
    two["importNumber"],
} == {
    1,
    2,
}

print(
    "[PASS] TWO_RECEIPTS_IMPORTED"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/verify" \
  >/tmp/terrascope-step42-ledger-valid.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step42-ledger-valid.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["ledgerEntryCount"] == 2
assert payload["verifiedEntryCount"] == 2

print(
    "[PASS] OFFLINE_RECEIPT_LEDGER_READY"
)
PY

echo
echo "===== CREATE SIGNED CHECKPOINT WITH ACTIVE KEY3 ====="

stop_backend

start_with_key \
  "${KEY3_ID}" \
  "${KEY3_PRIVATE}" \
  "${KEY3_PUBLIC}"

echo "[PASS] KEY3_CHECKPOINT_RUNTIME_READY"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints" \
  >/tmp/terrascope-step42-checkpoint.json

CHECKPOINT_ID="$(
  python3 \
    - "${KEY3_ID}" \
<<'PY'
import json
import re
import sys

key_id = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step42-checkpoint.json",
        encoding="utf-8",
    )
)

assert payload["checkpointNumber"] == 1
assert payload["coveredLedgerNumber"] == 2
assert payload["entryCount"] == 2

assert payload["validReceiptCount"] == 2
assert payload["invalidReceiptCount"] == 0

assert payload["keyId"] == key_id
assert payload["algorithm"] == "ED25519"

assert (
    payload["checkpointVersion"]
    == "TS-OFFLINE-TRUST-RECEIPT-CHECKPOINT-V1"
)

assert payload["previousCheckpointHash"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["publicKeyFingerprint"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["coveredLedgerHash"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["checkpointHash"],
)

assert payload["signatureBase64"]

print(
    payload["id"]
)
PY
)"

echo "CHECKPOINT_ID=${CHECKPOINT_ID}"

echo "[PASS] SIGNED_CHECKPOINT_CREATED"

echo
echo "===== SAME HEAD + SAME KEY IDEMPOTENCY ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints" \
  >/tmp/terrascope-step42-checkpoint-repeat.json

python3 \
  - "${CHECKPOINT_ID}" \
<<'PY'
import json
import sys

expected_id = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step42-checkpoint-repeat.json",
        encoding="utf-8",
    )
)

assert payload["id"] == expected_id
assert payload["checkpointNumber"] == 1

print(
    "[PASS] CHECKPOINT_IDEMPOTENCY"
)
PY

echo
echo "===== CHECKPOINT HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints" \
  >/tmp/terrascope-step42-checkpoint-history.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step42-checkpoint-history.json",
        encoding="utf-8",
    )
)

assert len(
    payload["checkpoints"]
) == 1

assert payload["checkpoints"][0]["checkpointNumber"] == 1

print(
    "[PASS] CHECKPOINT_HISTORY"
)
PY

echo
echo "===== SIGNED CHECKPOINT VERIFICATION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints/verify" \
  >/tmp/terrascope-step42-checkpoint-valid.json

CHECKPOINT_HASH="$(
  python3 - <<'PY'
import json
import re

payload = json.load(
    open(
        "/tmp/terrascope-step42-checkpoint-valid.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["checkpointCount"] == 1
assert payload["verifiedCheckpointCount"] == 1

assert payload["brokenCheckpointNumber"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["checkpointHeadHash"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["coveredLedgerHeadHash"],
)

print(
    payload["checkpointHeadHash"]
)
PY
)"

echo "SIGNED_CHECKPOINT_HEAD=${CHECKPOINT_HASH}"

echo "[PASS] SIGNED_CHECKPOINT_VERIFIED"

echo
echo "===== LEDGER REWRITE TAMPER DETECTION ====="

ORIGINAL_LEDGER_HASH="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT ledger_hash
      FROM resilience_governance_offline_trust_receipt_ledger
      WHERE ledger_number=2;
      " \
    | tr -d '[:space:]'
)"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_offline_trust_receipt_ledger

      SET ledger_hash =
          '0000000000000000000000000000000000000000000000000000000000000000'

      WHERE ledger_number=2;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints/verify" \
  >/tmp/terrascope-step42-ledger-tampered.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step42-ledger-tampered.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert (
    "ledger is invalid"
    in payload["message"].lower()

    or "covered ledger head"
       in payload["message"].lower()
)

print(
    "[PASS] LEDGER_REWRITE_TAMPER_DETECTION"
)
PY

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_offline_trust_receipt_ledger

      SET ledger_hash =
          '${ORIGINAL_LEDGER_HASH}'

      WHERE ledger_number=2;
    " \
  >/dev/null

echo "[PASS] LEDGER_HASH_RESTORED"

echo
echo "===== CHECKPOINT SIGNATURE TAMPER DETECTION ====="

ORIGINAL_SIGNATURE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT signature_base64
      FROM resilience_governance_offline_trust_receipt_checkpoints
      WHERE id=
            '${CHECKPOINT_ID}'::uuid;
      " \
    | tr -d '\n'
)"

TAMPER_SIGNATURE="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_offline_trust_receipt_checkpoints

      SET signature_base64 =
          '${TAMPER_SIGNATURE}'

      WHERE id=
            '${CHECKPOINT_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints/verify" \
  >/tmp/terrascope-step42-signature-tampered.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step42-signature-tampered.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["brokenCheckpointNumber"] == 1

assert (
    "signature verification failed"
    in payload["message"]
)

print(
    "[PASS] CHECKPOINT_SIGNATURE_TAMPER_DETECTION"
)
PY

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_offline_trust_receipt_checkpoints

      SET signature_base64 =
          '${ORIGINAL_SIGNATURE}'

      WHERE id=
            '${CHECKPOINT_ID}'::uuid;
    " \
  >/dev/null

echo "[PASS] CHECKPOINT_SIGNATURE_RESTORED"

echo
echo "===== PRIVATE-KEY-FREE CHECKPOINT VERIFICATION ====="

stop_backend

start_without_key

echo "[PASS] KEYLESS_CHECKPOINT_RUNTIME_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/checkpoints/verify" \
  >/tmp/terrascope-step42-keyless-valid.json

python3 \
  - "${CHECKPOINT_HASH}" \
<<'PY'
import json
import sys

expected_head = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step42-keyless-valid.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["checkpointCount"] == 1
assert payload["verifiedCheckpointCount"] == 1

assert payload["checkpointHeadHash"] == expected_head

print(
    "[PASS] PRIVATE_KEY_FREE_CHECKPOINT_VERIFICATION"
)
PY

echo
echo "===== V42 DATABASE CONTRACT ====="

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
            'resilience_governance_offline_trust_receipt_checkpoints';
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
      FROM resilience_governance_offline_trust_receipt_checkpoints;
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
            'resilience_governance_offline_trust_receipt_checkpoints'::regclass
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
            'OFFLINE_TRUST_RECEIPT_CHECKPOINT_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

echo "CHECKPOINT_COLUMN_COUNT=${COLUMN_COUNT}"
echo "CHECKPOINT_ROW_COUNT=${ROW_COUNT}"
echo "CHECKPOINT_SOURCE_FK_COUNT=${FK_COUNT}"
echo "CHECKPOINT_ALLOCATOR_NEXT=${ALLOCATOR_NEXT}"

[[ "${COLUMN_COUNT}" == "15" ]]
[[ "${ROW_COUNT}" == "1" ]]
[[ "${FK_COUNT}" == "0" ]]
[[ "${ALLOCATOR_NEXT}" == "2" ]]

echo "[PASS] V42_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 42 FIXTURE ====="

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

      UPDATE resilience_governance_sequence_allocators

      SET
          next_value = 1,
          updated_at = NOW()

      WHERE allocator_key IN (
          'OFFLINE_TRUST_IMPORT_NUMBER_V1',
          'OFFLINE_TRUST_LEDGER_NUMBER_V1',
          'OFFLINE_TRUST_RECEIPT_CHECKPOINT_NUMBER_V1'
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
      FROM resilience_governance_offline_trust_receipt_checkpoints;
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
      FROM resilience_governance_offline_trust_receipt_ledger;
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
      FROM resilience_governance_offline_trust_verification_receipts;
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_CHECKPOINT_COUNT=${FINAL_CHECKPOINT_COUNT}"
echo "FINAL_LEDGER_COUNT=${FINAL_LEDGER_COUNT}"
echo "FINAL_RECEIPT_COUNT=${FINAL_RECEIPT_COUNT}"

[[ "${FINAL_CHECKPOINT_COUNT}" == "0" ]]
[[ "${FINAL_LEDGER_COUNT}" == "0" ]]
[[ "${FINAL_RECEIPT_COUNT}" == "0" ]]

echo "[PASS] STEP42_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 42 VERIFICATION PASSED"
echo "========================================================================"
