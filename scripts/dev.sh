#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

echo "========================================================================"
echo " TERRASCOPE — LOCAL DEVELOPMENT"
echo "========================================================================"
echo

docker compose up -d postgres

echo
echo "PostgreSQL is starting on localhost:55433."
echo
echo "Run the backend in terminal 1:"
echo
echo "  cd ${ROOT_DIR}/apps/backend"
echo "  mvn spring-boot:run"
echo
echo "Run the frontend in terminal 2:"
echo
echo "  cd ${ROOT_DIR}/apps/web"
echo "  npm run dev"
echo
echo "Then open:"
echo
echo "  http://localhost:3000"
echo
echo "Backend API:"
echo
echo "  http://localhost:8087/api/v1/status"
echo
