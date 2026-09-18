#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step45-backend.log"

ARTIFACT="/tmp/terrascope-step45-checkpoint-verifier.jar"

SECOND_BUILD="/tmp/terrascope-step45-checkpoint-verifier-second.jar"

THIRD_BUILD="/tmp/terrascope-step45-checkpoint-verifier-third.jar"

RELEASE_TOOL_DIR="/tmp/terrascope-step45-release-tools"

RELEASE_SIGNER_JAR="${RELEASE_TOOL_DIR}/terrascope-verifier-release-signer.jar"

RELEASE_VERIFIER_JAR="${RELEASE_TOOL_DIR}/terrascope-verifier-release-verifier.jar"

KEYGEN_SOURCE="/tmp/TerraScopeStep45ReleaseKeygen.java"

PRIVATE_KEY="/tmp/terrascope-step45-release-private.b64"

PUBLIC_KEY="/tmp/terrascope-step45-release-public.b64"

RELEASE_PIN="/tmp/terrascope-step45-release-pin.sha256"

WRONG_RELEASE_PIN="/tmp/terrascope-step45-wrong-release-pin.sha256"

RELEASE_MANIFEST="/tmp/terrascope-step45-release.manifest"

TAMPER_ARTIFACT_DIR="/tmp/terrascope-step45-tampered-artifact"

TAMPER_ARTIFACT="${TAMPER_ARTIFACT_DIR}/terrascope-step45-checkpoint-verifier.jar"

MANIFEST_TAMPER="/tmp/terrascope-step45-release-manifest-tampered.manifest"

SIGNATURE_TAMPER="/tmp/terrascope-step45-release-signature-tampered.manifest"

RELEASE_KEY_ID="terrascope-release-step45-ed25519-v1"

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
echo " TERRASCOPE — STEP 45 VERIFICATION"
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
echo "===== SYSTEM VERSION + FLYWAY ====="

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
  >/tmp/terrascope-step45-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step45-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.45.0"

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

V44_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM flyway_schema_history
      WHERE version='44';
      " \
    | tr -d '[:space:]'
)"

V45_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM flyway_schema_history
      WHERE version='45';
      " \
    | tr -d '[:space:]'
)"

echo "FLYWAY_V44_COUNT=${V44_COUNT}"
echo "FLYWAY_V45_COUNT=${V45_COUNT}"

[[ "${V44_COUNT}" == "0" ]]
[[ "${V45_COUNT}" == "0" ]]

echo "[PASS] FLYWAY_SCHEMA_UNCHANGED"

echo
echo "===== THREE CLEAN ARTIFACT BUILDS ====="

rm -f \
  "${ARTIFACT}" \
  "${SECOND_BUILD}" \
  "${THIRD_BUILD}"

bash \
  tools/checkpoint-bundle-verifier/build.sh \
  "${ARTIFACT}"

sleep 1

bash \
  tools/checkpoint-bundle-verifier/build.sh \
  "${SECOND_BUILD}"

sleep 1

bash \
  tools/checkpoint-bundle-verifier/build.sh \
  "${THIRD_BUILD}"

SHA_ONE="$(
  shasum -a 256 \
    "${ARTIFACT}" \
    | awk '{print $1}'
)"

SHA_TWO="$(
  shasum -a 256 \
    "${SECOND_BUILD}" \
    | awk '{print $1}'
)"

SHA_THREE="$(
  shasum -a 256 \
    "${THIRD_BUILD}" \
    | awk '{print $1}'
)"

echo "ARTIFACT_BUILD_1_SHA256=${SHA_ONE}"
echo "ARTIFACT_BUILD_2_SHA256=${SHA_TWO}"
echo "ARTIFACT_BUILD_3_SHA256=${SHA_THREE}"

[[ "${SHA_ONE}" == "${SHA_TWO}" ]]
[[ "${SHA_TWO}" == "${SHA_THREE}" ]]

echo "[PASS] DETERMINISTIC_ARTIFACT_BUILD"

echo
echo "===== BUILD RELEASE TOOLS ====="

rm -rf "${RELEASE_TOOL_DIR}"

bash \
  tools/verifier-release-integrity/build.sh \
  "${RELEASE_TOOL_DIR}"

[[ -s "${RELEASE_SIGNER_JAR}" ]]
[[ -s "${RELEASE_VERIFIER_JAR}" ]]

echo "[PASS] RELEASE_TOOLS_READY"

echo
echo "===== GENERATE TEMPORARY RELEASE KEY ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.util.Base64;

public class TerraScopeStep45ReleaseKeygen {

    public static void main(
            String[] args
    ) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        KeyPair pair =
                generator.generateKeyPair();

        Files.writeString(
                Path.of(
                        args[0]
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
                        args[1]
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
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${PRIVATE_KEY}" \
  "${PUBLIC_KEY}"

[[ -s "${PRIVATE_KEY}" ]]
[[ -s "${PUBLIC_KEY}" ]]

echo "[PASS] TEMPORARY_RELEASE_KEY_READY"

echo
echo "===== SIGN RELEASE MANIFEST ====="

rm -f "${RELEASE_MANIFEST}"

java \
  -jar "${RELEASE_SIGNER_JAR}" \
  --artifact "${ARTIFACT}" \
  --private-key "${PRIVATE_KEY}" \
  --public-key "${PUBLIC_KEY}" \
  --release-key-id "${RELEASE_KEY_ID}" \
  --output "${RELEASE_MANIFEST}" \
  | tee /tmp/terrascope-step45-sign.log

grep \
  -q \
  '^SIGNATURE_SELF_VERIFICATION=PASS$' \
  /tmp/terrascope-step45-sign.log

RELEASE_KEY_FINGERPRINT="$(
  grep \
    '^RELEASE_KEY_FINGERPRINT=' \
    /tmp/terrascope-step45-sign.log \
    | cut -d= -f2-
)"

SIGNED_ARTIFACT_SHA="$(
  grep \
    '^ARTIFACT_SHA256=' \
    /tmp/terrascope-step45-sign.log \
    | cut -d= -f2-
)"

echo "RELEASE_KEY_FINGERPRINT=${RELEASE_KEY_FINGERPRINT}"
echo "SIGNED_ARTIFACT_SHA256=${SIGNED_ARTIFACT_SHA}"

[[ "${SIGNED_ARTIFACT_SHA}" == "${SHA_ONE}" ]]

printf '%s\n' \
  "${RELEASE_KEY_FINGERPRINT}" \
  > "${RELEASE_PIN}"

WRONG_PIN="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_PIN}" == "${RELEASE_KEY_FINGERPRINT}" ]]; then
  WRONG_PIN="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_PIN}" \
  > "${WRONG_RELEASE_PIN}"

echo "[PASS] SIGNED_RELEASE_MANIFEST_READY"

echo
echo "===== RELEASE MANIFEST CONTRACT ====="

python3 \
  - \
  "${RELEASE_MANIFEST}" \
  "${SHA_ONE}" \
  "${RELEASE_KEY_FINGERPRINT}" \
<<'PY'
from pathlib import Path
import re
import sys

path = Path(
    sys.argv[1]
)

artifact_sha = sys.argv[2]
fingerprint = sys.argv[3]

lines = path.read_text(
    encoding="utf-8",
).splitlines()

expected_prefixes = [
    "manifestVersion=",
    "artifactName=",
    "artifactVersion=",
    "artifactSha256=",
    "releaseKeyId=",
    "algorithm=",
    "releasePublicKeyX509Base64=",
    "releasePublicKeyFingerprint=",
    "signatureBase64=",
]

assert len(lines) == len(
    expected_prefixes
)

for line, prefix in zip(
    lines,
    expected_prefixes,
):
    assert line.startswith(
        prefix
    )

assert lines[0] == (
    "manifestVersion="
    "TS-VERIFIER-RELEASE-MANIFEST-V1"
)

assert lines[2] == (
    "artifactVersion="
    "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1"
)

assert lines[3] == (
    "artifactSha256="
    + artifact_sha
)

assert lines[5] == (
    "algorithm=ED25519"
)

assert lines[7] == (
    "releasePublicKeyFingerprint="
    + fingerprint
)

assert re.fullmatch(
    r"signatureBase64=.+",
    lines[8],
)

print(
    "[PASS] RELEASE_MANIFEST_CONTRACT"
)
PY

echo
echo "===== PROVE PRIVATE KEY IS NOT EMBEDDED ====="

PRIVATE_KEY_VALUE="$(
  cat "${PRIVATE_KEY}"
)"

if grep \
    -F \
    "${PRIVATE_KEY_VALUE}" \
    "${RELEASE_MANIFEST}" \
    >/dev/null; then

  echo "[FAIL] Private key leaked into release manifest."
  exit 1
fi

if jar tf "${RELEASE_VERIFIER_JAR}" \
    | grep \
        -Ei \
        'private.*key|pkcs8' \
        >/dev/null; then

  echo "[FAIL] Suspicious private-key resource found in release verifier JAR."
  exit 1
fi

echo "[PASS] PRIVATE_KEY_NOT_EMBEDDED"

echo
echo "===== DELETE RELEASE PRIVATE KEY ====="

rm -f "${PRIVATE_KEY}"

[[ ! -e "${PRIVATE_KEY}" ]]

echo "[PASS] RELEASE_PRIVATE_KEY_DESTROYED_BEFORE_VERIFICATION"

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
echo "===== VALID OFFLINE RELEASE AUTHENTICATION ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${ARTIFACT}" \
  --manifest "${RELEASE_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  | tee /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^MANIFEST_SHAPE_VALID=true$' \
  /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^ARTIFACT_NAME_VALID=true$' \
  /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^ARTIFACT_VERSION_VALID=true$' \
  /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^PUBLIC_KEY_FINGERPRINT_VALID=true$' \
  /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step45-release-valid.log

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step45-release-valid.log

echo "[PASS] OFFLINE_RELEASE_AUTHENTICATION"

echo
echo "===== WRONG RELEASE KEY PIN ====="

trap - ERR
set +e

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${ARTIFACT}" \
  --manifest "${RELEASE_MANIFEST}" \
  --release-key-pin "${WRONG_RELEASE_PIN}" \
  > /tmp/terrascope-step45-release-wrong-pin.log \
  2>&1

WRONG_PIN_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step45-release-wrong-pin.log

echo "WRONG_RELEASE_PIN_EXIT=${WRONG_PIN_STATUS}"

[[ "${WRONG_PIN_STATUS}" == "2" ]]

grep \
  -q \
  '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step45-release-wrong-pin.log

grep \
  -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step45-release-wrong-pin.log

grep \
  -q \
  '^RELEASE_KEY_PIN_VALID=false$' \
  /tmp/terrascope-step45-release-wrong-pin.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step45-release-wrong-pin.log

echo "[PASS] WRONG_RELEASE_KEY_PIN_REJECTION"

echo
echo "===== ARTIFACT TAMPER ====="

rm -rf "${TAMPER_ARTIFACT_DIR}"

mkdir -p "${TAMPER_ARTIFACT_DIR}"

cp \
  "${ARTIFACT}" \
  "${TAMPER_ARTIFACT}"

printf '\x00' \
  >> "${TAMPER_ARTIFACT}"

trap - ERR
set +e

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${TAMPER_ARTIFACT}" \
  --manifest "${RELEASE_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  > /tmp/terrascope-step45-release-artifact-tamper.log \
  2>&1

ARTIFACT_TAMPER_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step45-release-artifact-tamper.log

[[ "${ARTIFACT_TAMPER_STATUS}" == "2" ]]

grep \
  -q \
  '^ARTIFACT_HASH_VALID=false$' \
  /tmp/terrascope-step45-release-artifact-tamper.log

grep \
  -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step45-release-artifact-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step45-release-artifact-tamper.log

echo "[PASS] RELEASE_ARTIFACT_TAMPER_DETECTION"

echo
echo "===== MANIFEST PAYLOAD TAMPER ====="

python3 \
  - \
  "${RELEASE_MANIFEST}" \
  "${MANIFEST_TAMPER}" \
<<'PY'
from pathlib import Path
import sys

source = Path(
    sys.argv[1]
)

target = Path(
    sys.argv[2]
)

text = source.read_text(
    encoding="utf-8",
)

text = text.replace(
    (
        "artifactVersion="
        "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1"
    ),
    (
        "artifactVersion="
        "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-TAMPERED"
    ),
    1,
)

target.write_text(
    text,
    encoding="utf-8",
)
PY

trap - ERR
set +e

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${ARTIFACT}" \
  --manifest "${MANIFEST_TAMPER}" \
  --release-key-pin "${RELEASE_PIN}" \
  > /tmp/terrascope-step45-release-manifest-tamper.log \
  2>&1

MANIFEST_TAMPER_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step45-release-manifest-tamper.log

[[ "${MANIFEST_TAMPER_STATUS}" == "2" ]]

grep \
  -q \
  '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step45-release-manifest-tamper.log

grep \
  -q \
  '^ARTIFACT_VERSION_VALID=false$' \
  /tmp/terrascope-step45-release-manifest-tamper.log

grep \
  -q \
  '^SIGNATURE_VALID=false$' \
  /tmp/terrascope-step45-release-manifest-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step45-release-manifest-tamper.log

echo "[PASS] RELEASE_MANIFEST_TAMPER_DETECTION"

echo
echo "===== MANIFEST SIGNATURE TAMPER ====="

python3 \
  - \
  "${RELEASE_MANIFEST}" \
  "${SIGNATURE_TAMPER}" \
<<'PY'
from pathlib import Path
import base64
import sys

source = Path(
    sys.argv[1]
)

target = Path(
    sys.argv[2]
)

lines = source.read_text(
    encoding="utf-8",
).splitlines()

assert lines[-1].startswith(
    "signatureBase64="
)

lines[-1] = (
    "signatureBase64="
    + base64.b64encode(
        bytes(64)
    ).decode()
)

target.write_text(
    "\n".join(
        lines
    )
    + "\n",
    encoding="utf-8",
)
PY

trap - ERR
set +e

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${ARTIFACT}" \
  --manifest "${SIGNATURE_TAMPER}" \
  --release-key-pin "${RELEASE_PIN}" \
  > /tmp/terrascope-step45-release-signature-tamper.log \
  2>&1

SIGNATURE_TAMPER_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step45-release-signature-tamper.log

[[ "${SIGNATURE_TAMPER_STATUS}" == "2" ]]

grep \
  -q \
  '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step45-release-signature-tamper.log

grep \
  -q \
  '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step45-release-signature-tamper.log

grep \
  -q \
  '^SIGNATURE_VALID=false$' \
  /tmp/terrascope-step45-release-signature-tamper.log

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step45-release-signature-tamper.log

echo "[PASS] RELEASE_SIGNATURE_TAMPER_DETECTION"

echo
echo "===== PROVE OFFLINE STATE REMAINED ====="

RUNNING_POSTGRES_AFTER="$(
  docker compose ps \
    --status running \
    -q postgres \
    | awk 'END { print NR + 0 }'
)"

echo "RUNNING_POSTGRES_COUNT_AFTER_RELEASE_VERIFY=${RUNNING_POSTGRES_AFTER}"

[[ "${RUNNING_POSTGRES_AFTER}" == "0" ]]

if curl \
    --silent \
    --max-time 1 \
    "http://localhost:8087/actuator/health" \
    >/dev/null 2>&1; then

  echo "[FAIL] Backend became reachable during release verification."
  exit 1
fi

[[ ! -e "${PRIVATE_KEY}" ]]

echo "[PASS] RELEASE_VERIFICATION_REMAINED_AIR_GAPPED"
echo "[PASS] PRIVATE_KEY_REMAINED_ABSENT"

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

echo "[PASS] FLYWAY_V1_TO_V43_PRESERVED"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 45 VERIFICATION PASSED"
echo "========================================================================"
