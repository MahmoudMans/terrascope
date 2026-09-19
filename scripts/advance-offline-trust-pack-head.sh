#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage:" >&2
  echo "  $0 init <head-file> <genesis-pack> <pack-pin> <trust-pack-verifier>" >&2
  echo "  $0 apply <head-file> <candidate-pack> <pack-pin> <trust-pack-verifier>" >&2
  echo "  $0 recover <head-file>" >&2
  echo "  $0 audit <head-file>" >&2
}

if [[ "$#" -lt 1 ]]; then
  usage
  exit 64
fi

COMMAND="$1"

if [[ "${COMMAND}" == "recover" || "${COMMAND}" == "audit" ]]; then

  if [[ "$#" -ne 2 ]]; then
    usage
    exit 64
  fi

  HEAD_FILE="$2"

  PACK_FILE=""
  PACK_PIN=""
  PACK_VERIFIER=""

elif [[ "${COMMAND}" == "init" || "${COMMAND}" == "apply" ]]; then

  if [[ "$#" -ne 5 ]]; then
    usage
    exit 64
  fi

  HEAD_FILE="$2"
  PACK_FILE="$3"
  PACK_PIN="$4"
  PACK_VERIFIER="$5"

else
  echo "Unsupported command: ${COMMAND}" >&2

  usage

  exit 64
fi

for TOOL in \
  bash \
  jar \
  shasum \
  sed \
  grep \
  mkdir \
  python3
do
  command -v "${TOOL}" >/dev/null
done

HEAD_DIR="$(
  dirname "${HEAD_FILE}"
)"

HEAD_BASENAME="$(
  basename "${HEAD_FILE}"
)"

LOCK_DIR="${HEAD_FILE}.lock"

JOURNAL_FILE="${HEAD_FILE}.journal"

PENDING_FILE="${HEAD_FILE}.pending"

LEDGER_FILE="${HEAD_FILE}.ledger"

JOURNAL_VERSION="TS-OFFLINE-TRUST-PACK-HEAD-JOURNAL-V2"

HEAD_VERSION="TS-OFFLINE-TRUST-PACK-HEAD-V1"

LEDGER_VERSION="TS-OFFLINE-TRUST-PACK-HEAD-LEDGER-V1"

mkdir -p "${HEAD_DIR}"

sha256_file() {
  shasum -a 256 \
    "$1" \
    | awk '{print $1}'
}

sha256_text() {
  printf '%s' "$1" \
    | shasum -a 256 \
    | awk '{print $1}'
}

fsync_file() {
  python3 \
    - "$1" \
<<'PY'
import os
import sys

fd = os.open(
    sys.argv[1],
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)
PY
}

fsync_directory() {
  python3 \
    - "$1" \
<<'PY'
import os
import sys

fd = os.open(
    sys.argv[1],
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)
PY
}

durable_replace() {
  python3 \
    - \
    "$1" \
    "$2" \
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
    directory,
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)
PY
}

durable_remove() {
  python3 \
    - "$1" \
<<'PY'
import os
import sys

path = sys.argv[1]

directory = (
    os.path.dirname(
        path
    )
    or "."
)

if os.path.lexists(path):
    os.unlink(path)

fd = os.open(
    directory,
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)
PY
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

journal_value() {
  local KEY="$1"

  local COUNT
  local VALUE

  COUNT="$(
    grep \
      -c \
      "^${KEY}=" \
      "${JOURNAL_FILE}" \
      || true
  )"

  [[ "${COUNT}" == "1" ]]

  VALUE="$(
    sed -n \
      "s/^${KEY}=//p" \
      "${JOURNAL_FILE}"
  )"

  [[ -n "${VALUE}" ]]

  printf '%s' "${VALUE}"
}

manifest_value() {
  local MANIFEST="$1"
  local KEY="$2"

  local COUNT
  local VALUE

  COUNT="$(
    grep \
      -c \
      "^${KEY}=" \
      "${MANIFEST}" \
      || true
  )"

  [[ "${COUNT}" == "1" ]]

  VALUE="$(
    sed -n \
      "s/^${KEY}=//p" \
      "${MANIFEST}"
  )"

  [[ -n "${VALUE}" ]]

  printf '%s' "${VALUE}"
}

build_receipt_payload() {
  local OPERATION="$1"
  local SEQUENCE="$2"
  local PACK_SHA="$3"
  local BEFORE_HEAD_SHA="$4"
  local AFTER_HEAD_SHA="$5"
  local SUBJECT_VERSION="$6"
  local BUILDER_HEAD="$7"
  local PREVIOUS_RECEIPT="$8"

  printf '%s' \
    "ledgerVersion=${LEDGER_VERSION}|operation=${OPERATION}|sequence=${SEQUENCE}|packSha256=${PACK_SHA}|beforeHeadSha256=${BEFORE_HEAD_SHA}|afterHeadSha256=${AFTER_HEAD_SHA}|subjectSystemVersion=${SUBJECT_VERSION}|builderAuthorityHead=${BUILDER_HEAD}|previousReceiptSha256=${PREVIOUS_RECEIPT}"
}

ledger_chain_only() {
  python3 \
    - \
    "${LEDGER_FILE}" \
    "${LEDGER_VERSION}" \
<<'PY'
from pathlib import Path
import hashlib
import re
import sys

ledger_path = Path(
    sys.argv[1]
)

expected_version = sys.argv[2]

if not ledger_path.is_file():
    raise SystemExit(
        4
    )

raw = ledger_path.read_text(
    encoding="utf-8"
)

if not raw.endswith(
    "\n"
):
    raise SystemExit(
        4
    )

lines = raw.splitlines()

if not lines:
    raise SystemExit(
        4
    )

previous_receipt = "GENESIS"
previous_after = "GENESIS"
previous_sequence = 0

tail = None

keys = [
    "ledgerVersion",
    "operation",
    "sequence",
    "packSha256",
    "beforeHeadSha256",
    "afterHeadSha256",
    "subjectSystemVersion",
    "builderAuthorityHead",
    "previousReceiptSha256",
]

for index, line in enumerate(
    lines,
    start=1,
):
    parts = line.split(
        "|"
    )

    if len(parts) != 10:
        raise SystemExit(
            4
        )

    values = {}

    for part in parts:
        if "=" not in part:
            raise SystemExit(
                4
            )

        key, value = part.split(
            "=",
            1,
        )

        if (
            not key
            or not value
            or key in values
        ):
            raise SystemExit(
                4
            )

        values[key] = value

    if set(values) != set(
        keys
        + ["receiptSha256"]
    ):
        raise SystemExit(
            4
        )

    if values["ledgerVersion"] != expected_version:
        raise SystemExit(
            4
        )

    try:
        sequence = int(
            values["sequence"]
        )
    except ValueError:
        raise SystemExit(
            4
        )

    if sequence != previous_sequence + 1:
        raise SystemExit(
            4
        )

    if not re.fullmatch(
        r"[0-9a-f]{64}",
        values["packSha256"],
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

    if index == 1:
        if values["operation"] != "INIT":
            raise SystemExit(
                4
            )

        if values["beforeHeadSha256"] != "GENESIS":
            raise SystemExit(
                4
            )

        if values["previousReceiptSha256"] != "GENESIS":
            raise SystemExit(
                4
            )
    else:
        if values["operation"] != "APPLY":
            raise SystemExit(
                4
            )

        if values["beforeHeadSha256"] != previous_after:
            raise SystemExit(
                4
            )

        if values["previousReceiptSha256"] != previous_receipt:
            raise SystemExit(
                4
            )

    if not re.fullmatch(
        r"[0-9a-f]{64}",
        values["afterHeadSha256"],
    ):
        raise SystemExit(
            4
        )

    payload = "|".join(
        f"{key}={values[key]}"
        for key in keys
    )

    receipt = hashlib.sha256(
        payload.encode(
            "utf-8"
        )
    ).hexdigest()

    if receipt != values["receiptSha256"]:
        raise SystemExit(
            4
        )

    canonical = (
        payload
        + "|receiptSha256="
        + receipt
    )

    if canonical != line:
        raise SystemExit(
            4
        )

    previous_receipt = receipt
    previous_after = values["afterHeadSha256"]
    previous_sequence = sequence

    tail = values | {
        "receiptSha256": receipt
    }

print(
    "TRUST_PACK_HEAD_LEDGER_CHAIN_VALID=true"
)

print(
    f"TRUST_PACK_HEAD_LEDGER_RECORD_COUNT={len(lines)}"
)

print(
    "TRUST_PACK_HEAD_LEDGER_TAIL_RECEIPT_SHA256="
    + tail["receiptSha256"]
)

print(
    "TRUST_PACK_HEAD_LEDGER_TAIL_AFTER_HEAD_SHA256="
    + tail["afterHeadSha256"]
)

print(
    "TRUST_PACK_HEAD_LEDGER_TAIL_PACK_SHA256="
    + tail["packSha256"]
)

print(
    "TRUST_PACK_HEAD_LEDGER_TAIL_SEQUENCE="
    + tail["sequence"]
)

print(
    "TRUST_PACK_HEAD_LEDGER_TAIL_SUBJECT_SYSTEM_VERSION="
    + tail["subjectSystemVersion"]
)

print(
    "TRUST_PACK_HEAD_LEDGER_TAIL_BUILDER_AUTHORITY_HEAD="
    + tail["builderAuthorityHead"]
)
PY
}

load_ledger_chain() {
  local OUTPUT
  local CHAIN_STATUS=0

  if OUTPUT="$(
    ledger_chain_only
  )"
  then
    CHAIN_STATUS=0
  else
    CHAIN_STATUS=$?
  fi

  if [[ "${CHAIN_STATUS}" != "0" ]]; then
    echo "TRUST_PACK_HEAD_LEDGER_CHAIN_VALID=false"

    return "${CHAIN_STATUS}"
  fi

  echo "${OUTPUT}"

  LEDGER_RECORD_COUNT="$(
    printf '%s\n' "${OUTPUT}" \
      | sed -n \
        's/^TRUST_PACK_HEAD_LEDGER_RECORD_COUNT=//p'
  )"

  LEDGER_TAIL_RECEIPT="$(
    printf '%s\n' "${OUTPUT}" \
      | sed -n \
        's/^TRUST_PACK_HEAD_LEDGER_TAIL_RECEIPT_SHA256=//p'
  )"

  LEDGER_TAIL_AFTER_HEAD="$(
    printf '%s\n' "${OUTPUT}" \
      | sed -n \
        's/^TRUST_PACK_HEAD_LEDGER_TAIL_AFTER_HEAD_SHA256=//p'
  )"

  LEDGER_TAIL_PACK_SHA="$(
    printf '%s\n' "${OUTPUT}" \
      | sed -n \
        's/^TRUST_PACK_HEAD_LEDGER_TAIL_PACK_SHA256=//p'
  )"

  LEDGER_TAIL_SEQUENCE="$(
    printf '%s\n' "${OUTPUT}" \
      | sed -n \
        's/^TRUST_PACK_HEAD_LEDGER_TAIL_SEQUENCE=//p'
  )"

  LEDGER_TAIL_SUBJECT_VERSION="$(
    printf '%s\n' "${OUTPUT}" \
      | sed -n \
        's/^TRUST_PACK_HEAD_LEDGER_TAIL_SUBJECT_SYSTEM_VERSION=//p'
  )"

  LEDGER_TAIL_BUILDER_HEAD="$(
    printf '%s\n' "${OUTPUT}" \
      | sed -n \
        's/^TRUST_PACK_HEAD_LEDGER_TAIL_BUILDER_AUTHORITY_HEAD=//p'
  )"

  if [[ ! "${LEDGER_RECORD_COUNT}" =~ ^[1-9][0-9]*$ ]]; then
    return 4
  fi

  if [[ ! "${LEDGER_TAIL_RECEIPT}" =~ ^[0-9a-f]{64}$ ]]; then
    return 4
  fi

  if [[ ! "${LEDGER_TAIL_AFTER_HEAD}" =~ ^[0-9a-f]{64}$ ]]; then
    return 4
  fi

  if [[ ! "${LEDGER_TAIL_PACK_SHA}" =~ ^[0-9a-f]{64}$ ]]; then
    return 4
  fi

  if [[ ! "${LEDGER_TAIL_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]; then
    return 4
  fi

  if [[ ! "${LEDGER_TAIL_SUBJECT_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    return 4
  fi

  if [[ ! "${LEDGER_TAIL_BUILDER_HEAD}" =~ ^[0-9a-f]{40}$ ]]; then
    return 4
  fi

  return 0
}

audit_ledger_against_head() {
  if [[ ! -s "${HEAD_FILE}" ]]; then
    return 4
  fi

  if [[ ! -s "${LEDGER_FILE}" ]]; then
    return 4
  fi

  local LOAD_STATUS=0

  if load_ledger_chain
  then
    LOAD_STATUS=0
  else
    LOAD_STATUS=$?
  fi

  if [[ "${LOAD_STATUS}" != "0" ]]; then
    return "${LOAD_STATUS}"
  fi

  local CURRENT_HEAD_SHA

  if CURRENT_HEAD_SHA="$(
    sha256_file "${HEAD_FILE}"
  )"
  then
    :
  else
    return $?
  fi

  local HEAD_SEQUENCE
  local HEAD_PACK_SHA
  local HEAD_SUBJECT_VERSION
  local HEAD_BUILDER_HEAD
  local HEAD_VERSION_VALUE

  if HEAD_VERSION_VALUE="$(
    head_value headVersion
  )"
  then
    :
  else
    return $?
  fi

  if HEAD_SEQUENCE="$(
    head_value acceptedSequence
  )"
  then
    :
  else
    return $?
  fi

  if HEAD_PACK_SHA="$(
    head_value acceptedPackSha256
  )"
  then
    :
  else
    return $?
  fi

  if HEAD_SUBJECT_VERSION="$(
    head_value subjectSystemVersion
  )"
  then
    :
  else
    return $?
  fi

  if HEAD_BUILDER_HEAD="$(
    head_value builderAuthorityHead
  )"
  then
    :
  else
    return $?
  fi

  if [[ "${HEAD_VERSION_VALUE}" != "${HEAD_VERSION}" ]]; then
    return 4
  fi

  if [[ "${CURRENT_HEAD_SHA}" != "${LEDGER_TAIL_AFTER_HEAD}" ]]; then
    return 4
  fi

  if [[ "${HEAD_SEQUENCE}" != "${LEDGER_TAIL_SEQUENCE}" ]]; then
    return 4
  fi

  if [[ "${HEAD_PACK_SHA}" != "${LEDGER_TAIL_PACK_SHA}" ]]; then
    return 4
  fi

  if [[ "${HEAD_SUBJECT_VERSION}" != "${LEDGER_TAIL_SUBJECT_VERSION}" ]]; then
    return 4
  fi

  if [[ "${HEAD_BUILDER_HEAD}" != "${LEDGER_TAIL_BUILDER_HEAD}" ]]; then
    return 4
  fi

  echo "TRUST_PACK_HEAD_LEDGER_HEAD_MATCH=true"

  echo "TRUST_PACK_HEAD_LEDGER_VALID=true"

  return 0
}

append_ledger_receipt() {
  local OPERATION="$1"
  local SEQUENCE="$2"
  local PACK_SHA="$3"
  local BEFORE_HEAD_SHA="$4"
  local AFTER_HEAD_SHA="$5"
  local SUBJECT_VERSION="$6"
  local BUILDER_HEAD="$7"
  local PREVIOUS_RECEIPT="$8"
  local EXPECTED_RECEIPT="${9:-}"

  local PAYLOAD
  local RECEIPT
  local LINE
  local TEMP_LEDGER

  PAYLOAD="$(
    build_receipt_payload \
      "${OPERATION}" \
      "${SEQUENCE}" \
      "${PACK_SHA}" \
      "${BEFORE_HEAD_SHA}" \
      "${AFTER_HEAD_SHA}" \
      "${SUBJECT_VERSION}" \
      "${BUILDER_HEAD}" \
      "${PREVIOUS_RECEIPT}"
  )"

  RECEIPT="$(
    sha256_text "${PAYLOAD}"
  )"

  if [[ -n "${EXPECTED_RECEIPT}" ]]; then
    [[ "${RECEIPT}" == "${EXPECTED_RECEIPT}" ]]
  fi

  LINE="${PAYLOAD}|receiptSha256=${RECEIPT}"

  TEMP_LEDGER="${LEDGER_FILE}.tmp.$$"

  rm -f "${TEMP_LEDGER}"

  if [[ -e "${LEDGER_FILE}" ]]; then
    cat "${LEDGER_FILE}" \
      > "${TEMP_LEDGER}"
  fi

  printf '%s\n' \
    "${LINE}" \
    >> "${TEMP_LEDGER}"

  chmod 600 "${TEMP_LEDGER}"

  fsync_file "${TEMP_LEDGER}"

  durable_replace \
    "${TEMP_LEDGER}" \
    "${LEDGER_FILE}"

  echo "TRUST_PACK_HEAD_LEDGER_FSYNC=true"

  echo "TRUST_PACK_HEAD_LEDGER_RECEIPT_SHA256=${RECEIPT}"
}

if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
  echo "TRUST_PACK_HEAD_LOCK_ACQUIRED=false"
  echo "TRUST_PACK_HEAD_MUTATED=false"
  echo "TRUST_PACK_HEAD_RESULT=LOCKED"

  exit 3
fi

LOCK_HELD=true

cleanup_lock() {
  if [[ "${LOCK_HELD:-false}" == "true" ]]; then
    rm -rf "${LOCK_DIR}"
  fi
}

trap cleanup_lock EXIT

echo "TRUST_PACK_HEAD_LOCK_ACQUIRED=true"

# ============================================================================
# AUDIT
# ============================================================================

if [[ "${COMMAND}" == "audit" ]]; then

  if [[ -e "${JOURNAL_FILE}" || -e "${PENDING_FILE}" ]]; then
    echo "TRUST_PACK_HEAD_RECOVERY_REQUIRED=true"
    echo "TRUST_PACK_HEAD_LEDGER_VALID=false"
    echo "TRUST_PACK_HEAD_AUDIT_RESULT=RECOVERY_REQUIRED"

    exit 4
  fi

  if ! audit_ledger_against_head; then
    echo "TRUST_PACK_HEAD_LEDGER_VALID=false"
    echo "TRUST_PACK_HEAD_AUDIT_RESULT=INVALID"

    exit 4
  fi

  echo "TRUST_PACK_HEAD_AUDIT_RESULT=VALID"

  exit 0
fi

# ============================================================================
# RECOVERY
# ============================================================================

if [[ "${COMMAND}" == "recover" ]]; then

  if [[ ! -s "${JOURNAL_FILE}" ]]; then

    if [[ -s "${HEAD_FILE}" && -s "${LEDGER_FILE}" ]]; then
      audit_ledger_against_head

      echo "TRUST_PACK_HEAD_JOURNAL_PRESENT=false"
      echo "TRUST_PACK_HEAD_MUTATED=false"
      echo "TRUST_PACK_HEAD_RECOVERY_RESULT=NOT_REQUIRED"

      exit 0
    fi

    echo "TRUST_PACK_HEAD_JOURNAL_PRESENT=false"
    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=INVALID"

    exit 4
  fi

  echo "TRUST_PACK_HEAD_JOURNAL_PRESENT=true"

  JOURNAL_VERSION_VALUE="$(
    journal_value journalVersion
  )"

  JOURNAL_OPERATION="$(
    journal_value operation
  )"

  BEFORE_HEAD_SHA="$(
    journal_value beforeHeadSha256
  )"

  AFTER_HEAD_SHA="$(
    journal_value afterHeadSha256
  )"

  CANDIDATE_SEQUENCE="$(
    journal_value candidateSequence
  )"

  CANDIDATE_PACK_SHA="$(
    journal_value candidatePackSha256
  )"

  CANDIDATE_SUBJECT_VERSION="$(
    journal_value candidateSubjectSystemVersion
  )"

  CANDIDATE_BUILDER_HEAD="$(
    journal_value candidateBuilderAuthorityHead
  )"

  PREVIOUS_RECEIPT="$(
    journal_value previousReceiptSha256
  )"

  JOURNAL_RECEIPT="$(
    journal_value receiptSha256
  )"

  [[ "${JOURNAL_VERSION_VALUE}" == "${JOURNAL_VERSION}" ]]

  [[ "${JOURNAL_OPERATION}" == "INIT" || "${JOURNAL_OPERATION}" == "APPLY" ]]

  [[ "${AFTER_HEAD_SHA}" =~ ^[0-9a-f]{64}$ ]]

  [[ "${CANDIDATE_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]

  [[ "${CANDIDATE_PACK_SHA}" =~ ^[0-9a-f]{64}$ ]]

  [[ "${CANDIDATE_SUBJECT_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]

  [[ "${CANDIDATE_BUILDER_HEAD}" =~ ^[0-9a-f]{40}$ ]]

  [[ "${JOURNAL_RECEIPT}" =~ ^[0-9a-f]{64}$ ]]

  if [[ "${JOURNAL_OPERATION}" == "INIT" ]]; then
    [[ "${BEFORE_HEAD_SHA}" == "GENESIS" ]]
    [[ "${PREVIOUS_RECEIPT}" == "GENESIS" ]]
  else
    [[ "${BEFORE_HEAD_SHA}" =~ ^[0-9a-f]{64}$ ]]
    [[ "${PREVIOUS_RECEIPT}" =~ ^[0-9a-f]{64}$ ]]
  fi

  EXPECTED_RECEIPT_PAYLOAD="$(
    build_receipt_payload \
      "${JOURNAL_OPERATION}" \
      "${CANDIDATE_SEQUENCE}" \
      "${CANDIDATE_PACK_SHA}" \
      "${BEFORE_HEAD_SHA}" \
      "${AFTER_HEAD_SHA}" \
      "${CANDIDATE_SUBJECT_VERSION}" \
      "${CANDIDATE_BUILDER_HEAD}" \
      "${PREVIOUS_RECEIPT}"
  )"

  EXPECTED_RECEIPT_SHA="$(
    sha256_text "${EXPECTED_RECEIPT_PAYLOAD}"
  )"

  [[ "${EXPECTED_RECEIPT_SHA}" == "${JOURNAL_RECEIPT}" ]]

  if [[ "${JOURNAL_OPERATION}" == "INIT" ]]; then

    if [[ ! -e "${HEAD_FILE}" ]]; then

      [[ ! -e "${LEDGER_FILE}" ]]

      [[ -s "${PENDING_FILE}" ]]

      PENDING_SHA="$(
        sha256_file "${PENDING_FILE}"
      )"

      [[ "${PENDING_SHA}" == "${AFTER_HEAD_SHA}" ]]

      durable_remove "${PENDING_FILE}"

      durable_remove "${JOURNAL_FILE}"

      echo "TRUST_PACK_HEAD_RECOVERY_STATE=PRE_RENAME"

      echo "TRUST_PACK_HEAD_MUTATED=false"

      echo "TRUST_PACK_HEAD_RECOVERY_RESULT=ROLLED_BACK"

      exit 0
    fi

    CURRENT_HEAD_SHA="$(
      sha256_file "${HEAD_FILE}"
    )"

    if [[ "${CURRENT_HEAD_SHA}" != "${AFTER_HEAD_SHA}" ]]; then
      echo "TRUST_PACK_HEAD_RECOVERY_STATE=CORRUPT"
      echo "TRUST_PACK_HEAD_RECOVERY_RESULT=INVALID"

      exit 4
    fi

    if [[ ! -e "${LEDGER_FILE}" ]]; then
      append_ledger_receipt \
        "INIT" \
        "${CANDIDATE_SEQUENCE}" \
        "${CANDIDATE_PACK_SHA}" \
        "${BEFORE_HEAD_SHA}" \
        "${AFTER_HEAD_SHA}" \
        "${CANDIDATE_SUBJECT_VERSION}" \
        "${CANDIDATE_BUILDER_HEAD}" \
        "${PREVIOUS_RECEIPT}" \
        "${JOURNAL_RECEIPT}"
    else
      load_ledger_chain

      [[ "${LEDGER_TAIL_RECEIPT}" == "${JOURNAL_RECEIPT}" ]]
    fi

    audit_ledger_against_head

    durable_remove "${JOURNAL_FILE}"

    echo "TRUST_PACK_HEAD_RECOVERY_STATE=POST_RENAME"

    echo "TRUST_PACK_HEAD_MUTATED=false"

    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=COMMITTED"

    exit 0
  fi

  [[ -s "${HEAD_FILE}" ]]
  [[ -s "${LEDGER_FILE}" ]]

  CURRENT_HEAD_SHA="$(
    sha256_file "${HEAD_FILE}"
  )"

  if [[ "${CURRENT_HEAD_SHA}" == "${BEFORE_HEAD_SHA}" ]]; then

    audit_ledger_against_head

    [[ "${LEDGER_TAIL_RECEIPT}" == "${PREVIOUS_RECEIPT}" ]]

    [[ -s "${PENDING_FILE}" ]]

    PENDING_SHA="$(
      sha256_file "${PENDING_FILE}"
    )"

    [[ "${PENDING_SHA}" == "${AFTER_HEAD_SHA}" ]]

    durable_remove "${PENDING_FILE}"

    durable_remove "${JOURNAL_FILE}"

    echo "TRUST_PACK_HEAD_RECOVERY_STATE=PRE_RENAME"

    echo "TRUST_PACK_HEAD_MUTATED=false"

    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=ROLLED_BACK"

    exit 0
  fi

  if [[ "${CURRENT_HEAD_SHA}" != "${AFTER_HEAD_SHA}" ]]; then
    echo "TRUST_PACK_HEAD_RECOVERY_STATE=CORRUPT"

    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=INVALID"

    exit 4
  fi

  load_ledger_chain

  if [[ "${LEDGER_TAIL_RECEIPT}" == "${PREVIOUS_RECEIPT}" ]]; then

    append_ledger_receipt \
      "APPLY" \
      "${CANDIDATE_SEQUENCE}" \
      "${CANDIDATE_PACK_SHA}" \
      "${BEFORE_HEAD_SHA}" \
      "${AFTER_HEAD_SHA}" \
      "${CANDIDATE_SUBJECT_VERSION}" \
      "${CANDIDATE_BUILDER_HEAD}" \
      "${PREVIOUS_RECEIPT}" \
      "${JOURNAL_RECEIPT}"

  elif [[ "${LEDGER_TAIL_RECEIPT}" == "${JOURNAL_RECEIPT}" ]]; then

    echo "TRUST_PACK_HEAD_LEDGER_RECEIPT_ALREADY_PRESENT=true"

  else
    echo "TRUST_PACK_HEAD_RECOVERY_STATE=CORRUPT"

    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=INVALID"

    exit 4
  fi

  audit_ledger_against_head

  durable_remove "${JOURNAL_FILE}"

  echo "TRUST_PACK_HEAD_RECOVERY_STATE=POST_RENAME"

  echo "TRUST_PACK_HEAD_MUTATED=false"

  echo "TRUST_PACK_HEAD_RECOVERY_RESULT=COMMITTED"

  exit 0
fi

# ============================================================================
# INIT / APPLY COMMON AUTHORITY
# ============================================================================

[[ -s "${PACK_FILE}" ]]
[[ -s "${PACK_PIN}" ]]
[[ -x "${PACK_VERIFIER}" ]]

if [[ -e "${JOURNAL_FILE}" || -e "${PENDING_FILE}" ]]; then
  echo "TRUST_PACK_HEAD_RECOVERY_REQUIRED=true"

  echo "TRUST_PACK_HEAD_MUTATED=false"

  echo "TRUST_PACK_HEAD_RESULT=RECOVERY_REQUIRED"

  exit 4
fi

PACK_SHA="$(
  sha256_file "${PACK_FILE}"
)"

PIN_SHA="$(
  tr -d '[:space:]' \
    < "${PACK_PIN}"
)"

PIN_SHA="$(
  printf '%s' "${PIN_SHA}" \
    | tr '[:upper:]' '[:lower:]'
)"

[[ "${PIN_SHA}" =~ ^[0-9a-f]{64}$ ]]
[[ "${PACK_SHA}" == "${PIN_SHA}" ]]

VERIFY_LOG="$(
  mktemp \
    "${TMPDIR:-/tmp}/terrascope-head-verify.XXXXXX"
)"

MANIFEST_DIR="$(
  mktemp -d \
    "${TMPDIR:-/tmp}/terrascope-head-manifest.XXXXXX"
)"

cleanup_all() {
  rm -f "${VERIFY_LOG}"

  rm -rf "${MANIFEST_DIR}"

  if [[ "${LOCK_HELD:-false}" == "true" ]]; then
    rm -rf "${LOCK_DIR}"
  fi
}

trap cleanup_all EXIT

extract_manifest() {
  rm -rf "${MANIFEST_DIR}"

  mkdir -p "${MANIFEST_DIR}"

  local PACK_ABS

  PACK_ABS="$(
    cd "$(dirname "${PACK_FILE}")"
    pwd
  )/$(basename "${PACK_FILE}")"

  (
    cd "${MANIFEST_DIR}"

    jar xf \
      "${PACK_ABS}" \
      pack.manifest
  )

  [[ -s "${MANIFEST_DIR}/pack.manifest" ]]
}

verify_candidate() {
  local EXPECTED_SEQUENCE="$1"
  local EXPECTED_PREDECESSOR="$2"

  rm -f "${VERIFY_LOG}"

  local STATUS=0

  if bash \
      "${PACK_VERIFIER}" \
      "${PACK_FILE}" \
      "${PACK_PIN}" \
      "${EXPECTED_SEQUENCE}" \
      "${EXPECTED_PREDECESSOR}" \
      > "${VERIFY_LOG}" \
      2>&1
  then
    STATUS=0
  else
    STATUS=$?
  fi

  cat "${VERIFY_LOG}"

  echo "TRUST_PACK_HEAD_CANDIDATE_VERIFY_EXIT=${STATUS}"

  if [[ "${STATUS}" != "0" ]]; then
    echo "TRUST_PACK_HEAD_MUTATED=false"

    echo "TRUST_PACK_HEAD_RESULT=REJECTED"

    exit "${STATUS}"
  fi

  grep \
    -q \
    '^TRUST_PACK_VERIFICATION_RESULT=VALID$' \
    "${VERIFY_LOG}"

  extract_manifest
}

write_pending_head() {
  local SEQUENCE="$1"
  local PACK_SHA_VALUE="$2"
  local SUBJECT_VERSION="$3"
  local BUILDER_HEAD="$4"

  rm -f "${PENDING_FILE}"

  cat > "${PENDING_FILE}" <<EOF
headVersion=${HEAD_VERSION}
acceptedSequence=${SEQUENCE}
acceptedPackSha256=${PACK_SHA_VALUE}
subjectSystemVersion=${SUBJECT_VERSION}
builderAuthorityHead=${BUILDER_HEAD}
EOF

  chmod 600 "${PENDING_FILE}"

  fsync_file "${PENDING_FILE}"

  fsync_directory "${HEAD_DIR}"

  echo "TRUST_PACK_HEAD_PENDING_FSYNC=true"
}

publish_head() {
  durable_replace \
    "${PENDING_FILE}" \
    "${HEAD_FILE}"

  echo "TRUST_PACK_HEAD_ATOMIC_RENAME=true"

  echo "TRUST_PACK_HEAD_DIRECTORY_FSYNC=true"
}

prepare_journal() {
  local OPERATION="$1"
  local BEFORE_HEAD_SHA="$2"
  local AFTER_HEAD_SHA="$3"
  local CANDIDATE_SEQUENCE="$4"
  local CANDIDATE_PACK_SHA="$5"
  local SUBJECT_VERSION="$6"
  local BUILDER_HEAD="$7"
  local PREVIOUS_RECEIPT="$8"
  local RECEIPT_SHA="$9"

  local JOURNAL_TEMP

  JOURNAL_TEMP="${JOURNAL_FILE}.tmp.$$"

  rm -f "${JOURNAL_TEMP}"

  cat > "${JOURNAL_TEMP}" <<EOF
journalVersion=${JOURNAL_VERSION}
operation=${OPERATION}
beforeHeadSha256=${BEFORE_HEAD_SHA}
afterHeadSha256=${AFTER_HEAD_SHA}
candidateSequence=${CANDIDATE_SEQUENCE}
candidatePackSha256=${CANDIDATE_PACK_SHA}
candidateSubjectSystemVersion=${SUBJECT_VERSION}
candidateBuilderAuthorityHead=${BUILDER_HEAD}
previousReceiptSha256=${PREVIOUS_RECEIPT}
receiptSha256=${RECEIPT_SHA}
EOF

  chmod 600 "${JOURNAL_TEMP}"

  fsync_file "${JOURNAL_TEMP}"

  durable_replace \
    "${JOURNAL_TEMP}" \
    "${JOURNAL_FILE}"

  echo "TRUST_PACK_HEAD_JOURNAL_FSYNC=true"

  echo "TRUST_PACK_HEAD_JOURNAL_PREPARED=true"
}

# ============================================================================
# INIT
# ============================================================================

if [[ "${COMMAND}" == "init" ]]; then

  if [[ -e "${HEAD_FILE}" || -e "${LEDGER_FILE}" ]]; then
    echo "TRUST_PACK_HEAD_ALREADY_EXISTS=true"

    echo "TRUST_PACK_HEAD_MUTATED=false"

    echo "TRUST_PACK_HEAD_RESULT=REJECTED"

    exit 2
  fi

  verify_candidate \
    "1" \
    "GENESIS"

  MANIFEST="${MANIFEST_DIR}/pack.manifest"

  PACK_SEQUENCE="$(
    manifest_value "${MANIFEST}" packSequence
  )"

  PACK_PREDECESSOR="$(
    manifest_value "${MANIFEST}" predecessorPackSha256
  )"

  SUBJECT_VERSION="$(
    manifest_value "${MANIFEST}" subjectSystemVersion
  )"

  BUILDER_HEAD="$(
    manifest_value "${MANIFEST}" builderAuthorityHead
  )"

  [[ "${PACK_SEQUENCE}" == "1" ]]
  [[ "${PACK_PREDECESSOR}" == "GENESIS" ]]

  write_pending_head \
    "${PACK_SEQUENCE}" \
    "${PACK_SHA}" \
    "${SUBJECT_VERSION}" \
    "${BUILDER_HEAD}"

  AFTER_HEAD_SHA="$(
    sha256_file "${PENDING_FILE}"
  )"

  RECEIPT_PAYLOAD="$(
    build_receipt_payload \
      "INIT" \
      "${PACK_SEQUENCE}" \
      "${PACK_SHA}" \
      "GENESIS" \
      "${AFTER_HEAD_SHA}" \
      "${SUBJECT_VERSION}" \
      "${BUILDER_HEAD}" \
      "GENESIS"
  )"

  RECEIPT_SHA="$(
    sha256_text "${RECEIPT_PAYLOAD}"
  )"

  prepare_journal \
    "INIT" \
    "GENESIS" \
    "${AFTER_HEAD_SHA}" \
    "${PACK_SEQUENCE}" \
    "${PACK_SHA}" \
    "${SUBJECT_VERSION}" \
    "${BUILDER_HEAD}" \
    "GENESIS" \
    "${RECEIPT_SHA}"

  if [[ "${TERRASCOPE_HEAD_FAULT:-}" == "AFTER_JOURNAL" ]]; then
    echo "TRUST_PACK_HEAD_FAULT_INJECTED=AFTER_JOURNAL"

    echo "TRUST_PACK_HEAD_MUTATED=false"

    echo "TRUST_PACK_HEAD_RESULT=INTERRUPTED"

    exit 97
  fi

  publish_head

  if [[ "${TERRASCOPE_HEAD_FAULT:-}" == "AFTER_RENAME" ]]; then
    echo "TRUST_PACK_HEAD_FAULT_INJECTED=AFTER_RENAME"

    echo "TRUST_PACK_HEAD_MUTATED=true"

    echo "TRUST_PACK_HEAD_RESULT=INTERRUPTED"

    exit 98
  fi

  append_ledger_receipt \
    "INIT" \
    "${PACK_SEQUENCE}" \
    "${PACK_SHA}" \
    "GENESIS" \
    "${AFTER_HEAD_SHA}" \
    "${SUBJECT_VERSION}" \
    "${BUILDER_HEAD}" \
    "GENESIS" \
    "${RECEIPT_SHA}"

  if [[ "${TERRASCOPE_HEAD_FAULT:-}" == "AFTER_LEDGER" ]]; then
    echo "TRUST_PACK_HEAD_FAULT_INJECTED=AFTER_LEDGER"

    echo "TRUST_PACK_HEAD_MUTATED=true"

    echo "TRUST_PACK_HEAD_RESULT=INTERRUPTED"

    exit 99
  fi

  audit_ledger_against_head

  durable_remove "${JOURNAL_FILE}"

  echo "TRUST_PACK_HEAD_JOURNAL_CLEARED=true"

  echo "TRUST_PACK_HEAD_ACCEPTED_SEQUENCE=${PACK_SEQUENCE}"

  echo "TRUST_PACK_HEAD_ACCEPTED_PACK_SHA256=${PACK_SHA}"

  echo "TRUST_PACK_HEAD_MUTATED=true"

  echo "TRUST_PACK_HEAD_RESULT=INITIALIZED"

  exit 0
fi

# ============================================================================
# APPLY
# ============================================================================

[[ -s "${HEAD_FILE}" ]]
[[ -s "${LEDGER_FILE}" ]]

if ! audit_ledger_against_head; then
  echo "TRUST_PACK_HEAD_LEDGER_VALID=false"

  echo "TRUST_PACK_HEAD_MUTATED=false"

  echo "TRUST_PACK_HEAD_RESULT=LEDGER_INVALID"

  exit 4
fi

HEAD_VERSION_VALUE="$(
  head_value headVersion
)"

ACCEPTED_SEQUENCE="$(
  head_value acceptedSequence
)"

ACCEPTED_PACK_SHA="$(
  head_value acceptedPackSha256
)"

[[ "${HEAD_VERSION_VALUE}" == "${HEAD_VERSION}" ]]

BEFORE_HEAD_SHA="$(
  sha256_file "${HEAD_FILE}"
)"

EXPECTED_SEQUENCE="$(
  python3 \
    - "${ACCEPTED_SEQUENCE}" \
<<'PY'
import sys

print(
    int(
        sys.argv[1]
    )
    + 1
)
PY
)"

EXPECTED_PREDECESSOR="${ACCEPTED_PACK_SHA}"

PREVIOUS_RECEIPT="${LEDGER_TAIL_RECEIPT}"

echo "TRUST_PACK_HEAD_EXPECTED_NEXT_SEQUENCE=${EXPECTED_SEQUENCE}"

echo "TRUST_PACK_HEAD_EXPECTED_PREDECESSOR_SHA256=${EXPECTED_PREDECESSOR}"

echo "TRUST_PACK_HEAD_LEDGER_PREVIOUS_RECEIPT_SHA256=${PREVIOUS_RECEIPT}"

verify_candidate \
  "${EXPECTED_SEQUENCE}" \
  "${EXPECTED_PREDECESSOR}"

MANIFEST="${MANIFEST_DIR}/pack.manifest"

PACK_SEQUENCE="$(
  manifest_value "${MANIFEST}" packSequence
)"

PACK_PREDECESSOR="$(
  manifest_value "${MANIFEST}" predecessorPackSha256
)"

SUBJECT_VERSION="$(
  manifest_value "${MANIFEST}" subjectSystemVersion
)"

BUILDER_HEAD="$(
  manifest_value "${MANIFEST}" builderAuthorityHead
)"

[[ "${PACK_SEQUENCE}" == "${EXPECTED_SEQUENCE}" ]]
[[ "${PACK_PREDECESSOR}" == "${EXPECTED_PREDECESSOR}" ]]

write_pending_head \
  "${PACK_SEQUENCE}" \
  "${PACK_SHA}" \
  "${SUBJECT_VERSION}" \
  "${BUILDER_HEAD}"

AFTER_HEAD_SHA="$(
  sha256_file "${PENDING_FILE}"
)"

RECEIPT_PAYLOAD="$(
  build_receipt_payload \
    "APPLY" \
    "${PACK_SEQUENCE}" \
    "${PACK_SHA}" \
    "${BEFORE_HEAD_SHA}" \
    "${AFTER_HEAD_SHA}" \
    "${SUBJECT_VERSION}" \
    "${BUILDER_HEAD}" \
    "${PREVIOUS_RECEIPT}"
)"

RECEIPT_SHA="$(
  sha256_text "${RECEIPT_PAYLOAD}"
)"

prepare_journal \
  "APPLY" \
  "${BEFORE_HEAD_SHA}" \
  "${AFTER_HEAD_SHA}" \
  "${PACK_SEQUENCE}" \
  "${PACK_SHA}" \
  "${SUBJECT_VERSION}" \
  "${BUILDER_HEAD}" \
  "${PREVIOUS_RECEIPT}" \
  "${RECEIPT_SHA}"

if [[ "${TERRASCOPE_HEAD_FAULT:-}" == "AFTER_JOURNAL" ]]; then
  echo "TRUST_PACK_HEAD_FAULT_INJECTED=AFTER_JOURNAL"

  echo "TRUST_PACK_HEAD_MUTATED=false"

  echo "TRUST_PACK_HEAD_RESULT=INTERRUPTED"

  exit 97
fi

publish_head

if [[ "${TERRASCOPE_HEAD_FAULT:-}" == "AFTER_RENAME" ]]; then
  echo "TRUST_PACK_HEAD_FAULT_INJECTED=AFTER_RENAME"

  echo "TRUST_PACK_HEAD_MUTATED=true"

  echo "TRUST_PACK_HEAD_RESULT=INTERRUPTED"

  exit 98
fi

append_ledger_receipt \
  "APPLY" \
  "${PACK_SEQUENCE}" \
  "${PACK_SHA}" \
  "${BEFORE_HEAD_SHA}" \
  "${AFTER_HEAD_SHA}" \
  "${SUBJECT_VERSION}" \
  "${BUILDER_HEAD}" \
  "${PREVIOUS_RECEIPT}" \
  "${RECEIPT_SHA}"

if [[ "${TERRASCOPE_HEAD_FAULT:-}" == "AFTER_LEDGER" ]]; then
  echo "TRUST_PACK_HEAD_FAULT_INJECTED=AFTER_LEDGER"

  echo "TRUST_PACK_HEAD_MUTATED=true"

  echo "TRUST_PACK_HEAD_RESULT=INTERRUPTED"

  exit 99
fi

audit_ledger_against_head

durable_remove "${JOURNAL_FILE}"

echo "TRUST_PACK_HEAD_JOURNAL_CLEARED=true"

echo "TRUST_PACK_HEAD_ACCEPTED_SEQUENCE=${PACK_SEQUENCE}"

echo "TRUST_PACK_HEAD_ACCEPTED_PACK_SHA256=${PACK_SHA}"

echo "TRUST_PACK_HEAD_LEDGER_ACCEPTED_RECEIPT_SHA256=${RECEIPT_SHA}"

echo "TRUST_PACK_HEAD_MUTATED=true"

echo "TRUST_PACK_HEAD_RESULT=ADVANCED"
