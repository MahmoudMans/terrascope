#!/usr/bin/env bash
set -Eeuo pipefail

MODE=""

if [[ "$#" -eq 5 ]]; then
  MODE="V1"

  CAPSULE_VERIFIER="$1"
  RELEASE_CAPSULE="$2"
  ROOT_PIN="$3"
  STATE_HEAD_PIN="$4"
  OUTPUT_PACK="$5"

  PACK_SEQUENCE=""
  SUBJECT_SYSTEM_VERSION=""
  BUILDER_AUTHORITY_HEAD=""
  PREDECESSOR_PACK_SHA256=""

elif [[ "$#" -eq 9 ]]; then
  MODE="V2"

  CAPSULE_VERIFIER="$1"
  RELEASE_CAPSULE="$2"
  ROOT_PIN="$3"
  STATE_HEAD_PIN="$4"
  PACK_SEQUENCE="$5"
  SUBJECT_SYSTEM_VERSION="$6"
  BUILDER_AUTHORITY_HEAD="$7"
  PREDECESSOR_PACK_SHA256="$8"
  OUTPUT_PACK="$9"

else
  echo "Legacy usage:" >&2
  echo "  $0 <capsule-verifier.jar> <release-capsule.zip> <root-pin> <state-head-pin> <output-pack.zip>" >&2
  echo >&2
  echo "V2 usage:" >&2
  echo "  $0 <capsule-verifier.jar> <release-capsule.zip> <root-pin> <state-head-pin> <sequence> <system-version> <builder-authority-head> <predecessor-sha256|GENESIS> <output-pack.zip>" >&2

  exit 64
fi

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

if [[ "${MODE}" == "V2" ]]; then

  [[ "${PACK_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]

  [[ "${SUBJECT_SYSTEM_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]

  [[ "${BUILDER_AUTHORITY_HEAD}" =~ ^[0-9a-fA-F]{40}$ ]]

  BUILDER_AUTHORITY_HEAD="$(
    printf '%s' "${BUILDER_AUTHORITY_HEAD}" \
      | tr '[:upper:]' '[:lower:]'
  )"

  if [[ "${PACK_SEQUENCE}" == "1" ]]; then
    [[ "${PREDECESSOR_PACK_SHA256}" == "GENESIS" ]]
  else
    [[ "${PREDECESSOR_PACK_SHA256}" =~ ^[0-9a-fA-F]{64}$ ]]

    PREDECESSOR_PACK_SHA256="$(
      printf '%s' "${PREDECESSOR_PACK_SHA256}" \
        | tr '[:upper:]' '[:lower:]'
    )"
  fi
fi

WORK_DIR="$(
  mktemp -d \
    "${TMPDIR:-/tmp}/terrascope-offline-trust-pack.XXXXXX"
)"

cleanup() {
  rm -rf "${WORK_DIR}"
}

trap cleanup EXIT

CAPSULE_VERIFIER_SHA="$(
  shasum -a 256 "${CAPSULE_VERIFIER}" \
    | awk '{print $1}'
)"

RELEASE_CAPSULE_SHA="$(
  shasum -a 256 "${RELEASE_CAPSULE}" \
    | awk '{print $1}'
)"

ROOT_PIN_FILE_SHA="$(
  shasum -a 256 "${ROOT_PIN}" \
    | awk '{print $1}'
)"

STATE_HEAD_FILE_SHA="$(
  shasum -a 256 "${STATE_HEAD_PIN}" \
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

if [[ "${MODE}" == "V1" ]]; then
  cat > "${WORK_DIR}/pack.manifest" <<MANIFEST
packVersion=TS-OFFLINE-TRUST-PACK-V1
capsuleVerifierSha256=${CAPSULE_VERIFIER_SHA}
releaseCapsuleSha256=${RELEASE_CAPSULE_SHA}
rootPinFileSha256=${ROOT_PIN_FILE_SHA}
stateHeadPinFileSha256=${STATE_HEAD_FILE_SHA}
MANIFEST
else
  cat > "${WORK_DIR}/pack.manifest" <<MANIFEST
packVersion=TS-OFFLINE-TRUST-PACK-V2
packSequence=${PACK_SEQUENCE}
subjectSystemVersion=${SUBJECT_SYSTEM_VERSION}
builderAuthorityHead=${BUILDER_AUTHORITY_HEAD}
predecessorPackSha256=${PREDECESSOR_PACK_SHA256}
capsuleVerifierSha256=${CAPSULE_VERIFIER_SHA}
releaseCapsuleSha256=${RELEASE_CAPSULE_SHA}
rootPinFileSha256=${ROOT_PIN_FILE_SHA}
stateHeadPinFileSha256=${STATE_HEAD_FILE_SHA}
MANIFEST
fi

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
  shasum \
  sed \
  grep
do
  command -v "${TOOL}" >/dev/null
done

manifest_value() {
  local KEY="$1"

  local COUNT
  local VALUE

  COUNT="$(
    grep \
      -c \
      "^${KEY}=" \
      pack.manifest \
      || true
  )"

  [[ "${COUNT}" == "1" ]]

  VALUE="$(
    sed -n \
      "s/^${KEY}=//p" \
      pack.manifest
  )"

  [[ -n "${VALUE}" ]]

  printf '%s' "${VALUE}"
}

check_sha() {
  local FILE="$1"
  local MANIFEST_KEY="$2"

  local EXPECTED
  local ACTUAL

  EXPECTED="$(
    manifest_value "${MANIFEST_KEY}"
  )"

  ACTUAL="$(
    shasum -a 256 "${FILE}" \
      | awk '{print $1}'
  )"

  echo "${MANIFEST_KEY}_EXPECTED=${EXPECTED}"
  echo "${MANIFEST_KEY}_ACTUAL=${ACTUAL}"

  [[ "${EXPECTED}" == "${ACTUAL}" ]]
}

PACK_VERSION="$(
  manifest_value packVersion
)"

echo "TRUST_PACK_VERSION=${PACK_VERSION}"

if [[ "${PACK_VERSION}" == "TS-OFFLINE-TRUST-PACK-V2" ]]; then
  echo "TRUST_PACK_SEQUENCE=$(manifest_value packSequence)"
  echo "TRUST_PACK_SUBJECT_SYSTEM_VERSION=$(manifest_value subjectSystemVersion)"
  echo "TRUST_PACK_BUILDER_AUTHORITY_HEAD=$(manifest_value builderAuthorityHead)"
  echo "TRUST_PACK_PREDECESSOR_SHA256=$(manifest_value predecessorPackSha256)"

elif [[ "${PACK_VERSION}" != "TS-OFFLINE-TRUST-PACK-V1" ]]; then
  echo "Unsupported trust-pack version." >&2

  exit 2
fi

check_sha \
  capsule-verifier.jar \
  capsuleVerifierSha256

check_sha \
  release.capsule.zip \
  releaseCapsuleSha256

check_sha \
  root.sha256 \
  rootPinFileSha256

check_sha \
  state-head.sha256 \
  stateHeadPinFileSha256

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

chmod +x "${WORK_DIR}/run.sh"

mkdir -p \
  "$(dirname "${OUTPUT_PACK}")"

rm -f "${OUTPUT_PACK}"

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
        path = root / name

        assert path.is_file()

        info = zipfile.ZipInfo(
            filename=name,
            date_time=fixed_date,
        )

        info.compress_type = zipfile.ZIP_STORED

        info.create_system = 3

        mode = (
            0o100755
            if name == "run.sh"
            else 0o100644
        )

        info.external_attr = (
            mode << 16
        )

        info.flag_bits = 0

        archive.writestr(
            info,
            path.read_bytes(),
        )
PY

[[ -s "${OUTPUT_PACK}" ]]

PACK_SHA="$(
  shasum -a 256 "${OUTPUT_PACK}" \
    | awk '{print $1}'
)"

if [[ "${MODE}" == "V1" ]]; then
  echo "TRUST_PACK_VERSION=TS-OFFLINE-TRUST-PACK-V1"
else
  echo "TRUST_PACK_VERSION=TS-OFFLINE-TRUST-PACK-V2"
  echo "TRUST_PACK_SEQUENCE=${PACK_SEQUENCE}"
  echo "TRUST_PACK_SUBJECT_SYSTEM_VERSION=${SUBJECT_SYSTEM_VERSION}"
  echo "TRUST_PACK_BUILDER_AUTHORITY_HEAD=${BUILDER_AUTHORITY_HEAD}"
  echo "TRUST_PACK_PREDECESSOR_SHA256=${PREDECESSOR_PACK_SHA256}"
fi

echo "TRUST_PACK_ENTRY_COUNT=6"
echo "TRUST_PACK_SHA256=${PACK_SHA}"
echo "TRUST_PACK_BUILD_RESULT=PASS"
