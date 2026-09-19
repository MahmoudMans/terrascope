#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage:" >&2
  echo "  $0 init <head-file> <genesis-pack> <pack-pin> <trust-pack-verifier>" >&2
  echo "  $0 apply <head-file> <candidate-pack> <pack-pin> <trust-pack-verifier>" >&2
  echo "  $0 recover <head-file>" >&2
}

if [[ "$#" -lt 1 ]]; then
  usage
  exit 64
fi

COMMAND="$1"

if [[ "${COMMAND}" == "recover" ]]; then

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

JOURNAL_VERSION="TS-OFFLINE-TRUST-PACK-HEAD-JOURNAL-V1"

HEAD_VERSION="TS-OFFLINE-TRUST-PACK-HEAD-V1"

mkdir -p "${HEAD_DIR}"

fsync_file() {
  local FILE="$1"

  python3 \
    - "${FILE}" \
<<'PY'
import os
import sys

path = sys.argv[1]

fd = os.open(
    path,
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)
PY
}

fsync_directory() {
  local DIR="$1"

  python3 \
    - "${DIR}" \
<<'PY'
import os
import sys

path = sys.argv[1]

fd = os.open(
    path,
    os.O_RDONLY,
)

try:
    os.fsync(fd)
finally:
    os.close(fd)
PY
}

durable_replace() {
  local SOURCE="$1"
  local DESTINATION="$2"

  python3 \
    - \
    "${SOURCE}" \
    "${DESTINATION}" \
<<'PY'
import os
import sys

source = sys.argv[1]
destination = sys.argv[2]

directory = os.path.dirname(
    destination
) or "."

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
  local FILE="$1"

  python3 \
    - "${FILE}" \
<<'PY'
import os
import sys

path = sys.argv[1]

directory = os.path.dirname(
    path
) or "."

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

sha256_file() {
  shasum -a 256 \
    "$1" \
    | awk '{print $1}'
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
# RECOVERY
# ============================================================================

if [[ "${COMMAND}" == "recover" ]]; then

  if [[ ! -s "${JOURNAL_FILE}" ]]; then
    echo "TRUST_PACK_HEAD_JOURNAL_PRESENT=false"
    echo "TRUST_PACK_HEAD_MUTATED=false"
    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=NOT_REQUIRED"

    exit 0
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

  [[ "${JOURNAL_VERSION_VALUE}" == "${JOURNAL_VERSION}" ]]
  [[ "${JOURNAL_OPERATION}" == "APPLY" ]]
  [[ "${BEFORE_HEAD_SHA}" =~ ^[0-9a-f]{64}$ ]]
  [[ "${AFTER_HEAD_SHA}" =~ ^[0-9a-f]{64}$ ]]
  [[ "${CANDIDATE_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]
  [[ "${CANDIDATE_PACK_SHA}" =~ ^[0-9a-f]{64}$ ]]
  [[ "${CANDIDATE_SUBJECT_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
  [[ "${CANDIDATE_BUILDER_HEAD}" =~ ^[0-9a-f]{40}$ ]]
  [[ -s "${HEAD_FILE}" ]]

  CURRENT_HEAD_SHA="$(
    sha256_file "${HEAD_FILE}"
  )"

  echo "TRUST_PACK_HEAD_RECOVERY_BEFORE_SHA256=${BEFORE_HEAD_SHA}"
  echo "TRUST_PACK_HEAD_RECOVERY_AFTER_SHA256=${AFTER_HEAD_SHA}"
  echo "TRUST_PACK_HEAD_RECOVERY_CURRENT_SHA256=${CURRENT_HEAD_SHA}"

  if [[ "${CURRENT_HEAD_SHA}" == "${BEFORE_HEAD_SHA}" ]]; then

    if [[ ! -s "${PENDING_FILE}" ]]; then
      echo "TRUST_PACK_HEAD_RECOVERY_STATE=CORRUPT"
      echo "TRUST_PACK_HEAD_MUTATED=false"
      echo "TRUST_PACK_HEAD_RECOVERY_RESULT=INVALID"

      exit 4
    fi

    PENDING_SHA="$(
      sha256_file "${PENDING_FILE}"
    )"

    echo "TRUST_PACK_HEAD_RECOVERY_PENDING_SHA256=${PENDING_SHA}"

    if [[ "${PENDING_SHA}" != "${AFTER_HEAD_SHA}" ]]; then
      echo "TRUST_PACK_HEAD_RECOVERY_STATE=CORRUPT"
      echo "TRUST_PACK_HEAD_MUTATED=false"
      echo "TRUST_PACK_HEAD_RECOVERY_RESULT=INVALID"

      exit 4
    fi

    durable_remove "${PENDING_FILE}"

    durable_remove "${JOURNAL_FILE}"

    echo "TRUST_PACK_HEAD_RECOVERY_STATE=PRE_RENAME"

    echo "TRUST_PACK_HEAD_MUTATED=false"

    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=ROLLED_BACK"

    exit 0
  fi

  if [[ "${CURRENT_HEAD_SHA}" == "${AFTER_HEAD_SHA}" ]]; then

    if [[ -e "${PENDING_FILE}" ]]; then
      durable_remove "${PENDING_FILE}"
    fi

    durable_remove "${JOURNAL_FILE}"

    echo "TRUST_PACK_HEAD_RECOVERY_STATE=POST_RENAME"

    echo "TRUST_PACK_HEAD_MUTATED=false"

    echo "TRUST_PACK_HEAD_RECOVERY_RESULT=COMMITTED"

    exit 0
  fi

  echo "TRUST_PACK_HEAD_RECOVERY_STATE=CORRUPT"

  echo "TRUST_PACK_HEAD_MUTATED=false"

  echo "TRUST_PACK_HEAD_RECOVERY_RESULT=INVALID"

  exit 4
fi

# ============================================================================
# INIT / APPLY COMMON INPUT AUTHORITY
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

# ============================================================================
# INIT
# ============================================================================

if [[ "${COMMAND}" == "init" ]]; then

  if [[ -e "${HEAD_FILE}" ]]; then
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
    manifest_value \
      "${MANIFEST}" \
      packSequence
  )"

  PACK_PREDECESSOR="$(
    manifest_value \
      "${MANIFEST}" \
      predecessorPackSha256
  )"

  SUBJECT_VERSION="$(
    manifest_value \
      "${MANIFEST}" \
      subjectSystemVersion
  )"

  BUILDER_HEAD="$(
    manifest_value \
      "${MANIFEST}" \
      builderAuthorityHead
  )"

  [[ "${PACK_SEQUENCE}" == "1" ]]
  [[ "${PACK_PREDECESSOR}" == "GENESIS" ]]

  write_pending_head \
    "${PACK_SEQUENCE}" \
    "${PACK_SHA}" \
    "${SUBJECT_VERSION}" \
    "${BUILDER_HEAD}"

  publish_head

  echo "TRUST_PACK_HEAD_VERSION=${HEAD_VERSION}"
  echo "TRUST_PACK_HEAD_ACCEPTED_SEQUENCE=${PACK_SEQUENCE}"
  echo "TRUST_PACK_HEAD_ACCEPTED_PACK_SHA256=${PACK_SHA}"
  echo "TRUST_PACK_HEAD_SUBJECT_SYSTEM_VERSION=${SUBJECT_VERSION}"
  echo "TRUST_PACK_HEAD_BUILDER_AUTHORITY_HEAD=${BUILDER_HEAD}"

  echo "TRUST_PACK_HEAD_MUTATED=true"
  echo "TRUST_PACK_HEAD_RESULT=INITIALIZED"

  exit 0
fi

# ============================================================================
# APPLY
# ============================================================================

[[ -s "${HEAD_FILE}" ]]

HEAD_VERSION_VALUE="$(
  head_value headVersion
)"

ACCEPTED_SEQUENCE="$(
  head_value acceptedSequence
)"

ACCEPTED_PACK_SHA="$(
  head_value acceptedPackSha256
)"

ACCEPTED_SUBJECT_VERSION="$(
  head_value subjectSystemVersion
)"

ACCEPTED_BUILDER_HEAD="$(
  head_value builderAuthorityHead
)"

[[ "${HEAD_VERSION_VALUE}" == "${HEAD_VERSION}" ]]
[[ "${ACCEPTED_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]
[[ "${ACCEPTED_PACK_SHA}" =~ ^[0-9a-f]{64}$ ]]
[[ "${ACCEPTED_SUBJECT_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
[[ "${ACCEPTED_BUILDER_HEAD}" =~ ^[0-9a-f]{40}$ ]]

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

echo "TRUST_PACK_HEAD_VERSION=${HEAD_VERSION_VALUE}"
echo "TRUST_PACK_HEAD_CURRENT_SEQUENCE=${ACCEPTED_SEQUENCE}"
echo "TRUST_PACK_HEAD_CURRENT_PACK_SHA256=${ACCEPTED_PACK_SHA}"
echo "TRUST_PACK_HEAD_EXPECTED_NEXT_SEQUENCE=${EXPECTED_SEQUENCE}"
echo "TRUST_PACK_HEAD_EXPECTED_PREDECESSOR_SHA256=${EXPECTED_PREDECESSOR}"

verify_candidate \
  "${EXPECTED_SEQUENCE}" \
  "${EXPECTED_PREDECESSOR}"

MANIFEST="${MANIFEST_DIR}/pack.manifest"

PACK_SEQUENCE="$(
  manifest_value \
    "${MANIFEST}" \
    packSequence
)"

PACK_PREDECESSOR="$(
  manifest_value \
    "${MANIFEST}" \
    predecessorPackSha256
)"

SUBJECT_VERSION="$(
  manifest_value \
    "${MANIFEST}" \
    subjectSystemVersion
)"

BUILDER_HEAD="$(
  manifest_value \
    "${MANIFEST}" \
    builderAuthorityHead
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

JOURNAL_TEMP="${JOURNAL_FILE}.tmp.$$"

rm -f "${JOURNAL_TEMP}"

cat > "${JOURNAL_TEMP}" <<EOF
journalVersion=${JOURNAL_VERSION}
operation=APPLY
beforeHeadSha256=${BEFORE_HEAD_SHA}
afterHeadSha256=${AFTER_HEAD_SHA}
candidateSequence=${PACK_SEQUENCE}
candidatePackSha256=${PACK_SHA}
candidateSubjectSystemVersion=${SUBJECT_VERSION}
candidateBuilderAuthorityHead=${BUILDER_HEAD}
EOF

chmod 600 "${JOURNAL_TEMP}"

fsync_file "${JOURNAL_TEMP}"

durable_replace \
  "${JOURNAL_TEMP}" \
  "${JOURNAL_FILE}"

echo "TRUST_PACK_HEAD_JOURNAL_FSYNC=true"

echo "TRUST_PACK_HEAD_JOURNAL_PREPARED=true"

echo "TRUST_PACK_HEAD_JOURNAL_BEFORE_SHA256=${BEFORE_HEAD_SHA}"

echo "TRUST_PACK_HEAD_JOURNAL_AFTER_SHA256=${AFTER_HEAD_SHA}"

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

durable_remove "${JOURNAL_FILE}"

echo "TRUST_PACK_HEAD_JOURNAL_CLEARED=true"

echo "TRUST_PACK_HEAD_ACCEPTED_SEQUENCE=${PACK_SEQUENCE}"

echo "TRUST_PACK_HEAD_ACCEPTED_PACK_SHA256=${PACK_SHA}"

echo "TRUST_PACK_HEAD_SUBJECT_SYSTEM_VERSION=${SUBJECT_VERSION}"

echo "TRUST_PACK_HEAD_BUILDER_AUTHORITY_HEAD=${BUILDER_HEAD}"

echo "TRUST_PACK_HEAD_MUTATED=true"

echo "TRUST_PACK_HEAD_RESULT=ADVANCED"
