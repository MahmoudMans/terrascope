#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step5-backend.log"

cleanup() {
  if [ -n "${BACKEND_PID}" ]; then

    kill \
      "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true

    wait \
      "${BACKEND_PID}" \
      >/dev/null 2>&1 \
      || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 5 VERIFICATION"
echo "========================================================================"

echo
echo "===== COMPOSE ====="

docker compose config >/dev/null

echo "[PASS] COMPOSE_CONFIGURATION"

echo
echo "===== POSTGRESQL ====="

docker compose up -d postgres

for attempt in $(seq 1 30); do

  if docker compose exec -T postgres \
      pg_isready \
      -U terrascope \
      -d terrascope \
      >/dev/null 2>&1; then

    echo "[PASS] POSTGRESQL_READY"
    break
  fi

  if [ "${attempt}" -eq 30 ]; then
    echo "[FAIL] POSTGRESQL_NOT_READY"
    exit 1
  fi

  sleep 1
done

echo
echo "===== BACKEND TESTS ====="

(
  cd apps/backend
  mvn test
)

echo "[PASS] BACKEND_TESTS"

echo
echo "===== BACKEND PACKAGE ====="

(
  cd apps/backend
  mvn -DskipTests package
)

echo "[PASS] BACKEND_PACKAGE"

echo
echo "===== FRONTEND LINT ====="

(
  cd apps/web

  npx eslint . \
    --max-warnings=0
)

echo "[PASS] FRONTEND_LINT"

echo
echo "===== FRONTEND TYPECHECK ====="

(
  cd apps/web
  npm run typecheck
)

echo "[PASS] FRONTEND_TYPECHECK"

echo
echo "===== FRONTEND BUILD ====="

(
  cd apps/web
  npm run build
)

echo "[PASS] FRONTEND_BUILD"

echo
echo "===== BACKEND RUNTIME ====="

rm -f "${BACKEND_LOG}"

(
  cd apps/backend
  mvn spring-boot:run
) > "${BACKEND_LOG}" 2>&1 &

BACKEND_PID=$!

for attempt in $(seq 1 45); do

  if curl \
      --fail \
      --silent \
      "http://localhost:8087/actuator/health" \
      >/tmp/terrascope-step5-health.json \
      2>/dev/null; then

    echo "[PASS] BACKEND_RUNTIME_READY"
    break
  fi

  if ! kill -0 \
      "${BACKEND_PID}" \
      >/dev/null 2>&1; then

    echo "[FAIL] BACKEND_STOPPED"

    cat "${BACKEND_LOG}"

    exit 1
  fi

  if [ "${attempt}" -eq 45 ]; then

    echo "[FAIL] BACKEND_TIMEOUT"

    cat "${BACKEND_LOG}"

    exit 1
  fi

  sleep 1
done

echo
echo "===== STATUS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step5-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step5-status.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["status"] == "operational"
assert payload["version"] == "0.5.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

echo
echo "===== STEP 4 REGRESSION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/hazards/layers" \
  >/tmp/terrascope-step5-layers.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/watchlist" \
  >/tmp/terrascope-step5-watchlist.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step5-layers.json",
    "r",
    encoding="utf-8",
) as handle:
    layers = json.load(handle)

with open(
    "/tmp/terrascope-step5-watchlist.json",
    "r",
    encoding="utf-8",
) as handle:
    watchlist = json.load(handle)

assert len(layers) == 4

assert all(
    layer["available"]
    for layer in layers
)

assert "summary" in watchlist

assert isinstance(
    watchlist["entries"],
    list,
)

print(
    "[PASS] STEP4_RUNTIME_REGRESSION"
)
PY

echo
echo "===== FLYWAY V5 ====="

for VERSION in 1 2 3 4 5; do

  COUNT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version = '${VERSION}'
          AND success = TRUE;
        " \
      | tr -d '[:space:]'
  )"

  echo "FLYWAY_V${VERSION}_COUNT=${COUNT}"

  if [ "${COUNT}" != "1" ]; then
    echo "[FAIL] FLYWAY_V${VERSION}"
    exit 1
  fi
done

echo "[PASS] FLYWAY_V5_RUNTIME"

echo
echo "===== CLEAN VERIFICATION SCENARIOS ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM simulation_scenarios
      WHERE name LIKE 'Verification %';
    " \
  >/dev/null

echo "[PASS] SIMULATION_PRE_CLEAN"

echo
echo "===== HISTORY CONTRACT ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/simulations" \
  >/tmp/terrascope-step5-history-before.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step5-history-before.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert isinstance(
    payload["runs"],
    list,
)

assert "totalCount" in payload
assert "extremeCount" in payload
assert "severeCount" in payload
assert "averageRiskScore" in payload

print(
    "[PASS] SIMULATION_HISTORY_CONTRACT"
)
PY

echo
echo "===== RUN EARTHQUAKE SIMULATION ====="

cat > \
  /tmp/terrascope-step5-earthquake.json \
<<'JSON'
{
  "name": "Verification earthquake scenario",
  "hazardType": "earthquakes",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "radiusKm": 120.0,
  "intensity": 8.0,
  "exposedPopulation": 1500000,
  "infrastructureResiliencePct": 50.0,
  "emergencyReadinessPct": 60.0
}
JSON

RUN_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step5-earthquake-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step5-earthquake.json \
    "http://localhost:8087/api/v1/simulations/run"
)"

echo "SIMULATION_RUN_HTTP=${RUN_HTTP}"

if [ "${RUN_HTTP}" != "200" ]; then

  cat \
    /tmp/terrascope-step5-earthquake-response.json

  exit 1
fi

SCENARIO_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step5-earthquake-response.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

scenario = payload["scenario"]
result = payload["result"]

assert (
    scenario["name"]
    == "Verification earthquake scenario"
)

assert (
    scenario["hazardType"]
    == "earthquakes"
)

assert (
    result["methodologyVersion"]
    == "TERRASCOPE_IMPACT_V1"
)

assert (
    result["affectedPopulation"]
    > 0
)

assert (
    result["displacedPopulation"]
    >= 0
)

assert (
    result["displacedPopulation"]
    <= result["affectedPopulation"]
)

assert (
    result["estimatedEconomicLossMusd"]
    >= 0
)

assert (
    0
    <= result["infrastructureDisruptionPct"]
    <= 100
)

assert (
    0
    <= result["responsePressurePct"]
    <= 100
)

assert (
    0
    <= result["compositeRiskScore"]
    <= 100
)

assert result["riskBand"] in {
    "LOW",
    "MODERATE",
    "HIGH",
    "SEVERE",
    "EXTREME",
}

print(
    scenario["id"]
)
PY
)"

echo "SCENARIO_ID=${SCENARIO_ID}"

echo "[PASS] SIMULATION_RUN"

echo
echo "===== SIMULATION PERSISTENCE ====="

SCENARIO_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM simulation_scenarios
      WHERE id = '${SCENARIO_ID}';
      " \
    | tr -d '[:space:]'
)"

RUN_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM simulation_runs
      WHERE scenario_id = '${SCENARIO_ID}';
      " \
    | tr -d '[:space:]'
)"

echo "SIMULATION_SCENARIO_COUNT=${SCENARIO_COUNT}"
echo "SIMULATION_RUN_COUNT=${RUN_COUNT}"

if [ "${SCENARIO_COUNT}" != "1" ]; then
  echo "[FAIL] SIMULATION_SCENARIO_PERSISTENCE"
  exit 1
fi

if [ "${RUN_COUNT}" != "1" ]; then
  echo "[FAIL] SIMULATION_RUN_PERSISTENCE"
  exit 1
fi

echo "[PASS] SIMULATION_PERSISTENCE"

echo
echo "===== HISTORY AFTER RUN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/simulations" \
  >/tmp/terrascope-step5-history-after.json

python3 - "${SCENARIO_ID}" <<'PY'
import json
import sys

scenario_id = sys.argv[1]

with open(
    "/tmp/terrascope-step5-history-after.json",
    "r",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

matching = [
    item
    for item in payload["runs"]
    if (
        item["scenario"]["id"]
        == scenario_id
    )
]

assert len(matching) == 1

assert (
    payload["totalCount"]
    >= 1
)

print(
    "[PASS] SIMULATION_HISTORY"
)
PY

echo
echo "===== RUNTIME MONOTONICITY ====="

cat > \
  /tmp/terrascope-step5-low.json \
<<'JSON'
{
  "name": "Verification low intensity",
  "hazardType": "wildfires",
  "latitude": 36.8,
  "longitude": 10.2,
  "radiusKm": 90.0,
  "intensity": 3.0,
  "exposedPopulation": 500000,
  "infrastructureResiliencePct": 60.0,
  "emergencyReadinessPct": 70.0
}
JSON

cat > \
  /tmp/terrascope-step5-high.json \
<<'JSON'
{
  "name": "Verification high intensity",
  "hazardType": "wildfires",
  "latitude": 36.8,
  "longitude": 10.2,
  "radiusKm": 90.0,
  "intensity": 8.0,
  "exposedPopulation": 500000,
  "infrastructureResiliencePct": 60.0,
  "emergencyReadinessPct": 70.0
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step5-low.json \
  "http://localhost:8087/api/v1/simulations/run" \
  >/tmp/terrascope-step5-low-response.json

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step5-high.json \
  "http://localhost:8087/api/v1/simulations/run" \
  >/tmp/terrascope-step5-high-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step5-low-response.json",
    "r",
    encoding="utf-8",
) as handle:
    low = json.load(handle)

with open(
    "/tmp/terrascope-step5-high-response.json",
    "r",
    encoding="utf-8",
) as handle:
    high = json.load(handle)

assert (
    high["result"]["compositeRiskScore"]
    > low["result"]["compositeRiskScore"]
)

assert (
    high["result"]["affectedPopulation"]
    > low["result"]["affectedPopulation"]
)

assert (
    high["result"]["estimatedEconomicLossMusd"]
    > low["result"]["estimatedEconomicLossMusd"]
)

print(
    "[PASS] SIMULATION_MONOTONICITY"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

SCENARIO_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'simulation_scenarios'
        AND column_name IN (
          'id',
          'name',
          'hazard_type',
          'latitude',
          'longitude',
          'radius_km',
          'intensity',
          'exposed_population',
          'infrastructure_resilience_pct',
          'emergency_readiness_pct',
          'created_at'
        );
      " \
    | tr -d '[:space:]'
)"

RUN_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'simulation_runs'
        AND column_name IN (
          'id',
          'scenario_id',
          'affected_population',
          'displaced_population',
          'estimated_economic_loss_musd',
          'infrastructure_disruption_pct',
          'response_pressure_pct',
          'composite_risk_score',
          'risk_band',
          'methodology_version',
          'created_at'
        );
      " \
    | tr -d '[:space:]'
)"

echo "SCENARIO_COLUMN_COUNT=${SCENARIO_COLUMNS}"
echo "RUN_COLUMN_COUNT=${RUN_COLUMNS}"

if [ "${SCENARIO_COLUMNS}" != "11" ]; then
  exit 1
fi

if [ "${RUN_COLUMNS}" != "11" ]; then
  exit 1
fi

echo "[PASS] SIMULATION_DATABASE_CONTRACT"

echo
echo "===== CLEAN VERIFICATION DATA ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM simulation_scenarios
      WHERE name LIKE 'Verification %';
    " \
  >/dev/null

VERIFY_SCENARIOS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM simulation_scenarios
      WHERE name LIKE 'Verification %';
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_SIMULATION_COUNT=${VERIFY_SCENARIOS}"

if [ "${VERIFY_SCENARIOS}" != "0" ]; then
  exit 1
fi

echo "[PASS] SIMULATION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 5 VERIFICATION PASSED"
echo "========================================================================"
