#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -lt 1 ]]; then
  echo "Usage:" >&2
  echo "  $0 init <head-file> <genesis-pack> <pack-pin> <trust-pack-verifier>" >&2
  echo "  $0 apply <head-file> <candidate-pack> <pack-pin> <trust-pack-verifier>" >&2

  exit 64
fi

COMMAND="$1"

if [[ "${COMMAND}" != "init" && "${COMMAND}" != "apply" ]]; then
  echo "Unsupported command: ${COMMAND}" >&2

  exit 64
fi

if [[ "$#" -ne 5 ]]; then
  echo "Usage:" >&2
  echo "  $0 init <head-file> <genesis-pack> <pack-pin> <trust-pack-verifier>" >&2
  echo "  $0 apply <head-file> <candidate-pack> <pack-pin> <trust-pack-verifier>" >&2

  exit 64
fi

HEAD_FILE="$2"

PACK_FILE="$3"

PACK_PIN="$4"

PACK_VERIFIER="$5"

for TOOL in \
  bash \
  jar \
  shasum \
  sed \
  grep \
  mkdir \
  mv
do
  command -v "${TOOL}" >/dev/null
done

[[ -s "${PACK_FILE}" ]]
[[ -s "${PACK_PIN}" ]]
[[ -x "${PACK_VERIFIER}" ]]

HEAD_DIR="$(
  dirname "${HEAD_FILE}"
)"

HEAD_BASENAME="$(
  basename "${HEAD_FILE}"
)"

mkdir -p "${HEAD_DIR}"

LOCK_DIR="${HEAD_FILE}.lock"

if ! mkdir "${LOCK_DIR}" 2>/dev/null; then
  echo "TRUST_PACK_HEAD_LOCK_ACQUIRED=false"
  echo "TRUST_PACK_HEAD_MUTATED=false"
  echo "TRUST_PACK_HEAD_RESULT=LOCKED"

  exit 3
fi

LOCK_HELD=true

cleanup() {
  if [[ "${LOCK_HELD:-false}" == "true" ]]; then
    rm -rf "${LOCK_DIR}"
  fi
}

trap cleanup EXIT

echo "TRUST_PACK_HEAD_LOCK_ACQUIRED=true"

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

extract_pack_manifest() {
  local DEST="$1"

  rm -rf "${DEST}"

  mkdir -p "${DEST}"

  local PACK_ABS

  PACK_ABS="$(
    cd "$(dirname "${PACK_FILE}")"
    pwd
  )/$(basename "${PACK_FILE}")"

  (
    cd "${DEST}"

    jar xf \
      "${PACK_ABS}" \
      pack.manifest
  )

  [[ -s "${DEST}/pack.manifest" ]]
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

atomic_write_head() {
  local SEQUENCE="$1"
  local PACK_SHA="$2"
  local SUBJECT_VERSION="$3"
  local BUILDER_HEAD="$4"

  local TEMP_FILE

  TEMP_FILE="${HEAD_DIR}/.${HEAD_BASENAME}.tmp.$$"

  rm -f "${TEMP_FILE}"

  cat > "${TEMP_FILE}" <<EOF
headVersion=TS-OFFLINE-TRUST-PACK-HEAD-V1
acceptedSequence=${SEQUENCE}
acceptedPackSha256=${PACK_SHA}
subjectSystemVersion=${SUBJECT_VERSION}
builderAuthorityHead=${BUILDER_HEAD}
EOF

  chmod 600 "${TEMP_FILE}"

  mv \
    -f \
    "${TEMP_FILE}" \
    "${HEAD_FILE}"

  echo "TRUST_PACK_HEAD_ATOMIC_RENAME=true"
}

verify_and_extract_metadata() {
  local EXPECTED_SEQUENCE="$1"
  local EXPECTED_PREDECESSOR="$2"
  local VERIFY_LOG="$3"
  local MANIFEST_DIR="$4"

  rm -f "${VERIFY_LOG}"

  local VERIFY_STATUS=0

  if bash \
      "${PACK_VERIFIER}" \
      "${PACK_FILE}" \
      "${PACK_PIN}" \
      "${EXPECTED_SEQUENCE}" \
      "${EXPECTED_PREDECESSOR}" \
      > "${VERIFY_LOG}" \
      2>&1
  then
    VERIFY_STATUS=0
  else
    VERIFY_STATUS=$?
  fi

  cat "${VERIFY_LOG}"

  echo "TRUST_PACK_HEAD_CANDIDATE_VERIFY_EXIT=${VERIFY_STATUS}"

  if [[ "${VERIFY_STATUS}" != "0" ]]; then
    echo "TRUST_PACK_HEAD_MUTATED=false"
    echo "TRUST_PACK_HEAD_RESULT=REJECTED"

    exit "${VERIFY_STATUS}"
  fi

  grep \
    -q \
    '^TRUST_PACK_VERIFICATION_RESULT=VALID$' \
    "${VERIFY_LOG}"

  extract_pack_manifest \
    "${MANIFEST_DIR}"
}

PACK_SHA="$(
  shasum -a 256 "${PACK_FILE}" \
    | awk '{print $1}'
)"

PIN_SHA="$(
  tr -d '[:space:]' \
    < "${PACK_PIN}"
)"

PIN_SHA="$(
  printf '%s' "${PIN_SHA}" \
    | tr '[:upper:]' '[:lower:]'
)"

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

if [[ "${COMMAND}" == "init" ]]; then

  if [[ -e "${HEAD_FILE}" ]]; then
    echo "TRUST_PACK_HEAD_ALREADY_EXISTS=true"
    echo "TRUST_PACK_HEAD_MUTATED=false"
    echo "TRUST_PACK_HEAD_RESULT=REJECTED"

    exit 2
  fi

  verify_and_extract_metadata \
    "1" \
    "GENESIS" \
    "${VERIFY_LOG}" \
    "${MANIFEST_DIR}"

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

  atomic_write_head \
    "${PACK_SEQUENCE}" \
    "${PACK_SHA}" \
    "${SUBJECT_VERSION}" \
    "${BUILDER_HEAD}"

  echo "TRUST_PACK_HEAD_VERSION=TS-OFFLINE-TRUST-PACK-HEAD-V1"
  echo "TRUST_PACK_HEAD_ACCEPTED_SEQUENCE=${PACK_SEQUENCE}"
  echo "TRUST_PACK_HEAD_ACCEPTED_PACK_SHA256=${PACK_SHA}"
  echo "TRUST_PACK_HEAD_SUBJECT_SYSTEM_VERSION=${SUBJECT_VERSION}"
  echo "TRUST_PACK_HEAD_BUILDER_AUTHORITY_HEAD=${BUILDER_HEAD}"

  echo "TRUST_PACK_HEAD_MUTATED=true"
  echo "TRUST_PACK_HEAD_RESULT=INITIALIZED"

  exit 0
fi

[[ -s "${HEAD_FILE}" ]]

HEAD_VERSION="$(
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

[[ "${HEAD_VERSION}" == "TS-OFFLINE-TRUST-PACK-HEAD-V1" ]]

[[ "${ACCEPTED_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]

[[ "${ACCEPTED_PACK_SHA}" =~ ^[0-9a-f]{64}$ ]]

[[ "${ACCEPTED_SUBJECT_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]

[[ "${ACCEPTED_BUILDER_HEAD}" =~ ^[0-9a-f]{40}$ ]]

EXPECTED_SEQUENCE="$(
  python3 \
    - "${ACCEPTED_SEQUENCE}" \
<<'PY'
import sys

value = int(
    sys.argv[1]
)

print(
    value + 1
)
PY
)"

EXPECTED_PREDECESSOR="${ACCEPTED_PACK_SHA}"

echo "TRUST_PACK_HEAD_VERSION=${HEAD_VERSION}"
echo "TRUST_PACK_HEAD_CURRENT_SEQUENCE=${ACCEPTED_SEQUENCE}"
echo "TRUST_PACK_HEAD_CURRENT_PACK_SHA256=${ACCEPTED_PACK_SHA}"
echo "TRUST_PACK_HEAD_EXPECTED_NEXT_SEQUENCE=${EXPECTED_SEQUENCE}"
echo "TRUST_PACK_HEAD_EXPECTED_PREDECESSOR_SHA256=${EXPECTED_PREDECESSOR}"

verify_and_extract_metadata \
  "${EXPECTED_SEQUENCE}" \
  "${EXPECTED_PREDECESSOR}" \
  "${VERIFY_LOG}" \
  "${MANIFEST_DIR}"

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

atomic_write_head \
  "${PACK_SEQUENCE}" \
  "${PACK_SHA}" \
  "${SUBJECT_VERSION}" \
  "${BUILDER_HEAD}"

echo "TRUST_PACK_HEAD_ACCEPTED_SEQUENCE=${PACK_SEQUENCE}"
echo "TRUST_PACK_HEAD_ACCEPTED_PACK_SHA256=${PACK_SHA}"
echo "TRUST_PACK_HEAD_SUBJECT_SYSTEM_VERSION=${SUBJECT_VERSION}"
echo "TRUST_PACK_HEAD_BUILDER_AUTHORITY_HEAD=${BUILDER_HEAD}"

echo "TRUST_PACK_HEAD_MUTATED=true"
echo "TRUST_PACK_HEAD_RESULT=ADVANCED"
