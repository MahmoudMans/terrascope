#!/usr/bin/env bash
set -Eeuo pipefail

SEAL_VERSION="TS-OFFLINE-TRUST-HEAD-SEAL-V1"

usage() {
  echo "Usage:" >&2
  echo "  $0 create <head-file> <seal-file> <head-advancer>" >&2
  echo "  $0 verify <head-file> <seal-file> <seal-sha256-pin-file> <head-advancer>" >&2
}

if [[ "$#" -lt 1 ]]; then
  usage
  exit 64
fi

COMMAND="$1"

if [[ "${COMMAND}" == "create" ]]; then

  if [[ "$#" -ne 4 ]]; then
    usage
    exit 64
  fi

  HEAD_FILE="$2"
  SEAL_FILE="$3"
  HEAD_ADVANCER="$4"
  SEAL_PIN=""

elif [[ "${COMMAND}" == "verify" ]]; then

  if [[ "$#" -ne 5 ]]; then
    usage
    exit 64
  fi

  HEAD_FILE="$2"
  SEAL_FILE="$3"
  SEAL_PIN="$4"
  HEAD_ADVANCER="$5"

else
  echo "Unsupported command: ${COMMAND}" >&2

  usage
  exit 64
fi

LEDGER_FILE="${HEAD_FILE}.ledger"

for TOOL in \
  bash \
  shasum \
  sed \
  grep \
  python3
do
  command -v "${TOOL}" >/dev/null
done

[[ -s "${HEAD_FILE}" ]]
[[ -s "${LEDGER_FILE}" ]]
[[ -x "${HEAD_ADVANCER}" ]]

sha256_file() {
  shasum -a 256 \
    "$1" \
    | awk '{print $1}'
}

output_value() {
  local TEXT="$1"
  local KEY="$2"

  local COUNT
  local VALUE

  COUNT="$(
    printf '%s\n' "${TEXT}" \
      | grep \
        -c \
        "^${KEY}=" \
      || true
  )"

  [[ "${COUNT}" == "1" ]]

  VALUE="$(
    printf '%s\n' "${TEXT}" \
      | sed -n \
        "s/^${KEY}=//p"
  )"

  [[ -n "${VALUE}" ]]

  printf '%s' "${VALUE}"
}

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

audit_head() {
  local AUDIT_OUTPUT
  local AUDIT_STATUS=0

  if AUDIT_OUTPUT="$(
    bash \
      "${HEAD_ADVANCER}" \
      audit \
      "${HEAD_FILE}" \
      2>&1
  )"
  then
    AUDIT_STATUS=0
  else
    AUDIT_STATUS=$?
  fi

  echo "TRUST_HEAD_AUDIT_EXECUTED=true"

  printf '%s\n' "${AUDIT_OUTPUT}"

  echo "TRUST_HEAD_AUDIT_EXIT=${AUDIT_STATUS}"

  if [[ "${AUDIT_STATUS}" != "0" ]]; then
    return "${AUDIT_STATUS}"
  fi

  if ! printf '%s\n' "${AUDIT_OUTPUT}" \
      | grep \
        -q \
        '^TRUST_PACK_HEAD_LEDGER_VALID=true$'
  then
    return 4
  fi

  if ! printf '%s\n' "${AUDIT_OUTPUT}" \
      | grep \
        -q \
        '^TRUST_PACK_HEAD_AUDIT_RESULT=VALID$'
  then
    return 4
  fi

  AUDIT_RECORD_COUNT="$(
    output_value \
      "${AUDIT_OUTPUT}" \
      TRUST_PACK_HEAD_LEDGER_RECORD_COUNT
  )"

  AUDIT_TAIL_RECEIPT="$(
    output_value \
      "${AUDIT_OUTPUT}" \
      TRUST_PACK_HEAD_LEDGER_TAIL_RECEIPT_SHA256
  )"

  AUDIT_TAIL_HEAD="$(
    output_value \
      "${AUDIT_OUTPUT}" \
      TRUST_PACK_HEAD_LEDGER_TAIL_AFTER_HEAD_SHA256
  )"

  AUDIT_TAIL_PACK="$(
    output_value \
      "${AUDIT_OUTPUT}" \
      TRUST_PACK_HEAD_LEDGER_TAIL_PACK_SHA256
  )"

  AUDIT_TAIL_SEQUENCE="$(
    output_value \
      "${AUDIT_OUTPUT}" \
      TRUST_PACK_HEAD_LEDGER_TAIL_SEQUENCE
  )"

  AUDIT_TAIL_SUBJECT_VERSION="$(
    output_value \
      "${AUDIT_OUTPUT}" \
      TRUST_PACK_HEAD_LEDGER_TAIL_SUBJECT_SYSTEM_VERSION
  )"

  AUDIT_TAIL_BUILDER_HEAD="$(
    output_value \
      "${AUDIT_OUTPUT}" \
      TRUST_PACK_HEAD_LEDGER_TAIL_BUILDER_AUTHORITY_HEAD
  )"

  return 0
}

atomic_write_file() {
  local TEMP_FILE="$1"
  local DESTINATION="$2"

  python3 \
    - \
    "${TEMP_FILE}" \
    "${DESTINATION}" \
<<'PY'
import os
import sys

source = sys.argv[1]
destination = sys.argv[2]

directory = (
    os.path.dirname(
        destination
    )
    or "."
)

os.replace(
    source,
    destination,
)

fd = os.open(
    destination,
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)

dfd = os.open(
    directory,
    os.O_RDONLY,
)

try:
    os.fsync(dfd)
finally:
    os.close(dfd)
PY
}

parse_seal() {
  local PARSE_OUTPUT
  local PARSE_STATUS=0

  if PARSE_OUTPUT="$(
    python3 \
      - "${SEAL_FILE}" \
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

lines = raw.splitlines()

expected_keys = [
    "sealVersion",
    "acceptedSequence",
    "headSha256",
    "ledgerSha256",
    "ledgerRecordCount",
    "ledgerTailReceiptSha256",
    "acceptedPackSha256",
    "subjectSystemVersion",
    "builderAuthorityHead",
]

if len(lines) != len(
    expected_keys
):
    raise SystemExit(
        4
    )

values = {}

for expected_key, line in zip(
    expected_keys,
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
        key != expected_key
        or not value
        or key in values
    ):
        raise SystemExit(
            4
        )

    values[key] = value

if (
    values["sealVersion"]
    != "TS-OFFLINE-TRUST-HEAD-SEAL-V1"
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

if not re.fullmatch(
    r"[0-9a-f]{64}",
    values["headSha256"],
):
    raise SystemExit(
        4
    )

if not re.fullmatch(
    r"[0-9a-f]{64}",
    values["ledgerSha256"],
):
    raise SystemExit(
        4
    )

if not re.fullmatch(
    r"[1-9][0-9]*",
    values["ledgerRecordCount"],
):
    raise SystemExit(
        4
    )

if not re.fullmatch(
    r"[0-9a-f]{64}",
    values["ledgerTailReceiptSha256"],
):
    raise SystemExit(
        4
    )

if not re.fullmatch(
    r"[0-9a-f]{64}",
    values["acceptedPackSha256"],
):
    raise SystemExit(
        4
    )

if not re.fullmatch(
    r"[0-9]+\.[0-9]+\.[0-9]+",
    values["subjectSystemVersion"],
):
    raise SystemExit(
        4
    )

if not re.fullmatch(
    r"[0-9a-f]{40}",
    values["builderAuthorityHead"],
):
    raise SystemExit(
        4
    )

for key in expected_keys:
    print(
        f"{key}={values[key]}"
    )
PY
  )"
  then
    PARSE_STATUS=0
  else
    PARSE_STATUS=$?
  fi

  if [[ "${PARSE_STATUS}" != "0" ]]; then
    return "${PARSE_STATUS}"
  fi

  SEAL_ACCEPTED_SEQUENCE="$(
    output_value \
      "${PARSE_OUTPUT}" \
      acceptedSequence
  )"

  SEAL_HEAD_SHA="$(
    output_value \
      "${PARSE_OUTPUT}" \
      headSha256
  )"

  SEAL_LEDGER_SHA="$(
    output_value \
      "${PARSE_OUTPUT}" \
      ledgerSha256
  )"

  SEAL_RECORD_COUNT="$(
    output_value \
      "${PARSE_OUTPUT}" \
      ledgerRecordCount
  )"

  SEAL_TAIL_RECEIPT="$(
    output_value \
      "${PARSE_OUTPUT}" \
      ledgerTailReceiptSha256
  )"

  SEAL_ACCEPTED_PACK="$(
    output_value \
      "${PARSE_OUTPUT}" \
      acceptedPackSha256
  )"

  SEAL_SUBJECT_VERSION="$(
    output_value \
      "${PARSE_OUTPUT}" \
      subjectSystemVersion
  )"

  SEAL_BUILDER_HEAD="$(
    output_value \
      "${PARSE_OUTPUT}" \
      builderAuthorityHead
  )"

  return 0
}

# ============================================================================
# CREATE
# ============================================================================

if [[ "${COMMAND}" == "create" ]]; then

  AUDIT_STATUS=0

  if audit_head
  then
    AUDIT_STATUS=0
  else
    AUDIT_STATUS=$?
  fi

  if [[ "${AUDIT_STATUS}" != "0" ]]; then
    echo "TRUST_HEAD_SEAL_CREATE_RESULT=INVALID"

    exit "${AUDIT_STATUS}"
  fi

  HEAD_SHA="$(
    sha256_file "${HEAD_FILE}"
  )"

  LEDGER_SHA="$(
    sha256_file "${LEDGER_FILE}"
  )"

  HEAD_SEQUENCE="$(
    head_value acceptedSequence
  )"

  HEAD_PACK="$(
    head_value acceptedPackSha256
  )"

  HEAD_SUBJECT_VERSION="$(
    head_value subjectSystemVersion
  )"

  HEAD_BUILDER_HEAD="$(
    head_value builderAuthorityHead
  )"

  [[ "${AUDIT_TAIL_HEAD}" == "${HEAD_SHA}" ]]
  [[ "${AUDIT_TAIL_SEQUENCE}" == "${HEAD_SEQUENCE}" ]]
  [[ "${AUDIT_TAIL_PACK}" == "${HEAD_PACK}" ]]
  [[ "${AUDIT_TAIL_SUBJECT_VERSION}" == "${HEAD_SUBJECT_VERSION}" ]]
  [[ "${AUDIT_TAIL_BUILDER_HEAD}" == "${HEAD_BUILDER_HEAD}" ]]

  SEAL_DIR="$(
    dirname "${SEAL_FILE}"
  )"

  SEAL_BASE="$(
    basename "${SEAL_FILE}"
  )"

  mkdir -p "${SEAL_DIR}"

  TEMP_SEAL="${SEAL_DIR}/.${SEAL_BASE}.tmp.$$"

  rm -f "${TEMP_SEAL}"

  cat > "${TEMP_SEAL}" <<EOF
sealVersion=${SEAL_VERSION}
acceptedSequence=${HEAD_SEQUENCE}
headSha256=${HEAD_SHA}
ledgerSha256=${LEDGER_SHA}
ledgerRecordCount=${AUDIT_RECORD_COUNT}
ledgerTailReceiptSha256=${AUDIT_TAIL_RECEIPT}
acceptedPackSha256=${HEAD_PACK}
subjectSystemVersion=${HEAD_SUBJECT_VERSION}
builderAuthorityHead=${HEAD_BUILDER_HEAD}
EOF

  chmod 600 "${TEMP_SEAL}"

  atomic_write_file \
    "${TEMP_SEAL}" \
    "${SEAL_FILE}"

  SEAL_SHA="$(
    sha256_file "${SEAL_FILE}"
  )"

  echo "TRUST_HEAD_SEAL_VERSION=${SEAL_VERSION}"
  echo "TRUST_HEAD_SEAL_ACCEPTED_SEQUENCE=${HEAD_SEQUENCE}"
  echo "TRUST_HEAD_SEAL_HEAD_SHA256=${HEAD_SHA}"
  echo "TRUST_HEAD_SEAL_LEDGER_SHA256=${LEDGER_SHA}"
  echo "TRUST_HEAD_SEAL_LEDGER_RECORD_COUNT=${AUDIT_RECORD_COUNT}"
  echo "TRUST_HEAD_SEAL_LEDGER_TAIL_RECEIPT_SHA256=${AUDIT_TAIL_RECEIPT}"
  echo "TRUST_HEAD_SEAL_ACCEPTED_PACK_SHA256=${HEAD_PACK}"
  echo "TRUST_HEAD_SEAL_SUBJECT_SYSTEM_VERSION=${HEAD_SUBJECT_VERSION}"
  echo "TRUST_HEAD_SEAL_BUILDER_AUTHORITY_HEAD=${HEAD_BUILDER_HEAD}"
  echo "TRUST_HEAD_SEAL_SHA256=${SEAL_SHA}"
  echo "TRUST_HEAD_SEAL_CREATE_RESULT=PASS"

  exit 0
fi

# ============================================================================
# VERIFY
# ============================================================================

[[ -s "${SEAL_FILE}" ]]
[[ -s "${SEAL_PIN}" ]]

EXPECTED_SEAL_SHA="$(
  tr -d '[:space:]' \
    < "${SEAL_PIN}"
)"

EXPECTED_SEAL_SHA="$(
  printf '%s' "${EXPECTED_SEAL_SHA}" \
    | tr '[:upper:]' '[:lower:]'
)"

if [[ ! "${EXPECTED_SEAL_SHA}" =~ ^[0-9a-f]{64}$ ]]; then
  echo "TRUST_HEAD_SEAL_PIN_FORMAT_VALID=false"
  echo "TRUST_HEAD_SEAL_OUTER_HASH_VALID=false"
  echo "TRUST_HEAD_AUDIT_EXECUTED=false"
  echo "TRUST_HEAD_SEAL_STATE_MATCH=false"
  echo "TRUST_HEAD_SEAL_RESULT=INVALID"

  exit 2
fi

ACTUAL_SEAL_SHA="$(
  sha256_file "${SEAL_FILE}"
)"

echo "TRUST_HEAD_SEAL_EXPECTED_SHA256=${EXPECTED_SEAL_SHA}"
echo "TRUST_HEAD_SEAL_ACTUAL_SHA256=${ACTUAL_SEAL_SHA}"

if [[ "${EXPECTED_SEAL_SHA}" != "${ACTUAL_SEAL_SHA}" ]]; then
  echo "TRUST_HEAD_SEAL_OUTER_HASH_VALID=false"
  echo "TRUST_HEAD_AUDIT_EXECUTED=false"
  echo "TRUST_HEAD_SEAL_STATE_MATCH=false"
  echo "TRUST_HEAD_SEAL_RESULT=INVALID"

  exit 2
fi

echo "TRUST_HEAD_SEAL_OUTER_HASH_VALID=true"

PARSE_STATUS=0

if parse_seal
then
  PARSE_STATUS=0
else
  PARSE_STATUS=$?
fi

if [[ "${PARSE_STATUS}" != "0" ]]; then
  echo "TRUST_HEAD_SEAL_STRUCTURE_VALID=false"
  echo "TRUST_HEAD_AUDIT_EXECUTED=false"
  echo "TRUST_HEAD_SEAL_STATE_MATCH=false"
  echo "TRUST_HEAD_SEAL_RESULT=INVALID"

  exit 2
fi

echo "TRUST_HEAD_SEAL_STRUCTURE_VALID=true"

AUDIT_STATUS=0

if audit_head
then
  AUDIT_STATUS=0
else
  AUDIT_STATUS=$?
fi

if [[ "${AUDIT_STATUS}" != "0" ]]; then
  echo "TRUST_HEAD_SEAL_STATE_MATCH=false"
  echo "TRUST_HEAD_SEAL_RESULT=INVALID"

  exit 2
fi

CURRENT_HEAD_SHA="$(
  sha256_file "${HEAD_FILE}"
)"

CURRENT_LEDGER_SHA="$(
  sha256_file "${LEDGER_FILE}"
)"

CURRENT_SEQUENCE="$(
  head_value acceptedSequence
)"

CURRENT_PACK="$(
  head_value acceptedPackSha256
)"

CURRENT_SUBJECT_VERSION="$(
  head_value subjectSystemVersion
)"

CURRENT_BUILDER_HEAD="$(
  head_value builderAuthorityHead
)"

HEAD_HASH_MATCH=false
LEDGER_HASH_MATCH=false
RECORD_COUNT_MATCH=false
TAIL_RECEIPT_MATCH=false
SEQUENCE_MATCH=false
PACK_MATCH=false
SUBJECT_VERSION_MATCH=false
BUILDER_HEAD_MATCH=false

[[ "${CURRENT_HEAD_SHA}" == "${SEAL_HEAD_SHA}" ]] \
  && HEAD_HASH_MATCH=true

[[ "${CURRENT_LEDGER_SHA}" == "${SEAL_LEDGER_SHA}" ]] \
  && LEDGER_HASH_MATCH=true

[[ "${AUDIT_RECORD_COUNT}" == "${SEAL_RECORD_COUNT}" ]] \
  && RECORD_COUNT_MATCH=true

[[ "${AUDIT_TAIL_RECEIPT}" == "${SEAL_TAIL_RECEIPT}" ]] \
  && TAIL_RECEIPT_MATCH=true

[[ "${CURRENT_SEQUENCE}" == "${SEAL_ACCEPTED_SEQUENCE}" ]] \
  && SEQUENCE_MATCH=true

[[ "${CURRENT_PACK}" == "${SEAL_ACCEPTED_PACK}" ]] \
  && PACK_MATCH=true

[[ "${CURRENT_SUBJECT_VERSION}" == "${SEAL_SUBJECT_VERSION}" ]] \
  && SUBJECT_VERSION_MATCH=true

[[ "${CURRENT_BUILDER_HEAD}" == "${SEAL_BUILDER_HEAD}" ]] \
  && BUILDER_HEAD_MATCH=true

echo "TRUST_HEAD_SEAL_HEAD_HASH_MATCH=${HEAD_HASH_MATCH}"
echo "TRUST_HEAD_SEAL_LEDGER_HASH_MATCH=${LEDGER_HASH_MATCH}"
echo "TRUST_HEAD_SEAL_LEDGER_RECORD_COUNT_MATCH=${RECORD_COUNT_MATCH}"
echo "TRUST_HEAD_SEAL_TAIL_RECEIPT_MATCH=${TAIL_RECEIPT_MATCH}"
echo "TRUST_HEAD_SEAL_SEQUENCE_MATCH=${SEQUENCE_MATCH}"
echo "TRUST_HEAD_SEAL_PACK_MATCH=${PACK_MATCH}"
echo "TRUST_HEAD_SEAL_SUBJECT_VERSION_MATCH=${SUBJECT_VERSION_MATCH}"
echo "TRUST_HEAD_SEAL_BUILDER_AUTHORITY_HEAD_MATCH=${BUILDER_HEAD_MATCH}"

if [[ "${HEAD_HASH_MATCH}" != "true" \
   || "${LEDGER_HASH_MATCH}" != "true" \
   || "${RECORD_COUNT_MATCH}" != "true" \
   || "${TAIL_RECEIPT_MATCH}" != "true" \
   || "${SEQUENCE_MATCH}" != "true" \
   || "${PACK_MATCH}" != "true" \
   || "${SUBJECT_VERSION_MATCH}" != "true" \
   || "${BUILDER_HEAD_MATCH}" != "true" ]]
then
  echo "TRUST_HEAD_SEAL_STATE_MATCH=false"
  echo "TRUST_HEAD_SEAL_RESULT=INVALID"

  exit 2
fi

echo "TRUST_HEAD_SEAL_STATE_MATCH=true"

echo "TRUST_HEAD_SEAL_RESULT=VALID"
