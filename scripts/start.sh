#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

SIGN_IN="false"
DEMO="false"

usage() {
  echo "Usage: scripts/start.sh [--sign-in] [--demo]"
  echo
  echo "  --sign-in   turn on sign-in and create the first administrator"
  echo "  --demo      fill the app with a small demo dataset once it is running"
  echo "              (needs Node.js 18 or newer on this computer)"
}

for argument in "$@"; do
  case "${argument}" in
    --sign-in)
      SIGN_IN="true"
      ;;
    --demo)
      DEMO="true"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: ${argument}" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed. Install Docker Desktop and run this again." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but not running. Start Docker Desktop and run this again." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (the 'docker compose' command)." >&2
  exit 1
fi

if [[ "${DEMO}" == "true" ]]; then
  if ! command -v node >/dev/null 2>&1 || ! node -e 'process.exit(Number(process.versions.node.split(".")[0]) >= 18 ? 0 : 1)' >/dev/null 2>&1; then
    echo "--demo needs Node.js 18 or newer on this computer to load the demo data." >&2
    echo "Install it, or start without --demo and run: node scripts/seed-demo.mjs" >&2
    exit 1
  fi
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from .env.example."
fi

value_of() {
  local found

  found="$(grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-)" || true

  if [[ -n "${found}" ]]; then
    printf '%s' "${found}"
  else
    printf '%s' "$2"
  fi
}

ADMIN_USERNAME=""
ADMIN_PASSWORD=""

if [[ "${SIGN_IN}" == "true" ]]; then
  if [[ "$(value_of TERRASCOPE_SECURITY_ENABLED false)" == "true" ]]; then
    echo "Sign-in is already turned on in .env."
  else
    if ! command -v openssl >/dev/null 2>&1; then
      echo "openssl is needed to generate the secret and the first password." >&2
      exit 1
    fi

    TOKEN_SECRET="$(openssl rand -hex 32)"
    ADMIN_PASSWORD="$(openssl rand -hex 12)"
    ADMIN_USERNAME="$(value_of TERRASCOPE_SECURITY_ADMIN_USERNAME admin)"

    grep -v -E '^(TERRASCOPE_SECURITY_ENABLED|TERRASCOPE_SECURITY_TOKEN_SECRET|TERRASCOPE_SECURITY_ADMIN_USERNAME|TERRASCOPE_SECURITY_ADMIN_PASSWORD)=' .env > .env.next || true

    {
      cat .env.next
      printf 'TERRASCOPE_SECURITY_ENABLED=true\n'
      printf 'TERRASCOPE_SECURITY_TOKEN_SECRET=%s\n' "${TOKEN_SECRET}"
      printf 'TERRASCOPE_SECURITY_ADMIN_USERNAME=%s\n' "${ADMIN_USERNAME}"
      printf 'TERRASCOPE_SECURITY_ADMIN_PASSWORD=%s\n' "${ADMIN_PASSWORD}"
    } > .env

    rm -f .env.next
  fi
fi

echo
echo "Building and starting TerraScope. The first build takes a few minutes."
echo

if ! docker compose up -d --build --wait; then
  echo >&2
  echo "Something did not start. Recent logs:" >&2
  docker compose logs --tail 40 backend web >&2 || true
  exit 1
fi

DEMO_STATE=""

if [[ "${DEMO}" == "true" ]]; then
  echo
  echo "Loading demo data."

  if node scripts/seed-demo.mjs; then
    DEMO_STATE="loaded"
  else
    DEMO_STATE="partial"
  fi
fi

echo
echo "========================================================================"
echo " TERRASCOPE IS RUNNING"
echo "========================================================================"
echo
echo "Open:         http://localhost:$(value_of WEB_PORT 3000)"
echo "Backend API:  http://localhost:$(value_of BACKEND_PORT 8087)/api/v1/status"

if [[ -n "${ADMIN_PASSWORD}" ]]; then
  echo
  echo "Sign-in is on. First administrator:"
  echo "  username: ${ADMIN_USERNAME}"
  echo "  password: ${ADMIN_PASSWORD}"
  echo
  echo "These are also saved in .env."
elif [[ "$(value_of TERRASCOPE_SECURITY_ENABLED false)" == "true" ]]; then
  echo
  echo "Sign-in is on. Use the administrator account from .env."
fi

if [[ "${DEMO_STATE}" == "loaded" ]]; then
  echo
  echo "Demo data is loaded."
elif [[ "${DEMO_STATE}" == "partial" ]]; then
  echo
  echo "Demo data was not fully loaded. Run again to fill the gaps:"
  echo "  node scripts/seed-demo.mjs"
fi

echo
echo "Stop:         docker compose down"
echo "Logs:         docker compose logs -f"
echo
