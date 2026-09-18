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

BACKEND_LOG="/tmp/terrascope-step48-backend.log"

WORK="/tmp/terrascope-step48"

TOOLS="${WORK}/tools"

OLD_DIR="${WORK}/old"
CURRENT_DIR="${WORK}/current"

ARTIFACT_NAME="terrascope-checkpoint-bundle-verifier.jar"

OLD_ARTIFACT="${OLD_DIR}/${ARTIFACT_NAME}"
CURRENT_ARTIFACT="${CURRENT_DIR}/${ARTIFACT_NAME}"

OLD_MANIFEST="${OLD_DIR}/release.manifest"
CURRENT_MANIFEST="${CURRENT_DIR}/release.manifest"

ROOT_PRIVATE="${WORK}/root-private.b64"
ROOT_PUBLIC="${WORK}/root-public.b64"
ROOT_PIN="${WORK}/root.sha256"

INTERMEDIATE_PRIVATE="${WORK}/intermediate-private.b64"
INTERMEDIATE_PUBLIC="${WORK}/intermediate-public.b64"
INTERMEDIATE_DIRECT_PIN="${WORK}/intermediate-direct.sha256"

TERMINAL_PRIVATE="${WORK}/terminal-private.b64"
TERMINAL_PUBLIC="${WORK}/terminal-public.b64"
TERMINAL_DIRECT_PIN="${WORK}/terminal-direct.sha256"

TRANSPARENCY_PRIVATE="${WORK}/transparency-private.b64"
TRANSPARENCY_PUBLIC="${WORK}/transparency-public.b64"
TRANSPARENCY_PIN="${WORK}/transparency.sha256"

FULL_LINEAGE="${WORK}/release-authority.lineage"
STALE_LINEAGE="${WORK}/release-authority-stale.lineage"

STALE_HEAD_PIN="${WORK}/stale-lineage-head.sha256"
CURRENT_HEAD_PIN="${WORK}/current-lineage-head.sha256"
WRONG_HEAD_PIN="${WORK}/wrong-lineage-head.sha256"
WRONG_ROOT_PIN="${WORK}/wrong-root.sha256"

STALE_DERIVED_PIN="${WORK}/stale-derived-release.sha256"
CURRENT_DERIVED_PIN="${WORK}/current-derived-release.sha256"
OFFLINE_CURRENT_DERIVED_PIN="${WORK}/offline-current-derived-release.sha256"

SHOULD_NOT_EXIST_PIN="${WORK}/should-not-exist.sha256"

SIGNATURE_TAMPER_LINEAGE="${WORK}/signature-tampered.lineage"

TRANSPARENCY_LEDGER="${WORK}/release-transparency.log"
TRANSPARENCY_HEAD_PIN="${WORK}/transparency-head.sha256"

SIGNER_JAR="${TOOLS}/terrascope-verifier-release-signer.jar"
RELEASE_VERIFIER_JAR="${TOOLS}/terrascope-verifier-release-verifier.jar"
TRANSPARENCY_JAR="${TOOLS}/terrascope-verifier-release-transparency.jar"
AUTHORITY_JAR="${TOOLS}/terrascope-verifier-release-authority-lineage.jar"

KEYGEN_SOURCE="${WORK}/TerraScopeStep48Keygen.java"

ROOT_KEY_ID="terrascope-release-authority-v1"
INTERMEDIATE_KEY_ID="terrascope-release-authority-v2"
TERMINAL_KEY_ID="terrascope-release-authority-v3"

TRANSPARENCY_KEY_ID="terrascope-transparency-step48-v1"

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
echo " TERRASCOPE — STEP 48 VERIFICATION"
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
  > /tmp/terrascope-step48-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step48-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.48.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 43)
do
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

for VERSION in 44 45 46 47 48
do
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
  "${TOOLS}" \
  "${OLD_DIR}" \
  "${CURRENT_DIR}"

echo "[PASS] FIXTURE_READY"

echo
echo "===== BUILD FOUR RELEASE TOOLS ====="

bash \
  tools/verifier-release-integrity/build.sh \
  "${TOOLS}"

[[ -s "${SIGNER_JAR}" ]]
[[ -s "${RELEASE_VERIFIER_JAR}" ]]
[[ -s "${TRANSPARENCY_JAR}" ]]
[[ -s "${AUTHORITY_JAR}" ]]

echo "[PASS] FOUR_RELEASE_TOOLS_READY"

echo
echo "===== AUTHORITY TOOL VERSION ====="

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage /does/not/exist \
  --root-pin /does/not/exist \
  --head-pin /does/not/exist \
  >/tmp/terrascope-step48-version-probe.log \
  2>&1 \
  || true

grep \
  -q \
  '^AUTHORITY_TOOL_VERSION=TS-VERIFIER-RELEASE-AUTHORITY-LINEAGE-TOOL-V2$' \
  /tmp/terrascope-step48-version-probe.log

echo "[PASS] AUTHORITY_TOOL_V2_RUNTIME"

echo
echo "===== BUILD CURRENT + OLD ARTIFACTS ====="

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
echo "===== GENERATE FOUR KEY AUTHORITIES ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;

import java.util.Base64;
import java.util.HexFormat;

public class TerraScopeStep48Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        if (
                args.length != 12
        ) {
            throw new IllegalArgumentException(
                    "Expected four private/public/fingerprint triplets."
            );
        }

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        for (
                int index = 0;
                index < 4;
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
                                    MessageDigest
                                            .getInstance(
                                                    "SHA-256"
                                            )
                                            .digest(
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
  "${INTERMEDIATE_PRIVATE}" \
  "${INTERMEDIATE_PUBLIC}" \
  "${INTERMEDIATE_DIRECT_PIN}" \
  "${TERMINAL_PRIVATE}" \
  "${TERMINAL_PUBLIC}" \
  "${TERMINAL_DIRECT_PIN}" \
  "${TRANSPARENCY_PRIVATE}" \
  "${TRANSPARENCY_PUBLIC}" \
  "${TRANSPARENCY_PIN}"

ROOT_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${ROOT_PIN}"
)"

INTERMEDIATE_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${INTERMEDIATE_DIRECT_PIN}"
)"

TERMINAL_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${TERMINAL_DIRECT_PIN}"
)"

echo "ROOT_FINGERPRINT=${ROOT_FINGERPRINT}"
echo "INTERMEDIATE_FINGERPRINT=${INTERMEDIATE_FINGERPRINT}"
echo "TERMINAL_FINGERPRINT=${TERMINAL_FINGERPRINT}"

[[ "${ROOT_FINGERPRINT}" != "${INTERMEDIATE_FINGERPRINT}" ]]
[[ "${INTERMEDIATE_FINGERPRINT}" != "${TERMINAL_FINGERPRINT}" ]]

echo "[PASS] ROOT_INTERMEDIATE_TERMINAL_KEYS_READY"

echo
echo "===== TRANSITION 1: ROOT -> INTERMEDIATE ====="

java \
  -jar "${AUTHORITY_JAR}" \
  append \
  --lineage "${FULL_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --outgoing-key-id "${ROOT_KEY_ID}" \
  --outgoing-private-key "${ROOT_PRIVATE}" \
  --outgoing-public-key "${ROOT_PUBLIC}" \
  --replacement-key-id "${INTERMEDIATE_KEY_ID}" \
  --replacement-public-key "${INTERMEDIATE_PUBLIC}" \
  | tee "${WORK}/transition-1.log"

grep \
  -q \
  '^SEQUENCE=1$' \
  "${WORK}/transition-1.log"

HEAD_ONE="$(
  grep \
    '^TRANSITION_HASH=' \
    "${WORK}/transition-1.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${HEAD_ONE}" \
  > "${STALE_HEAD_PIN}"

echo "STALE_AUTHORITY_HEAD=${HEAD_ONE}"

echo "[PASS] ROOT_TO_INTERMEDIATE_TRANSITION"

echo
echo "===== TRANSITION 2: INTERMEDIATE -> TERMINAL ====="

java \
  -jar "${AUTHORITY_JAR}" \
  append \
  --lineage "${FULL_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --outgoing-key-id "${INTERMEDIATE_KEY_ID}" \
  --outgoing-private-key "${INTERMEDIATE_PRIVATE}" \
  --outgoing-public-key "${INTERMEDIATE_PUBLIC}" \
  --replacement-key-id "${TERMINAL_KEY_ID}" \
  --replacement-public-key "${TERMINAL_PUBLIC}" \
  | tee "${WORK}/transition-2.log"

grep \
  -q \
  '^SEQUENCE=2$' \
  "${WORK}/transition-2.log"

grep \
  -q \
  "^PREVIOUS_TRANSITION_HASH=${HEAD_ONE}$" \
  "${WORK}/transition-2.log"

HEAD_TWO="$(
  grep \
    '^TRANSITION_HASH=' \
    "${WORK}/transition-2.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${HEAD_TWO}" \
  > "${CURRENT_HEAD_PIN}"

echo "CURRENT_AUTHORITY_HEAD=${HEAD_TWO}"

[[ "${HEAD_ONE}" != "${HEAD_TWO}" ]]

echo "[PASS] INTERMEDIATE_TO_TERMINAL_TRANSITION"

echo
echo "===== CREATE AUTHENTIC STALE PREFIX ====="

python3 \
  - \
  "${FULL_LINEAGE}" \
  "${STALE_LINEAGE}" \
<<'PY'
from pathlib import Path
import sys

source = Path(
    sys.argv[1]
).read_text(
    encoding="utf-8",
)

blocks = [
    block
    for block in source.strip().split(
        "\n\n"
    )
    if block.strip()
]

assert len(blocks) == 2

Path(
    sys.argv[2]
).write_text(
    blocks[0]
    + "\n\n",
    encoding="utf-8",
)

print(
    "[PASS] AUTHENTIC_STALE_PREFIX_CREATED"
)
PY

echo
echo "===== VERIFY HISTORICAL PREFIX AGAINST ITS HISTORICAL HEAD ====="

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${STALE_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${STALE_HEAD_PIN}" \
  --terminal-pin-output "${STALE_DERIVED_PIN}" \
  | tee /tmp/terrascope-step48-stale-historical.log

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q '^LINEAGE_VALID=true$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q '^TRANSITION_COUNT=1$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q "^TERMINAL_KEY_ID=${INTERMEDIATE_KEY_ID}$" \
  /tmp/terrascope-step48-stale-historical.log

STALE_DERIVED="$(
  tr -d '[:space:]' \
    < "${STALE_DERIVED_PIN}"
)"

[[ "${STALE_DERIVED}" == "${INTERMEDIATE_FINGERPRINT}" ]]

echo "[PASS] STALE_PREFIX_IS_CRYPTOGRAPHICALLY_AUTHENTIC"

echo
echo "===== VERIFY FULL CURRENT LINEAGE ====="

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${FULL_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${CURRENT_HEAD_PIN}" \
  --terminal-pin-output "${CURRENT_DERIVED_PIN}" \
  | tee /tmp/terrascope-step48-full-current.log

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^LINEAGE_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^TRANSITION_COUNT=2$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^VERIFIED_TRANSITION_COUNT=2$' \
  /tmp/terrascope-step48-full-current.log

grep -q "^TERMINAL_KEY_ID=${TERMINAL_KEY_ID}$" \
  /tmp/terrascope-step48-full-current.log

CURRENT_DERIVED="$(
  tr -d '[:space:]' \
    < "${CURRENT_DERIVED_PIN}"
)"

[[ "${CURRENT_DERIVED}" == "${TERMINAL_FINGERPRINT}" ]]

echo "[PASS] CURRENT_TERMINAL_PIN_DERIVED_WITH_CURRENT_HEAD"

echo
echo "===== SIGN OLD RELEASE WITH INTERMEDIATE AUTHORITY ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --private-key "${INTERMEDIATE_PRIVATE}" \
  --public-key "${INTERMEDIATE_PUBLIC}" \
  --release-key-id "${INTERMEDIATE_KEY_ID}" \
  --output "${OLD_MANIFEST}" \
  > "${WORK}/old-sign.log"

grep \
  -q \
  '^SIGNATURE_SELF_VERIFICATION=PASS$' \
  "${WORK}/old-sign.log"

echo "[PASS] INTERMEDIATE_SIGNED_OLD_RELEASE"

echo
echo "===== SIGN CURRENT RELEASE WITH TERMINAL AUTHORITY ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --private-key "${TERMINAL_PRIVATE}" \
  --public-key "${TERMINAL_PUBLIC}" \
  --release-key-id "${TERMINAL_KEY_ID}" \
  --output "${CURRENT_MANIFEST}" \
  > "${WORK}/current-sign.log"

grep \
  -q \
  '^SIGNATURE_SELF_VERIFICATION=PASS$' \
  "${WORK}/current-sign.log"

echo "[PASS] TERMINAL_SIGNED_CURRENT_RELEASE"

echo
echo "===== VERIFY BOTH RELEASE FIXTURES ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${STALE_DERIVED_PIN}" \
  > "${WORK}/old-release-precheck.log"

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${CURRENT_DERIVED_PIN}" \
  > "${WORK}/current-release-precheck.log"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${WORK}/old-release-precheck.log"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${WORK}/current-release-precheck.log"

echo "[PASS] OLD_AND_CURRENT_RELEASES_AUTHENTIC"

echo
echo "===== CREATE RELEASE TRANSPARENCY HISTORY ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${STALE_DERIVED_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/transparency-old.log"

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${CURRENT_DERIVED_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/transparency-current.log"

TRANSPARENCY_HEAD="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/transparency-current.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${TRANSPARENCY_HEAD}" \
  > "${TRANSPARENCY_HEAD_PIN}"

echo "CURRENT_TRANSPARENCY_HEAD=${TRANSPARENCY_HEAD}"

echo "[PASS] TRANSPARENCY_HISTORY_READY"

echo
echo "===== PREPARE NEGATIVE PINS + SIGNATURE TAMPER ====="

WRONG_ROOT="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_ROOT}" == "${ROOT_FINGERPRINT}" ]]; then
  WRONG_ROOT="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_ROOT}" \
  > "${WRONG_ROOT_PIN}"

WRONG_HEAD="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_HEAD}" == "${HEAD_TWO}" ]]; then
  WRONG_HEAD="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_HEAD}" \
  > "${WRONG_HEAD_PIN}"

python3 \
  - \
  "${FULL_LINEAGE}" \
  "${SIGNATURE_TAMPER_LINEAGE}" \
<<'PY'
from pathlib import Path
import base64
import sys

blocks = [
    block.splitlines()
    for block in Path(
        sys.argv[1]
    ).read_text(
        encoding="utf-8",
    ).strip().split(
        "\n\n"
    )
    if block.strip()
]

assert len(blocks) == 2

for index, line in enumerate(
    blocks[1]
):
    if line.startswith(
        "signatureBase64="
    ):
        blocks[1][index] = (
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

Path(
    sys.argv[2]
).write_text(
    "\n\n".join(
        "\n".join(
            block
        )
        for block in blocks
    )
    + "\n\n",
    encoding="utf-8",
)

print(
    "[PASS] NEGATIVE_AUTHORITY_FIXTURES_READY"
)
PY

echo
echo "===== DESTROY PRIVATE KEYS + DIRECT SUCCESSOR PINS ====="

rm -f \
  "${ROOT_PRIVATE}" \
  "${INTERMEDIATE_PRIVATE}" \
  "${TERMINAL_PRIVATE}" \
  "${TRANSPARENCY_PRIVATE}" \
  "${INTERMEDIATE_DIRECT_PIN}" \
  "${TERMINAL_DIRECT_PIN}"

[[ ! -e "${ROOT_PRIVATE}" ]]
[[ ! -e "${INTERMEDIATE_PRIVATE}" ]]
[[ ! -e "${TERMINAL_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]

[[ ! -e "${INTERMEDIATE_DIRECT_PIN}" ]]
[[ ! -e "${TERMINAL_DIRECT_PIN}" ]]

echo "[PASS] PRIVATE_KEYS_DESTROYED"
echo "[PASS] DIRECT_SUCCESSOR_PINS_REMOVED"

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
echo "===== FULL CURRENT AUTHORITY OFFLINE ====="

rm -f "${OFFLINE_CURRENT_DERIVED_PIN}"

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${FULL_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${CURRENT_HEAD_PIN}" \
  --terminal-pin-output "${OFFLINE_CURRENT_DERIVED_PIN}" \
  | tee /tmp/terrascope-step48-full-current.log

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^LINEAGE_VALID=true$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^TRANSITION_COUNT=2$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^VERIFIED_TRANSITION_COUNT=2$' \
  /tmp/terrascope-step48-full-current.log

grep -q '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step48-full-current.log

OFFLINE_CURRENT_PIN="$(
  tr -d '[:space:]' \
    < "${OFFLINE_CURRENT_DERIVED_PIN}"
)"

[[ "${OFFLINE_CURRENT_PIN}" == "${CURRENT_DERIVED}" ]]

echo "[PASS] CURRENT_AUTHORITY_HEAD_ACCEPTED_OFFLINE"

echo
echo "===== STALE PREFIX WITH ITS OLD HEAD IS INTERNALLY VALID ====="

rm -f "${STALE_DERIVED_PIN}"

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${STALE_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${STALE_HEAD_PIN}" \
  --terminal-pin-output "${STALE_DERIVED_PIN}" \
  | tee /tmp/terrascope-step48-stale-historical.log

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q '^LINEAGE_VALID=true$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q '^TRANSITION_COUNT=1$' \
  /tmp/terrascope-step48-stale-historical.log

grep -q "^TERMINAL_KEY_ID=${INTERMEDIATE_KEY_ID}$" \
  /tmp/terrascope-step48-stale-historical.log

echo "[PASS] HISTORICAL_PREFIX_REMAINS_CRYPTOGRAPHICALLY_VALID"

echo
echo "===== OLD RELEASE VALID UNDER STALE DERIVED AUTHORITY ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${STALE_DERIVED_PIN}" \
  | tee /tmp/terrascope-step48-old-release.log

grep -q '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step48-old-release.log

grep -q '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step48-old-release.log

grep -q '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step48-old-release.log

grep -q '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step48-old-release.log

echo "[PASS] STALE_ROLLBACK_PACKAGE_IS_INTERNALLY_AUTHENTIC"

echo
echo "===== REJECT STALE PREFIX AGAINST CURRENT HEAD ====="

rm -f "${SHOULD_NOT_EXIST_PIN}"

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${STALE_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${CURRENT_HEAD_PIN}" \
  --terminal-pin-output "${SHOULD_NOT_EXIST_PIN}" \
  > /tmp/terrascope-step48-stale-current-head.log \
  2>&1

STALE_CURRENT_STATUS=$?

set -e

cat /tmp/terrascope-step48-stale-current-head.log

echo "STALE_PREFIX_CURRENT_HEAD_EXIT=${STALE_CURRENT_STATUS}"

[[ "${STALE_CURRENT_STATUS}" == "2" ]]

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step48-stale-current-head.log

grep -q '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step48-stale-current-head.log

grep -q '^HEAD_PIN_VALID=false$' \
  /tmp/terrascope-step48-stale-current-head.log

grep -q '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step48-stale-current-head.log

grep -q '^TRANSITION_COUNT=1$' \
  /tmp/terrascope-step48-stale-current-head.log

grep -q '^VERIFIED_TRANSITION_COUNT=1$' \
  /tmp/terrascope-step48-stale-current-head.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step48-stale-current-head.log

grep \
  -q \
  'cryptographically valid but its head does not match' \
  /tmp/terrascope-step48-stale-current-head.log

[[ ! -e "${SHOULD_NOT_EXIST_PIN}" ]]

echo "[PASS] AUTHORITY_STALE_PREFIX_ROLLBACK_REJECTION"
echo "[PASS] STALE_PREFIX_CANNOT_DERIVE_TRUSTED_TERMINAL_PIN"

echo
echo "===== REJECT CURRENT LINEAGE WITH STALE HEAD PIN ====="

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${FULL_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${STALE_HEAD_PIN}" \
  > /tmp/terrascope-step48-current-stale-head.log \
  2>&1

CURRENT_STALE_STATUS=$?

set -e

cat /tmp/terrascope-step48-current-stale-head.log

[[ "${CURRENT_STALE_STATUS}" == "2" ]]

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step48-current-stale-head.log

grep -q '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step48-current-stale-head.log

grep -q '^HEAD_PIN_VALID=false$' \
  /tmp/terrascope-step48-current-stale-head.log

grep -q '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step48-current-stale-head.log

echo "[PASS] WRONG_AUTHORITY_HEAD_PIN_REJECTION"

echo
echo "===== CURRENT RELEASE UNDER CURRENT DERIVED AUTHORITY ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${OFFLINE_CURRENT_DERIVED_PIN}" \
  | tee /tmp/terrascope-step48-current-release.log

grep -q '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step48-current-release.log

grep -q '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step48-current-release.log

grep -q '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step48-current-release.log

grep -q '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step48-current-release.log

echo "[PASS] CURRENT_RELEASE_ACCEPTED_FROM_HEAD_PINNED_AUTHORITY"

echo
echo "===== CURRENT RELEASE TRANSPARENCY ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${OFFLINE_CURRENT_DERIVED_PIN}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --head-pin "${TRANSPARENCY_HEAD_PIN}" \
  | tee /tmp/terrascope-step48-transparency.log

grep -q '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step48-transparency.log

grep -q '^TRANSPARENCY_LEDGER_VALID=true$' \
  /tmp/terrascope-step48-transparency.log

grep -q '^TRANSPARENCY_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step48-transparency.log

grep -q '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step48-transparency.log

grep -q '^LATEST_RELEASE_BINDING_VALID=true$' \
  /tmp/terrascope-step48-transparency.log

grep -q '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step48-transparency.log

echo "[PASS] CURRENT_AUTHORITY_AND_RELEASE_TRANSPARENCY_COMPOSE"

echo
echo "===== WRONG ROOT PIN ====="

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${FULL_LINEAGE}" \
  --root-pin "${WRONG_ROOT_PIN}" \
  --head-pin "${CURRENT_HEAD_PIN}" \
  > /tmp/terrascope-step48-wrong-root.log \
  2>&1

WRONG_ROOT_STATUS=$?

set -e

cat /tmp/terrascope-step48-wrong-root.log

[[ "${WRONG_ROOT_STATUS}" == "2" ]]

grep -q '^ROOT_PIN_VALID=false$' \
  /tmp/terrascope-step48-wrong-root.log

grep -q '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step48-wrong-root.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step48-wrong-root.log

echo "[PASS] WRONG_ROOT_PIN_REJECTION"

echo
echo "===== TRANSITION SIGNATURE TAMPER ====="

set +e

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${SIGNATURE_TAMPER_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${CURRENT_HEAD_PIN}" \
  > /tmp/terrascope-step48-signature-tamper.log \
  2>&1

SIGNATURE_TAMPER_STATUS=$?

set -e

cat /tmp/terrascope-step48-signature-tamper.log

[[ "${SIGNATURE_TAMPER_STATUS}" == "2" ]]

grep -q '^LINEAGE_VALID=false$' \
  /tmp/terrascope-step48-signature-tamper.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step48-signature-tamper.log

echo "[PASS] AUTHORITY_SIGNATURE_TAMPER_DETECTION"

echo
echo "===== TRUE AIR-GAP PROOF ====="

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

  echo "[FAIL] Backend became reachable during Step 48 verification."
  exit 1
fi

[[ ! -e "${ROOT_PRIVATE}" ]]
[[ ! -e "${INTERMEDIATE_PRIVATE}" ]]
[[ ! -e "${TERMINAL_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]

[[ ! -e "${INTERMEDIATE_DIRECT_PIN}" ]]
[[ ! -e "${TERMINAL_DIRECT_PIN}" ]]

echo "[PASS] TRUE_OFFLINE_AUTHORITY_HEAD_VERIFICATION"
echo "[PASS] PRIVATE_KEYS_REMAINED_ABSENT"
echo "[PASS] CURRENT_TERMINAL_PIN_WAS_DERIVED"

echo
echo "===== RESTORE POSTGRES ====="

docker compose start postgres

wait_postgres

for VERSION in $(seq 1 43)
do
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
echo " TERRASCOPE — STEP 48 VERIFICATION PASSED"
echo "========================================================================"
