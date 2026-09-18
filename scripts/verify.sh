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

BACKEND_LOG="/tmp/terrascope-step46-backend.log"

WORK="/tmp/terrascope-step46"

CURRENT_DIR="${WORK}/current"
OLD_DIR="${WORK}/old"

CURRENT_ARTIFACT="${CURRENT_DIR}/terrascope-checkpoint-bundle-verifier.jar"
OLD_ARTIFACT="${OLD_DIR}/terrascope-checkpoint-bundle-verifier.jar"

CURRENT_MANIFEST="${CURRENT_DIR}/release.manifest"
OLD_MANIFEST="${OLD_DIR}/release.manifest"

RELEASE_PRIVATE="${WORK}/release-private.b64"
RELEASE_PUBLIC="${WORK}/release-public.b64"
RELEASE_PIN="${WORK}/release-pin.sha256"

TRANSPARENCY_PRIVATE="${WORK}/transparency-private.b64"
TRANSPARENCY_PUBLIC="${WORK}/transparency-public.b64"
TRANSPARENCY_PIN="${WORK}/transparency-pin.sha256"

HEAD_PIN="${WORK}/head.sha256"

WRONG_RELEASE_PIN="${WORK}/wrong-release-pin.sha256"
WRONG_TRANSPARENCY_PIN="${WORK}/wrong-transparency-pin.sha256"

LEDGER="${WORK}/release-transparency.log"

TRUNCATED_LEDGER="${WORK}/release-transparency-truncated.log"
TAMPERED_LEDGER="${WORK}/release-transparency-tampered.log"

TOOLS="${WORK}/tools"

SIGNER_JAR="${TOOLS}/terrascope-verifier-release-signer.jar"
RELEASE_VERIFIER_JAR="${TOOLS}/terrascope-verifier-release-verifier.jar"
TRANSPARENCY_JAR="${TOOLS}/terrascope-verifier-release-transparency.jar"

KEYGEN_SOURCE="${WORK}/TerraScopeStep46Keygen.java"

RELEASE_KEY_ID="terrascope-release-step46-ed25519-v1"
TRANSPARENCY_KEY_ID="terrascope-transparency-step46-ed25519-v1"

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
echo " TERRASCOPE — STEP 46 VERIFICATION"
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
  > "${WORK}-status.json"

python3 \
  - "${WORK}-status.json" \
<<'PY'
import json
import sys

payload = json.load(
    open(
        sys.argv[1],
        encoding="utf-8",
    )
)

assert payload["version"] == "0.46.0"

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

for VERSION in 44 45 46; do
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
echo "===== BUILD RELEASE TOOLS ====="

rm -rf "${WORK}"

mkdir -p \
  "${CURRENT_DIR}" \
  "${OLD_DIR}" \
  "${TOOLS}"

bash \
  tools/verifier-release-integrity/build.sh \
  "${TOOLS}"

[[ -s "${SIGNER_JAR}" ]]
[[ -s "${RELEASE_VERIFIER_JAR}" ]]
[[ -s "${TRANSPARENCY_JAR}" ]]

echo "[PASS] RELEASE_TOOLS_READY"

echo
echo "===== BUILD CURRENT + SYNTHETIC OLD RELEASE ====="

bash \
  tools/checkpoint-bundle-verifier/build.sh \
  "${CURRENT_ARTIFACT}"

cp \
  "${CURRENT_ARTIFACT}" \
  "${OLD_ARTIFACT}"

# The old artifact remains a legitimately signed release, but differs
# from the current artifact. This gives the rollback test a distinct
# historical release without changing repository source.
printf '\x00' \
  >> "${OLD_ARTIFACT}"

CURRENT_SHA="$(
  shasum -a 256 \
    "${CURRENT_ARTIFACT}" \
    | awk '{print $1}'
)"

OLD_SHA="$(
  shasum -a 256 \
    "${OLD_ARTIFACT}" \
    | awk '{print $1}'
)"

echo "CURRENT_ARTIFACT_SHA256=${CURRENT_SHA}"
echo "OLD_ARTIFACT_SHA256=${OLD_SHA}"

[[ "${CURRENT_SHA}" != "${OLD_SHA}" ]]

echo "[PASS] DISTINCT_CURRENT_AND_OLD_RELEASE_FIXTURES"

echo
echo "===== GENERATE RELEASE + TRANSPARENCY KEYS ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.util.Base64;

public class TerraScopeStep46Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        for (
                int index = 0;
                index < 2;
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
  "${RELEASE_PRIVATE}" \
  "${RELEASE_PUBLIC}" \
  "${TRANSPARENCY_PRIVATE}" \
  "${TRANSPARENCY_PUBLIC}"

echo "[PASS] RELEASE_AND_TRANSPARENCY_KEYS_READY"

echo
echo "===== SIGN OLD RELEASE ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --private-key "${RELEASE_PRIVATE}" \
  --public-key "${RELEASE_PUBLIC}" \
  --release-key-id "${RELEASE_KEY_ID}" \
  --output "${OLD_MANIFEST}" \
  > "${WORK}/old-sign.log"

echo
echo "===== SIGN CURRENT RELEASE ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --private-key "${RELEASE_PRIVATE}" \
  --public-key "${RELEASE_PUBLIC}" \
  --release-key-id "${RELEASE_KEY_ID}" \
  --output "${CURRENT_MANIFEST}" \
  > "${WORK}/current-sign.log"

RELEASE_FINGERPRINT="$(
  grep \
    '^RELEASE_KEY_FINGERPRINT=' \
    "${WORK}/current-sign.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${RELEASE_FINGERPRINT}" \
  > "${RELEASE_PIN}"

WRONG_RELEASE="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_RELEASE}" == "${RELEASE_FINGERPRINT}" ]]; then
  WRONG_RELEASE="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_RELEASE}" \
  > "${WRONG_RELEASE_PIN}"

echo "RELEASE_KEY_FINGERPRINT=${RELEASE_FINGERPRINT}"

echo "[PASS] TWO_AUTHENTIC_SIGNED_RELEASES_READY"

echo
echo "===== VERIFY BOTH INDIVIDUAL RELEASES ====="

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  > "${WORK}/old-release-valid.log"

java \
  -jar "${RELEASE_VERIFIER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  > "${WORK}/current-release-valid.log"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${WORK}/old-release-valid.log"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${WORK}/current-release-valid.log"

echo "[PASS] OLD_AND_CURRENT_RELEASES_INDIVIDUALLY_AUTHENTIC"

echo
echo "===== APPEND OLD RELEASE TO TRANSPARENCY LEDGER ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${LEDGER}" \
  | tee "${WORK}/append-old.log"

grep \
  -q \
  '^SEQUENCE=1$' \
  "${WORK}/append-old.log"

FIRST_HEAD="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/append-old.log" \
    | cut -d= -f2-
)"

TRANSPARENCY_FINGERPRINT="$(
  grep \
    '^TRANSPARENCY_KEY_FINGERPRINT=' \
    "${WORK}/append-old.log" \
    | cut -d= -f2-
)"

echo "FIRST_TRANSPARENCY_HEAD=${FIRST_HEAD}"
echo "TRANSPARENCY_KEY_FINGERPRINT=${TRANSPARENCY_FINGERPRINT}"

echo "[PASS] OLD_RELEASE_TRANSPARENCY_ENTRY"

echo
echo "===== APPEND CURRENT RELEASE ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${LEDGER}" \
  | tee "${WORK}/append-current.log"

grep \
  -q \
  '^SEQUENCE=2$' \
  "${WORK}/append-current.log"

CURRENT_HEAD="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/append-current.log" \
    | cut -d= -f2-
)"

echo "CURRENT_TRANSPARENCY_HEAD=${CURRENT_HEAD}"

[[ "${CURRENT_HEAD}" != "${FIRST_HEAD}" ]]

printf '%s\n' \
  "${TRANSPARENCY_FINGERPRINT}" \
  > "${TRANSPARENCY_PIN}"

printf '%s\n' \
  "${CURRENT_HEAD}" \
  > "${HEAD_PIN}"

WRONG_TRANSPARENCY="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_TRANSPARENCY}" == "${TRANSPARENCY_FINGERPRINT}" ]]; then
  WRONG_TRANSPARENCY="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_TRANSPARENCY}" \
  > "${WRONG_TRANSPARENCY_PIN}"

echo "[PASS] CURRENT_RELEASE_TRANSPARENCY_ENTRY"

echo
echo "===== TRANSPARENCY LEDGER CONTRACT ====="

python3 \
  - "${LEDGER}" \
  "${FIRST_HEAD}" \
  "${CURRENT_HEAD}" \
<<'PY'
from pathlib import Path
import re
import sys

ledger = Path(
    sys.argv[1]
).read_text(
    encoding="utf-8",
)

first_head = sys.argv[2]
current_head = sys.argv[3]

blocks = [
    block
    for block in ledger.strip().split(
        "\n\n"
    )
    if block.strip()
]

assert len(blocks) == 2

first = blocks[0].splitlines()
second = blocks[1].splitlines()

assert len(first) == 12
assert len(second) == 12

assert "sequence=1" in first
assert "sequence=2" in second

assert "previousEntryHash=GENESIS" in first

assert (
    "previousEntryHash="
    + first_head
    in second
)

assert (
    "entryHash="
    + current_head
    in second
)

for value in (
    first_head,
    current_head,
):
    assert re.fullmatch(
        r"[0-9a-f]{64}",
        value,
    )

print(
    "[PASS] TRANSPARENCY_LEDGER_CONTRACT"
)
PY

echo
echo "===== DESTROY BOTH PRIVATE KEYS ====="

rm -f \
  "${RELEASE_PRIVATE}" \
  "${TRANSPARENCY_PRIVATE}"

[[ ! -e "${RELEASE_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]

echo "[PASS] PRIVATE_KEYS_DESTROYED_BEFORE_VERIFICATION"

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

  echo "[FAIL] TerraScope backend is still reachable."
  exit 1
fi

echo "[PASS] TERRASCOPE_BACKEND_OFFLINE"
echo "[PASS] POSTGRES_OFFLINE"

echo
echo "===== CURRENT RELEASE TRANSPARENCY VERIFICATION ====="

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  --ledger "${LEDGER}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --head-pin "${HEAD_PIN}" \
  | tee /tmp/terrascope-step46-current.log

grep -q '^RELEASE_MANIFEST_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^ARTIFACT_HASH_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^RELEASE_SIGNATURE_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^RELEASE_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^TRANSPARENCY_LEDGER_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^TRANSPARENCY_KEY_PIN_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^LATEST_RELEASE_BINDING_VALID=true$' \
  /tmp/terrascope-step46-current.log

grep -q '^LEDGER_ENTRY_COUNT=2$' \
  /tmp/terrascope-step46-current.log

grep -q '^VERIFIED_LEDGER_ENTRY_COUNT=2$' \
  /tmp/terrascope-step46-current.log

grep -q '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step46-current.log

echo "[PASS] CURRENT_RELEASE_TRANSPARENCY_VERIFICATION"

echo
echo "===== OLD AUTHENTIC RELEASE ROLLBACK ATTEMPT ====="

trap - ERR
set +e

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  --ledger "${LEDGER}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --head-pin "${HEAD_PIN}" \
  > /tmp/terrascope-step46-old-full-ledger.log \
  2>&1

ROLLBACK_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step46-old-full-ledger.log

echo "ROLLBACK_ATTEMPT_EXIT=${ROLLBACK_STATUS}"

[[ "${ROLLBACK_STATUS}" == "2" ]]

grep -q '^RELEASE_MANIFEST_VALID=true$' \
  /tmp/terrascope-step46-old-full-ledger.log

grep -q '^RELEASE_SIGNATURE_VALID=true$' \
  /tmp/terrascope-step46-old-full-ledger.log

grep -q '^TRANSPARENCY_LEDGER_VALID=true$' \
  /tmp/terrascope-step46-old-full-ledger.log

grep -q '^HEAD_PIN_VALID=true$' \
  /tmp/terrascope-step46-old-full-ledger.log

grep -q '^LATEST_RELEASE_BINDING_VALID=false$' \
  /tmp/terrascope-step46-old-full-ledger.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step46-old-full-ledger.log

echo "[PASS] AUTHENTIC_OLD_RELEASE_ROLLBACK_REJECTION"

echo
echo "===== TRUNCATED LEDGER REPLAY ATTEMPT ====="

python3 \
  - \
  "${LEDGER}" \
  "${TRUNCATED_LEDGER}" \
<<'PY'
from pathlib import Path
import sys

source = Path(
    sys.argv[1]
).read_text(
    encoding="utf-8",
)

target = Path(
    sys.argv[2]
)

blocks = [
    block
    for block in source.strip().split(
        "\n\n"
    )
    if block.strip()
]

assert len(blocks) == 2

target.write_text(
    blocks[0]
    + "\n\n",
    encoding="utf-8",
)
PY

trap - ERR
set +e

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  --ledger "${TRUNCATED_LEDGER}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --head-pin "${HEAD_PIN}" \
  > /tmp/terrascope-step46-truncated-ledger.log \
  2>&1

TRUNCATION_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step46-truncated-ledger.log

[[ "${TRUNCATION_STATUS}" == "2" ]]

grep -q '^TRANSPARENCY_LEDGER_VALID=true$' \
  /tmp/terrascope-step46-truncated-ledger.log

grep -q '^HEAD_PIN_VALID=false$' \
  /tmp/terrascope-step46-truncated-ledger.log

grep -q '^LATEST_RELEASE_BINDING_VALID=true$' \
  /tmp/terrascope-step46-truncated-ledger.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step46-truncated-ledger.log

echo "[PASS] TRANSPARENCY_LOG_TRUNCATION_REJECTION"

echo
echo "===== WRONG TRANSPARENCY KEY PIN ====="

trap - ERR
set +e

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  --ledger "${LEDGER}" \
  --transparency-key-pin "${WRONG_TRANSPARENCY_PIN}" \
  --head-pin "${HEAD_PIN}" \
  > /tmp/terrascope-step46-wrong-transparency-pin.log \
  2>&1

WRONG_TRANSPARENCY_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step46-wrong-transparency-pin.log

[[ "${WRONG_TRANSPARENCY_STATUS}" == "2" ]]

grep -q '^TRANSPARENCY_KEY_PIN_VALID=false$' \
  /tmp/terrascope-step46-wrong-transparency-pin.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step46-wrong-transparency-pin.log

echo "[PASS] WRONG_TRANSPARENCY_KEY_PIN_REJECTION"

echo
echo "===== WRONG RELEASE KEY PIN ====="

trap - ERR
set +e

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${WRONG_RELEASE_PIN}" \
  --ledger "${LEDGER}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --head-pin "${HEAD_PIN}" \
  > /tmp/terrascope-step46-wrong-release-pin.log \
  2>&1

WRONG_RELEASE_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step46-wrong-release-pin.log

[[ "${WRONG_RELEASE_STATUS}" == "2" ]]

grep -q '^RELEASE_KEY_PIN_VALID=false$' \
  /tmp/terrascope-step46-wrong-release-pin.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step46-wrong-release-pin.log

echo "[PASS] WRONG_RELEASE_KEY_PIN_REJECTION"

echo
echo "===== TRANSPARENCY LEDGER TAMPER ====="

python3 \
  - \
  "${LEDGER}" \
  "${TAMPERED_LEDGER}" \
<<'PY'
from pathlib import Path
import sys

source = Path(
    sys.argv[1]
).read_text(
    encoding="utf-8",
)

target = Path(
    sys.argv[2]
)

blocks = [
    block.splitlines()
    for block in source.strip().split(
        "\n\n"
    )
    if block.strip()
]

assert len(blocks) == 2

for index, line in enumerate(
    blocks[1]
):
    if line.startswith(
        "artifactSha256="
    ):
        blocks[1][index] = (
            "artifactSha256="
            + (
                "0" * 64
            )
        )

        break
else:
    raise AssertionError(
        "artifactSha256 not found"
    )

target.write_text(
    "\n\n".join(
        "\n".join(
            block
        )
        for block in blocks
    )
    + "\n\n",
    encoding="utf-8",
)
PY

trap - ERR
set +e

java \
  -jar "${TRANSPARENCY_JAR}" \
  verify \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${RELEASE_PIN}" \
  --ledger "${TAMPERED_LEDGER}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --head-pin "${HEAD_PIN}" \
  > /tmp/terrascope-step46-ledger-tamper.log \
  2>&1

LEDGER_TAMPER_STATUS=$?

set -e
trap cleanup EXIT

cat /tmp/terrascope-step46-ledger-tamper.log

[[ "${LEDGER_TAMPER_STATUS}" == "2" ]]

grep -q '^TRANSPARENCY_LEDGER_VALID=false$' \
  /tmp/terrascope-step46-ledger-tamper.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step46-ledger-tamper.log

echo "[PASS] TRANSPARENCY_LEDGER_TAMPER_DETECTION"

echo
echo "===== TRUE OFFLINE STATE PROOF ====="

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

  echo "[FAIL] Backend became reachable during transparency verification."
  exit 1
fi

[[ ! -e "${RELEASE_PRIVATE}" ]]
[[ ! -e "${TRANSPARENCY_PRIVATE}" ]]

echo "[PASS] TRUE_OFFLINE_ANTI_ROLLBACK_VERIFICATION"
echo "[PASS] PRIVATE_KEYS_REMAINED_ABSENT"

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
echo " TERRASCOPE — STEP 46 VERIFICATION PASSED"
echo "========================================================================"
