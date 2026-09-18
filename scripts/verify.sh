#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

if [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(
    /usr/libexec/java_home
  )"

  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step47-backend.log"

WORK="/tmp/terrascope-step47"

OLD_DIR="${WORK}/old"
CURRENT_DIR="${WORK}/current"
TOOLS_DIR="${WORK}/tools"

ARTIFACT_NAME="terrascope-checkpoint-bundle-verifier.jar"

OLD_ARTIFACT="${OLD_DIR}/${ARTIFACT_NAME}"
CURRENT_ARTIFACT="${CURRENT_DIR}/${ARTIFACT_NAME}"

OLD_MANIFEST="${OLD_DIR}/release.manifest"
CURRENT_MANIFEST="${CURRENT_DIR}/release.manifest"

ROOT_PRIVATE="${WORK}/release-root-private.b64"
ROOT_PUBLIC="${WORK}/release-root-public.b64"
ROOT_PIN="${WORK}/release-root.sha256"

SUCCESSOR_PRIVATE="${WORK}/release-successor-private.b64"
SUCCESSOR_PUBLIC="${WORK}/release-successor-public.b64"
SUCCESSOR_DIRECT_PIN="${WORK}/release-successor-direct.sha256"

TRANSPARENCY_PRIVATE="${WORK}/transparency-private.b64"
TRANSPARENCY_PUBLIC="${WORK}/transparency-public.b64"
TRANSPARENCY_PIN="${WORK}/transparency.sha256"

DERIVED_TERMINAL_PIN="${WORK}/release-terminal-derived.sha256"
DERIVED_TERMINAL_PIN_OFFLINE="${WORK}/release-terminal-derived-offline.sha256"

WRONG_ROOT_PIN="${WORK}/wrong-root.sha256"

LINEAGE="${WORK}/release-authority.lineage"
TAMPERED_LINEAGE="${WORK}/release-authority-tampered.lineage"
SIGNATURE_TAMPERED_LINEAGE="${WORK}/release-authority-signature-tampered.lineage"
TRUNCATED_LINEAGE="${WORK}/release-authority-truncated.lineage"

TRANSPARENCY_LEDGER="${WORK}/release-transparency.log"
TRANSPARENCY_HEAD_PIN="${WORK}/transparency-head.sha256"

SIGNER_JAR="${TOOLS_DIR}/terrascope-verifier-release-signer.jar"
RELEASE_VERIFIER_JAR="${TOOLS_DIR}/terrascope-verifier-release-verifier.jar"
TRANSPARENCY_JAR="${TOOLS_DIR}/terrascope-verifier-release-transparency.jar"
AUTHORITY_JAR="${TOOLS_DIR}/terrascope-verifier-release-authority-lineage.jar"

KEYGEN_SOURCE="${WORK}/TerraScopeStep47Keygen.java"

ROOT_KEY_ID="terrascope-release-authority-v1"
SUCCESSOR_KEY_ID="terrascope-release-authority-v2"

TRANSPARENCY_KEY_ID="terrascope-transparency-step47-v1"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

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

stop_backend() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true

    BACKEND_PID=""
  fi

  sleep 1
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 47 VERIFICATION"
echo "========================================================================"

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

echo
echo "===== STATUS + FLYWAY ====="

rm -f "${BACKEND_LOG}"

(
  cd apps/backend

  mvn spring-boot:run
) >> "${BACKEND_LOG}" 2>&1 &

BACKEND_PID=$!

wait_backend

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  > /tmp/terrascope-step47-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step47-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.47.0"

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

  [[ "${COUNT}" == "1" ]]
done

for VERSION in 44 45 46 47; do
  COUNT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version='${VERSION}';
        " \
      | tr -d '[:space:]'
  )"

  echo "FLYWAY_V${VERSION}_COUNT=${COUNT}"

  [[ "${COUNT}" == "0" ]]
done

echo "[PASS] FLYWAY_SCHEMA_UNCHANGED"

echo
echo "===== CLEAN FIXTURE ====="

rm -rf "${WORK}"

mkdir -p \
  "${OLD_DIR}" \
  "${CURRENT_DIR}" \
  "${TOOLS_DIR}"

echo "[PASS] FIXTURE_READY"

echo
echo "===== BUILD RELEASE TOOLS ====="

bash \
  tools/verifier-release-integrity/build.sh \
  "${TOOLS_DIR}"

[[ -s "${SIGNER_JAR}" ]]
[[ -s "${RELEASE_VERIFIER_JAR}" ]]
[[ -s "${TRANSPARENCY_JAR}" ]]
[[ -s "${AUTHORITY_JAR}" ]]

echo "[PASS] FOUR_RELEASE_TOOLS_READY"

echo
echo "===== BUILD OLD + CURRENT ARTIFACT FIXTURES ====="

bash \
  tools/checkpoint-bundle-verifier/build.sh \
  "${CURRENT_ARTIFACT}"

cp \
  "${CURRENT_ARTIFACT}" \
  "${OLD_ARTIFACT}"

printf '\x00' \
  >> "${OLD_ARTIFACT}"

CURRENT_ARTIFACT_SHA="$(
  shasum -a 256 \
    "${CURRENT_ARTIFACT}" \
    | awk '{print $1}'
)"

OLD_ARTIFACT_SHA="$(
  shasum -a 256 \
    "${OLD_ARTIFACT}" \
    | awk '{print $1}'
)"

echo "CURRENT_ARTIFACT_SHA256=${CURRENT_ARTIFACT_SHA}"
echo "OLD_ARTIFACT_SHA256=${OLD_ARTIFACT_SHA}"

[[ "${CURRENT_ARTIFACT_SHA}" != "${OLD_ARTIFACT_SHA}" ]]

echo "[PASS] DISTINCT_RELEASE_ARTIFACTS"

echo
echo "===== GENERATE ROOT, SUCCESSOR + TRANSPARENCY KEYS ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;

import java.util.Base64;
import java.util.HexFormat;

public class TerraScopeStep47Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        if (
                args.length != 9
        ) {
            throw new IllegalArgumentException(
                    "Expected three output triplets."
            );
        }

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        for (
                int index = 0;
                index < 3;
                index++
        ) {
            KeyPair pair =
                    generator.generateKeyPair();

            byte[] publicBytes =
                    pair
                            .getPublic()
                            .getEncoded();

            String fingerprint =
                    HexFormat
                            .of()
                            .formatHex(
                                    digest.digest(
                                            publicBytes
                                    )
                            );

            Files.writeString(
                    Path.of(
                            args[index * 3]
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
                            args[index * 3 + 1]
                    ),

                    Base64
                            .getEncoder()
                            .encodeToString(
                                    publicBytes
                            )
            );

            Files.writeString(
                    Path.of(
                            args[index * 3 + 2]
                    ),

                    fingerprint
                    + "\n"
            );
        }
    }
}
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${ROOT_PRIVATE}" \
  "${ROOT_PUBLIC}" \
  "${ROOT_PIN}" \
  "${SUCCESSOR_PRIVATE}" \
  "${SUCCESSOR_PUBLIC}" \
  "${SUCCESSOR_DIRECT_PIN}" \
  "${TRANSPARENCY_PRIVATE}" \
  "${TRANSPARENCY_PUBLIC}" \
  "${TRANSPARENCY_PIN}"

ROOT_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${ROOT_PIN}"
)"

SUCCESSOR_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${SUCCESSOR_DIRECT_PIN}"
)"

TRANSPARENCY_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${TRANSPARENCY_PIN}"
)"

echo "ROOT_RELEASE_KEY_FINGERPRINT=${ROOT_FINGERPRINT}"
echo "SUCCESSOR_RELEASE_KEY_FINGERPRINT=${SUCCESSOR_FINGERPRINT}"
echo "TRANSPARENCY_KEY_FINGERPRINT=${TRANSPARENCY_FINGERPRINT}"

[[ "${ROOT_FINGERPRINT}" != "${SUCCESSOR_FINGERPRINT}" ]]

echo "[PASS] THREE_KEY_AUTHORITIES_READY"

echo
echo "===== SIGN OLD RELEASE WITH ROOT KEY ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --private-key "${ROOT_PRIVATE}" \
  --public-key "${ROOT_PUBLIC}" \
  --release-key-id "${ROOT_KEY_ID}" \
  --output "${OLD_MANIFEST}" \
  > "${WORK}/old-sign.log"

grep \
  -q \
  '^SIGNATURE_SELF_VERIFICATION=PASS$' \
  "${WORK}/old-sign.log"

echo "[PASS] ROOT_SIGNED_OLD_RELEASE"

echo
echo "===== SIGN CURRENT RELEASE WITH SUCCESSOR KEY ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --private-key "${SUCCESSOR_PRIVATE}" \
  --public-key "${SUCCESSOR_PUBLIC}" \
  --release-key-id "${SUCCESSOR_KEY_ID}" \
  --output "${CURRENT_MANIFEST}" \
  > "${WORK}/current-sign.log"

grep \
  -q \
  '^SIGNATURE_SELF_VERIFICATION=PASS$' \
  "${WORK}/current-sign.log"

echo "[PASS] SUCCESSOR_SIGNED_CURRENT_RELEASE"

echo
echo "===== CREATE ROOT -> SUCCESSOR AUTHORITY TRANSITION ====="

java \
  -jar "${AUTHORITY_JAR}" \
  append \
  --lineage "${LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --outgoing-key-id "${ROOT_KEY_ID}" \
  --outgoing-private-key "${ROOT_PRIVATE}" \
  --outgoing-public-key "${ROOT_PUBLIC}" \
  --replacement-key-id "${SUCCESSOR_KEY_ID}" \
  --replacement-public-key "${SUCCESSOR_PUBLIC}" \
  | tee "${WORK}/authority-append.log"

grep \
  -q \
  '^SEQUENCE=1$' \
  "${WORK}/authority-append.log"

grep \
  -q \
  '^PREVIOUS_TRANSITION_HASH=GENESIS$' \
  "${WORK}/authority-append.log"

grep \
  -q \
  '^APPEND_RESULT=PASS$' \
  "${WORK}/authority-append.log"

echo "[PASS] RELEASE_AUTHORITY_ROTATION_CREATED"

echo
echo "===== VERIFY LINEAGE + DERIVE CURRENT RELEASE PIN ====="

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --terminal-pin-output "${DERIVED_TERMINAL_PIN}" \
  | tee /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^LINEAGE_VALID=true$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^TRANSITION_COUNT=1$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^VERIFIED_TRANSITION_COUNT=1$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  "^TERMINAL_KEY_ID=${SUCCESSOR_KEY_ID}$" \
  /tmp/terrascope-step47-lineage-valid.log

DERIVED_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${DERIVED_TERMINAL_PIN}"
)"

echo "DERIVED_TERMINAL_FINGERPRINT=${DERIVED_FINGERPRINT}"

[[ "${DERIVED_FINGERPRINT}" == "${SUCCESSOR_FINGERPRINT}" ]]

echo "[PASS] TERMINAL_RELEASE_PIN_DERIVED_FROM_ROOT_LINEAGE"

echo
echo "===== VERIFY BOTH SIGNED RELEASES BEFORE TRANSPARENCY ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${ROOT_PIN}" \
  > "${WORK}/old-release-precheck.log"

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${DERIVED_TERMINAL_PIN}" \
  > "${WORK}/current-release-precheck.log"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${WORK}/old-release-precheck.log"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${WORK}/current-release-precheck.log"

echo "[PASS] BOTH_ROTATION_RELEASES_CRYPTOGRAPHICALLY_VALID"

echo
echo "===== CREATE STEP46 TRANSPARENCY HISTORY ACROSS ROTATION ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${ROOT_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/transparency-old.log"

FIRST_HEAD="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/transparency-old.log" \
    | cut -d= -f2-
)"

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${DERIVED_TERMINAL_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/transparency-current.log"

CURRENT_HEAD="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/transparency-current.log" \
    | cut -d= -f2-
)"

echo "FIRST_TRANSPARENCY_HEAD=${FIRST_HEAD}"
echo "CURRENT_TRANSPARENCY_HEAD=${CURRENT_HEAD}"

[[ "${FIRST_HEAD}" != "${CURRENT_HEAD}" ]]

printf '%s\n' \
  "${CURRENT_HEAD}" \
  > "${TRANSPARENCY_HEAD_PIN}"

echo "[PASS] RELEASE_ROTATION_TRANSPARENCY_HISTORY_READY"

echo
echo "===== LINEAGE CANONICAL CONTRACT ====="

python3 \
  - \
  "${LINEAGE}" \
  "${ROOT_FINGERPRINT}" \
  "${SUCCESSOR_FINGERPRINT}" \
<<'PY'
from pathlib import Path
import re
import sys

lineage = Path(
    sys.argv[1]
).read_text(
    encoding="utf-8",
)

root = sys.argv[2]
successor = sys.argv[3]

lines = lineage.strip().splitlines()

prefixes = [
    "lineageVersion=",
    "sequence=",
    "outgoingKeyId=",
    "outgoingPublicKeyX509Base64=",
    "outgoingKeyFingerprint=",
    "replacementKeyId=",
    "replacementPublicKeyX509Base64=",
    "replacementKeyFingerprint=",
    "previousTransitionHash=",
    "signatureBase64=",
    "transitionHash=",
]

assert len(lines) == len(prefixes)

for line, prefix in zip(
    lines,
    prefixes,
):
    assert line.startswith(
        prefix
    )

assert lines[0] == (
    "lineageVersion="
    "TS-VERIFIER-RELEASE-AUTHORITY-LINEAGE-V1"
)

assert lines[1] == "sequence=1"

assert lines[4] == (
    "outgoingKeyFingerprint="
    + root
)

assert lines[7] == (
    "replacementKeyFingerprint="
    + successor
)

assert lines[8] == (
    "previousTransitionHash=GENESIS"
)

assert re.fullmatch(
    r"transitionHash=[0-9a-f]{64}",
    lines[10],
)

print(
    "[PASS] RELEASE_AUTHORITY_LINEAGE_CONTRACT"
)
PY

echo
echo "===== PREPARE NEGATIVE LINEAGE FIXTURES ====="

WRONG_ROOT="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_ROOT}" == "${ROOT_FINGERPRINT}" ]]; then
  WRONG_ROOT="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_ROOT}" \
  > "${WRONG_ROOT_PIN}"

python3 \
  - \
  "${LINEAGE}" \
  "${TAMPERED_LINEAGE}" \
  "${SIGNATURE_TAMPERED_LINEAGE}" \
  "${TRUNCATED_LINEAGE}" \
<<'PY'
from pathlib import Path
import base64
import sys

source = Path(
    sys.argv[1]
)

tampered = Path(
    sys.argv[2]
)

signature_tampered = Path(
    sys.argv[3]
)

truncated = Path(
    sys.argv[4]
)

lines = source.read_text(
    encoding="utf-8",
).splitlines()

replacement_lines = list(
    lines
)

for index, line in enumerate(
    replacement_lines
):
    if line.startswith(
        "replacementKeyFingerprint="
    ):
        replacement_lines[index] = (
            "replacementKeyFingerprint="
            + (
                "0" * 64
            )
        )

        break
else:
    raise AssertionError(
        "replacementKeyFingerprint not found"
    )

tampered.write_text(
    "\n".join(
        replacement_lines
    )
    + "\n\n",
    encoding="utf-8",
)

signature_lines = list(
    lines
)

for index, line in enumerate(
    signature_lines
):
    if line.startswith(
        "signatureBase64="
    ):
        signature_lines[index] = (
            "signatureBase64="
            + base64.b64encode(
                bytes(64)
            ).decode()
        )

        break
else:
    raise AssertionError(
        "signatureBase64 not found"
    )

signature_tampered.write_text(
    "\n".join(
        signature_lines
    )
    + "\n\n",
    encoding="utf-8",
)

truncated.write_text(
    "",
    encoding="utf-8",
)

print(
    "[PASS] NEGATIVE_LINEAGE_FIXTURES_READY"
)
PY

echo
echo "===== DESTROY ALL PRIVATE KEYS ====="

rm -f \
  "${ROOT_PRIVATE}" \
  "${SUCCESSOR_PRIVATE}" \
  "${TRANSPARENCY_PRIVATE}"

rm -f \
  "${SUCCESSOR_DIRECT_PIN}"

[[ ! -e "${ROOT_PRIVATE}" ]]
[[ ! -e "${SUCCESSOR_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]
[[ ! -e "${SUCCESSOR_DIRECT_PIN}" ]]

echo "[PASS] PRIVATE_KEYS_DESTROYED_BEFORE_VERIFICATION"
echo "[PASS] DIRECT_SUCCESSOR_PIN_REMOVED_BEFORE_VERIFICATION"

echo
echo "===== STOP TERRASCOPE + POSTGRES ====="

stop_backend

docker compose stop postgres

RUNNING_POSTGRES="$(
  docker compose ps \
    --status running \
    -q postgres \
    | awk 'END { print NR + 0 }'
)"

echo "RUNNING_POSTGRES_COUNT=${RUNNING_POSTGRES}"

[[ "${RUNNING_POSTGRES}" == "0" ]]

if curl \
    --silent \
    --max-time 1 \
    "http://localhost:8087/actuator/health" \
    >/dev/null 2>&1; then

  echo "[FAIL] TerraScope backend remains reachable."
  exit 1
fi

echo "[PASS] TERRASCOPE_BACKEND_OFFLINE"
echo "[PASS] POSTGRES_OFFLINE"

echo
echo "===== RE-DERIVE SUCCESSOR PIN ENTIRELY OFFLINE ====="

rm -f "${DERIVED_TERMINAL_PIN_OFFLINE}"

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --terminal-pin-output "${DERIVED_TERMINAL_PIN_OFFLINE}" \
  | tee /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^LINEAGE_VALID=true$' \
  /tmp/terrascope-step47-lineage-valid.log

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step47-lineage-valid.log

OFFLINE_TERMINAL_PIN="$(
  tr -d '[:space:]' \
    < "${DERIVED_TERMINAL_PIN_OFFLINE}"
)"

echo "OFFLINE_DERIVED_TERMINAL_PIN=${OFFLINE_TERMINAL_PIN}"

[[ "${OFFLINE_TERMINAL_PIN}" == "${DERIVED_FINGERPRINT}" ]]

echo "[PASS] OFFLINE_ROOT_TO_SUCCESSOR_DERIVATION"

echo
echo "===== CURRENT RELEASE UNDER DERIVED SUCCESSOR AUTHORITY ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${DERIVED_TERMINAL_PIN_OFFLINE}" \
  | tee /tmp/terrascope-step47-current-release.log

grep \
  -q \
  '^MANIFEST_SHAPE_VALID=true$' \
  /tmp/terrascope-step47-current-release.log

grep \
  -q \
  '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step47-current-release.log

grep \
  -q \
  '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step47-current-release.log

grep \
  -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step47-current-release.log

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step47-current-release.log

echo "[PASS] SUCCESSOR_RELEASE_ACCEPTED_FROM_ROOT_LINEAGE"

echo
echo "===== PROVE OLD RELEASE REMAINS CRYPTOGRAPHICALLY AUTHENTIC ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${ROOT_PIN}" \
  | tee /tmp/terrascope-step47-old-root-release.log

grep \
  -q \
  '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step47-old-root-release.log

grep \
  -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step47-old-root-release.log

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step47-old-root-release.log

echo "[PASS] OLD_ROOT_RELEASE_STILL_AUTHENTIC"

echo
echo "===== REJECT OLD RELEASE UNDER CURRENT AUTHORITY ====="

set +e

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${DERIVED_TERMINAL_PIN_OFFLINE}" \
  > /tmp/terrascope-step47-old-current-authority.log \
  2>&1

OLD_CURRENT_STATUS=$?

set -e

cat /tmp/terrascope-step47-old-current-authority.log

echo "OLD_RELEASE_CURRENT_AUTHORITY_EXIT=${OLD_CURRENT_STATUS}"

[[ "${OLD_CURRENT_STATUS}" == "2" ]]

grep \
  -q \
  '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step47-old-current-authority.log

grep \
  -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step47-old-current-authority.log

grep \
  -q \
  '^RELEASE_KEY_PIN_VALID=false$' \
  /tmp/terrascope-step47-old-current-authority.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step47-old-current-authority.log

echo "[PASS] OLD_RELEASE_REJECTED_AFTER_AUTHORITY_ROTATION"

echo
echo "===== CURRENT RELEASE + TRANSPARENCY HEAD ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${DERIVED_TERMINAL_PIN_OFFLINE}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --head-pin "${TRANSPARENCY_HEAD_PIN}" \
  | tee /tmp/terrascope-step47-current-transparency.log

grep \
  -q \
  '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step47-current-transparency.log

grep \
  -q \
  '^TRANSPARENCY_LEDGER_VALID=true$' \
  /tmp/terrascope-step47-current-transparency.log

grep \
  -q \
  '^TRANSPARENCY_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step47-current-transparency.log

grep \
  -q \
  '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step47-current-transparency.log

grep \
  -q \
  '^LATEST_RELEASE_BINDING_VALID=true$' \
  /tmp/terrascope-step47-current-transparency.log

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step47-current-transparency.log

echo "[PASS] ROTATED_AUTHORITY_INTEGRATED_WITH_TRANSPARENCY"

echo
echo "===== WRONG ROOT PIN ====="

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${LINEAGE}" \
  --root-pin "${WRONG_ROOT_PIN}" \
  > /tmp/terrascope-step47-wrong-root.log \
  2>&1

WRONG_ROOT_STATUS=$?

set -e

cat /tmp/terrascope-step47-wrong-root.log

[[ "${WRONG_ROOT_STATUS}" == "2" ]]

grep \
  -q \
  '^ROOT_PIN_VALID=false$' \
  /tmp/terrascope-step47-wrong-root.log

grep \
  -q \
  '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step47-wrong-root.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step47-wrong-root.log

echo "[PASS] WRONG_RELEASE_ROOT_PIN_REJECTION"

echo
echo "===== LINEAGE FIELD TAMPER ====="

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${TAMPERED_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  > /tmp/terrascope-step47-lineage-tamper.log \
  2>&1

LINEAGE_TAMPER_STATUS=$?

set -e

cat /tmp/terrascope-step47-lineage-tamper.log

[[ "${LINEAGE_TAMPER_STATUS}" == "2" ]]

grep \
  -q \
  '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step47-lineage-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step47-lineage-tamper.log

echo "[PASS] RELEASE_AUTHORITY_LINEAGE_TAMPER_DETECTION"

echo
echo "===== TRANSITION SIGNATURE TAMPER ====="

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${SIGNATURE_TAMPERED_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  > /tmp/terrascope-step47-signature-tamper.log \
  2>&1

SIGNATURE_TAMPER_STATUS=$?

set -e

cat /tmp/terrascope-step47-signature-tamper.log

[[ "${SIGNATURE_TAMPER_STATUS}" == "2" ]]

grep \
  -q \
  '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step47-signature-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step47-signature-tamper.log

echo "[PASS] RELEASE_AUTHORITY_SIGNATURE_TAMPER_DETECTION"

echo
echo "===== LINEAGE TRUNCATION ====="

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${TRUNCATED_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  > /tmp/terrascope-step47-truncated-lineage.log \
  2>&1

TRUNCATED_STATUS=$?

set -e

cat /tmp/terrascope-step47-truncated-lineage.log

[[ "${TRUNCATED_STATUS}" == "2" ]]

grep \
  -q \
  '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step47-truncated-lineage.log

grep \
  -q \
  '^TRANSITION_COUNT=0$' \
  /tmp/terrascope-step47-truncated-lineage.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step47-truncated-lineage.log

echo "[PASS] RELEASE_AUTHORITY_LINEAGE_TRUNCATION_REJECTION"

echo
echo "===== TRUE AIR-GAP AUTHORITY PROOF ====="

RUNNING_POSTGRES_AFTER="$(
  docker compose ps \
    --status running \
    -q postgres \
    | awk 'END { print NR + 0 }'
)"

echo "RUNNING_POSTGRES_COUNT_AFTER_TESTS=${RUNNING_POSTGRES_AFTER}"

[[ "${RUNNING_POSTGRES_AFTER}" == "0" ]]

if curl \
    --silent \
    --max-time 1 \
    "http://localhost:8087/actuator/health" \
    >/dev/null 2>&1; then

  echo "[FAIL] TerraScope backend became reachable during Step 47 verification."
  exit 1
fi

[[ ! -e "${ROOT_PRIVATE}" ]]
[[ ! -e "${SUCCESSOR_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]
[[ ! -e "${SUCCESSOR_DIRECT_PIN}" ]]

echo "[PASS] TRUE_OFFLINE_RELEASE_AUTHORITY_VERIFICATION"
echo "[PASS] PRIVATE_KEYS_REMAINED_ABSENT"
echo "[PASS] SUCCESSOR_PIN_WAS_DERIVED_NOT_PREPINNED"

echo
echo "===== RESTORE POSTGRES ====="

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

echo "[PASS] FLYWAY_V1_TO_V43_PRESERVED"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 47 VERIFICATION PASSED"
echo "========================================================================"
