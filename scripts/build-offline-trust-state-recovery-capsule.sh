#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 6 ]]; then
  echo "Usage: $0 <head-file> <seal-file> <seal-pin> <head-advancer> <head-sealer> <output-recovery.zip>" >&2

  exit 64
fi

HEAD_FILE="$1"
SEAL_FILE="$2"
SEAL_PIN="$3"
HEAD_ADVANCER="$4"
HEAD_SEALER="$5"
OUTPUT_CAPSULE="$6"

LEDGER_FILE="${HEAD_FILE}.ledger"

RECOVERY_VERSION="TS-OFFLINE-TRUST-STATE-RECOVERY-CAPSULE-V1"

for TOOL in \
  bash \
  shasum \
  python3
do
  command -v "${TOOL}" >/dev/null
done

for FILE in \
  "${HEAD_FILE}" \
  "${LEDGER_FILE}" \
  "${SEAL_FILE}" \
  "${SEAL_PIN}" \
  "${HEAD_ADVANCER}" \
  "${HEAD_SEALER}"
do
  [[ -s "${FILE}" ]]
done

[[ -x "${HEAD_ADVANCER}" ]]
[[ -x "${HEAD_SEALER}" ]]

VERIFY_OUTPUT=""
VERIFY_STATUS=0

if VERIFY_OUTPUT="$(
  bash \
    "${HEAD_SEALER}" \
    verify \
    "${HEAD_FILE}" \
    "${SEAL_FILE}" \
    "${SEAL_PIN}" \
    "${HEAD_ADVANCER}" \
    2>&1
)"
then
  VERIFY_STATUS=0
else
  VERIFY_STATUS=$?
fi

printf '%s\n' "${VERIFY_OUTPUT}"

echo "RECOVERY_SOURCE_SEAL_VERIFY_EXIT=${VERIFY_STATUS}"

[[ "${VERIFY_STATUS}" == "0" ]]

printf '%s\n' "${VERIFY_OUTPUT}" \
  | grep \
    -q \
    '^TRUST_HEAD_SEAL_RESULT=VALID$'

head_value() {
  local KEY="$1"

  local COUNT
  local VALUE

  COUNT="$(
    grep \
      -c \
      "^${KEY}=" \
      "${HEAD_FILE}" \
      || true
  )"

  [[ "${COUNT}" == "1" ]]

  VALUE="$(
    sed -n \
      "s/^${KEY}=//p" \
      "${HEAD_FILE}"
  )"

  [[ -n "${VALUE}" ]]

  printf '%s' "${VALUE}"
}

sha256_file() {
  shasum -a 256 \
    "$1" \
    | awk '{print $1}'
}

ACCEPTED_SEQUENCE="$(
  head_value acceptedSequence
)"

HEAD_SHA="$(
  sha256_file "${HEAD_FILE}"
)"

LEDGER_SHA="$(
  sha256_file "${LEDGER_FILE}"
)"

SEAL_SHA="$(
  sha256_file "${SEAL_FILE}"
)"

SEAL_PIN_FILE_SHA="$(
  sha256_file "${SEAL_PIN}"
)"

HEAD_ADVANCER_SHA="$(
  sha256_file "${HEAD_ADVANCER}"
)"

HEAD_SEALER_SHA="$(
  sha256_file "${HEAD_SEALER}"
)"

SEAL_PIN_VALUE="$(
  tr -d '[:space:]' \
    < "${SEAL_PIN}"
)"

[[ "${SEAL_PIN_VALUE}" == "${SEAL_SHA}" ]]

WORK_DIR="$(
  mktemp -d \
    "${TMPDIR:-/tmp}/terrascope-recovery-build.XXXXXX"
)"

cleanup() {
  rm -rf "${WORK_DIR}"
}

trap cleanup EXIT

cp \
  "${HEAD_FILE}" \
  "${WORK_DIR}/trust.head"

cp \
  "${LEDGER_FILE}" \
  "${WORK_DIR}/trust.head.ledger"

cp \
  "${SEAL_FILE}" \
  "${WORK_DIR}/trust.seal"

cp \
  "${SEAL_PIN}" \
  "${WORK_DIR}/trust.seal.sha256"

cp \
  "${HEAD_ADVANCER}" \
  "${WORK_DIR}/advance-offline-trust-pack-head.sh"

cp \
  "${HEAD_SEALER}" \
  "${WORK_DIR}/seal-offline-trust-pack-head.sh"

chmod 600 \
  "${WORK_DIR}/trust.head" \
  "${WORK_DIR}/trust.head.ledger" \
  "${WORK_DIR}/trust.seal" \
  "${WORK_DIR}/trust.seal.sha256"

chmod 755 \
  "${WORK_DIR}/advance-offline-trust-pack-head.sh" \
  "${WORK_DIR}/seal-offline-trust-pack-head.sh"

cat > "${WORK_DIR}/recovery.manifest" <<EOF
recoveryCapsuleVersion=${RECOVERY_VERSION}
acceptedSequence=${ACCEPTED_SEQUENCE}
headSha256=${HEAD_SHA}
ledgerSha256=${LEDGER_SHA}
sealSha256=${SEAL_SHA}
sealPinFileSha256=${SEAL_PIN_FILE_SHA}
headAdvancerSha256=${HEAD_ADVANCER_SHA}
headSealerSha256=${HEAD_SEALER_SHA}
EOF

chmod 600 \
  "${WORK_DIR}/recovery.manifest"

mkdir -p \
  "$(dirname "${OUTPUT_CAPSULE}")"

rm -f "${OUTPUT_CAPSULE}"

python3 \
  - \
  "${WORK_DIR}" \
  "${OUTPUT_CAPSULE}" \
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
    "advance-offline-trust-pack-head.sh",
    "recovery.manifest",
    "seal-offline-trust-pack-head.sh",
    "trust.head",
    "trust.head.ledger",
    "trust.seal",
    "trust.seal.sha256",
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
        info.flag_bits = 0

        if name.endswith(
            ".sh"
        ):
            mode = 0o100755
        else:
            mode = 0o100600

        info.external_attr = (
            mode << 16
        )

        archive.writestr(
            info,
            path.read_bytes(),
        )
PY

[[ -s "${OUTPUT_CAPSULE}" ]]

CAPSULE_SHA="$(
  sha256_file "${OUTPUT_CAPSULE}"
)"

echo "RECOVERY_CAPSULE_VERSION=${RECOVERY_VERSION}"
echo "RECOVERY_CAPSULE_ACCEPTED_SEQUENCE=${ACCEPTED_SEQUENCE}"
echo "RECOVERY_CAPSULE_ENTRY_COUNT=7"
echo "RECOVERY_CAPSULE_HEAD_SHA256=${HEAD_SHA}"
echo "RECOVERY_CAPSULE_LEDGER_SHA256=${LEDGER_SHA}"
echo "RECOVERY_CAPSULE_SEAL_SHA256=${SEAL_SHA}"
echo "RECOVERY_CAPSULE_SHA256=${CAPSULE_SHA}"
echo "RECOVERY_CAPSULE_BUILD_RESULT=PASS"
