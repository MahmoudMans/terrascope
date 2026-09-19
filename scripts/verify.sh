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

BACKEND_LOG="/tmp/terrascope-step50-backend.log"

WORK="/tmp/terrascope-step50-work"

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
INTERMEDIATE_PIN="${WORK}/intermediate.sha256"

TERMINAL_PRIVATE="${WORK}/terminal-private.b64"
TERMINAL_PUBLIC="${WORK}/terminal-public.b64"
TERMINAL_PIN="${WORK}/terminal.sha256"

TRANSPARENCY_PRIVATE="${WORK}/transparency-private.b64"
TRANSPARENCY_PUBLIC="${WORK}/transparency-public.b64"
TRANSPARENCY_PIN="${WORK}/transparency.sha256"

AUTHORITY_LINEAGE="${WORK}/release-authority.lineage"
OLD_AUTHORITY_LINEAGE="${OLD_DIR}/release-authority.lineage"

AUTHORITY_HEAD_ONE="${WORK}/authority-head-one.sha256"
AUTHORITY_HEAD_TWO="${WORK}/authority-head-two.sha256"

TRANSPARENCY_LEDGER="${WORK}/release-transparency.log"
OLD_TRANSPARENCY_LEDGER="${OLD_DIR}/release-transparency.log"

TRANSPARENCY_HEAD_ONE="${WORK}/transparency-head-one.sha256"
TRANSPARENCY_HEAD_TWO="${WORK}/transparency-head-two.sha256"

OLD_STATE_HEAD="${WORK}/old-state-head.sha256"
CURRENT_STATE_HEAD="${WORK}/current-state-head.sha256"

SIGNER_JAR="${TOOLS}/terrascope-verifier-release-signer.jar"

RELEASE_VERIFIER_JAR="${TOOLS}/terrascope-verifier-release-verifier.jar"

TRANSPARENCY_JAR="${TOOLS}/terrascope-verifier-release-transparency.jar"

AUTHORITY_JAR="${TOOLS}/terrascope-verifier-release-authority-lineage.jar"

TRUST_STATE_JAR="${TOOLS}/terrascope-verifier-release-trust-state.jar"

CAPSULE_JAR="${TOOLS}/terrascope-verifier-release-capsule.jar"

CURRENT_CAPSULE_A="/tmp/terrascope-step50-current-a.capsule.zip"

CURRENT_CAPSULE_B="/tmp/terrascope-step50-current-b.capsule.zip"

STALE_CAPSULE="/tmp/terrascope-step50-stale.capsule.zip"

CAPSULE_TOOL_EXTERNAL="/tmp/terrascope-step50-capsule-verifier.jar"

ROOT_PIN_EXTERNAL="/tmp/terrascope-step50-root.sha256"

CURRENT_STATE_HEAD_EXTERNAL="/tmp/terrascope-step50-current-state.sha256"

OLD_STATE_HEAD_EXTERNAL="/tmp/terrascope-step50-old-state.sha256"

WRONG_ROOT_EXTERNAL="/tmp/terrascope-step50-wrong-root.sha256"

ARTIFACT_TAMPER_CAPSULE="/tmp/terrascope-step50-artifact-tampered.capsule.zip"

TOOL_TAMPER_CAPSULE="/tmp/terrascope-step50-tool-tampered.capsule.zip"

SIGNATURE_TAMPER_CAPSULE="/tmp/terrascope-step50-signature-tampered.capsule.zip"

KEYGEN_SOURCE="${WORK}/TerraScopeStep50Keygen.java"

ROOT_KEY_ID="terrascope-release-authority-v1"

INTERMEDIATE_KEY_ID="terrascope-release-authority-v2"

TERMINAL_KEY_ID="terrascope-release-authority-v3"

TRANSPARENCY_KEY_ID="terrascope-transparency-step50-v1"

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
echo " TERRASCOPE — STEP 50 VERIFICATION"
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
  > /tmp/terrascope-step50-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step50-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.56.0"

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
  49 \
  50
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

rm -f \
  "${CURRENT_CAPSULE_A}" \
  "${CURRENT_CAPSULE_B}" \
  "${STALE_CAPSULE}" \
  "${CAPSULE_TOOL_EXTERNAL}" \
  "${ROOT_PIN_EXTERNAL}" \
  "${CURRENT_STATE_HEAD_EXTERNAL}" \
  "${OLD_STATE_HEAD_EXTERNAL}" \
  "${WRONG_ROOT_EXTERNAL}" \
  "${ARTIFACT_TAMPER_CAPSULE}" \
  "${TOOL_TAMPER_CAPSULE}" \
  "${SIGNATURE_TAMPER_CAPSULE}"

mkdir -p \
  "${TOOLS}" \
  "${OLD_DIR}" \
  "${CURRENT_DIR}"

echo "[PASS] FIXTURE_READY"

echo
echo "===== BUILD SIX RELEASE TOOLS ====="

bash \
  tools/verifier-release-integrity/build.sh \
  "${TOOLS}"

[[ -s "${SIGNER_JAR}" ]]
[[ -s "${RELEASE_VERIFIER_JAR}" ]]
[[ -s "${TRANSPARENCY_JAR}" ]]
[[ -s "${AUTHORITY_JAR}" ]]
[[ -s "${TRUST_STATE_JAR}" ]]
[[ -s "${CAPSULE_JAR}" ]]

echo "[PASS] SIX_RELEASE_TOOLS_READY"

echo
echo "===== BUILD OLD + CURRENT CHECKPOINT VERIFIER ARTIFACTS ====="

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
echo "===== GENERATE ROOT / INTERMEDIATE / TERMINAL / TRANSPARENCY KEYS ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;

import java.util.Base64;
import java.util.HexFormat;

public class TerraScopeStep50Keygen {

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
  "${INTERMEDIATE_PIN}" \
  "${TERMINAL_PRIVATE}" \
  "${TERMINAL_PUBLIC}" \
  "${TERMINAL_PIN}" \
  "${TRANSPARENCY_PRIVATE}" \
  "${TRANSPARENCY_PUBLIC}" \
  "${TRANSPARENCY_PIN}"

ROOT_FINGERPRINT="$(
  tr -d '[:space:]' \
    < "${ROOT_PIN}"
)"

echo "ROOT_FINGERPRINT=${ROOT_FINGERPRINT}"

echo "[PASS] FOUR_KEY_AUTHORITIES_READY"

echo
echo "===== HISTORICAL AUTHORITY TRANSITION ====="

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
  > "${WORK}/authority-one.log"

AUTHORITY_ONE_HASH="$(
  grep \
    '^TRANSITION_HASH=' \
    "${WORK}/authority-one.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${AUTHORITY_ONE_HASH}" \
  > "${AUTHORITY_HEAD_ONE}"

cp \
  "${AUTHORITY_LINEAGE}" \
  "${OLD_AUTHORITY_LINEAGE}"

echo "[PASS] HISTORICAL_AUTHORITY_STATE_READY"

echo
echo "===== HISTORICAL RELEASE + TRANSPARENCY ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${OLD_ARTIFACT}" \
  --private-key "${INTERMEDIATE_PRIVATE}" \
  --public-key "${INTERMEDIATE_PUBLIC}" \
  --release-key-id "${INTERMEDIATE_KEY_ID}" \
  --output "${OLD_MANIFEST}" \
  > "${WORK}/old-sign.log"

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${OLD_ARTIFACT}" \
  --manifest "${OLD_MANIFEST}" \
  --release-key-pin "${INTERMEDIATE_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/old-transparency.log"

TRANSPARENCY_ONE_HASH="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/old-transparency.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${TRANSPARENCY_ONE_HASH}" \
  > "${TRANSPARENCY_HEAD_ONE}"

cp \
  "${TRANSPARENCY_LEDGER}" \
  "${OLD_TRANSPARENCY_LEDGER}"

echo "[PASS] HISTORICAL_RELEASE_TRANSPARENCY_READY"

echo
echo "===== HISTORICAL TRUST STATE ====="

java \
  -jar "${TRUST_STATE_JAR}" \
  issue \
  --state-sequence 1 \
  --release-root-key-id "${ROOT_KEY_ID}" \
  --release-root-private-key "${ROOT_PRIVATE}" \
  --release-root-public-key "${ROOT_PUBLIC}" \
  --release-authority-head-pin "${AUTHORITY_HEAD_ONE}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --transparency-head-pin "${TRANSPARENCY_HEAD_ONE}" \
  --output "${OLD_STATE}" \
  > "${WORK}/old-state.log"

OLD_STATE_HASH="$(
  grep \
    '^STATE_HASH=' \
    "${WORK}/old-state.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${OLD_STATE_HASH}" \
  > "${OLD_STATE_HEAD}"

echo "OLD_TRUST_STATE_HASH=${OLD_STATE_HASH}"

echo "[PASS] HISTORICAL_TRUST_STATE_READY"

echo
echo "===== CREATE HISTORICAL CAPSULE ====="

java \
  -jar "${CAPSULE_JAR}" \
  create \
  --artifact "${OLD_ARTIFACT}" \
  --release-manifest "${OLD_MANIFEST}" \
  --authority-lineage "${OLD_AUTHORITY_LINEAGE}" \
  --transparency-ledger "${OLD_TRANSPARENCY_LEDGER}" \
  --trust-state "${OLD_STATE}" \
  --release-verifier-jar "${RELEASE_VERIFIER_JAR}" \
  --transparency-verifier-jar "${TRANSPARENCY_JAR}" \
  --authority-verifier-jar "${AUTHORITY_JAR}" \
  --trust-state-verifier-jar "${TRUST_STATE_JAR}" \
  --release-root-key-id "${ROOT_KEY_ID}" \
  --release-root-private-key "${ROOT_PRIVATE}" \
  --release-root-public-key "${ROOT_PUBLIC}" \
  --root-pin "${ROOT_PIN}" \
  --output "${STALE_CAPSULE}" \
  > "${WORK}/stale-capsule-create.log"

grep \
  -q \
  '^CREATE_RESULT=PASS$' \
  "${WORK}/stale-capsule-create.log"

echo "[PASS] HISTORICAL_CAPSULE_CREATED"

echo
echo "===== CURRENT AUTHORITY TRANSITION ====="

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
  > "${WORK}/authority-two.log"

AUTHORITY_TWO_HASH="$(
  grep \
    '^TRANSITION_HASH=' \
    "${WORK}/authority-two.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${AUTHORITY_TWO_HASH}" \
  > "${AUTHORITY_HEAD_TWO}"

[[ "${AUTHORITY_ONE_HASH}" != "${AUTHORITY_TWO_HASH}" ]]

echo "[PASS] CURRENT_AUTHORITY_STATE_READY"

echo
echo "===== CURRENT RELEASE + TRANSPARENCY ====="

java \
  -jar "${SIGNER_JAR}" \
  --artifact "${CURRENT_ARTIFACT}" \
  --private-key "${TERMINAL_PRIVATE}" \
  --public-key "${TERMINAL_PUBLIC}" \
  --release-key-id "${TERMINAL_KEY_ID}" \
  --output "${CURRENT_MANIFEST}" \
  > "${WORK}/current-sign.log"

java \
  -jar "${TRANSPARENCY_JAR}" \
  append \
  --artifact "${CURRENT_ARTIFACT}" \
  --manifest "${CURRENT_MANIFEST}" \
  --release-key-pin "${TERMINAL_PIN}" \
  --transparency-private-key "${TRANSPARENCY_PRIVATE}" \
  --transparency-public-key "${TRANSPARENCY_PUBLIC}" \
  --transparency-key-id "${TRANSPARENCY_KEY_ID}" \
  --ledger "${TRANSPARENCY_LEDGER}" \
  > "${WORK}/current-transparency.log"

TRANSPARENCY_TWO_HASH="$(
  grep \
    '^ENTRY_HASH=' \
    "${WORK}/current-transparency.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${TRANSPARENCY_TWO_HASH}" \
  > "${TRANSPARENCY_HEAD_TWO}"

[[ "${TRANSPARENCY_ONE_HASH}" != "${TRANSPARENCY_TWO_HASH}" ]]

echo "[PASS] CURRENT_RELEASE_TRANSPARENCY_READY"

echo
echo "===== CURRENT TRUST STATE ====="

java \
  -jar "${TRUST_STATE_JAR}" \
  issue \
  --state-sequence 2 \
  --release-root-key-id "${ROOT_KEY_ID}" \
  --release-root-private-key "${ROOT_PRIVATE}" \
  --release-root-public-key "${ROOT_PUBLIC}" \
  --release-authority-head-pin "${AUTHORITY_HEAD_TWO}" \
  --transparency-key-pin "${TRANSPARENCY_PIN}" \
  --transparency-head-pin "${TRANSPARENCY_HEAD_TWO}" \
  --output "${CURRENT_STATE}" \
  > "${WORK}/current-state.log"

CURRENT_STATE_HASH="$(
  grep \
    '^STATE_HASH=' \
    "${WORK}/current-state.log" \
    | cut -d= -f2-
)"

printf '%s\n' \
  "${CURRENT_STATE_HASH}" \
  > "${CURRENT_STATE_HEAD}"

echo "CURRENT_TRUST_STATE_HASH=${CURRENT_STATE_HASH}"

[[ "${CURRENT_STATE_HASH}" != "${OLD_STATE_HASH}" ]]

echo "[PASS] CURRENT_TRUST_STATE_READY"

echo
echo "===== CREATE CURRENT CAPSULE TWICE ====="

for OUTPUT in \
  "${CURRENT_CAPSULE_A}" \
  "${CURRENT_CAPSULE_B}"
do
  java \
    -jar "${CAPSULE_JAR}" \
    create \
    --artifact "${CURRENT_ARTIFACT}" \
    --release-manifest "${CURRENT_MANIFEST}" \
    --authority-lineage "${AUTHORITY_LINEAGE}" \
    --transparency-ledger "${TRANSPARENCY_LEDGER}" \
    --trust-state "${CURRENT_STATE}" \
    --release-verifier-jar "${RELEASE_VERIFIER_JAR}" \
    --transparency-verifier-jar "${TRANSPARENCY_JAR}" \
    --authority-verifier-jar "${AUTHORITY_JAR}" \
    --trust-state-verifier-jar "${TRUST_STATE_JAR}" \
    --release-root-key-id "${ROOT_KEY_ID}" \
    --release-root-private-key "${ROOT_PRIVATE}" \
    --release-root-public-key "${ROOT_PUBLIC}" \
    --root-pin "${ROOT_PIN}" \
    --output "${OUTPUT}"
done

CURRENT_CAPSULE_SHA_A="$(
  shasum -a 256 \
    "${CURRENT_CAPSULE_A}" \
    | awk '{print $1}'
)"

CURRENT_CAPSULE_SHA_B="$(
  shasum -a 256 \
    "${CURRENT_CAPSULE_B}" \
    | awk '{print $1}'
)"

echo "CURRENT_CAPSULE_SHA_A=${CURRENT_CAPSULE_SHA_A}"
echo "CURRENT_CAPSULE_SHA_B=${CURRENT_CAPSULE_SHA_B}"

[[ "${CURRENT_CAPSULE_SHA_A}" == "${CURRENT_CAPSULE_SHA_B}" ]]

echo "[PASS] DETERMINISTIC_CAPSULE_REPRODUCIBILITY"

echo
echo "===== CAPSULE ENTRY CONTRACT ====="

python3 \
  - "${CURRENT_CAPSULE_A}" \
<<'PY'
import sys
import zipfile

expected = {
    "capsule.manifest",

    "payload/terrascope-checkpoint-bundle-verifier.jar",

    "payload/release.manifest",

    "payload/release-authority.lineage",

    "payload/release-transparency.log",

    "payload/trust-state.manifest",

    "tools/terrascope-verifier-release-verifier.jar",

    "tools/terrascope-verifier-release-transparency.jar",

    "tools/terrascope-verifier-release-authority-lineage.jar",

    "tools/terrascope-verifier-release-trust-state.jar",
}

with zipfile.ZipFile(
    sys.argv[1],
    "r",
) as archive:
    names = archive.namelist()

assert len(names) == 10
assert len(set(names)) == 10
assert set(names) == expected

with zipfile.ZipFile(
    sys.argv[1],
    "r",
) as archive:
    manifest = archive.read(
        "capsule.manifest"
    ).decode(
        "utf-8"
    )

assert (
    "capsuleVersion="
    "TS-VERIFIER-RELEASE-CAPSULE-V1\n"
    in manifest
)

assert (
    "algorithm=ED25519\n"
    in manifest
)

print(
    "[PASS] CAPSULE_ENTRY_CONTRACT"
)
PY

echo
echo "===== PRESERVE ONLY EXTERNAL VERIFY INPUTS ====="

cp \
  "${CAPSULE_JAR}" \
  "${CAPSULE_TOOL_EXTERNAL}"

cp \
  "${ROOT_PIN}" \
  "${ROOT_PIN_EXTERNAL}"

cp \
  "${CURRENT_STATE_HEAD}" \
  "${CURRENT_STATE_HEAD_EXTERNAL}"

cp \
  "${OLD_STATE_HEAD}" \
  "${OLD_STATE_HEAD_EXTERNAL}"

WRONG_ROOT="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_ROOT}" == "${ROOT_FINGERPRINT}" ]]; then
  WRONG_ROOT="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_ROOT}" \
  > "${WRONG_ROOT_EXTERNAL}"

[[ -s "${CAPSULE_TOOL_EXTERNAL}" ]]
[[ -s "${ROOT_PIN_EXTERNAL}" ]]
[[ -s "${CURRENT_STATE_HEAD_EXTERNAL}" ]]

echo "[PASS] EXTERNAL_VERIFY_INPUTS_PRESERVED"

echo
echo "===== BUILD NEGATIVE CAPSULE FIXTURES ====="

python3 \
  - \
  "${CURRENT_CAPSULE_A}" \
  "${ARTIFACT_TAMPER_CAPSULE}" \
  "${TOOL_TAMPER_CAPSULE}" \
  "${SIGNATURE_TAMPER_CAPSULE}" \
<<'PY'
import base64
import sys
import zipfile

source = sys.argv[1]

artifact_target = sys.argv[2]
tool_target = sys.argv[3]
signature_target = sys.argv[4]

with zipfile.ZipFile(
    source,
    "r",
) as archive:
    entries = {
        name:
            archive.read(
                name
            )
        for name in archive.namelist()
    }

artifact_entries = dict(
    entries
)

artifact_entries[
    "payload/terrascope-checkpoint-bundle-verifier.jar"
] += b"\x00"

with zipfile.ZipFile(
    artifact_target,
    "w",
    compression=zipfile.ZIP_STORED,
) as archive:
    for name in sorted(
        artifact_entries
    ):
        archive.writestr(
            name,
            artifact_entries[name],
        )

tool_entries = dict(
    entries
)

tool_entries[
    "tools/terrascope-verifier-release-verifier.jar"
] += b"\x00"

with zipfile.ZipFile(
    tool_target,
    "w",
    compression=zipfile.ZIP_STORED,
) as archive:
    for name in sorted(
        tool_entries
    ):
        archive.writestr(
            name,
            tool_entries[name],
        )

signature_entries = dict(
    entries
)

manifest = signature_entries[
    "capsule.manifest"
].decode(
    "utf-8"
).splitlines()

for index, line in enumerate(
    manifest
):
    if line.startswith(
        "signatureBase64="
    ):
        manifest[index] = (
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

signature_entries[
    "capsule.manifest"
] = (
    "\n".join(
        manifest
    )
    + "\n"
).encode(
    "utf-8"
)

with zipfile.ZipFile(
    signature_target,
    "w",
    compression=zipfile.ZIP_STORED,
) as archive:
    for name in sorted(
        signature_entries
    ):
        archive.writestr(
            name,
            signature_entries[name],
        )

print(
    "[PASS] NEGATIVE_CAPSULE_FIXTURES_READY"
)
PY

echo
echo "===== DESTROY ALL SOURCE FIXTURE MATERIAL ====="

rm -rf \
  "${WORK}"

[[ ! -e "${WORK}" ]]

echo "[PASS] PRIVATE_KEYS_DESTROYED"
echo "[PASS] SOURCE_PAYLOADS_REMOVED"
echo "[PASS] SOURCE_HELPER_JARS_REMOVED"

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
echo "===== VERIFY CURRENT PORTABLE CAPSULE ====="

java \
  -jar "${CAPSULE_TOOL_EXTERNAL}" \
  verify \
  --capsule "${CURRENT_CAPSULE_A}" \
  --root-pin "${ROOT_PIN_EXTERNAL}" \
  --state-head-pin "${CURRENT_STATE_HEAD_EXTERNAL}" \
  | tee /tmp/terrascope-step50-current.log

grep -q '^CAPSULE_STRUCTURE_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^CAPSULE_MANIFEST_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^ROOT_FINGERPRINT_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^CAPSULE_SIGNATURE_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^CAPSULE_ENTRY_HASHES_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^TRUST_STATE_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^AUTHORITY_LINEAGE_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^RELEASE_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^TRANSPARENCY_VALID=true$' \
  /tmp/terrascope-step50-current.log

grep -q '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step50-current.log

echo "[PASS] CURRENT_CAPSULE_OFFLINE_VERIFICATION"

echo
echo "===== HISTORICAL CAPSULE WITH HISTORICAL STATE HASH ====="

java \
  -jar "${CAPSULE_TOOL_EXTERNAL}" \
  verify \
  --capsule "${STALE_CAPSULE}" \
  --root-pin "${ROOT_PIN_EXTERNAL}" \
  --state-head-pin "${OLD_STATE_HEAD_EXTERNAL}" \
  | tee /tmp/terrascope-step50-stale-historical.log

grep -q '^CAPSULE_SIGNATURE_VALID=true$' \
  /tmp/terrascope-step50-stale-historical.log

grep -q '^CAPSULE_ENTRY_HASHES_VALID=true$' \
  /tmp/terrascope-step50-stale-historical.log

grep -q '^TRUST_STATE_VALID=true$' \
  /tmp/terrascope-step50-stale-historical.log

grep -q '^AUTHORITY_LINEAGE_VALID=true$' \
  /tmp/terrascope-step50-stale-historical.log

grep -q '^RELEASE_VALID=true$' \
  /tmp/terrascope-step50-stale-historical.log

grep -q '^TRANSPARENCY_VALID=true$' \
  /tmp/terrascope-step50-stale-historical.log

grep -q '^VERIFICATION_RESULT=VALID$' \
  /tmp/terrascope-step50-stale-historical.log

echo "[PASS] HISTORICAL_CAPSULE_IS_AUTHENTIC"

echo
echo "===== REJECT HISTORICAL CAPSULE AGAINST CURRENT TRUST STATE ====="

set +e

java \
  -jar "${CAPSULE_TOOL_EXTERNAL}" \
  verify \
  --capsule "${STALE_CAPSULE}" \
  --root-pin "${ROOT_PIN_EXTERNAL}" \
  --state-head-pin "${CURRENT_STATE_HEAD_EXTERNAL}" \
  > /tmp/terrascope-step50-stale-current.log \
  2>&1

STALE_STATUS=$?

set -e

cat \
  /tmp/terrascope-step50-stale-current.log

echo "STALE_CAPSULE_CURRENT_STATE_EXIT=${STALE_STATUS}"

[[ "${STALE_STATUS}" == "2" ]]

grep -q '^CAPSULE_SIGNATURE_VALID=true$' \
  /tmp/terrascope-step50-stale-current.log

grep -q '^CAPSULE_ENTRY_HASHES_VALID=true$' \
  /tmp/terrascope-step50-stale-current.log

grep -q '^TRUST_STATE_VALID=false$' \
  /tmp/terrascope-step50-stale-current.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step50-stale-current.log

echo "[PASS] STALE_CAPSULE_ROLLBACK_REJECTION"

echo
echo "===== WRONG ROOT PIN ====="

set +e

java \
  -jar "${CAPSULE_TOOL_EXTERNAL}" \
  verify \
  --capsule "${CURRENT_CAPSULE_A}" \
  --root-pin "${WRONG_ROOT_EXTERNAL}" \
  --state-head-pin "${CURRENT_STATE_HEAD_EXTERNAL}" \
  > /tmp/terrascope-step50-wrong-root.log \
  2>&1

WRONG_ROOT_STATUS=$?

set -e

cat \
  /tmp/terrascope-step50-wrong-root.log

[[ "${WRONG_ROOT_STATUS}" == "2" ]]

grep -q '^ROOT_PIN_VALID=false$' \
  /tmp/terrascope-step50-wrong-root.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step50-wrong-root.log

echo "[PASS] WRONG_CAPSULE_ROOT_PIN_REJECTION"

echo
echo "===== ARTIFACT TAMPER ====="

set +e

java \
  -jar "${CAPSULE_TOOL_EXTERNAL}" \
  verify \
  --capsule "${ARTIFACT_TAMPER_CAPSULE}" \
  --root-pin "${ROOT_PIN_EXTERNAL}" \
  --state-head-pin "${CURRENT_STATE_HEAD_EXTERNAL}" \
  > /tmp/terrascope-step50-artifact-tamper.log \
  2>&1

ARTIFACT_TAMPER_STATUS=$?

set -e

cat \
  /tmp/terrascope-step50-artifact-tamper.log

[[ "${ARTIFACT_TAMPER_STATUS}" == "2" ]]

grep -q '^CAPSULE_SIGNATURE_VALID=true$' \
  /tmp/terrascope-step50-artifact-tamper.log

grep -q '^CAPSULE_ENTRY_HASHES_VALID=false$' \
  /tmp/terrascope-step50-artifact-tamper.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step50-artifact-tamper.log

echo "[PASS] CAPSULE_ARTIFACT_TAMPER_DETECTION"

echo
echo "===== EMBEDDED TOOL TAMPER ====="

set +e

java \
  -jar "${CAPSULE_TOOL_EXTERNAL}" \
  verify \
  --capsule "${TOOL_TAMPER_CAPSULE}" \
  --root-pin "${ROOT_PIN_EXTERNAL}" \
  --state-head-pin "${CURRENT_STATE_HEAD_EXTERNAL}" \
  > /tmp/terrascope-step50-tool-tamper.log \
  2>&1

TOOL_TAMPER_STATUS=$?

set -e

cat \
  /tmp/terrascope-step50-tool-tamper.log

[[ "${TOOL_TAMPER_STATUS}" == "2" ]]

grep -q '^CAPSULE_SIGNATURE_VALID=true$' \
  /tmp/terrascope-step50-tool-tamper.log

grep -q '^CAPSULE_ENTRY_HASHES_VALID=false$' \
  /tmp/terrascope-step50-tool-tamper.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step50-tool-tamper.log

echo "[PASS] CAPSULE_EMBEDDED_TOOL_TAMPER_DETECTION"

echo
echo "===== CAPSULE SIGNATURE TAMPER ====="

set +e

java \
  -jar "${CAPSULE_TOOL_EXTERNAL}" \
  verify \
  --capsule "${SIGNATURE_TAMPER_CAPSULE}" \
  --root-pin "${ROOT_PIN_EXTERNAL}" \
  --state-head-pin "${CURRENT_STATE_HEAD_EXTERNAL}" \
  > /tmp/terrascope-step50-signature-tamper.log \
  2>&1

SIGNATURE_TAMPER_STATUS=$?

set -e

cat \
  /tmp/terrascope-step50-signature-tamper.log

[[ "${SIGNATURE_TAMPER_STATUS}" == "2" ]]

grep -q '^ROOT_PIN_VALID=true$' \
  /tmp/terrascope-step50-signature-tamper.log

grep -q '^CAPSULE_SIGNATURE_VALID=false$' \
  /tmp/terrascope-step50-signature-tamper.log

grep -q '^CAPSULE_ENTRY_HASHES_VALID=true$' \
  /tmp/terrascope-step50-signature-tamper.log

grep -q '^VERIFICATION_RESULT=INVALID$' \
  /tmp/terrascope-step50-signature-tamper.log

echo "[PASS] CAPSULE_SIGNATURE_TAMPER_DETECTION"

echo
echo "===== TRUE PORTABILITY + AIR GAP ====="

[[ ! -e "${WORK}" ]]

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

  echo "[FAIL] Backend became reachable during Step 50 verification."
  exit 1
fi

echo "[PASS] CAPSULE_SOURCE_STATE_ABSENT"
echo "[PASS] TRUE_OFFLINE_PORTABLE_CAPSULE_VERIFICATION"
echo "[PASS] ONLY_CAPSULE_ROOT_PIN_AND_STATE_HASH_REQUIRED"

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
echo " TERRASCOPE — STEP 50 VERIFICATION PASSED"
echo "========================================================================"
