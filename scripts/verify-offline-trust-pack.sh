#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 4 ]]; then
  echo "Usage: $0 <trust-pack.zip> <trusted-pack-sha256-file> <expected-sequence> <expected-predecessor-sha256|GENESIS>" >&2

  exit 64
fi

PACK_FILE="$1"

PACK_PIN="$2"

EXPECTED_SEQUENCE="$3"

EXPECTED_PREDECESSOR="$4"

for TOOL in \
  bash \
  java \
  jar \
  shasum \
  sed \
  grep \
  sort \
  diff
do
  command -v "${TOOL}" >/dev/null
done

[[ -s "${PACK_FILE}" ]]
[[ -s "${PACK_PIN}" ]]

[[ "${EXPECTED_SEQUENCE}" =~ ^[1-9][0-9]*$ ]]

if [[ "${EXPECTED_SEQUENCE}" == "1" ]]; then
  [[ "${EXPECTED_PREDECESSOR}" == "GENESIS" ]]
else
  [[ "${EXPECTED_PREDECESSOR}" =~ ^[0-9a-fA-F]{64}$ ]]

  EXPECTED_PREDECESSOR="$(
    printf '%s' "${EXPECTED_PREDECESSOR}" \
      | tr '[:upper:]' '[:lower:]'
  )"
fi

EXPECTED_PACK_SHA="$(
  tr -d '[:space:]' \
    < "${PACK_PIN}"
)"

[[ "${EXPECTED_PACK_SHA}" =~ ^[0-9a-fA-F]{64}$ ]]

EXPECTED_PACK_SHA="$(
  printf '%s' "${EXPECTED_PACK_SHA}" \
    | tr '[:upper:]' '[:lower:]'
)"

ACTUAL_PACK_SHA="$(
  shasum -a 256 "${PACK_FILE}" \
    | awk '{print $1}'
)"

echo "TRUST_PACK_EXPECTED_SHA256=${EXPECTED_PACK_SHA}"
echo "TRUST_PACK_ACTUAL_SHA256=${ACTUAL_PACK_SHA}"

if [[ "${EXPECTED_PACK_SHA}" != "${ACTUAL_PACK_SHA}" ]]; then
  echo "TRUST_PACK_OUTER_HASH_VALID=false"
  echo "TRUST_PACK_STRUCTURE_VALID=false"
  echo "TRUST_PACK_EXTRACTED=false"
  echo "TRUST_PACK_CONTINUITY_VALID=false"
  echo "TRUST_PACK_INNER_EXECUTED=false"
  echo "TRUST_PACK_VERIFICATION_RESULT=INVALID"

  exit 2
fi

echo "TRUST_PACK_OUTER_HASH_VALID=true"

EXPECTED_LIST="$(
  cat <<'EOF'
capsule-verifier.jar
pack.manifest
release.capsule.zip
root.sha256
run.sh
state-head.sha256
EOF
)"

ACTUAL_LIST="$(
  jar tf "${PACK_FILE}" \
    | sed '/^[[:space:]]*$/d' \
    | sort
)"

if [[ "${ACTUAL_LIST}" != "${EXPECTED_LIST}" ]]; then
  echo "TRUST_PACK_STRUCTURE_VALID=false"
  echo "TRUST_PACK_EXTRACTED=false"
  echo "TRUST_PACK_CONTINUITY_VALID=false"
  echo "TRUST_PACK_INNER_EXECUTED=false"
  echo "TRUST_PACK_VERIFICATION_RESULT=INVALID"

  exit 2
fi

echo "TRUST_PACK_STRUCTURE_VALID=true"

WORK_DIR="$(
  mktemp -d \
    "${TMPDIR:-/tmp}/terrascope-trust-pack-verify.XXXXXX"
)"

cleanup() {
  rm -rf "${WORK_DIR}"
}

trap cleanup EXIT

PACK_ABS="$(
  cd "$(dirname "${PACK_FILE}")"
  pwd
)/$(basename "${PACK_FILE}")"

(
  cd "${WORK_DIR}"

  jar xf "${PACK_ABS}"
)

echo "TRUST_PACK_EXTRACTED=true"

manifest_value() {
  local KEY="$1"

  local COUNT
  local VALUE

  COUNT="$(
    grep \
      -c \
      "^${KEY}=" \
      "${WORK_DIR}/pack.manifest" \
      || true
  )"

  [[ "${COUNT}" == "1" ]] || return 1

  VALUE="$(
    sed -n \
      "s/^${KEY}=//p" \
      "${WORK_DIR}/pack.manifest"
  )"

  [[ -n "${VALUE}" ]] || return 1

  printf '%s' "${VALUE}"
}

PACK_VERSION="$(
  manifest_value packVersion
)"

PACK_SEQUENCE="$(
  manifest_value packSequence
)"

PACK_PREDECESSOR="$(
  manifest_value predecessorPackSha256
)"

SUBJECT_VERSION="$(
  manifest_value subjectSystemVersion
)"

BUILDER_HEAD="$(
  manifest_value builderAuthorityHead
)"

echo "TRUST_PACK_VERSION=${PACK_VERSION}"
echo "TRUST_PACK_SEQUENCE=${PACK_SEQUENCE}"
echo "TRUST_PACK_SUBJECT_SYSTEM_VERSION=${SUBJECT_VERSION}"
echo "TRUST_PACK_BUILDER_AUTHORITY_HEAD=${BUILDER_HEAD}"
echo "TRUST_PACK_PREDECESSOR_SHA256=${PACK_PREDECESSOR}"

CONTINUITY_VALID=true

if [[ "${PACK_VERSION}" != "TS-OFFLINE-TRUST-PACK-V2" ]]; then
  CONTINUITY_VALID=false
fi

if [[ "${PACK_SEQUENCE}" != "${EXPECTED_SEQUENCE}" ]]; then
  CONTINUITY_VALID=false
fi

if [[ "${PACK_PREDECESSOR}" != "${EXPECTED_PREDECESSOR}" ]]; then
  CONTINUITY_VALID=false
fi

if [[ ! "${SUBJECT_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  CONTINUITY_VALID=false
fi

if [[ ! "${BUILDER_HEAD}" =~ ^[0-9a-f]{40}$ ]]; then
  CONTINUITY_VALID=false
fi

if [[ "${CONTINUITY_VALID}" != "true" ]]; then
  echo "TRUST_PACK_CONTINUITY_VALID=false"
  echo "TRUST_PACK_INNER_EXECUTED=false"
  echo "TRUST_PACK_VERIFICATION_RESULT=INVALID"

  exit 2
fi

echo "TRUST_PACK_CONTINUITY_VALID=true"

echo "TRUST_PACK_INNER_EXECUTED=true"

(
  cd "${WORK_DIR}"

  bash run.sh
)

echo "TRUST_PACK_VERIFICATION_RESULT=VALID"
