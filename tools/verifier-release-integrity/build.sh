#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  pwd
)"

OUTPUT_DIR="${1:-${ROOT_DIR}/dist}"

SIGNER_SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeVerifierReleaseSigner.java"

VERIFIER_SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeVerifierReleaseVerifier.java"

TRANSPARENCY_SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeVerifierReleaseTransparency.java"

AUTHORITY_SOURCE="${ROOT_DIR}/src/com/terrascope/tools/TerraScopeVerifierReleaseAuthorityLineage.java"

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
  "${BUILD_DIR}/verifier" \
  "${BUILD_DIR}/transparency" \
  "${BUILD_DIR}/authority"

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

javac \
  --release 21 \
  -encoding UTF-8 \
  -g:none \
  -d "${BUILD_DIR}/transparency" \
  "${TRANSPARENCY_SOURCE}"

javac \
  --release 21 \
  -encoding UTF-8 \
  -g:none \
  -d "${BUILD_DIR}/authority" \
  "${AUTHORITY_SOURCE}"

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

cat > "${BUILD_DIR}/TRANSPARENCY.MF" <<'MANIFEST'
Manifest-Version: 1.0
Main-Class: com.terrascope.tools.TerraScopeVerifierReleaseTransparency
Implementation-Version: TS-VERIFIER-RELEASE-TRANSPARENCY-TOOL-V1

MANIFEST

cat > "${BUILD_DIR}/AUTHORITY.MF" <<'MANIFEST'
Manifest-Version: 1.0
Main-Class: com.terrascope.tools.TerraScopeVerifierReleaseAuthorityLineage
Implementation-Version: TS-VERIFIER-RELEASE-AUTHORITY-LINEAGE-TOOL-V1

MANIFEST

SIGNER_JAR="${OUTPUT_DIR}/terrascope-verifier-release-signer.jar"

VERIFIER_JAR="${OUTPUT_DIR}/terrascope-verifier-release-verifier.jar"

TRANSPARENCY_JAR="${OUTPUT_DIR}/terrascope-verifier-release-transparency.jar"

AUTHORITY_JAR="${OUTPUT_DIR}/terrascope-verifier-release-authority-lineage.jar"

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

jar \
  --create \
  --file "${TRANSPARENCY_JAR}" \
  --manifest "${BUILD_DIR}/TRANSPARENCY.MF" \
  --date=2000-01-01T00:00:00Z \
  -C "${BUILD_DIR}/transparency" \
  .

jar \
  --create \
  --file "${AUTHORITY_JAR}" \
  --manifest "${BUILD_DIR}/AUTHORITY.MF" \
  --date=2000-01-01T00:00:00Z \
  -C "${BUILD_DIR}/authority" \
  .

[[ -s "${SIGNER_JAR}" ]]
[[ -s "${VERIFIER_JAR}" ]]
[[ -s "${TRANSPARENCY_JAR}" ]]
[[ -s "${AUTHORITY_JAR}" ]]

echo "RELEASE_SIGNER_JAR=${SIGNER_JAR}"
echo "RELEASE_VERIFIER_JAR=${VERIFIER_JAR}"
echo "RELEASE_TRANSPARENCY_JAR=${TRANSPARENCY_JAR}"
echo "RELEASE_AUTHORITY_LINEAGE_JAR=${AUTHORITY_JAR}"

echo "[PASS] VERIFIER_RELEASE_INTEGRITY_BUILD"
