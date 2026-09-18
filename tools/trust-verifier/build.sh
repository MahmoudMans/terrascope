#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  pwd
)"

OUTPUT="${1:-/tmp/terrascope-trust-verifier.jar}"

BUILD_DIR="/tmp/terrascope-trust-verifier-build"

SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeTrustVerifier.java"

rm -rf "${BUILD_DIR}"

mkdir -p "${BUILD_DIR}/classes"

javac \
  --release 21 \
  -d "${BUILD_DIR}/classes" \
  "${SOURCE}"

jar \
  --create \
  --file "${OUTPUT}" \
  --main-class com.terrascope.tools.TerraScopeTrustVerifier \
  -C "${BUILD_DIR}/classes" \
  .

test -s "${OUTPUT}"

echo "STANDALONE_VERIFIER_JAR=${OUTPUT}"
echo "[PASS] STANDALONE_VERIFIER_BUILD"
