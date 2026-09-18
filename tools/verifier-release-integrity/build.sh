#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  pwd
)"

OUTPUT_DIR="${1:-${ROOT_DIR}/dist}"

SIGNER_SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeVerifierReleaseSigner.java"

VERIFIER_SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeVerifierReleaseVerifier.java"

BUILD_DIR="$(
  mktemp -d \
    "${TMPDIR:-/tmp}/terrascope-verifier-release-integrity.XXXXXX"
)"

cleanup() {
  rm -rf "${BUILD_DIR}"
}

trap cleanup EXIT

mkdir -p \
  "${OUTPUT_DIR}" \
  "${BUILD_DIR}/signer" \
  "${BUILD_DIR}/verifier"

javac \
  --release 21 \
  -encoding UTF-8 \
  -g:none \
  -d "${BUILD_DIR}/signer" \
  "${SIGNER_SOURCE}"

javac \
  --release 21 \
  -encoding UTF-8 \
  -g:none \
  -d "${BUILD_DIR}/verifier" \
  "${VERIFIER_SOURCE}"

cat > "${BUILD_DIR}/SIGNER.MF" <<'MANIFEST'
Manifest-Version: 1.0
Main-Class: com.terrascope.tools.TerraScopeVerifierReleaseSigner
Implementation-Version: TS-VERIFIER-RELEASE-INTEGRITY-V1

MANIFEST

cat > "${BUILD_DIR}/VERIFIER.MF" <<'MANIFEST'
Manifest-Version: 1.0
Main-Class: com.terrascope.tools.TerraScopeVerifierReleaseVerifier
Implementation-Version: TS-VERIFIER-RELEASE-INTEGRITY-V1

MANIFEST

SIGNER_JAR="${OUTPUT_DIR}/terrascope-verifier-release-signer.jar"

VERIFIER_JAR="${OUTPUT_DIR}/terrascope-verifier-release-verifier.jar"

jar \
  --create \
  --file "${SIGNER_JAR}" \
  --manifest "${BUILD_DIR}/SIGNER.MF" \
  --date=2000-01-01T00:00:00Z \
  -C "${BUILD_DIR}/signer" \
  .

jar \
  --create \
  --file "${VERIFIER_JAR}" \
  --manifest "${BUILD_DIR}/VERIFIER.MF" \
  --date=2000-01-01T00:00:00Z \
  -C "${BUILD_DIR}/verifier" \
  .

[[ -s "${SIGNER_JAR}" ]]
[[ -s "${VERIFIER_JAR}" ]]

echo "RELEASE_SIGNER_JAR=${SIGNER_JAR}"
echo "RELEASE_VERIFIER_JAR=${VERIFIER_JAR}"
echo "[PASS] VERIFIER_RELEASE_INTEGRITY_BUILD"
