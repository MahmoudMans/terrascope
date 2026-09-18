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
  -g:none \
  -d "${BUILD_DIR}/classes" \
  "${SOURCE}"

cat > "${BUILD_DIR}/MANIFEST.MF" <<'MANIFEST'
Manifest-Version: 1.0
Main-Class: com.terrascope.tools.TerraScopeCheckpointBundleVerifier
Implementation-Version: TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1

MANIFEST

jar \
  --create \
  --file "${OUTPUT}" \
  --manifest "${BUILD_DIR}/MANIFEST.MF" \
  --date=2000-01-01T00:00:00Z \
  -C "${BUILD_DIR}/classes" \
  .

[[ -s "${OUTPUT}" ]]

echo "CHECKPOINT_BUNDLE_VERIFIER_JAR=${OUTPUT}"
echo "[PASS] CHECKPOINT_BUNDLE_VERIFIER_DETERMINISTIC_BUILD"
