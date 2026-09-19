#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "Usage: $0 <recovery-capsule.zip> <capsule-sha256-pin-file> <destination-directory>" >&2

  exit 64
fi

CAPSULE_FILE="$1"
CAPSULE_PIN="$2"
DESTINATION="$3"

RECOVERY_VERSION="TS-OFFLINE-TRUST-STATE-RECOVERY-CAPSULE-V1"

for TOOL in \
  bash \
  jar \
  shasum \
  sed \
  grep \
  python3
do
  command -v "${TOOL}" >/dev/null
done

[[ -s "${CAPSULE_FILE}" ]]
[[ -s "${CAPSULE_PIN}" ]]

if [[ -e "${DESTINATION}" ]]; then
  echo "RECOVERY_DESTINATION_ALREADY_EXISTS=true"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 3
fi

sha256_file() {
  shasum -a 256 \
    "$1" \
    | awk '{print $1}'
}

EXPECTED_CAPSULE_SHA="$(
  tr -d '[:space:]' \
    < "${CAPSULE_PIN}"
)"

EXPECTED_CAPSULE_SHA="$(
  printf '%s' "${EXPECTED_CAPSULE_SHA}" \
    | tr '[:upper:]' '[:lower:]'
)"

if [[ ! "${EXPECTED_CAPSULE_SHA}" =~ ^[0-9a-f]{64}$ ]]; then
  echo "RECOVERY_CAPSULE_PIN_FORMAT_VALID=false"
  echo "RECOVERY_CAPSULE_OUTER_HASH_VALID=false"
  echo "RECOVERY_CAPSULE_EXTRACTED=false"
  echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=false"
  echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

ACTUAL_CAPSULE_SHA="$(
  sha256_file "${CAPSULE_FILE}"
)"

echo "RECOVERY_CAPSULE_EXPECTED_SHA256=${EXPECTED_CAPSULE_SHA}"
echo "RECOVERY_CAPSULE_ACTUAL_SHA256=${ACTUAL_CAPSULE_SHA}"

if [[ "${EXPECTED_CAPSULE_SHA}" != "${ACTUAL_CAPSULE_SHA}" ]]; then
  echo "RECOVERY_CAPSULE_OUTER_HASH_VALID=false"
  echo "RECOVERY_CAPSULE_STRUCTURE_VALID=false"
  echo "RECOVERY_CAPSULE_EXTRACTED=false"
  echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=false"
  echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

echo "RECOVERY_CAPSULE_OUTER_HASH_VALID=true"

EXPECTED_ENTRIES="$(
  cat <<'EOF'
advance-offline-trust-pack-head.sh
recovery.manifest
seal-offline-trust-pack-head.sh
trust.head
trust.head.ledger
trust.seal
trust.seal.sha256
EOF
)"

ACTUAL_ENTRIES="$(
  jar tf "${CAPSULE_FILE}" \
    | sed '/^[[:space:]]*$/d' \
    | sort
)"

if [[ "${ACTUAL_ENTRIES}" != "${EXPECTED_ENTRIES}" ]]; then
  echo "RECOVERY_CAPSULE_STRUCTURE_VALID=false"
  echo "RECOVERY_CAPSULE_EXTRACTED=false"
  echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=false"
  echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

echo "RECOVERY_CAPSULE_STRUCTURE_VALID=true"

DEST_PARENT="$(
  dirname "${DESTINATION}"
)"

DEST_BASE="$(
  basename "${DESTINATION}"
)"

mkdir -p "${DEST_PARENT}"

WORK_DIR="$(
  mktemp -d \
    "${DEST_PARENT}/.${DEST_BASE}.restore.XXXXXX"
)"

PUBLISHED=false

cleanup() {
  if [[ "${PUBLISHED}" != "true" ]]; then
    rm -rf "${WORK_DIR}"
  fi
}

trap cleanup EXIT

CAPSULE_ABS="$(
  cd "$(dirname "${CAPSULE_FILE}")"
  pwd
)/$(basename "${CAPSULE_FILE}")"

(
  cd "${WORK_DIR}"

  jar xf "${CAPSULE_ABS}"
)

echo "RECOVERY_CAPSULE_EXTRACTED=true"

chmod 755 \
  "${WORK_DIR}/advance-offline-trust-pack-head.sh" \
  "${WORK_DIR}/seal-offline-trust-pack-head.sh"

chmod 600 \
  "${WORK_DIR}/recovery.manifest" \
  "${WORK_DIR}/trust.head" \
  "${WORK_DIR}/trust.head.ledger" \
  "${WORK_DIR}/trust.seal" \
  "${WORK_DIR}/trust.seal.sha256"

MANIFEST_STATUS=0

if MANIFEST_OUTPUT="$(
  python3 \
    - "${WORK_DIR}/recovery.manifest" \
<<'PY'
from pathlib import Path
import re
import sys

path = Path(
    sys.argv[1]
)

raw = path.read_text(
    encoding="utf-8"
)

if not raw.endswith(
    "\n"
):
    raise SystemExit(
        4
    )

expected = [
    "recoveryCapsuleVersion",
    "acceptedSequence",
    "headSha256",
    "ledgerSha256",
    "sealSha256",
    "sealPinFileSha256",
    "headAdvancerSha256",
    "headSealerSha256",
]

lines = raw.splitlines()

if len(lines) != len(
    expected
):
    raise SystemExit(
        4
    )

values = {}

for key_expected, line in zip(
    expected,
    lines,
):
    if "=" not in line:
        raise SystemExit(
            4
        )

    key, value = line.split(
        "=",
        1,
    )

    if (
        key != key_expected
        or not value
        or key in values
    ):
        raise SystemExit(
            4
        )

    values[key] = value

if (
    values["recoveryCapsuleVersion"]
    != "TS-OFFLINE-TRUST-STATE-RECOVERY-CAPSULE-V1"
):
    raise SystemExit(
        4
    )

if not re.fullmatch(
    r"[1-9][0-9]*",
    values["acceptedSequence"],
):
    raise SystemExit(
        4
    )

for key in (
    "headSha256",
    "ledgerSha256",
    "sealSha256",
    "sealPinFileSha256",
    "headAdvancerSha256",
    "headSealerSha256",
):
    if not re.fullmatch(
        r"[0-9a-f]{64}",
        values[key],
    ):
        raise SystemExit(
            4
        )

for key in expected:
    print(
        f"{key}={values[key]}"
    )
PY
)"
then
  MANIFEST_STATUS=0
else
  MANIFEST_STATUS=$?
fi

if [[ "${MANIFEST_STATUS}" != "0" ]]; then
  echo "RECOVERY_CAPSULE_MANIFEST_VALID=false"
  echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=false"
  echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

echo "RECOVERY_CAPSULE_MANIFEST_VALID=true"

manifest_value() {
  local KEY="$1"

  local COUNT
  local VALUE

  COUNT="$(
    printf '%s\n' "${MANIFEST_OUTPUT}" \
      | grep \
        -c \
        "^${KEY}=" \
      || true
  )"

  [[ "${COUNT}" == "1" ]]

  VALUE="$(
    printf '%s\n' "${MANIFEST_OUTPUT}" \
      | sed -n \
        "s/^${KEY}=//p"
  )"

  [[ -n "${VALUE}" ]]

  printf '%s' "${VALUE}"
}

EXPECTED_HEAD_SHA="$(
  manifest_value headSha256
)"

EXPECTED_LEDGER_SHA="$(
  manifest_value ledgerSha256
)"

EXPECTED_SEAL_SHA="$(
  manifest_value sealSha256
)"

EXPECTED_SEAL_PIN_FILE_SHA="$(
  manifest_value sealPinFileSha256
)"

EXPECTED_ADVANCER_SHA="$(
  manifest_value headAdvancerSha256
)"

EXPECTED_SEALER_SHA="$(
  manifest_value headSealerSha256
)"

EXPECTED_SEQUENCE="$(
  manifest_value acceptedSequence
)"

ACTUAL_HEAD_SHA="$(
  sha256_file "${WORK_DIR}/trust.head"
)"

ACTUAL_LEDGER_SHA="$(
  sha256_file "${WORK_DIR}/trust.head.ledger"
)"

ACTUAL_SEAL_SHA="$(
  sha256_file "${WORK_DIR}/trust.seal"
)"

ACTUAL_SEAL_PIN_FILE_SHA="$(
  sha256_file "${WORK_DIR}/trust.seal.sha256"
)"

ACTUAL_ADVANCER_SHA="$(
  sha256_file "${WORK_DIR}/advance-offline-trust-pack-head.sh"
)"

ACTUAL_SEALER_SHA="$(
  sha256_file "${WORK_DIR}/seal-offline-trust-pack-head.sh"
)"

echo "RECOVERY_HEAD_EXPECTED_SHA256=${EXPECTED_HEAD_SHA}"
echo "RECOVERY_HEAD_ACTUAL_SHA256=${ACTUAL_HEAD_SHA}"

echo "RECOVERY_LEDGER_EXPECTED_SHA256=${EXPECTED_LEDGER_SHA}"
echo "RECOVERY_LEDGER_ACTUAL_SHA256=${ACTUAL_LEDGER_SHA}"

echo "RECOVERY_SEAL_EXPECTED_SHA256=${EXPECTED_SEAL_SHA}"
echo "RECOVERY_SEAL_ACTUAL_SHA256=${ACTUAL_SEAL_SHA}"

echo "RECOVERY_SEAL_PIN_FILE_EXPECTED_SHA256=${EXPECTED_SEAL_PIN_FILE_SHA}"
echo "RECOVERY_SEAL_PIN_FILE_ACTUAL_SHA256=${ACTUAL_SEAL_PIN_FILE_SHA}"

echo "RECOVERY_ADVANCER_EXPECTED_SHA256=${EXPECTED_ADVANCER_SHA}"
echo "RECOVERY_ADVANCER_ACTUAL_SHA256=${ACTUAL_ADVANCER_SHA}"

echo "RECOVERY_SEALER_EXPECTED_SHA256=${EXPECTED_SEALER_SHA}"
echo "RECOVERY_SEALER_ACTUAL_SHA256=${ACTUAL_SEALER_SHA}"

if [[ "${EXPECTED_HEAD_SHA}" != "${ACTUAL_HEAD_SHA}" \
   || "${EXPECTED_LEDGER_SHA}" != "${ACTUAL_LEDGER_SHA}" \
   || "${EXPECTED_SEAL_SHA}" != "${ACTUAL_SEAL_SHA}" \
   || "${EXPECTED_SEAL_PIN_FILE_SHA}" != "${ACTUAL_SEAL_PIN_FILE_SHA}" \
   || "${EXPECTED_ADVANCER_SHA}" != "${ACTUAL_ADVANCER_SHA}" \
   || "${EXPECTED_SEALER_SHA}" != "${ACTUAL_SEALER_SHA}" ]]
then
  echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=false"
  echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=false"
  echo "RECOVERY_CAPSULE_STATE_VALID=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

PIN_VALUE="$(
  tr -d '[:space:]' \
    < "${WORK_DIR}/trust.seal.sha256"
)"

if [[ "${PIN_VALUE}" != "${ACTUAL_SEAL_SHA}" ]]; then
  echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=false"
  echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=false"
  echo "RECOVERY_CAPSULE_STATE_VALID=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

HEAD_SEQUENCE="$(
  sed -n \
    's/^acceptedSequence=//p' \
    "${WORK_DIR}/trust.head"
)"

if [[ "${HEAD_SEQUENCE}" != "${EXPECTED_SEQUENCE}" ]]; then
  echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=false"
  echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=false"
  echo "RECOVERY_CAPSULE_STATE_VALID=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

echo "RECOVERY_CAPSULE_INTERNAL_HASHES_VALID=true"

VERIFY_STATUS=0

if VERIFY_OUTPUT="$(
  bash \
    "${WORK_DIR}/seal-offline-trust-pack-head.sh" \
    verify \
    "${WORK_DIR}/trust.head" \
    "${WORK_DIR}/trust.seal" \
    "${WORK_DIR}/trust.seal.sha256" \
    "${WORK_DIR}/advance-offline-trust-pack-head.sh" \
    2>&1
)"
then
  VERIFY_STATUS=0
else
  VERIFY_STATUS=$?
fi

echo "RECOVERY_CAPSULE_STATE_VERIFY_EXECUTED=true"

printf '%s\n' "${VERIFY_OUTPUT}"

echo "RECOVERY_CAPSULE_STATE_VERIFY_EXIT=${VERIFY_STATUS}"

if [[ "${VERIFY_STATUS}" != "0" ]]; then
  echo "RECOVERY_CAPSULE_STATE_VALID=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

if ! printf '%s\n' "${VERIFY_OUTPUT}" \
    | grep \
      -q \
      '^TRUST_HEAD_SEAL_RESULT=VALID$'
then
  echo "RECOVERY_CAPSULE_STATE_VALID=false"
  echo "RECOVERY_DESTINATION_PUBLISHED=false"
  echo "RECOVERY_RESULT=INVALID"

  exit 2
fi

echo "RECOVERY_CAPSULE_STATE_VALID=true"

python3 \
  - \
  "${WORK_DIR}" \
  "${DESTINATION}" \
<<'PY'
import os
import sys

source = sys.argv[1]
destination = sys.argv[2]

parent = (
    os.path.dirname(
        destination
    )
    or "."
)

if os.path.exists(
    destination
):
    raise SystemExit(
        3
    )

os.replace(
    source,
    destination,
)

fd = os.open(
    parent,
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)
PY

PUBLISHED=true

echo "RECOVERY_DESTINATION_PUBLISHED=true"

echo "RECOVERY_RESTORED_ACCEPTED_SEQUENCE=${EXPECTED_SEQUENCE}"

echo "RECOVERY_RESULT=VALID"
