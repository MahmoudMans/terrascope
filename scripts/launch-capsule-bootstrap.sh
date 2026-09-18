#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <bootstrap-script> <bootstrap-sha256-pin>" >&2

  exit 64
fi

BOOTSTRAP_SCRIPT="$1"

BOOTSTRAP_PIN="$2"

if [[ ! -f "${BOOTSTRAP_SCRIPT}" ]]; then
  echo "BOOTSTRAP_SCRIPT_PRESENT=false"
  echo "BOOTSTRAP_SCRIPT_EXECUTED=false"
  echo "LAUNCHER_RESULT=INVALID"

  exit 2
fi

if [[ ! -f "${BOOTSTRAP_PIN}" ]]; then
  echo "BOOTSTRAP_PIN_PRESENT=false"
  echo "BOOTSTRAP_SCRIPT_EXECUTED=false"
  echo "LAUNCHER_RESULT=INVALID"

  exit 2
fi

EXPECTED="$(
  tr -d '[:space:]' \
    < "${BOOTSTRAP_PIN}"
)"

if [[ ! "${EXPECTED}" =~ ^[0-9a-fA-F]{64}$ ]]; then
  echo "LAUNCHER_BOOTSTRAP_PIN_FORMAT_VALID=false"
  echo "BOOTSTRAP_SCRIPT_EXECUTED=false"
  echo "LAUNCHER_RESULT=INVALID"

  exit 2
fi

EXPECTED="$(
  printf '%s' \
    "${EXPECTED}" \
    | tr '[:upper:]' '[:lower:]'
)"

ACTUAL="$(
  shasum -a 256 \
    "${BOOTSTRAP_SCRIPT}" \
    | awk '{print $1}'
)"

echo "LAUNCHER_BOOTSTRAP_EXPECTED_SHA256=${EXPECTED}"

echo "LAUNCHER_BOOTSTRAP_ACTUAL_SHA256=${ACTUAL}"

if [[ "${EXPECTED}" != "${ACTUAL}" ]]; then
  echo "LAUNCHER_BOOTSTRAP_PIN_VALID=false"

  echo "BOOTSTRAP_SCRIPT_EXECUTED=false"

  echo "LAUNCHER_RESULT=INVALID"

  exit 2
fi

echo "LAUNCHER_BOOTSTRAP_PIN_VALID=true"

echo "BOOTSTRAP_SCRIPT_EXECUTED=true"

BOOTSTRAP_EXIT=0

if bash "${BOOTSTRAP_SCRIPT}"
then
  BOOTSTRAP_EXIT=0
else
  BOOTSTRAP_EXIT=$?
fi

echo "BOOTSTRAP_SCRIPT_EXIT=${BOOTSTRAP_EXIT}"

if [[ "${BOOTSTRAP_EXIT}" != "0" ]]; then
  echo "LAUNCHER_RESULT=INVALID"

  exit "${BOOTSTRAP_EXIT}"
fi

echo "LAUNCHER_RESULT=VALID"
