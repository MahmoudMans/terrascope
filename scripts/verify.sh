#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step39-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep39Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step39-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step39-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step39-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step39-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step39-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step39-key3-public.b64"

ENVELOPE="/tmp/terrascope-step39-lineage-envelope.json"

ROOT_PIN_FILE="/tmp/terrascope-step39-root.sha256"
WRONG_PIN_FILE="/tmp/terrascope-step39-wrong-root.sha256"

VALID_RECEIPT="/tmp/terrascope-step39-valid-receipt.json"
INVALID_RECEIPT="/tmp/terrascope-step39-invalid-receipt.json"

VALID_IMPORT_REQUEST="/tmp/terrascope-step39-valid-import-request.json"
INVALID_IMPORT_REQUEST="/tmp/terrascope-step39-invalid-import-request.json"

STANDALONE_JAR="/tmp/terrascope-step39-trust-verifier.jar"

KEY1_ID="verification-step39-ed25519-v1"
KEY2_ID="verification-step39-ed25519-v2"
KEY3_ID="verification-step39-ed25519-v3"

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
    "${WRONG_PIN_FILE}" \
    "${VALID_RECEIPT}" \
    "${INVALID_RECEIPT}" \
    "${VALID_IMPORT_REQUEST}" \
    "${INVALID_IMPORT_REQUEST}" \
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
echo " TERRASCOPE — STEP 39 VERIFICATION"
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

public class TerraScopeStep39Keygen {

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
  >/tmp/terrascope-step39-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step39-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.39.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 39); do
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

echo "[PASS] FLYWAY_V39_RUNTIME"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
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
    " \
  >/dev/null

echo "[PASS] STEP39_PRE_CLEAN"

cat > /tmp/terrascope-step39-register-root.json <<JSON
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
  --data @/tmp/terrascope-step39-register-root.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/tmp/terrascope-step39-root-anchor.json

echo "[PASS] ROOT_TRUST_ANCHOR_REGISTERED"

cat > /tmp/terrascope-step39-transition1.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 39 receipt transparency transition one."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step39-transition1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step39-transition1-response.json

echo "[PASS] ROOT_TO_KEY2_SIGNED_TRANSITION"

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

cat > /tmp/terrascope-step39-transition2.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 39 receipt transparency transition two."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step39-transition2.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step39-transition2-response.json

echo "[PASS] KEY2_TO_KEY3_SIGNED_TRANSITION"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages" \
  >/tmp/terrascope-step39-package.json

PACKAGE_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step39-package.json",
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

echo "[PASS] PORTABLE_LINEAGE_EXPORTED"

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

echo "[PASS] ROOT_PIN_FILES_READY"

stop_backend

docker compose stop postgres >/dev/null

echo "[PASS] AIR_GAP_RUNTIME_READY"

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${ENVELOPE}" \
  --root-pin "${ROOT_PIN_FILE}" \
  --receipt "${VALID_RECEIPT}" \
  >/tmp/terrascope-step39-valid-verifier.log

set +e

java \
  -jar "${STANDALONE_JAR}" \
  --envelope "${ENVELOPE}" \
  --root-pin "${WRONG_PIN_FILE}" \
  --receipt "${INVALID_RECEIPT}" \
  >/tmp/terrascope-step39-invalid-verifier.log

INVALID_EXIT=$?

set -e

[[ "${INVALID_EXIT}" == "2" ]]

echo "[PASS] AIR_GAPPED_VALID_AND_INVALID_RECEIPTS"

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

python3 \
  - \
  "${VALID_RECEIPT}" \
  "${INVALID_RECEIPT}" \
  "${VALID_IMPORT_REQUEST}" \
  "${INVALID_IMPORT_REQUEST}" \
<<'PY'
import json
import sys

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
    receipt_json = open(
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
                    receipt_json,
            },
            handle,
            separators=(",", ":"),
        )
PY

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${VALID_IMPORT_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step39-valid-import.json

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @"${INVALID_IMPORT_REQUEST}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step39-invalid-import.json

VALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step39-valid-import.json",
        encoding="utf-8",
    )
)

assert payload["verificationResult"] == "VALID"

print(
    payload["id"]
)
PY
)"

INVALID_RECEIPT_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step39-invalid-import.json",
        encoding="utf-8",
    )
)

assert payload["verificationResult"] == "INVALID"

print(
    payload["id"]
)
PY
)"

echo "VALID_RECEIPT_ID=${VALID_RECEIPT_ID}"
echo "INVALID_RECEIPT_ID=${INVALID_RECEIPT_ID}"

echo "[PASS] TWO_OFFLINE_RECEIPTS_IMPORTED"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger" \
  >/tmp/terrascope-step39-ledger-history.json

python3 \
  - "${VALID_RECEIPT_ID}" \
  "${INVALID_RECEIPT_ID}" \
<<'PY'
import json
import re
import sys

valid_id = sys.argv[1]
invalid_id = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step39-ledger-history.json",
        encoding="utf-8",
    )
)

entries = payload["entries"]

assert len(entries) == 2

assert entries[0]["ledgerNumber"] == 2
assert entries[0]["receiptId"] == invalid_id
assert entries[0]["verificationResult"] == "INVALID"

assert entries[1]["ledgerNumber"] == 1
assert entries[1]["receiptId"] == valid_id
assert entries[1]["verificationResult"] == "VALID"

assert entries[1]["previousLedgerHash"] is None

assert (
    entries[0]["previousLedgerHash"]
    == entries[1]["ledgerHash"]
)

for entry in entries:
    assert re.fullmatch(
        r"[0-9a-f]{64}",
        entry["receiptPayloadHash"],
    )

    assert re.fullmatch(
        r"[0-9a-f]{64}",
        entry["ledgerHash"],
    )

print(
    "[PASS] OFFLINE_RECEIPT_LEDGER_HISTORY"
)

print(
    "[PASS] OFFLINE_RECEIPT_LEDGER_CHAIN_LINKAGE"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/verify" \
  >/tmp/terrascope-step39-ledger-valid.json

HEAD_HASH="$(
  python3 - <<'PY'
import json
import re

payload = json.load(
    open(
        "/tmp/terrascope-step39-ledger-valid.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["ledgerEntryCount"] == 2
assert payload["verifiedEntryCount"] == 2
assert payload["brokenLedgerNumber"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["headLedgerHash"],
)

print(
    payload["headLedgerHash"]
)
PY
)"

echo "OFFLINE_RECEIPT_LEDGER_HEAD=${HEAD_HASH}"

echo "[PASS] OFFLINE_RECEIPT_LEDGER_VERIFIED"

ORIGINAL_ROOT_PIN_VALID="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT root_pin_valid
      FROM resilience_governance_offline_trust_verification_receipts
      WHERE id =
            '${VALID_RECEIPT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

[[ "${ORIGINAL_ROOT_PIN_VALID}" == "t" ]]

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_offline_trust_verification_receipts

      SET root_pin_valid =
          FALSE

      WHERE id =
            '${VALID_RECEIPT_ID}'::uuid;
    " \
  >/dev/null

echo "[PASS] RECEIPT_ROW_TAMPER_APPLIED"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/verify" \
  >/tmp/terrascope-step39-ledger-tampered.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step39-ledger-tampered.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["ledgerEntryCount"] == 2
assert payload["verifiedEntryCount"] == 0

assert payload["brokenLedgerNumber"] == 1

assert (
    "modified after ledger anchoring"
    in payload["message"]
)

print(
    "[PASS] OFFLINE_RECEIPT_ROW_TAMPER_DETECTION"
)
PY

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_offline_trust_verification_receipts

      SET root_pin_valid =
          TRUE

      WHERE id =
            '${VALID_RECEIPT_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/verify" \
  >/tmp/terrascope-step39-ledger-restored.json

python3 \
  - "${HEAD_HASH}" \
<<'PY'
import json
import sys

head = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step39-ledger-restored.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True
assert payload["verifiedEntryCount"] == 2
assert payload["headLedgerHash"] == head

print(
    "[PASS] OFFLINE_RECEIPT_ROW_RESTORATION"
)
PY

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
            'resilience_governance_offline_trust_receipt_ledger';
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
      FROM resilience_governance_offline_trust_receipt_ledger;
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
            'resilience_governance_offline_trust_receipt_ledger'::regclass
        AND contype='f';
      " \
    | tr -d '[:space:]'
)"

UNIQUE_RECEIPT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_offline_trust_receipt_ledger_receipt';
      " \
    | tr -d '[:space:]'
)"

echo "OFFLINE_LEDGER_COLUMN_COUNT=${COLUMN_COUNT}"
echo "OFFLINE_LEDGER_ROW_COUNT=${ROW_COUNT}"
echo "OFFLINE_LEDGER_FK_COUNT=${FK_COUNT}"
echo "OFFLINE_LEDGER_UNIQUE_RECEIPT_COUNT=${UNIQUE_RECEIPT_COUNT}"

[[ "${COLUMN_COUNT}" == "11" ]]
[[ "${ROW_COUNT}" == "2" ]]
[[ "${FK_COUNT}" == "1" ]]
[[ "${UNIQUE_RECEIPT_COUNT}" == "1" ]]

echo "[PASS] OFFLINE_RECEIPT_LEDGER_DATABASE_CONTRACT"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_offline_trust_receipt_ledger;

      DELETE
      FROM resilience_governance_offline_trust_verification_receipts
      WHERE id IN (
          '${VALID_RECEIPT_ID}'::uuid,
          '${INVALID_RECEIPT_ID}'::uuid
      );

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

echo "FINAL_OFFLINE_LEDGER_COUNT=${FINAL_LEDGER_COUNT}"
echo "FINAL_OFFLINE_RECEIPT_COUNT=${FINAL_RECEIPT_COUNT}"

[[ "${FINAL_LEDGER_COUNT}" == "0" ]]
[[ "${FINAL_RECEIPT_COUNT}" == "0" ]]

echo "[PASS] STEP39_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 39 VERIFICATION PASSED"
echo "========================================================================"
