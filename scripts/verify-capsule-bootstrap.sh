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

for TOOL in \
  java \
  shasum \
  curl
do
  command -v \
    "${TOOL}" \
    >/dev/null
done

CURRENT_CAPSULE="/tmp/terrascope-step50-current-a.capsule.zip"

STALE_CAPSULE="/tmp/terrascope-step50-stale.capsule.zip"

STEP50_VERIFIER="/tmp/terrascope-step50-capsule-verifier.jar"

ROOT_PIN="/tmp/terrascope-step50-root.sha256"

CURRENT_STATE_HEAD="/tmp/terrascope-step50-current-state.sha256"

OLD_STATE_HEAD="/tmp/terrascope-step50-old-state.sha256"

BUILD_A="/tmp/terrascope-step51-bootstrap-build-a"

BUILD_B="/tmp/terrascope-step51-bootstrap-build-b"

BOOTSTRAP_VERIFIER="/tmp/terrascope-step51-bootstrap-verifier.jar"

BOOTSTRAP_PIN="/tmp/terrascope-step51-bootstrap-verifier.sha256"

TAMPERED_VERIFIER="/tmp/terrascope-step51-tampered-verifier.jar"

WRONG_PIN="/tmp/terrascope-step51-wrong-verifier.sha256"

CURRENT_LOG="/tmp/terrascope-step51-current.log"

TAMPERED_LOG="/tmp/terrascope-step51-tampered-verifier.log"

WRONG_PIN_LOG="/tmp/terrascope-step51-wrong-verifier-pin.log"

STALE_CURRENT_LOG="/tmp/terrascope-step51-stale-current.log"

STALE_HISTORICAL_LOG="/tmp/terrascope-step51-stale-historical.log"

wait_postgres() {
  for attempt in $(seq 1 30); do

    if docker compose exec -T postgres \
        pg_isready \
          -U terrascope \
          -d terrascope \
        >/dev/null 2>&1
    then
      return 0
    fi

    [[ "${attempt}" -lt 30 ]]

    sleep 1
  done

  return 1
}

bootstrap_verify() {
  local verifier="$1"
  local verifier_pin="$2"
  local capsule="$3"
  local root_pin="$4"
  local state_head="$5"

  local expected
  local actual

  expected="$(
    tr -d '[:space:]' \
      < "${verifier_pin}"
  )"

  actual="$(
    shasum -a 256 \
      "${verifier}" \
      | awk '{print $1}'
  )"

  echo "BOOTSTRAP_VERIFIER_EXPECTED_SHA256=${expected}"

  echo "BOOTSTRAP_VERIFIER_ACTUAL_SHA256=${actual}"

  if [[ "${expected}" != "${actual}" ]]; then
    echo "BOOTSTRAP_VERIFIER_PIN_VALID=false"

    echo "CAPSULE_VERIFIER_EXECUTED=false"

    echo "BOOTSTRAP_VERIFICATION_RESULT=INVALID"

    return 2
  fi

  echo "BOOTSTRAP_VERIFIER_PIN_VALID=true"

  echo "CAPSULE_VERIFIER_EXECUTED=true"

  java \
    -jar "${verifier}" \
    verify \
    --capsule "${capsule}" \
    --root-pin "${root_pin}" \
    --state-head-pin "${state_head}"
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 51 BOOTSTRAP VERIFICATION"
echo "========================================================================"

echo
echo "===== STEP 50 PORTABLE EVIDENCE AUTHORITY ====="

for FILE in \
  "${CURRENT_CAPSULE}" \
  "${STALE_CAPSULE}" \
  "${STEP50_VERIFIER}" \
  "${ROOT_PIN}" \
  "${CURRENT_STATE_HEAD}" \
  "${OLD_STATE_HEAD}"
do
  [[ -s "${FILE}" ]]
done

echo "[PASS] STEP50_PORTABLE_EVIDENCE_AVAILABLE"

echo
echo "===== BUILD CAPSULE VERIFIER TWICE ====="

rm -rf \
  "${BUILD_A}" \
  "${BUILD_B}"

bash \
  tools/verifier-release-integrity/build.sh \
  "${BUILD_A}"

sleep 1

bash \
  tools/verifier-release-integrity/build.sh \
  "${BUILD_B}"

VERIFIER_A="${BUILD_A}/terrascope-verifier-release-capsule.jar"

VERIFIER_B="${BUILD_B}/terrascope-verifier-release-capsule.jar"

[[ -s "${VERIFIER_A}" ]]
[[ -s "${VERIFIER_B}" ]]

SHA_A="$(
  shasum -a 256 \
    "${VERIFIER_A}" \
    | awk '{print $1}'
)"

SHA_B="$(
  shasum -a 256 \
    "${VERIFIER_B}" \
    | awk '{print $1}'
)"

STEP50_SHA="$(
  shasum -a 256 \
    "${STEP50_VERIFIER}" \
    | awk '{print $1}'
)"

echo "BOOTSTRAP_BUILD_A_SHA256=${SHA_A}"

echo "BOOTSTRAP_BUILD_B_SHA256=${SHA_B}"

echo "STEP50_PRESERVED_VERIFIER_SHA256=${STEP50_SHA}"

[[ "${SHA_A}" == "${SHA_B}" ]]

[[ "${SHA_A}" == "${STEP50_SHA}" ]]

echo "[PASS] CAPSULE_VERIFIER_REPRODUCIBLE_BUILD"

echo "[PASS] STEP50_VERIFIER_MATCHES_REPRODUCIBLE_BUILD"

echo
echo "===== RETAIN INDEPENDENT VERIFIER PIN ====="

rm -f \
  "${BOOTSTRAP_VERIFIER}" \
  "${BOOTSTRAP_PIN}" \
  "${TAMPERED_VERIFIER}" \
  "${WRONG_PIN}"

cp \
  "${VERIFIER_A}" \
  "${BOOTSTRAP_VERIFIER}"

printf '%s\n' \
  "${SHA_A}" \
  > "${BOOTSTRAP_PIN}"

[[ -s "${BOOTSTRAP_VERIFIER}" ]]
[[ -s "${BOOTSTRAP_PIN}" ]]

echo "INDEPENDENT_CAPSULE_VERIFIER_SHA256=${SHA_A}"

echo "[PASS] CAPSULE_VERIFIER_PIN_RETAINED"

echo
echo "===== BUILD NEGATIVE BOOTSTRAP FIXTURES ====="

cp \
  "${BOOTSTRAP_VERIFIER}" \
  "${TAMPERED_VERIFIER}"

printf '\x00' \
  >> "${TAMPERED_VERIFIER}"

WRONG_SHA="ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

if [[ "${WRONG_SHA}" == "${SHA_A}" ]]; then
  WRONG_SHA="0000000000000000000000000000000000000000000000000000000000000000"
fi

printf '%s\n' \
  "${WRONG_SHA}" \
  > "${WRONG_PIN}"

TAMPERED_SHA="$(
  shasum -a 256 \
    "${TAMPERED_VERIFIER}" \
    | awk '{print $1}'
)"

echo "TAMPERED_VERIFIER_SHA256=${TAMPERED_SHA}"

[[ "${TAMPERED_SHA}" != "${SHA_A}" ]]

echo "[PASS] NEGATIVE_BOOTSTRAP_FIXTURES_READY"

echo
echo "===== DESTROY BUILD DIRECTORIES + ORIGINAL VERIFIER COPY ====="

rm -rf \
  "${BUILD_A}" \
  "${BUILD_B}"

rm -f \
  "${STEP50_VERIFIER}"

[[ ! -e "${BUILD_A}" ]]
[[ ! -e "${BUILD_B}" ]]
[[ ! -e "${STEP50_VERIFIER}" ]]

echo "[PASS] BUILD_SOURCES_REMOVED_BEFORE_BOOTSTRAP"

echo "[PASS] ORIGINAL_STEP50_VERIFIER_COPY_REMOVED"

echo
echo "===== STOP POSTGRES FOR TRUE OFFLINE BOOTSTRAP ====="

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
    >/dev/null 2>&1
then
  echo "[FAIL] TerraScope backend remains reachable."

  exit 1
fi

echo "[PASS] TERRASCOPE_BACKEND_OFFLINE"

echo "[PASS] POSTGRES_OFFLINE"

echo
echo "===== CURRENT CAPSULE THROUGH PINNED BOOTSTRAP ====="

rm -f \
  "${CURRENT_LOG}"

bootstrap_verify \
  "${BOOTSTRAP_VERIFIER}" \
  "${BOOTSTRAP_PIN}" \
  "${CURRENT_CAPSULE}" \
  "${ROOT_PIN}" \
  "${CURRENT_STATE_HEAD}" \
  | tee "${CURRENT_LOG}"

grep \
  -q \
  '^BOOTSTRAP_VERIFIER_PIN_VALID=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^CAPSULE_VERIFIER_EXECUTED=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^CAPSULE_STRUCTURE_VALID=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^CAPSULE_SIGNATURE_VALID=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^TRUST_STATE_VALID=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^AUTHORITY_LINEAGE_VALID=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^RELEASE_VALID=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^TRANSPARENCY_VALID=true$' \
  "${CURRENT_LOG}"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${CURRENT_LOG}"

echo "[PASS] PINNED_BOOTSTRAP_CURRENT_CAPSULE_VALID"

echo
echo "===== TAMPERED VERIFIER MUST NOT EXECUTE ====="

rm -f \
  "${TAMPERED_LOG}"

TAMPERED_STATUS=0

if bootstrap_verify \
    "${TAMPERED_VERIFIER}" \
    "${BOOTSTRAP_PIN}" \
    "${CURRENT_CAPSULE}" \
    "${ROOT_PIN}" \
    "${CURRENT_STATE_HEAD}" \
    > "${TAMPERED_LOG}" \
    2>&1
then
  TAMPERED_STATUS=0
else
  TAMPERED_STATUS=$?
fi

cat \
  "${TAMPERED_LOG}"

echo "TAMPERED_VERIFIER_EXIT=${TAMPERED_STATUS}"

[[ "${TAMPERED_STATUS}" == "2" ]]

grep \
  -q \
  '^BOOTSTRAP_VERIFIER_PIN_VALID=false$' \
  "${TAMPERED_LOG}"

grep \
  -q \
  '^CAPSULE_VERIFIER_EXECUTED=false$' \
  "${TAMPERED_LOG}"

grep \
  -q \
  '^BOOTSTRAP_VERIFICATION_RESULT=INVALID$' \
  "${TAMPERED_LOG}"

CAPSULE_TOOL_OUTPUT_COUNT="$(
  grep \
    -c \
    '^CAPSULE_TOOL_VERSION=' \
    "${TAMPERED_LOG}" \
    || true
)"

echo "TAMPERED_VERIFIER_CAPSULE_TOOL_OUTPUT_COUNT=${CAPSULE_TOOL_OUTPUT_COUNT}"

[[ "${CAPSULE_TOOL_OUTPUT_COUNT}" == "0" ]]

echo "[PASS] TAMPERED_CAPSULE_VERIFIER_REJECTED_PRE_EXECUTION"

echo
echo "===== WRONG VERIFIER PIN MUST NOT EXECUTE ====="

rm -f \
  "${WRONG_PIN_LOG}"

WRONG_PIN_STATUS=0

if bootstrap_verify \
    "${BOOTSTRAP_VERIFIER}" \
    "${WRONG_PIN}" \
    "${CURRENT_CAPSULE}" \
    "${ROOT_PIN}" \
    "${CURRENT_STATE_HEAD}" \
    > "${WRONG_PIN_LOG}" \
    2>&1
then
  WRONG_PIN_STATUS=0
else
  WRONG_PIN_STATUS=$?
fi

cat \
  "${WRONG_PIN_LOG}"

echo "WRONG_VERIFIER_PIN_EXIT=${WRONG_PIN_STATUS}"

[[ "${WRONG_PIN_STATUS}" == "2" ]]

grep \
  -q \
  '^BOOTSTRAP_VERIFIER_PIN_VALID=false$' \
  "${WRONG_PIN_LOG}"

grep \
  -q \
  '^CAPSULE_VERIFIER_EXECUTED=false$' \
  "${WRONG_PIN_LOG}"

CAPSULE_TOOL_OUTPUT_COUNT="$(
  grep \
    -c \
    '^CAPSULE_TOOL_VERSION=' \
    "${WRONG_PIN_LOG}" \
    || true
)"

echo "WRONG_PIN_CAPSULE_TOOL_OUTPUT_COUNT=${CAPSULE_TOOL_OUTPUT_COUNT}"

[[ "${CAPSULE_TOOL_OUTPUT_COUNT}" == "0" ]]

echo "[PASS] WRONG_CAPSULE_VERIFIER_PIN_REJECTED_PRE_EXECUTION"

echo
echo "===== HISTORICAL CAPSULE WITH HISTORICAL STATE ====="

rm -f \
  "${STALE_HISTORICAL_LOG}"

bootstrap_verify \
  "${BOOTSTRAP_VERIFIER}" \
  "${BOOTSTRAP_PIN}" \
  "${STALE_CAPSULE}" \
  "${ROOT_PIN}" \
  "${OLD_STATE_HEAD}" \
  | tee "${STALE_HISTORICAL_LOG}"

grep \
  -q \
  '^BOOTSTRAP_VERIFIER_PIN_VALID=true$' \
  "${STALE_HISTORICAL_LOG}"

grep \
  -q \
  '^CAPSULE_VERIFIER_EXECUTED=true$' \
  "${STALE_HISTORICAL_LOG}"

grep \
  -q \
  '^VERIFICATION_RESULT=VALID$' \
  "${STALE_HISTORICAL_LOG}"

echo "[PASS] PINNED_BOOTSTRAP_HISTORICAL_CAPSULE_AUTHENTIC"

echo
echo "===== STALE CAPSULE AGAINST CURRENT STATE ====="

rm -f \
  "${STALE_CURRENT_LOG}"

STALE_CURRENT_STATUS=0

if bootstrap_verify \
    "${BOOTSTRAP_VERIFIER}" \
    "${BOOTSTRAP_PIN}" \
    "${STALE_CAPSULE}" \
    "${ROOT_PIN}" \
    "${CURRENT_STATE_HEAD}" \
    > "${STALE_CURRENT_LOG}" \
    2>&1
then
  STALE_CURRENT_STATUS=0
else
  STALE_CURRENT_STATUS=$?
fi

cat \
  "${STALE_CURRENT_LOG}"

echo "STALE_CURRENT_EXIT=${STALE_CURRENT_STATUS}"

[[ "${STALE_CURRENT_STATUS}" == "2" ]]

grep \
  -q \
  '^BOOTSTRAP_VERIFIER_PIN_VALID=true$' \
  "${STALE_CURRENT_LOG}"

grep \
  -q \
  '^CAPSULE_VERIFIER_EXECUTED=true$' \
  "${STALE_CURRENT_LOG}"

grep \
  -q \
  '^TRUST_STATE_VALID=false$' \
  "${STALE_CURRENT_LOG}"

grep \
  -q \
  '^VERIFICATION_RESULT=INVALID$' \
  "${STALE_CURRENT_LOG}"

echo "[PASS] PINNED_BOOTSTRAP_PRESERVES_STALE_CAPSULE_REJECTION"

echo
echo "===== TRUE AIR-GAP BOOTSTRAP AUTHORITY ====="

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
    >/dev/null 2>&1
then
  echo "[FAIL] Backend became reachable during bootstrap tests."

  exit 1
fi

[[ ! -e "${BUILD_A}" ]]
[[ ! -e "${BUILD_B}" ]]
[[ ! -e "${STEP50_VERIFIER}" ]]

echo "[PASS] TRUE_OFFLINE_PINNED_CAPSULE_VERIFIER_BOOTSTRAP"

echo "[PASS] BUILD_DIRECTORIES_REMAINED_ABSENT"

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
echo " TERRASCOPE — STEP 51 BOOTSTRAP VERIFICATION PASSED"
echo "========================================================================"
