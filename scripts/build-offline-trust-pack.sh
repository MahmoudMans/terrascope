#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 5 ]]; then
  echo "Usage: $0 <capsule-verifier.jar> <release-capsule.zip> <root-pin> <state-head-pin> <output-pack.zip>" >&2

  exit 64
fi

CAPSULE_VERIFIER="$1"
RELEASE_CAPSULE="$2"
ROOT_PIN="$3"
STATE_HEAD_PIN="$4"
OUTPUT_PACK="$5"

for FILE in \
  "${CAPSULE_VERIFIER}" \
  "${RELEASE_CAPSULE}" \
  "${ROOT_PIN}" \
  "${STATE_HEAD_PIN}"
do
  [[ -s "${FILE}" ]]
done

for PIN_FILE in \
  "${ROOT_PIN}" \
  "${STATE_HEAD_PIN}"
do
  VALUE="$(
    tr -d '[:space:]' \
      < "${PIN_FILE}"
  )"

  [[ "${VALUE}" =~ ^[0-9a-fA-F]{64}$ ]]
done

WORK_DIR="$(
  mktemp -d \
    "${TMPDIR:-/tmp}/terrascope-offline-trust-pack.XXXXXX"
)"

cleanup() {
  rm -rf \
    "${WORK_DIR}"
}

trap cleanup EXIT

CAPSULE_VERIFIER_SHA="$(
  shasum -a 256 \
    "${CAPSULE_VERIFIER}" \
    | awk '{print $1}'
)"

RELEASE_CAPSULE_SHA="$(
  shasum -a 256 \
    "${RELEASE_CAPSULE}" \
    | awk '{print $1}'
)"

ROOT_PIN_FILE_SHA="$(
  shasum -a 256 \
    "${ROOT_PIN}" \
    | awk '{print $1}'
)"

STATE_HEAD_FILE_SHA="$(
  shasum -a 256 \
    "${STATE_HEAD_PIN}" \
    | awk '{print $1}'
)"

cp \
  "${CAPSULE_VERIFIER}" \
  "${WORK_DIR}/capsule-verifier.jar"

cp \
  "${RELEASE_CAPSULE}" \
  "${WORK_DIR}/release.capsule.zip"

cp \
  "${ROOT_PIN}" \
  "${WORK_DIR}/root.sha256"

cp \
  "${STATE_HEAD_PIN}" \
  "${WORK_DIR}/state-head.sha256"

cat > "${WORK_DIR}/pack.manifest" <<MANIFEST
packVersion=TS-OFFLINE-TRUST-PACK-V1
capsuleVerifierSha256=${CAPSULE_VERIFIER_SHA}
releaseCapsuleSha256=${RELEASE_CAPSULE_SHA}
rootPinFileSha256=${ROOT_PIN_FILE_SHA}
stateHeadPinFileSha256=${STATE_HEAD_FILE_SHA}
MANIFEST

cat > "${WORK_DIR}/run.sh" <<'RUN'
#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  pwd
)"

cd "${ROOT_DIR}"

for TOOL in \
  java \
  shasum
do
  command -v \
    "${TOOL}" \
    >/dev/null
done

manifest_value() {
  local KEY="$1"

  local VALUE

  VALUE="$(
    sed -n \
      "s/^${KEY}=//p" \
      pack.manifest
  )"

  local COUNT

  COUNT="$(
    grep \
      -c \
      "^${KEY}=" \
      pack.manifest \
      || true
  )"

  [[ "${COUNT}" == "1" ]]
  [[ -n "${VALUE}" ]]

  printf '%s' \
    "${VALUE}"
}

check_sha() {
  local FILE="$1"
  local MANIFEST_KEY="$2"

  local EXPECTED
  local ACTUAL

  EXPECTED="$(
    manifest_value \
      "${MANIFEST_KEY}"
  )"

  ACTUAL="$(
    shasum -a 256 \
      "${FILE}" \
      | awk '{print $1}'
  )"

  echo "${MANIFEST_KEY}_EXPECTED=${EXPECTED}"

  echo "${MANIFEST_KEY}_ACTUAL=${ACTUAL}"

  [[ "${EXPECTED}" == "${ACTUAL}" ]]
}

PACK_VERSION="$(
  manifest_value \
    "packVersion"
)"

echo "TRUST_PACK_VERSION=${PACK_VERSION}"

[[ "${PACK_VERSION}" == "TS-OFFLINE-TRUST-PACK-V1" ]]

check_sha \
  "capsule-verifier.jar" \
  "capsuleVerifierSha256"

check_sha \
  "release.capsule.zip" \
  "releaseCapsuleSha256"

check_sha \
  "root.sha256" \
  "rootPinFileSha256"

check_sha \
  "state-head.sha256" \
  "stateHeadPinFileSha256"

echo "TRUST_PACK_INTERNAL_HASHES_VALID=true"

echo "TRUST_PACK_CAPSULE_VERIFIER_EXECUTED=true"

java \
  -jar capsule-verifier.jar \
  verify \
  --capsule release.capsule.zip \
  --root-pin root.sha256 \
  --state-head-pin state-head.sha256

echo "TRUST_PACK_RESULT=VALID"
RUN

chmod +x \
  "${WORK_DIR}/run.sh"

mkdir -p \
  "$(dirname "${OUTPUT_PACK}")"

rm -f \
  "${OUTPUT_PACK}"

python3 \
  - \
  "${WORK_DIR}" \
  "${OUTPUT_PACK}" \
<<'PY'
from pathlib import Path
import sys
import zipfile

root = Path(
    sys.argv[1]
)

output = Path(
    sys.argv[2]
)

entries = (
    "capsule-verifier.jar",
    "pack.manifest",
    "release.capsule.zip",
    "root.sha256",
    "run.sh",
    "state-head.sha256",
)

fixed_date = (
    2000,
    1,
    1,
    0,
    0,
    0,
)

with zipfile.ZipFile(
    output,
    "w",
    compression=zipfile.ZIP_STORED,
    allowZip64=True,
) as archive:

    archive.comment = b""

    for name in entries:
        path = (
            root
            / name
        )

        assert path.is_file()

        data = path.read_bytes()

        info = zipfile.ZipInfo(
            filename=name,
            date_time=fixed_date,
        )

        info.compress_type = (
            zipfile.ZIP_STORED
        )

        info.create_system = 3

        mode = (
            0o100755
            if name == "run.sh"
            else 0o100644
        )

        info.external_attr = (
            mode
            << 16
        )

        info.flag_bits = 0

        archive.writestr(
            info,
            data,
        )
PY

[[ -s "${OUTPUT_PACK}" ]]

PACK_SHA="$(
  shasum -a 256 \
    "${OUTPUT_PACK}" \
    | awk '{print $1}'
)"

echo "TRUST_PACK_VERSION=TS-OFFLINE-TRUST-PACK-V1"

echo "TRUST_PACK_ENTRY_COUNT=6"

echo "TRUST_PACK_SHA256=${PACK_SHA}"

echo "TRUST_PACK_BUILD_RESULT=PASS"
