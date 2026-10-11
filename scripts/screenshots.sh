#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

TOOL_DIR="${ROOT_DIR}/tools/screenshots"

for argument in "$@"; do
  if [[ "${argument}" == "-h" || "${argument}" == "--help" || "${argument}" == "--list" ]]; then
    exec node "${TOOL_DIR}/capture.mjs" "$@"
  fi
done

if ! command -v node >/dev/null 2>&1 || ! node -e 'process.exit(Number(process.versions.node.split(".")[0]) >= 18 ? 0 : 1)' >/dev/null 2>&1; then
  echo "Node.js 18 or newer is needed to take the screenshots." >&2
  exit 1
fi

cd "${TOOL_DIR}"

if [[ ! -d node_modules/playwright ]]; then
  echo "Installing the screenshot tool (Playwright, one time)."
  npm install --no-audit --no-fund
fi

USES_OWN_BROWSER="false"

for argument in "$@"; do
  if [[ "${argument}" == "--channel" || "${argument}" == "--executable" ]]; then
    USES_OWN_BROWSER="true"
  fi
done

if [[ "${USES_OWN_BROWSER}" == "false" ]]; then
  npx playwright install chromium
fi

cd "${ROOT_DIR}"

exec node "${TOOL_DIR}/capture.mjs" "$@"
