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

BACKEND_LOG="/tmp/terrascope-step49-backend.log"

WORK="/tmp/terrascope-step49"

TOOLS="${WORK}/tools"

OLD_DIR="${WORK}/old"
CURRENT_DIR="${WORK}/current"

ARTIFACT_NAME="terrascope-checkpoint-bundle-verifier.jar"

OLD_ARTIFACT="${OLD_DIR}/${ARTIFACT_NAME}"
CURRENT_ARTIFACT="${CURRENT_DIR}/${ARTIFACT_NAME}"

OLD_MANIFEST="${OLD_DIR}/release.manifest"
CURRENT_MANIFEST="${CURRENT_DIR}/release.manifest"

OLD_STATE="${OLD_DIR}/trust-state.manifest"
CURRENT_STATE="${CURRENT_DIR}/trust-state.manifest"

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
TRANSPARENCY_DIRECT_PIN="${WORK}/transparency-direct.sha256"

AUTHORITY_LINEAGE="${WORK}/release-authority.lineage"

AUTHORITY_HEAD_ONE_PIN="${WORK}/authority-head-one.sha256"
AUTHORITY_HEAD_TWO_PIN="${WORK}/authority-head-two.sha256"

TRANSPARENCY_LEDGER="${WORK}/release-transparency.log"

TRANSPARENCY_HEAD_ONE_PIN="${WORK}/transparency-head-one.sha256"
TRANSPARENCY_HEAD_TWO_PIN="${WORK}/transparency-head-two.sha256"

OLD_STATE_HEAD_PIN="${WORK}/old-state-head.sha256"
CURRENT_STATE_HEAD_PIN="${WORK}/current-state-head.sha256"

DERIVED_AUTHORITY_HEAD="${WORK}/derived-authority-head.sha256"

DERIVED_TRANSPARENCY_KEY="${WORK}/derived-transparency-key.sha256"

DERIVED_TRANSPARENCY_HEAD="${WORK}/derived-transparency-head.sha256"

DERIVED_RELEASE_KEY="${WORK}/derived-release-key.sha256"

SHOULD_NOT_AUTHORITY="${WORK}/should-not-authority.sha256"

SHOULD_NOT_TRANSPARENCY_KEY="${WORK}/should-not-transparency-key.sha256"

SHOULD_NOT_TRANSPARENCY_HEAD="${WORK}/should-not-transparency-head.sha256"

WRONG_ROOT_PIN="${WORK}/wrong-root.sha256"
WRONG_STATE_HEAD_PIN="${WORK}/wrong-state-head.sha256"

FIELD_TAMPER_STATE="${WORK}/field-tamper-state.manifest"

SIGNATURE_TAMPER_STATE="${WORK}/signature-tamper-state.manifest"

SIGNER_JAR="${TOOLS}/terrascope-verifier-release-signer.jar"

RELEASE_VERIFIER_JAR="${TOOLS}/terrascope-verifier-release-verifier.jar"

TRANSPARENCY_JAR="${TOOLS}/terrascope-verifier-release-transparency.jar"

AUTHORITY_JAR="${TOOLS}/terrascope-verifier-release-authority-lineage.jar"

TRUST_STATE_JAR="${TOOLS}/terrascope-verifier-release-trust-state.jar"

KEYGEN_SOURCE="${WORK}/TerraScopeStep49Keygen.java"

ROOT_KEY_ID="terrascope-release-authority-v1"

INTERMEDIATE_KEY_ID="terrascope-release-authority-v2"

TERMINAL_KEY_ID="terrascope-release-authority-v3"

TRANSPARENCY_KEY_ID="terrascope-transparency-step49-v1"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true

    wait "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true
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
    kill "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true

    wait "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true

    BACKEND_PID=""
  fi

  sleep 1
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 49 VERIFICATION"
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

rm -f \
  "${BACKEND_LOG}"

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
  > /tmp/terrascope-step49-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step49-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.49.0"

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

for VERSION in \
  44 \
  45 \
  46 \
  47 \
  48 \
  49
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

rm -rf \
  "${WORK}"

mkdir -p \
  "${TOOLS}" \
  "${OLD_DIR}" \
  "${CURRENT_DIR}"

echo "[PASS] FIXTURE_READY"

echo
echo "===== BUILD FIVE RELEASE TOOLS ====="

bash \
  tools/verifier-release-integrity/build.sh \
  "${TOOLS}"

[[ -s "${SIGNER_JAR}" ]]
[[ -s "${RELEASE_VERIFIER_JAR}" ]]
[[ -s "${TRANSPARENCY_JAR}" ]]
[[ -s "${AUTHORITY_JAR}" ]]
[[ -s "${TRUST_STATE_JAR}" ]]

echo "[PASS] FIVE_RELEASE_TOOLS_READY"

echo
echo "===== BUILD DISTINCT RELEASE ARTIFACTS ====="

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

public class TerraScopeStep49Keygen {

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
  "${TRANSPARENCY_DIRECT_PIN}"

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

TRANSPARENCY_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${TRANSPARENCY_DIRECT_PIN}"
)"

echo "ROOT_FINGERPRINT=${ROOT_FINGERPRINT}"
echo "INTERMEDIATE_FINGERPRINT=${INTERMEDIATE_FINGERPRINT}"
echo "TERMINAL_FINGERPRINT=${TERMINAL_FINGERPRINT}"
echo "TRANSPARENCY_FINGERPRINT=${TRANSPARENCY_FINGERPRINT}"

[[ "${ROOT_FINGERPRINT}" != "${INTERMEDIATE_FINGERPRINT}" ]]
[[ "${INTERMEDIATE_FINGERPRINT}" != "${TERMINAL_FINGERPRINT}" ]]

echo "[PASS] FOUR_KEY_AUTHORITIES_READY"

echo
echo "===== AUTHORITY TRANSITION 1 ====="

java \
  -jar "${AUTHORITY_JAR}" \
  append \
  --lineage "${AUTHORITY_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --outgoing-key-id "${ROOT_KEY_ID}" \
  --outgoing-private-key "${ROOT_PRIVATE}" \
  --outgoing-public-key "${ROOT_PUBLIC}" \
  --replacement-key-id "${INTERMEDIATE_KEY_ID}" \
  --replacement-public-key "${INTERMEDIATE_PUBLIC}" \
  > "${WORK}/authority-transition-one.log"

AUTHORITY_HEAD_ONE="$(
  grep \
    '^TRANSITION_HASH=' \
    "${WORK}/authority-transition-one.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${AUTHORITY_HEAD_ONE}" \
  > "${AUTHORITY_HEAD_ONE_PIN}"

echo "AUTHORITY_HEAD_ONE=${AUTHORITY_HEAD_ONE}"

echo "[PASS] AUTHORITY_TRANSITION_ONE"

echo
echo "===== OLD RELEASE SIGNED BY INTERMEDIATE ====="

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

echo "[PASS] OLD_RELEASE_SIGNED_BY_INTERMEDIATE"

echo
echo "===== OLD RELEASE TRANSPARENCY ENTRY ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${INTERMEDIATE_DIRECT_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/transparency-old.log"

TRANSPARENCY_HEAD_ONE="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/transparency-old.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${TRANSPARENCY_HEAD_ONE}" \
  > "${TRANSPARENCY_HEAD_ONE_PIN}"

echo "TRANSPARENCY_HEAD_ONE=${TRANSPARENCY_HEAD_ONE}"

echo "[PASS] OLD_TRANSPARENCY_STATE_READY"

echo
echo "===== ISSUE HISTORICAL TRUST STATE ====="

java \
  -jar "${TRUST_STATE_JAR}" \
  issue \
  --state-sequence 1 \
  --release-root-key-id "${ROOT_KEY_ID}" \
  --release-root-private-key "${ROOT_PRIVATE}" \
  --release-root-public-key "${ROOT_PUBLIC}" \
  --release-authority-head-pin "${AUTHORITY_HEAD_ONE_PIN}" \
  --transparency-key-pin "${TRANSPARENCY_DIRECT_PIN}" \
  --transparency-head-pin "${TRANSPARENCY_HEAD_ONE_PIN}" \
  --output "${OLD_STATE}" \
  | tee "${WORK}/old-state-issue.log"

OLD_STATE_HASH="$(
  grep \
    '^STATE_HASH=' \
    "${WORK}/old-state-issue.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${OLD_STATE_HASH}" \
  > "${OLD_STATE_HEAD_PIN}"

echo "OLD_TRUST_STATE_HASH=${OLD_STATE_HASH}"

echo "[PASS] HISTORICAL_TRUST_STATE_ISSUED"

echo
echo "===== AUTHORITY TRANSITION 2 ====="

java \
  -jar "${AUTHORITY_JAR}" \
  append \
  --lineage "${AUTHORITY_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --outgoing-key-id "${INTERMEDIATE_KEY_ID}" \
  --outgoing-private-key "${INTERMEDIATE_PRIVATE}" \
  --outgoing-public-key "${INTERMEDIATE_PUBLIC}" \
  --replacement-key-id "${TERMINAL_KEY_ID}" \
  --replacement-public-key "${TERMINAL_PUBLIC}" \
  > "${WORK}/authority-transition-two.log"

AUTHORITY_HEAD_TWO="$(
  grep \
    '^TRANSITION_HASH=' \
    "${WORK}/authority-transition-two.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${AUTHORITY_HEAD_TWO}" \
  > "${AUTHORITY_HEAD_TWO_PIN}"

echo "AUTHORITY_HEAD_TWO=${AUTHORITY_HEAD_TWO}"

[[ "${AUTHORITY_HEAD_ONE}" != "${AUTHORITY_HEAD_TWO}" ]]

echo "[PASS] AUTHORITY_TRANSITION_TWO"

echo
echo "===== CURRENT RELEASE SIGNED BY TERMINAL ====="

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

echo "[PASS] CURRENT_RELEASE_SIGNED_BY_TERMINAL"

echo
echo "===== CURRENT RELEASE TRANSPARENCY ENTRY ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${TERMINAL_DIRECT_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/transparency-current.log"

TRANSPARENCY_HEAD_TWO="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/transparency-current.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${TRANSPARENCY_HEAD_TWO}" \
  > "${TRANSPARENCY_HEAD_TWO_PIN}"

echo "TRANSPARENCY_HEAD_TWO=${TRANSPARENCY_HEAD_TWO}"

[[ "${TRANSPARENCY_HEAD_ONE}" != "${TRANSPARENCY_HEAD_TWO}" ]]

echo "[PASS] CURRENT_TRANSPARENCY_STATE_READY"

echo
echo "===== ISSUE CURRENT TRUST STATE ====="

java \
  -jar "${TRUST_STATE_JAR}" \
  issue \
  --state-sequence 2 \
  --release-root-key-id "${ROOT_KEY_ID}" \
  --release-root-private-key "${ROOT_PRIVATE}" \
  --release-root-public-key "${ROOT_PUBLIC}" \
  --release-authority-head-pin "${AUTHORITY_HEAD_TWO_PIN}" \
  --transparency-key-pin "${TRANSPARENCY_DIRECT_PIN}" \
  --transparency-head-pin "${TRANSPARENCY_HEAD_TWO_PIN}" \
  --output "${CURRENT_STATE}" \
  | tee "${WORK}/current-state-issue.log"

CURRENT_STATE_HASH="$(
  grep \
    '^STATE_HASH=' \
    "${WORK}/current-state-issue.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${CURRENT_STATE_HASH}" \
  > "${CURRENT_STATE_HEAD_PIN}"

echo "CURRENT_TRUST_STATE_HASH=${CURRENT_STATE_HASH}"

[[ "${OLD_STATE_HASH}" != "${CURRENT_STATE_HASH}" ]]

echo "[PASS] CURRENT_TRUST_STATE_ISSUED"

echo
echo "===== TRUST STATE CONTRACT ====="

python3 \
  - \
  "${OLD_STATE}" \
  "${CURRENT_STATE}" \
  "${ROOT_FINGERPRINT}" \
  "${AUTHORITY_HEAD_ONE}" \
  "${AUTHORITY_HEAD_TWO}" \
  "${TRANSPARENCY_FINGERPRINT}" \
  "${TRANSPARENCY_HEAD_ONE}" \
  "${TRANSPARENCY_HEAD_TWO}" \
<<'PY'
from pathlib import Path
import re
import sys

old = Path(
    sys.argv[1]
).read_text(
    encoding="utf-8",
).splitlines()

current = Path(
    sys.argv[2]
).read_text(
    encoding="utf-8",
).splitlines()

root = sys.argv[3]

authority_one = sys.argv[4]
authority_two = sys.argv[5]

transparency_key = sys.argv[6]

transparency_one = sys.argv[7]
transparency_two = sys.argv[8]

prefixes = [
    "trustStateVersion=",
    "stateSequence=",
    "releaseRootKeyId=",
    "releaseRootPublicKeyX509Base64=",
    "releaseRootKeyFingerprint=",
    "releaseAuthorityHeadHash=",
    "transparencyKeyFingerprint=",
    "transparencyHeadHash=",
    "checkpointVerifierArtifactVersion=",
    "algorithm=",
    "signatureBase64=",
    "stateHash=",
]

for lines in (
    old,
    current,
):
    assert len(lines) == len(
        prefixes
    )

    for line, prefix in zip(
        lines,
        prefixes,
    ):
        assert line.startswith(
            prefix
        )

assert old[0] == (
    "trustStateVersion="
    "TS-VERIFIER-RELEASE-TRUST-STATE-V1"
)

assert current[0] == old[0]

assert old[1] == (
    "stateSequence=1"
)

assert current[1] == (
    "stateSequence=2"
)

assert old[4] == (
    "releaseRootKeyFingerprint="
    + root
)

assert current[4] == old[4]

assert old[5] == (
    "releaseAuthorityHeadHash="
    + authority_one
)

assert current[5] == (
    "releaseAuthorityHeadHash="
    + authority_two
)

assert old[6] == (
    "transparencyKeyFingerprint="
    + transparency_key
)

assert current[6] == old[6]

assert old[7] == (
    "transparencyHeadHash="
    + transparency_one
)

assert current[7] == (
    "transparencyHeadHash="
    + transparency_two
)

assert old[8] == (
    "checkpointVerifierArtifactVersion="
    "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1"
)

assert current[8] == old[8]

assert old[9] == (
    "algorithm=ED25519"
)

assert current[9] == old[9]

assert re.fullmatch(
    r"signatureBase64=.+",
    old[10],
)

assert re.fullmatch(
    r"signatureBase64=.+",
    current[10],
)

assert re.fullmatch(
    r"stateHash=[0-9a-f]{64}",
    old[11],
)

assert re.fullmatch(
    r"stateHash=[0-9a-f]{64}",
    current[11],
)

print(
    "[PASS] TRUST_STATE_CANONICAL_CONTRACT"
)
PY

echo
echo "===== PREPARE NEGATIVE TRUST STATES ====="

WRONG_ROOT="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_ROOT}" == "${ROOT_FINGERPRINT}" ]]; then
  WRONG_ROOT="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_ROOT}" \
  > "${WRONG_ROOT_PIN}"

WRONG_STATE_HEAD="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_STATE_HEAD}" == "${CURRENT_STATE_HASH}" ]]; then
  WRONG_STATE_HEAD="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_STATE_HEAD}" \
  > "${WRONG_STATE_HEAD_PIN}"

python3 \
  - \
  "${CURRENT_STATE}" \
  "${FIELD_TAMPER_STATE}" \
  "${SIGNATURE_TAMPER_STATE}" \
<<'PY'
from pathlib import Path
import base64
import hashlib
import sys

source = Path(
    sys.argv[1]
)

field_target = Path(
    sys.argv[2]
)

signature_target = Path(
    sys.argv[3]
)

lines = source.read_text(
    encoding="utf-8",
).splitlines()

field_lines = list(
    lines
)

for index, line in enumerate(
    field_lines
):
    if line.startswith(
        "releaseAuthorityHeadHash="
    ):
        field_lines[index] = (
            "releaseAuthorityHeadHash="
            + (
                "0" * 64
            )
        )

        break
else:
    raise AssertionError(
        "releaseAuthorityHeadHash not found"
    )

field_target.write_text(
    "\n".join(
        field_lines
    )
    + "\n",
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

        signature_index = index

        break
else:
    raise AssertionError(
        "signatureBase64 not found"
    )

payload = (
    "\n".join(
        signature_lines[:signature_index]
    )
    + "\n"
)

signature_line = (
    signature_lines[
        signature_index
    ]
    + "\n"
)

state_hash = hashlib.sha256(
    (
        payload
        + signature_line
    ).encode(
        "utf-8"
    )
).hexdigest()

for index, line in enumerate(
    signature_lines
):
    if line.startswith(
        "stateHash="
    ):
        signature_lines[index] = (
            "stateHash="
            + state_hash
        )

        break
else:
    raise AssertionError(
        "stateHash not found"
    )

signature_target.write_text(
    "\n".join(
        signature_lines
    )
    + "\n",
    encoding="utf-8",
)

print(
    "[PASS] NEGATIVE_TRUST_STATE_FIXTURES_READY"
)
PY

echo
echo "===== DESTROY PRIVATE KEYS + DIRECT TRUST PINS ====="

rm -f \
  "${ROOT_PRIVATE}" \
  "${INTERMEDIATE_PRIVATE}" \
  "${TERMINAL_PRIVATE}" \
  "${TRANSPARENCY_PRIVATE}" \
  "${INTERMEDIATE_DIRECT_PIN}" \
  "${TERMINAL_DIRECT_PIN}" \
  "${TRANSPARENCY_DIRECT_PIN}" \
  "${AUTHORITY_HEAD_ONE_PIN}" \
  "${AUTHORITY_HEAD_TWO_PIN}" \
  "${TRANSPARENCY_HEAD_ONE_PIN}" \
  "${TRANSPARENCY_HEAD_TWO_PIN}"

[[ ! -e "${ROOT_PRIVATE}" ]]
[[ ! -e "${INTERMEDIATE_PRIVATE}" ]]
[[ ! -e "${TERMINAL_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]

[[ ! -e "${INTERMEDIATE_DIRECT_PIN}" ]]
[[ ! -e "${TERMINAL_DIRECT_PIN}" ]]
[[ ! -e "${TRANSPARENCY_DIRECT_PIN}" ]]

[[ ! -e "${AUTHORITY_HEAD_ONE_PIN}" ]]
[[ ! -e "${AUTHORITY_HEAD_TWO_PIN}" ]]

[[ ! -e "${TRANSPARENCY_HEAD_ONE_PIN}" ]]
[[ ! -e "${TRANSPARENCY_HEAD_TWO_PIN}" ]]

echo "[PASS] ALL_PRIVATE_KEYS_DESTROYED"
echo "[PASS] DIRECT_COMPONENT_PINS_REMOVED"

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
echo "===== VERIFY CURRENT ROOT-SIGNED TRUST STATE ====="

rm -f \
  "${DERIVED_AUTHORITY_HEAD}" \
  "${DERIVED_TRANSPARENCY_KEY}" \
  "${DERIVED_TRANSPARENCY_HEAD}"

java \
  -jar "${TRUST_STATE_JAR}" \
  verify \
  --state "${CURRENT_STATE}" \
  --root-pin "${ROOT_PIN}" \
  --state-head-pin "${CURRENT_STATE_HEAD_PIN}" \
  --release-authority-head-output "${DERIVED_AUTHORITY_HEAD}" \
  --transparency-key-output "${DERIVED_TRANSPARENCY_KEY}" \
  --transparency-head-output "${DERIVED_TRANSPARENCY_HEAD}" \
  | tee /tmp/terrascope-step49-current-state.log

grep -q \
  '^TRUST_STATE_SHAPE_VALID=true$' \
  /tmp/terrascope-step49-current-state.log

grep -q \
  '^ROOT_FINGERPRINT_VALID=true$' \
  /tmp/terrascope-step49-current-state.log

grep -q \
  '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step49-current-state.log

grep -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step49-current-state.log

grep -q \
  '^STATE_HASH_VALID=true$' \
  /tmp/terrascope-step49-current-state.log

grep -q \
  '^STATE_HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step49-current-state.log

grep -q \
  '^STATE_SEQUENCE=2$' \
  /tmp/terrascope-step49-current-state.log

grep -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step49-current-state.log

[[ -s "${DERIVED_AUTHORITY_HEAD}" ]]
[[ -s "${DERIVED_TRANSPARENCY_KEY}" ]]
[[ -s "${DERIVED_TRANSPARENCY_HEAD}" ]]

DERIVED_AUTHORITY="$(
  tr -d '[:space:]' \
    < "${DERIVED_AUTHORITY_HEAD}"
)"

DERIVED_TRANSPARENCY="$(
  tr -d '[:space:]' \
    < "${DERIVED_TRANSPARENCY_KEY}"
)"

DERIVED_TRANSPARENCY_LEDGER_HEAD="$(
  tr -d '[:space:]' \
    < "${DERIVED_TRANSPARENCY_HEAD}"
)"

echo "DERIVED_AUTHORITY_HEAD=${DERIVED_AUTHORITY}"

echo "DERIVED_TRANSPARENCY_KEY=${DERIVED_TRANSPARENCY}"

echo "DERIVED_TRANSPARENCY_HEAD=${DERIVED_TRANSPARENCY_LEDGER_HEAD}"

[[ "${DERIVED_AUTHORITY}" == "${AUTHORITY_HEAD_TWO}" ]]

[[ "${DERIVED_TRANSPARENCY}" == "${TRANSPARENCY_FINGERPRINT}" ]]

[[ "${DERIVED_TRANSPARENCY_LEDGER_HEAD}" == "${TRANSPARENCY_HEAD_TWO}" ]]

echo "[PASS] CURRENT_TRUST_STATE_VERIFIED_OFFLINE"

echo "[PASS] COMPONENT_TRUST_PINS_DERIVED_FROM_TRUST_STATE"

echo
echo "===== VERIFY AUTHORITY USING DERIVED HEAD ====="

rm -f \
  "${DERIVED_RELEASE_KEY}"

java \
  -jar "${AUTHORITY_JAR}" \
  verify \
  --lineage "${AUTHORITY_LINEAGE}" \
  --root-pin "${ROOT_PIN}" \
  --head-pin "${DERIVED_AUTHORITY_HEAD}" \
  --terminal-pin-output "${DERIVED_RELEASE_KEY}" \
  | tee /tmp/terrascope-step49-authority.log

grep -q \
  '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step49-authority.log

grep -q \
  '^SUCCESSOR_CONTINUITY_VALID=true$' \
  /tmp/terrascope-step49-authority.log

grep -q \
  '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step49-authority.log

grep -q \
  '^LINEAGE_VALID=true$' \
  /tmp/terrascope-step49-authority.log

grep -q \
  '^TRANSITION_COUNT=2$' \
  /tmp/terrascope-step49-authority.log

grep -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step49-authority.log

DERIVED_RELEASE_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${DERIVED_RELEASE_KEY}"
)"

[[ "${DERIVED_RELEASE_FINGERPRINT}" == "${TERMINAL_FINGERPRINT}" ]]

echo "[PASS] CURRENT_RELEASE_KEY_DERIVED_FROM_TRUST_STATE"

echo
echo "===== CURRENT RELEASE VERIFICATION ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${DERIVED_RELEASE_KEY}" \
  | tee /tmp/terrascope-step49-release.log

grep -q \
  '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step49-release.log

grep -q \
  '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step49-release.log

grep -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step49-release.log

grep -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step49-release.log

echo "[PASS] CURRENT_RELEASE_VERIFIED_FROM_TRUST_STATE"

echo
echo "===== RELEASE TRANSPARENCY FROM DERIVED PINS ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${DERIVED_RELEASE_KEY}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  --transparency-key-pin "${DERIVED_TRANSPARENCY_KEY}" \
  --head-pin "${DERIVED_TRANSPARENCY_HEAD}" \
  | tee /tmp/terrascope-step49-transparency.log

grep -q \
  '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step49-transparency.log

grep -q \
  '^TRANSPARENCY_LEDGER_VALID=true$' \
  /tmp/terrascope-step49-transparency.log

grep -q \
  '^TRANSPARENCY_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step49-transparency.log

grep -q \
  '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step49-transparency.log

grep -q \
  '^LATEST_RELEASE_BINDING_VALID=true$' \
  /tmp/terrascope-step49-transparency.log

grep -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step49-transparency.log

echo "[PASS] TRANSPARENCY_VERIFIED_FROM_TRUST_STATE"

echo
echo "===== HISTORICAL TRUST STATE REMAINS AUTHENTIC ====="

java \
  -jar "${TRUST_STATE_JAR}" \
  verify \
  --state "${OLD_STATE}" \
  --root-pin "${ROOT_PIN}" \
  --state-head-pin "${OLD_STATE_HEAD_PIN}" \
  | tee /tmp/terrascope-step49-old-historical-state.log

grep -q \
  '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step49-old-historical-state.log

grep -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step49-old-historical-state.log

grep -q \
  '^STATE_HASH_VALID=true$' \
  /tmp/terrascope-step49-old-historical-state.log

grep -q \
  '^STATE_HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step49-old-historical-state.log

grep -q \
  '^STATE_SEQUENCE=1$' \
  /tmp/terrascope-step49-old-historical-state.log

grep -q \
  '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step49-old-historical-state.log

echo "[PASS] HISTORICAL_TRUST_STATE_IS_AUTHENTIC"

echo
echo "===== REJECT HISTORICAL STATE AGAINST CURRENT STATE HEAD ====="

rm -f \
  "${SHOULD_NOT_AUTHORITY}" \
  "${SHOULD_NOT_TRANSPARENCY_KEY}" \
  "${SHOULD_NOT_TRANSPARENCY_HEAD}"

set +e

java \
  -jar "${TRUST_STATE_JAR}" \
  verify \
  --state "${OLD_STATE}" \
  --root-pin "${ROOT_PIN}" \
  --state-head-pin "${CURRENT_STATE_HEAD_PIN}" \
  --release-authority-head-output "${SHOULD_NOT_AUTHORITY}" \
  --transparency-key-output "${SHOULD_NOT_TRANSPARENCY_KEY}" \
  --transparency-head-output "${SHOULD_NOT_TRANSPARENCY_HEAD}" \
  > /tmp/terrascope-step49-old-current-head.log \
  2>&1

STALE_STATE_STATUS=$?

set -e

cat \
  /tmp/terrascope-step49-old-current-head.log

echo "STALE_TRUST_STATE_EXIT=${STALE_STATE_STATUS}"

[[ "${STALE_STATE_STATUS}" == "2" ]]

grep -q \
  '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step49-old-current-head.log

grep -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step49-old-current-head.log

grep -q \
  '^STATE_HASH_VALID=true$' \
  /tmp/terrascope-step49-old-current-head.log

grep -q \
  '^STATE_HEAD_PIN_VALID=false$' \
  /tmp/terrascope-step49-old-current-head.log

grep -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step49-old-current-head.log

grep \
  -q \
  'authentic but is not the independently pinned current trust state' \
  /tmp/terrascope-step49-old-current-head.log

[[ ! -e "${SHOULD_NOT_AUTHORITY}" ]]
[[ ! -e "${SHOULD_NOT_TRANSPARENCY_KEY}" ]]
[[ ! -e "${SHOULD_NOT_TRANSPARENCY_HEAD}" ]]

echo "[PASS] STALE_TRUST_STATE_ROLLBACK_REJECTION"

echo "[PASS] STALE_TRUST_STATE_CANNOT_DERIVE_COMPONENT_PINS"

echo
echo "===== WRONG ROOT PIN ====="

set +e

java \
  -jar "${TRUST_STATE_JAR}" \
  verify \
  --state "${CURRENT_STATE}" \
  --root-pin "${WRONG_ROOT_PIN}" \
  --state-head-pin "${CURRENT_STATE_HEAD_PIN}" \
  > /tmp/terrascope-step49-wrong-root.log \
  2>&1

WRONG_ROOT_STATUS=$?

set -e

cat \
  /tmp/terrascope-step49-wrong-root.log

[[ "${WRONG_ROOT_STATUS}" == "2" ]]

grep -q \
  '^ROOT_PIN_VALID=false$' \
  /tmp/terrascope-step49-wrong-root.log

grep -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step49-wrong-root.log

echo "[PASS] WRONG_TRUST_STATE_ROOT_PIN_REJECTION"

echo
echo "===== WRONG CURRENT STATE HEAD ====="

set +e

java \
  -jar "${TRUST_STATE_JAR}" \
  verify \
  --state "${CURRENT_STATE}" \
  --root-pin "${ROOT_PIN}" \
  --state-head-pin "${WRONG_STATE_HEAD_PIN}" \
  > /tmp/terrascope-step49-wrong-state-head.log \
  2>&1

WRONG_HEAD_STATUS=$?

set -e

cat \
  /tmp/terrascope-step49-wrong-state-head.log

[[ "${WRONG_HEAD_STATUS}" == "2" ]]

grep -q \
  '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step49-wrong-state-head.log

grep -q \
  '^SIGNATURE_VALID=true$' \
  /tmp/terrascope-step49-wrong-state-head.log

grep -q \
  '^STATE_HASH_VALID=true$' \
  /tmp/terrascope-step49-wrong-state-head.log

grep -q \
  '^STATE_HEAD_PIN_VALID=false$' \
  /tmp/terrascope-step49-wrong-state-head.log

grep -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step49-wrong-state-head.log

echo "[PASS] WRONG_TRUST_STATE_HEAD_PIN_REJECTION"

echo
echo "===== TRUST STATE FIELD TAMPER ====="

set +e

java \
  -jar "${TRUST_STATE_JAR}" \
  verify \
  --state "${FIELD_TAMPER_STATE}" \
  --root-pin "${ROOT_PIN}" \
  --state-head-pin "${CURRENT_STATE_HEAD_PIN}" \
  > /tmp/terrascope-step49-field-tamper.log \
  2>&1

FIELD_TAMPER_STATUS=$?

set -e

cat \
  /tmp/terrascope-step49-field-tamper.log

[[ "${FIELD_TAMPER_STATUS}" == "2" ]]

grep -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step49-field-tamper.log

echo "[PASS] TRUST_STATE_FIELD_TAMPER_DETECTION"

echo
echo "===== TRUST STATE SIGNATURE TAMPER WITH RECOMPUTED STATE HASH ====="

TAMPERED_STATE_HASH="$(
  grep \
    '^stateHash=' \
    "${SIGNATURE_TAMPER_STATE}" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${TAMPERED_STATE_HASH}" \
  > "${WORK}/signature-tamper-head.sha256"

set +e

java \
  -jar "${TRUST_STATE_JAR}" \
  verify \
  --state "${SIGNATURE_TAMPER_STATE}" \
  --root-pin "${ROOT_PIN}" \
  --state-head-pin "${WORK}/signature-tamper-head.sha256" \
  > /tmp/terrascope-step49-signature-tamper.log \
  2>&1

SIGNATURE_TAMPER_STATUS=$?

set -e

cat \
  /tmp/terrascope-step49-signature-tamper.log

[[ "${SIGNATURE_TAMPER_STATUS}" == "2" ]]

grep -q \
  '^STATE_HASH_VALID=true$' \
  /tmp/terrascope-step49-signature-tamper.log

grep -q \
  '^STATE_HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step49-signature-tamper.log

grep -q \
  '^SIGNATURE_VALID=false$' \
  /tmp/terrascope-step49-signature-tamper.log

grep -q \
  '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step49-signature-tamper.log

echo "[PASS] TRUST_STATE_SIGNATURE_TAMPER_DETECTION"

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

  echo "[FAIL] Backend became reachable during Step 49 verification."

  exit 1
fi

[[ ! -e "${ROOT_PRIVATE}" ]]
[[ ! -e "${INTERMEDIATE_PRIVATE}" ]]
[[ ! -e "${TERMINAL_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]

[[ ! -e "${INTERMEDIATE_DIRECT_PIN}" ]]
[[ ! -e "${TERMINAL_DIRECT_PIN}" ]]
[[ ! -e "${TRANSPARENCY_DIRECT_PIN}" ]]

[[ ! -e "${AUTHORITY_HEAD_ONE_PIN}" ]]
[[ ! -e "${AUTHORITY_HEAD_TWO_PIN}" ]]

[[ ! -e "${TRANSPARENCY_HEAD_ONE_PIN}" ]]
[[ ! -e "${TRANSPARENCY_HEAD_TWO_PIN}" ]]

echo "[PASS] TRUE_OFFLINE_UNIFIED_TRUST_STATE_VERIFICATION"

echo "[PASS] PRIVATE_KEYS_REMAINED_ABSENT"

echo "[PASS] ONLY_ROOT_AND_TRUST_STATE_HEAD_WERE_INDEPENDENT_TRUST_INPUTS"

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
echo " TERRASCOPE — STEP 49 VERIFICATION PASSED"
echo "========================================================================"
