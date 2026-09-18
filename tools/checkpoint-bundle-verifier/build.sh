#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  pwd
)"

SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeCheckpointBundleVerifier.java"

OUTPUT="${1:-${ROOT_DIR}/terrascope-checkpoint-bundle-verifier.jar}"

BUILD_DIR="$(
  mktemp -d \
    "${TMPDIR:-/tmp}/terrascope-checkpoint-bundle-verifier.XXXXXX"
)"

cleanup() {
  rm -rf "${BUILD_DIR}"
}

trap cleanup EXIT

mkdir -p \
  "${BUILD_DIR}/classes"

javac \
  --release 21 \
  -encoding UTF-8 \
  -d "${BUILD_DIR}/classes" \
  "${SOURCE}"

jar \
  --create \
  --file "${OUTPUT}" \
  --main-class com.terrascope.tools.TerraScopeCheckpointBundleVerifier \
  -C "${BUILD_DIR}/classes" \
  .

[[ -s "${OUTPUT}" ]]

echo "CHECKPOINT_BUNDLE_VERIFIER_JAR=${OUTPUT}"
echo "[PASS] CHECKPOINT_BUNDLE_VERIFIER_BUILD"
