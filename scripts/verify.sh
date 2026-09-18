#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step36-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep36Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step36-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step36-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step36-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step36-key2-public.b64"

KEY3_PRIVATE_FILE="/tmp/terrascope-step36-key3-private.b64"
KEY3_PUBLIC_FILE="/tmp/terrascope-step36-key3-public.b64"

ENVELOPE_ONE="/tmp/terrascope-step36-lineage-one.json"
ENVELOPE_TWO="/tmp/terrascope-step36-lineage-two.json"

TAMPERED_ENVELOPE="/tmp/terrascope-step36-lineage-tampered.json"

KEY1_ID="verification-step36-ed25519-v1"
KEY2_ID="verification-step36-ed25519-v2"
KEY3_ID="verification-step36-ed25519-v3"

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
echo " TERRASCOPE — STEP 36 VERIFICATION"
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

public class TerraScopeStep36Keygen {

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
  >/tmp/terrascope-step36-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step36-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.36.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 36); do
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

echo "[PASS] FLYWAY_V36_RUNTIME"

echo
echo "===== PRE-CLEAN ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
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

echo "[PASS] STEP36_PRE_CLEAN"

echo
echo "===== REGISTER KEY 1 TRUST ANCHOR ====="

cat > /tmp/terrascope-step36-register-key1.json <<JSON
{
  "keyId": "${KEY1_ID}",
  "publicKeyX509Base64": "${KEY1_PUBLIC}"
}
JSON

REGISTER_KEY1_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step36-key1-anchor.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step36-register-key1.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors"
)"

echo "REGISTER_KEY1_HTTP=${REGISTER_KEY1_HTTP}"

if [[ "${REGISTER_KEY1_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step36-key1-anchor.json
  exit 1
fi

echo "[PASS] KEY1_TRUST_ANCHOR_REGISTERED"

echo
echo "===== ROTATE KEY1 -> KEY2 ====="

cat > /tmp/terrascope-step36-transition1-request.json <<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "replacementPublicKeyX509Base64": "${KEY2_PUBLIC}",
  "reason": "Step 36 portable trust-lineage transition one."
}
JSON

TRANSITION1_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step36-transition1.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step36-transition1-request.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate"
)"

echo "TRANSITION1_HTTP=${TRANSITION1_HTTP}"

if [[ "${TRANSITION1_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step36-transition1.json
  exit 1
fi

TRANSITION1_HASH="$(
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
        "/tmp/terrascope-step36-transition1.json",
        encoding="utf-8",
    )
)

assert payload["transitionNumber"] == 1

assert payload["outgoingKeyId"] == key1
assert payload["replacementKeyId"] == key2

assert payload["previousTransitionHash"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["transitionHash"],
)

print(
    payload["transitionHash"]
)
PY
)"

echo "TRANSITION1_HASH=${TRANSITION1_HASH}"

echo "[PASS] KEY1_TO_KEY2_SIGNED_TRANSITION"

echo
echo "===== RESTART WITH KEY 2 ====="

stop_backend

start_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] KEY2_RUNTIME_READY"

echo
echo "===== ROTATE KEY2 -> KEY3 ====="

cat > /tmp/terrascope-step36-transition2-request.json <<JSON
{
  "replacementKeyId": "${KEY3_ID}",
  "replacementPublicKeyX509Base64": "${KEY3_PUBLIC}",
  "reason": "Step 36 portable trust-lineage transition two."
}
JSON

TRANSITION2_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step36-transition2.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step36-transition2-request.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/rotate"
)"

echo "TRANSITION2_HTTP=${TRANSITION2_HTTP}"

if [[ "${TRANSITION2_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step36-transition2.json
  exit 1
fi

TRANSITION2_HASH="$(
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
previous = sys.argv[3]

payload = json.load(
    open(
        "/tmp/terrascope-step36-transition2.json",
        encoding="utf-8",
    )
)

assert payload["transitionNumber"] == 2

assert payload["outgoingKeyId"] == key2
assert payload["replacementKeyId"] == key3

assert payload["previousTransitionHash"] == previous

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["transitionHash"],
)

print(
    payload["transitionHash"]
)
PY
)"

echo "TRANSITION2_HASH=${TRANSITION2_HASH}"

echo "[PASS] KEY2_TO_KEY3_SIGNED_TRANSITION"

echo
echo "===== VERIFY LIVE SIGNED LINEAGE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-transitions/verify" \
  >/tmp/terrascope-step36-live-lineage.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step36-live-lineage.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["transitionCount"] == 2
assert payload["verifiedTransitionCount"] == 2

print(
    "[PASS] LIVE_SIGNED_LINEAGE_VERIFIED"
)
PY

echo
echo "===== CREATE PORTABLE LINEAGE PACKAGE ====="

PACKAGE_CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step36-package.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages"
)"

echo "PACKAGE_CREATE_HTTP=${PACKAGE_CREATE_HTTP}"

if [[ "${PACKAGE_CREATE_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step36-package.json
  exit 1
fi

PACKAGE_ID="$(
  python3 \
    - "${TRANSITION2_HASH}" \
<<'PY'
import json
import re
import sys

lineage_head = sys.argv[1]

payload = json.load(
    open(
        "/tmp/terrascope-step36-package.json",
        encoding="utf-8",
    )
)

assert payload["packageNumber"] == 1

assert (
    payload["packageVersion"]
    == "TS-GOVERNANCE-TRUST-LINEAGE-PACKAGE-V1"
)

assert payload["transitionCount"] == 2
assert payload["trustAnchorCount"] == 3

assert payload["firstTransitionNumber"] == 1
assert payload["lastTransitionNumber"] == 2

assert payload["lineageHeadHash"] == lineage_head

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["packageHash"],
)

print(
    payload["id"]
)
PY
)"

PACKAGE_HASH="$(
  python3 - <<'PY'
import json

print(
    json.load(
        open(
            "/tmp/terrascope-step36-package.json",
            encoding="utf-8",
        )
    )["packageHash"]
)
PY
)"

echo "PACKAGE_ID=${PACKAGE_ID}"
echo "PACKAGE_HASH=${PACKAGE_HASH}"

echo "[PASS] PORTABLE_LINEAGE_PACKAGE_CREATED"

echo
echo "===== IDEMPOTENT PACKAGE CREATION ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages" \
  >/tmp/terrascope-step36-package-repeat.json

python3 \
  - "${PACKAGE_ID}" \
  "${PACKAGE_HASH}" \
<<'PY'
import json
import sys

package_id = sys.argv[1]
package_hash = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step36-package-repeat.json",
        encoding="utf-8",
    )
)

assert payload["id"] == package_id
assert payload["packageHash"] == package_hash
assert payload["packageNumber"] == 1

print(
    "[PASS] PORTABLE_LINEAGE_PACKAGE_IDEMPOTENCY"
)
PY

echo
echo "===== DETERMINISTIC PORTABLE EXPORT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages/export/${PACKAGE_ID}" \
  --output "${ENVELOPE_ONE}"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages/export/${PACKAGE_ID}" \
  --output "${ENVELOPE_TWO}"

[[ -s "${ENVELOPE_ONE}" ]]
[[ -s "${ENVELOPE_TWO}" ]]

ENVELOPE_ONE_SHA="$(
  shasum -a 256 \
    "${ENVELOPE_ONE}" \
    | awk '{print $1}'
)"

ENVELOPE_TWO_SHA="$(
  shasum -a 256 \
    "${ENVELOPE_TWO}" \
    | awk '{print $1}'
)"

echo "LINEAGE_ENVELOPE_ONE_SHA=${ENVELOPE_ONE_SHA}"
echo "LINEAGE_ENVELOPE_TWO_SHA=${ENVELOPE_TWO_SHA}"

[[ "${ENVELOPE_ONE_SHA}" == "${ENVELOPE_TWO_SHA}" ]]

python3 \
  - "${ENVELOPE_ONE}" \
  "${PACKAGE_ID}" \
  "${PACKAGE_HASH}" \
  "${KEY1_ID}" \
  "${KEY3_ID}" \
<<'PY'
import json
import sys

file_name = sys.argv[1]
package_id = sys.argv[2]
package_hash = sys.argv[3]
key1 = sys.argv[4]
key3 = sys.argv[5]

envelope = json.load(
    open(
        file_name,
        encoding="utf-8",
    )
)

assert (
    envelope["envelopeVersion"]
    == "TS-GOVERNANCE-TRUST-LINEAGE-ENVELOPE-V1"
)

assert envelope["sourcePackageId"] == package_id
assert envelope["sourcePackageNumber"] == 1

assert (
    envelope["packageVersion"]
    == "TS-GOVERNANCE-TRUST-LINEAGE-PACKAGE-V1"
)

assert envelope["packageHash"] == package_hash

payload = json.loads(
    envelope["payloadJson"]
)

assert payload["rootKeyId"] == key1
assert payload["terminalKeyId"] == key3

assert len(
    payload["transitions"]
) == 2

assert len(
    payload["trustAnchors"]
) == 3

assert (
    payload["transitions"][0]["replacementKeyId"]
    == payload["transitions"][1]["outgoingKeyId"]
)

print(
    "[PASS] PORTABLE_LINEAGE_ENVELOPE_STRUCTURE"
)
PY

echo "[PASS] DETERMINISTIC_PORTABLE_LINEAGE_EXPORT"

echo
echo "===== LIVE STATELESS VERIFICATION ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE_ONE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/verify-envelope" \
  >/tmp/terrascope-step36-live-portable-verify.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step36-live-portable-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is True

assert payload["transitionCount"] == 2
assert payload["verifiedTransitionCount"] == 2
assert payload["trustAnchorCount"] == 3

print(
    "[PASS] LIVE_PORTABLE_LINEAGE_VERIFICATION"
)
PY

echo
echo "===== REMOVE ALL SOURCE LINEAGE STATE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
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

SOURCE_TRANSITION_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
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

PACKAGE_ROW_COUNT="$(
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

echo "SOURCE_TRANSITION_COUNT=${SOURCE_TRANSITION_COUNT}"
echo "SOURCE_ANCHOR_COUNT=${SOURCE_ANCHOR_COUNT}"
echo "PACKAGE_ROW_COUNT=${PACKAGE_ROW_COUNT}"

[[ "${SOURCE_TRANSITION_COUNT}" == "0" ]]
[[ "${SOURCE_ANCHOR_COUNT}" == "0" ]]
[[ "${PACKAGE_ROW_COUNT}" == "1" ]]

echo "[PASS] SOURCE_LINEAGE_STATE_REMOVED"

echo
echo "===== RESTART WITHOUT PRIVATE KEY ====="

stop_backend

start_without_key

echo "[PASS] KEYLESS_PORTABLE_LINEAGE_RUNTIME_READY"

echo
echo "===== SOURCE-FREE + PRIVATE-KEY-FREE VERIFICATION ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${ENVELOPE_ONE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/verify-envelope" \
  >/tmp/terrascope-step36-source-free-verify.json

python3 \
  - "${KEY1_ID}" \
  "${KEY3_ID}" \
<<'PY'
import json
import sys

key1 = sys.argv[1]
key3 = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step36-source-free-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is True

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is True

assert payload["transitionCount"] == 2
assert payload["verifiedTransitionCount"] == 2

assert payload["rootKeyId"] == key1
assert payload["terminalKeyId"] == key3

print(
    "[PASS] SOURCE_FREE_PORTABLE_LINEAGE_VERIFICATION"
)

print(
    "[PASS] PRIVATE_KEY_FREE_PORTABLE_LINEAGE_VERIFICATION"
)
PY

echo
echo "===== STORED EXPORT SURVIVES SOURCE REMOVAL ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages/export/${PACKAGE_ID}" \
  --output /tmp/terrascope-step36-source-free-export.json

SOURCE_FREE_EXPORT_SHA="$(
  shasum -a 256 \
    /tmp/terrascope-step36-source-free-export.json \
    | awk '{print $1}'
)"

echo "ORIGINAL_ENVELOPE_SHA=${ENVELOPE_ONE_SHA}"
echo "SOURCE_FREE_EXPORT_SHA=${SOURCE_FREE_EXPORT_SHA}"

[[ "${SOURCE_FREE_EXPORT_SHA}" == "${ENVELOPE_ONE_SHA}" ]]

echo "[PASS] SOURCE_FREE_DETERMINISTIC_REEXPORT"

echo
echo "===== BUILD CRYPTOGRAPHIC SIGNATURE TAMPER ====="

python3 \
  - \
  "${ENVELOPE_ONE}" \
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

assert (
    tampered_payload_json
    != payload_json
)

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

echo "[PASS] PORTABLE_LINEAGE_TAMPERED_ENVELOPE_CREATED"

echo
echo "===== STATELESS SIGNATURE TAMPER DETECTION ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary @"${TAMPERED_ENVELOPE}" \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/verify-envelope" \
  >/tmp/terrascope-step36-tamper-verify.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step36-tamper-verify.json",
        encoding="utf-8",
    )
)

assert payload["valid"] is False

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["lineageValid"] is False

assert (
    "signature verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] PORTABLE_LINEAGE_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== PACKAGE HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-lineage/packages" \
  >/tmp/terrascope-step36-package-history.json

python3 \
  - "${PACKAGE_ID}" \
  "${PACKAGE_HASH}" \
<<'PY'
import json
import sys

package_id = sys.argv[1]
package_hash = sys.argv[2]

payload = json.load(
    open(
        "/tmp/terrascope-step36-package-history.json",
        encoding="utf-8",
    )
)

packages = payload["packages"]

assert len(packages) == 1

assert packages[0]["id"] == package_id
assert packages[0]["packageHash"] == package_hash

assert packages[0]["transitionCount"] == 2
assert packages[0]["trustAnchorCount"] == 3

print(
    "[PASS] PORTABLE_LINEAGE_PACKAGE_HISTORY"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

PACKAGE_COLUMNS="$(
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
            'resilience_governance_trust_lineage_packages';
      " \
    | tr -d '[:space:]'
)"

PACKAGE_ROWS="$(
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

PACKAGE_HASH_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname=
            'uq_governance_trust_lineage_package_hash';
      " \
    | tr -d '[:space:]'
)"

SOURCE_FK_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid=
            'resilience_governance_trust_lineage_packages'::regclass
        AND contype='f';
      " \
    | tr -d '[:space:]'
)"

echo "TRUST_LINEAGE_PACKAGE_COLUMN_COUNT=${PACKAGE_COLUMNS}"
echo "TRUST_LINEAGE_PACKAGE_ROW_COUNT=${PACKAGE_ROWS}"
echo "TRUST_LINEAGE_PACKAGE_HASH_UNIQUE_COUNT=${PACKAGE_HASH_UNIQUE}"
echo "TRUST_LINEAGE_PACKAGE_SOURCE_FK_COUNT=${SOURCE_FK_COUNT}"

[[ "${PACKAGE_COLUMNS}" == "11" ]]
[[ "${PACKAGE_ROWS}" == "1" ]]
[[ "${PACKAGE_HASH_UNIQUE}" == "1" ]]
[[ "${SOURCE_FK_COUNT}" == "0" ]]

echo "[PASS] PORTABLE_LINEAGE_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 36 PACKAGE FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_trust_lineage_packages
      WHERE id =
            '${PACKAGE_ID}'::uuid;
    " \
  >/dev/null

FINAL_PACKAGE_COUNT="$(
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

echo "FINAL_STEP36_PACKAGE_COUNT=${FINAL_PACKAGE_COUNT}"

[[ "${FINAL_PACKAGE_COUNT}" == "0" ]]

echo "[PASS] STEP36_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 36 VERIFICATION PASSED"
echo "========================================================================"
