#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step40-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep40Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step40-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step40-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step40-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step40-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step40-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step40-key3-public.b64"

ENVELOPE="/tmp/terrascope-step40-lineage-envelope.json"
ROOT_PIN_FILE="/tmp/terrascope-step40-root.sha256"

STANDALONE_JAR="/tmp/terrascope-step40-trust-verifier.jar"

KEY1_ID="verification-step40-ed25519-v1"
KEY2_ID="verification-step40-ed25519-v2"
KEY3_ID="verification-step40-ed25519-v3"

IMPORT_COUNT=12

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
    "${STANDALONE_JAR}"

  rm -rf \
    /tmp/terrascope-step40-receipts \
    /tmp/terrascope-step40-requests \
    /tmp/terrascope-step40-responses \
    /tmp/terrascope-step40-statuses
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
echo " TERRASCOPE — STEP 40 VERIFICATION"
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

public class TerraScopeStep40Keygen {

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
  >/tmp/terrascope-step40-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step40-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.40.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 40); do
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

echo "[PASS] FLYWAY_V40_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

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

      UPDATE resilience_governance_sequence_allocators

      SET
          next_value = 1,
          updated_at = NOW()

      WHERE allocator_key IN (
          'OFFLINE_TRUST_IMPORT_NUMBER_V1',
          'OFFLINE_TRUST_LEDGER_NUMBER_V1'
      );
    " \
  >/dev/null

ALLOCATOR_ROW_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_sequence_allocators;
      " \
    | tr -d '[:space:]'
)"

ALLOCATOR_NEXT_SUM="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT SUM(next_value)
      FROM resilience_governance_sequence_allocators;
      " \
    | tr -d '[:space:]'
)"

echo "ALLOCATOR_ROW_COUNT=${ALLOCATOR_ROW_COUNT}"
echo "ALLOCATOR_NEXT_VALUE_SUM=${ALLOCATOR_NEXT_SUM}"

[[ "${ALLOCATOR_ROW_COUNT}" == "2" ]]
[[ "${ALLOCATOR_NEXT_SUM}" == "2" ]]

echo "[PASS] STEP40_PRE_CLEAN"

echo
echo "===== CREATE PORTABLE LINEAGE ====="

cat > /tmp/terrascope-step40-register-root.json <<JSON
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
  --data @/tmp/terrascope-step40-register-root.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/tmp/terrascope-step40-root-anchor.json

cat > /tmp/terrascope-step40-transition1.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 40 concurrent allocator transition one."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step40-transition1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step40-transition1-response.json

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

cat > /tmp/terrascope-step40-transition2.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 40 concurrent allocator transition two."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step40-transition2.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate" \
  >/tmp/terrascope-step40-transition2-response.json

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages" \
  >/tmp/terrascope-step40-package.json

PACKAGE_ID="$(
  python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step40-package.json",
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

echo "[PASS] CONCURRENCY_LINEAGE_FIXTURE_READY"

echo
echo "===== GENERATE ${IMPORT_COUNT} DISTINCT OFFLINE RECEIPTS ====="

rm -rf \
  /tmp/terrascope-step40-receipts \
  /tmp/terrascope-step40-requests \
  /tmp/terrascope-step40-responses \
  /tmp/terrascope-step40-statuses

mkdir -p \
  /tmp/terrascope-step40-receipts \
  /tmp/terrascope-step40-requests \
  /tmp/terrascope-step40-responses \
  /tmp/terrascope-step40-statuses

for INDEX in $(seq 1 "${IMPORT_COUNT}"); do
  RECEIPT_FILE="/tmp/terrascope-step40-receipts/receipt-${INDEX}.json"

  java \
    -jar "${STANDALONE_JAR}" \
    --envelope "${ENVELOPE}" \
    --root-pin "${ROOT_PIN_FILE}" \
    --receipt "${RECEIPT_FILE}" \
    >/tmp/terrascope-step40-receipt-${INDEX}.log

  grep \
    -q \
    'VERIFICATION_RESULT=VALID' \
    /tmp/terrascope-step40-receipt-${INDEX}.log

  sleep 0.02
done

UNIQUE_RECEIPT_HASH_COUNT="$(
  python3 - <<'PY'
import glob
import json

hashes = set()

for file_name in glob.glob(
    "/tmp/terrascope-step40-receipts/receipt-*.json"
):
    payload = json.load(
        open(
            file_name,
            encoding="utf-8",
        )
    )

    assert payload["valid"] is True

    hashes.add(
        payload["receiptHash"]
    )

print(
    len(hashes)
)
PY
)"

echo "UNIQUE_RECEIPT_HASH_COUNT=${UNIQUE_RECEIPT_HASH_COUNT}"

[[ "${UNIQUE_RECEIPT_HASH_COUNT}" == "${IMPORT_COUNT}" ]]

echo "[PASS] DISTINCT_OFFLINE_RECEIPTS_READY"

echo
echo "===== BUILD CONCURRENT IMPORT REQUESTS ====="

python3 - "${IMPORT_COUNT}" <<'PY'
import json
import sys

count = int(
    sys.argv[1]
)

for index in range(
    1,
    count + 1,
):
    source = (
        f"/tmp/terrascope-step40-receipts/receipt-{index}.json"
    )

    target = (
        f"/tmp/terrascope-step40-requests/request-{index}.json"
    )

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

echo "[PASS] CONCURRENT_IMPORT_REQUESTS_READY"

echo
echo "===== RESTART KEYLESS ====="

stop_backend

start_without_key

echo "[PASS] KEYLESS_CONCURRENT_IMPORT_RUNTIME_READY"

echo
echo "===== CONCURRENT IMPORT BURST ====="

PIDS=()

for INDEX in $(seq 1 "${IMPORT_COUNT}"); do
  (
    curl \
      --silent \
      --output "/tmp/terrascope-step40-responses/response-${INDEX}.json" \
      --write-out '%{http_code}' \
      --request POST \
      --header 'Content-Type: application/json' \
      --data @"/tmp/terrascope-step40-requests/request-${INDEX}.json" \
      "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
      >"/tmp/terrascope-step40-statuses/status-${INDEX}.txt"
  ) &

  PIDS+=(
    "$!"
  )
done

for PID in "${PIDS[@]}"; do
  wait "${PID}"
done

python3 - "${IMPORT_COUNT}" <<'PY'
from pathlib import Path
import json
import sys

count = int(
    sys.argv[1]
)

import_numbers = []

for index in range(
    1,
    count + 1,
):
    status = Path(
        f"/tmp/terrascope-step40-statuses/status-{index}.txt"
    ).read_text().strip()

    assert status == "200", (
        index,
        status,
        Path(
            f"/tmp/terrascope-step40-responses/response-{index}.json"
        ).read_text(),
    )

    payload = json.loads(
        Path(
            f"/tmp/terrascope-step40-responses/response-{index}.json"
        ).read_text()
    )

    assert payload["verificationResult"] == "VALID"

    import_numbers.append(
        payload["importNumber"]
    )

assert len(
    set(
        import_numbers
    )
) == count

assert sorted(
    import_numbers
) == list(
    range(
        1,
        count + 1,
    )
)

print(
    f"CONCURRENT_HTTP_200_COUNT={count}"
)

print(
    "CONCURRENT_IMPORT_NUMBERS="
    + ",".join(
        map(
            str,
            sorted(
                import_numbers
            ),
        )
    )
)

print(
    "[PASS] CONCURRENT_IMPORT_HTTP_AUTHORITY"
)
PY

echo
echo "========================================================================"
echo " CONCURRENT DATABASE AUTHORITY"
echo "========================================================================"

RECEIPT_COUNT="$(
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

RECEIPT_DISTINCT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(DISTINCT import_number)
      FROM resilience_governance_offline_trust_verification_receipts;
      " \
    | tr -d '[:space:]'
)"

RECEIPT_MIN="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT MIN(import_number)
      FROM resilience_governance_offline_trust_verification_receipts;
      " \
    | tr -d '[:space:]'
)"

RECEIPT_MAX="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT MAX(import_number)
      FROM resilience_governance_offline_trust_verification_receipts;
      " \
    | tr -d '[:space:]'
)"

LEDGER_COUNT="$(
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

LEDGER_DISTINCT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(DISTINCT ledger_number)
      FROM resilience_governance_offline_trust_receipt_ledger;
      " \
    | tr -d '[:space:]'
)"

LEDGER_MIN="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT MIN(ledger_number)
      FROM resilience_governance_offline_trust_receipt_ledger;
      " \
    | tr -d '[:space:]'
)"

LEDGER_MAX="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT MAX(ledger_number)
      FROM resilience_governance_offline_trust_receipt_ledger;
      " \
    | tr -d '[:space:]'
)"

echo "CONCURRENT_RECEIPT_COUNT=${RECEIPT_COUNT}"
echo "CONCURRENT_RECEIPT_DISTINCT_IMPORT_NUMBERS=${RECEIPT_DISTINCT}"
echo "CONCURRENT_RECEIPT_MIN=${RECEIPT_MIN}"
echo "CONCURRENT_RECEIPT_MAX=${RECEIPT_MAX}"

echo "CONCURRENT_LEDGER_COUNT=${LEDGER_COUNT}"
echo "CONCURRENT_LEDGER_DISTINCT_NUMBERS=${LEDGER_DISTINCT}"
echo "CONCURRENT_LEDGER_MIN=${LEDGER_MIN}"
echo "CONCURRENT_LEDGER_MAX=${LEDGER_MAX}"

[[ "${RECEIPT_COUNT}" == "${IMPORT_COUNT}" ]]
[[ "${RECEIPT_DISTINCT}" == "${IMPORT_COUNT}" ]]
[[ "${RECEIPT_MIN}" == "1" ]]
[[ "${RECEIPT_MAX}" == "${IMPORT_COUNT}" ]]

[[ "${LEDGER_COUNT}" == "${IMPORT_COUNT}" ]]
[[ "${LEDGER_DISTINCT}" == "${IMPORT_COUNT}" ]]
[[ "${LEDGER_MIN}" == "1" ]]
[[ "${LEDGER_MAX}" == "${IMPORT_COUNT}" ]]

echo "[PASS] CONCURRENT_MONOTONIC_NUMBER_AUTHORITY"

echo
echo "===== CONCURRENT HASH-CHAIN VERIFICATION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/ledger/verify" \
  >/tmp/terrascope-step40-ledger-verification.json

HEAD_HASH="$(
  python3 \
    - "${IMPORT_COUNT}" \
<<'PY'
import json
import re
import sys

count = int(
    sys.argv[1]
)

payload = json.load(
    open(
        "/tmp/terrascope-step40-ledger-verification.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["ledgerEntryCount"] == count
assert payload["verifiedEntryCount"] == count

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

echo "CONCURRENT_LEDGER_HEAD=${HEAD_HASH}"

echo "[PASS] CONCURRENT_HASH_CHAIN_VERIFICATION"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -AtF '|' \
    -c "
      SELECT
        ledger_number,
        COALESCE(
            previous_ledger_hash,
            '<null>'
        ),
        ledger_hash
      FROM resilience_governance_offline_trust_receipt_ledger
      ORDER BY ledger_number;
    " \
  >/tmp/terrascope-step40-ledger-chain.txt

python3 \
  - \
  /tmp/terrascope-step40-ledger-chain.txt \
  "${IMPORT_COUNT}" \
  "${HEAD_HASH}" \
<<'PY'
from pathlib import Path
import re
import sys

file_name = sys.argv[1]
count = int(
    sys.argv[2]
)
expected_head = sys.argv[3]

rows = []

for line in Path(
    file_name
).read_text().splitlines():
    if not line:
        continue

    number, previous_hash, ledger_hash = (
        line.split(
            "|"
        )
    )

    rows.append(
        (
            int(
                number
            ),
            previous_hash,
            ledger_hash,
        )
    )

assert len(
    rows
) == count

previous = None

for expected_number, row in enumerate(
    rows,
    start=1,
):
    number, previous_hash, ledger_hash = row

    assert number == expected_number

    if previous is None:
        assert previous_hash == "<null>"
    else:
        assert previous_hash == previous

    assert re.fullmatch(
        r"[0-9a-f]{64}",
        ledger_hash,
    )

    previous = ledger_hash

assert previous == expected_head

print(
    "[PASS] CONCURRENT_SQL_CHAIN_LINKAGE"
)
PY

echo
echo "===== ALLOCATOR STATE AFTER CONCURRENT BURST ====="

IMPORT_NEXT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            'OFFLINE_TRUST_IMPORT_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

LEDGER_NEXT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            'OFFLINE_TRUST_LEDGER_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

EXPECTED_NEXT="$(
  python3 \
    - "${IMPORT_COUNT}" \
<<'PY'
import sys

print(
    int(
        sys.argv[1]
    ) + 1
)
PY
)"

echo "IMPORT_ALLOCATOR_NEXT=${IMPORT_NEXT}"
echo "LEDGER_ALLOCATOR_NEXT=${LEDGER_NEXT}"
echo "EXPECTED_ALLOCATOR_NEXT=${EXPECTED_NEXT}"

[[ "${IMPORT_NEXT}" == "${EXPECTED_NEXT}" ]]
[[ "${LEDGER_NEXT}" == "${EXPECTED_NEXT}" ]]

echo "[PASS] ALLOCATOR_POST_CONCURRENCY_STATE"

echo
echo "===== IDEMPOTENT RE-IMPORT DOES NOT CONSUME NUMBERS ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step40-requests/request-1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/offline-verification/imports" \
  >/tmp/terrascope-step40-idempotent-response.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step40-idempotent-response.json",
        encoding="utf-8",
    )
)

assert payload["verificationResult"] == "VALID"

assert 1 <= payload["importNumber"] <= 12

print(
    "[PASS] IDEMPOTENT_REIMPORT_RESPONSE"
)
PY

IMPORT_NEXT_AFTER_IDEMPOTENT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            'OFFLINE_TRUST_IMPORT_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

LEDGER_NEXT_AFTER_IDEMPOTENT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            'OFFLINE_TRUST_LEDGER_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

[[ "${IMPORT_NEXT_AFTER_IDEMPOTENT}" == "${EXPECTED_NEXT}" ]]
[[ "${LEDGER_NEXT_AFTER_IDEMPOTENT}" == "${EXPECTED_NEXT}" ]]

echo "[PASS] IDEMPOTENT_REIMPORT_DOES_NOT_ADVANCE_ALLOCATORS"

echo
echo "===== TRANSACTION ROLLBACK PROOF ====="

IMPORT_BEFORE_ROLLBACK="${IMPORT_NEXT_AFTER_IDEMPOTENT}"
LEDGER_BEFORE_ROLLBACK="${LEDGER_NEXT_AFTER_IDEMPOTENT}"

docker compose exec -T postgres \
  psql \
    -v ON_ERROR_STOP=1 \
    -U terrascope \
    -d terrascope \
<<'SQL' >/tmp/terrascope-step40-import-rollback.txt
BEGIN;

UPDATE resilience_governance_sequence_allocators
SET
    next_value =
        next_value + 1,
    updated_at =
        NOW()
WHERE allocator_key =
      'OFFLINE_TRUST_IMPORT_NUMBER_V1'
RETURNING
    next_value - 1;

ROLLBACK;
SQL

docker compose exec -T postgres \
  psql \
    -v ON_ERROR_STOP=1 \
    -U terrascope \
    -d terrascope \
<<'SQL' >/tmp/terrascope-step40-ledger-rollback.txt
BEGIN;

UPDATE resilience_governance_sequence_allocators
SET
    next_value =
        next_value + 1,
    updated_at =
        NOW()
WHERE allocator_key =
      'OFFLINE_TRUST_LEDGER_NUMBER_V1'
RETURNING
    next_value - 1;

ROLLBACK;
SQL

IMPORT_AFTER_ROLLBACK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            'OFFLINE_TRUST_IMPORT_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

LEDGER_AFTER_ROLLBACK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            'OFFLINE_TRUST_LEDGER_NUMBER_V1';
      " \
    | tr -d '[:space:]'
)"

echo "IMPORT_BEFORE_ROLLBACK=${IMPORT_BEFORE_ROLLBACK}"
echo "IMPORT_AFTER_ROLLBACK=${IMPORT_AFTER_ROLLBACK}"

echo "LEDGER_BEFORE_ROLLBACK=${LEDGER_BEFORE_ROLLBACK}"
echo "LEDGER_AFTER_ROLLBACK=${LEDGER_AFTER_ROLLBACK}"

[[ "${IMPORT_AFTER_ROLLBACK}" == "${IMPORT_BEFORE_ROLLBACK}" ]]
[[ "${LEDGER_AFTER_ROLLBACK}" == "${LEDGER_BEFORE_ROLLBACK}" ]]

echo "[PASS] TRANSACTIONAL_ALLOCATOR_ROLLBACK_SAFETY"

echo
echo "===== DATABASE CONTRACT ====="

ALLOCATOR_COLUMNS="$(
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
            'resilience_governance_sequence_allocators';
      " \
    | tr -d '[:space:]'
)"

ALLOCATOR_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_sequence_allocators;
      " \
    | tr -d '[:space:]'
)"

ALLOCATOR_PK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid=
            'resilience_governance_sequence_allocators'::regclass
        AND contype='p';
      " \
    | tr -d '[:space:]'
)"

echo "ALLOCATOR_COLUMN_COUNT=${ALLOCATOR_COLUMNS}"
echo "ALLOCATOR_ROW_COUNT=${ALLOCATOR_ROWS}"
echo "ALLOCATOR_PRIMARY_KEY_COUNT=${ALLOCATOR_PK}"

[[ "${ALLOCATOR_COLUMNS}" == "4" ]]
[[ "${ALLOCATOR_ROWS}" == "2" ]]
[[ "${ALLOCATOR_PK}" == "1" ]]

echo "[PASS] TRANSACTIONAL_ALLOCATOR_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 40 FIXTURE ====="

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
          'OFFLINE_TRUST_LEDGER_NUMBER_V1'
      );
    " \
  >/dev/null

FINAL_RECEIPTS="$(
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

FINAL_LEDGER="$(
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

FINAL_ALLOCATOR_SUM="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT SUM(next_value)
      FROM resilience_governance_sequence_allocators;
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_RECEIPT_COUNT=${FINAL_RECEIPTS}"
echo "FINAL_LEDGER_COUNT=${FINAL_LEDGER}"
echo "FINAL_ALLOCATOR_NEXT_VALUE_SUM=${FINAL_ALLOCATOR_SUM}"

[[ "${FINAL_RECEIPTS}" == "0" ]]
[[ "${FINAL_LEDGER}" == "0" ]]
[[ "${FINAL_ALLOCATOR_SUM}" == "2" ]]

echo "[PASS] STEP40_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 40 VERIFICATION PASSED"
echo "========================================================================"
