#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step7-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 7 VERIFICATION"
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

  if [[ "${attempt}" -eq 30 ]]; then
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
  npx eslint . --max-warnings=0
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
      >/tmp/terrascope-step7-health.json \
      2>/dev/null; then

    echo "[PASS] BACKEND_RUNTIME_READY"
    break
  fi

  if ! kill -0 \
      "${BACKEND_PID}" \
      >/dev/null 2>&1; then

    cat "${BACKEND_LOG}"
    exit 1
  fi

  if [[ "${attempt}" -eq 45 ]]; then
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
  >/tmp/terrascope-step7-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step7-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["status"] == "operational"
assert payload["version"] == "0.7.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

echo
echo "===== STEP 6 REGRESSION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/regional-intelligence/history" \
  >/tmp/terrascope-step7-regional.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/simulations" \
  >/tmp/terrascope-step7-simulations.json

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/watchlist" \
  >/tmp/terrascope-step7-watchlist.json

python3 - <<'PY'
import json

for path, field in (
    (
        "/tmp/terrascope-step7-regional.json",
        "runs",
    ),
    (
        "/tmp/terrascope-step7-simulations.json",
        "runs",
    ),
    (
        "/tmp/terrascope-step7-watchlist.json",
        "entries",
    ),
):
    with open(
        path,
        encoding="utf-8",
    ) as handle:
        payload = json.load(handle)

    assert isinstance(
        payload[field],
        list,
    )

print(
    "[PASS] STEP6_RUNTIME_REGRESSION"
)
PY

echo
echo "===== FLYWAY V7 ====="

for VERSION in 1 2 3 4 5 6 7; do

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

  if [[ "${COUNT}" != "1" ]]; then
    exit 1
  fi
done

echo "[PASS] FLYWAY_V7_RUNTIME"

echo
echo "===== CLEAN VERIFICATION RULES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM hazard_alert_rules
      WHERE name LIKE 'Verification %';
    " \
  >/dev/null

echo "[PASS] ALERT_PRE_CLEAN"

echo
echo "===== EMPTY / CURRENT ALERT DASHBOARD ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/alerts" \
  >/tmp/terrascope-step7-dashboard-before.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step7-dashboard-before.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert isinstance(
    payload["rules"],
    list,
)

assert isinstance(
    payload["alerts"],
    list,
)

for key in (
    "ruleCount",
    "enabledRuleCount",
    "alertCount",
    "newAlertCount",
    "acknowledgedAlertCount",
):
    assert key in payload

print(
    "[PASS] ALERT_DASHBOARD_CONTRACT"
)
PY

echo
echo "===== CREATE ALERT RULE ====="

cat > \
  /tmp/terrascope-step7-rule.json \
<<'JSON'
{
  "name": "Verification earthquake watch",
  "hazardType": "earthquakes",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "radiusKm": 100.0,
  "minimumIntensity": 4.0
}
JSON

RULE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step7-rule-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step7-rule.json \
    "http://localhost:8087/api/v1/alerts/rules"
)"

echo "ALERT_RULE_HTTP=${RULE_HTTP}"

if [[ "${RULE_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step7-rule-response.json
  exit 1
fi

RULE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step7-rule-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["name"]
    == "Verification earthquake watch"
)

assert (
    payload["hazardType"]
    == "earthquakes"
)

assert payload["enabled"] is True

assert payload["radiusKm"] == 100.0
assert payload["minimumIntensity"] == 4.0

print(payload["id"])
PY
)"

echo "RULE_ID=${RULE_ID}"

echo "[PASS] ALERT_RULE_CREATE"

echo
echo "===== EVALUATE RULE ====="

cat > \
  /tmp/terrascope-step7-evaluate.json \
<<'JSON'
{
  "points": [
    {
      "id": "verification-near-earthquake",
      "hazardType": "earthquakes",
      "title": "Near verification earthquake",
      "latitude": 36.85,
      "longitude": 10.20,
      "intensity": 5.5,
      "observedAt": "2026-09-17T12:00:00Z"
    },
    {
      "id": "verification-low-earthquake",
      "hazardType": "earthquakes",
      "title": "Below threshold earthquake",
      "latitude": 36.84,
      "longitude": 10.22,
      "intensity": 3.0,
      "observedAt": "2026-09-17T11:00:00Z"
    },
    {
      "id": "verification-far-earthquake",
      "hazardType": "earthquakes",
      "title": "Far verification earthquake",
      "latitude": 40.00,
      "longitude": 14.00,
      "intensity": 7.0,
      "observedAt": "2026-09-17T10:00:00Z"
    },
    {
      "id": "verification-near-wildfire",
      "hazardType": "wildfires",
      "title": "Nearby wildfire",
      "latitude": 36.82,
      "longitude": 10.19,
      "intensity": null,
      "observedAt": "2026-09-17T09:00:00Z"
    }
  ]
}
JSON

EVALUATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step7-evaluate-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Accept: application/json' \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step7-evaluate.json \
    "http://localhost:8087/api/v1/alerts/evaluate"
)"

echo "ALERT_EVALUATE_HTTP=${EVALUATE_HTTP}"

if [[ "${EVALUATE_HTTP}" != "200" ]]; then
  cat /tmp/terrascope-step7-evaluate-response.json
  exit 1
fi

ALERT_ID="$(
  python3 - "${RULE_ID}" <<'PY'
import json
import sys

rule_id = sys.argv[1]

with open(
    "/tmp/terrascope-step7-evaluate-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["evaluatedRuleCount"] >= 1
assert payload["evaluatedPointCount"] == 4
assert payload["newlyTriggeredCount"] == 1

alerts = [
    alert
    for alert in payload["dashboard"]["alerts"]
    if alert["ruleId"] == rule_id
]

assert len(alerts) == 1

alert = alerts[0]

assert (
    alert["sourceEventId"]
    == "verification-near-earthquake"
)

assert alert["hazardType"] == "earthquakes"
assert alert["status"] == "NEW"
assert alert["intensity"] == 5.5
assert alert["distanceKm"] < 100

print(alert["id"])
PY
)"

echo "ALERT_ID=${ALERT_ID}"

echo "[PASS] ALERT_RULE_EVALUATION"

echo
echo "===== DEDUPLICATION ====="

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step7-evaluate.json \
  "http://localhost:8087/api/v1/alerts/evaluate" \
  >/tmp/terrascope-step7-evaluate-second.json

python3 - "${RULE_ID}" <<'PY'
import json
import sys

rule_id = sys.argv[1]

with open(
    "/tmp/terrascope-step7-evaluate-second.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["newlyTriggeredCount"] == 0

alerts = [
    alert
    for alert in payload["dashboard"]["alerts"]
    if alert["ruleId"] == rule_id
]

assert len(alerts) == 1

print(
    "[PASS] ALERT_DEDUPLICATION"
)
PY

DB_ALERT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM hazard_alert_events
      WHERE rule_id = '${RULE_ID}';
      " \
    | tr -d '[:space:]'
)"

echo "RULE_ALERT_ROW_COUNT=${DB_ALERT_COUNT}"

if [[ "${DB_ALERT_COUNT}" != "1" ]]; then
  exit 1
fi

echo "[PASS] ALERT_DATABASE_DEDUPLICATION"

echo
echo "===== ACKNOWLEDGE ALERT ====="

cat > \
  /tmp/terrascope-step7-status.json \
<<'JSON'
{
  "status": "ACKNOWLEDGED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step7-status.json \
  "http://localhost:8087/api/v1/alerts/events/${ALERT_ID}/status" \
  >/tmp/terrascope-step7-status-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step7-status-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["status"] == "ACKNOWLEDGED"

print(
    "[PASS] ALERT_ACKNOWLEDGE"
)
PY

echo
echo "===== DISABLE RULE ====="

curl \
  --fail \
  --silent \
  --request PATCH \
  "http://localhost:8087/api/v1/alerts/rules/${RULE_ID}/enabled?enabled=false" \
  >/tmp/terrascope-step7-disabled-rule.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step7-disabled-rule.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["enabled"] is False

print(
    "[PASS] ALERT_RULE_DISABLE"
)
PY

echo
echo "===== DISABLED RULE DOES NOT TRIGGER ====="

cat > \
  /tmp/terrascope-step7-disabled-evaluate.json \
<<'JSON'
{
  "points": [
    {
      "id": "verification-disabled-earthquake",
      "hazardType": "earthquakes",
      "title": "Disabled rule earthquake",
      "latitude": 36.82,
      "longitude": 10.19,
      "intensity": 8.0,
      "observedAt": "2026-09-17T13:00:00Z"
    }
  ]
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step7-disabled-evaluate.json \
  "http://localhost:8087/api/v1/alerts/evaluate" \
  >/tmp/terrascope-step7-disabled-evaluate-response.json

python3 - "${RULE_ID}" <<'PY'
import json
import sys

rule_id = sys.argv[1]

with open(
    "/tmp/terrascope-step7-disabled-evaluate-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

rule_specific = [
    alert
    for alert in payload["dashboard"]["alerts"]
    if (
        alert["ruleId"] == rule_id
        and alert["sourceEventId"]
        == "verification-disabled-earthquake"
    )
]

assert rule_specific == []

print(
    "[PASS] DISABLED_RULE_SUPPRESSION"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

RULE_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'hazard_alert_rules'
        AND column_name IN (
          'id',
          'name',
          'hazard_type',
          'center_latitude',
          'center_longitude',
          'radius_km',
          'minimum_intensity',
          'enabled',
          'created_at',
          'updated_at'
        );
      " \
    | tr -d '[:space:]'
)"

EVENT_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'hazard_alert_events'
        AND column_name IN (
          'id',
          'rule_id',
          'source_event_id',
          'hazard_type',
          'title',
          'event_latitude',
          'event_longitude',
          'intensity',
          'observed_at',
          'distance_km',
          'status',
          'triggered_at',
          'updated_at'
        );
      " \
    | tr -d '[:space:]'
)"

UNIQUE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname = 'uq_alert_event_rule_source';
      " \
    | tr -d '[:space:]'
)"

echo "ALERT_RULE_COLUMN_COUNT=${RULE_COLUMNS}"
echo "ALERT_EVENT_COLUMN_COUNT=${EVENT_COLUMNS}"
echo "ALERT_UNIQUE_CONSTRAINT_COUNT=${UNIQUE_COUNT}"

if [[ "${RULE_COLUMNS}" != "10" ]]; then
  exit 1
fi

if [[ "${EVENT_COLUMNS}" != "13" ]]; then
  exit 1
fi

if [[ "${UNIQUE_COUNT}" != "1" ]]; then
  exit 1
fi

echo "[PASS] ALERT_DATABASE_CONTRACT"

echo
echo "===== DELETE RULE + CASCADE ====="

DELETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step7-delete-body.txt \
    --write-out '%{http_code}' \
    --request DELETE \
    "http://localhost:8087/api/v1/alerts/rules/${RULE_ID}"
)"

echo "ALERT_RULE_DELETE_HTTP=${DELETE_HTTP}"

if [[ "${DELETE_HTTP}" != "204" ]]; then
  cat /tmp/terrascope-step7-delete-body.txt
  exit 1
fi

RULE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM hazard_alert_rules
      WHERE id = '${RULE_ID}';
      " \
    | tr -d '[:space:]'
)"

EVENT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM hazard_alert_events
      WHERE rule_id = '${RULE_ID}';
      " \
    | tr -d '[:space:]'
)"

echo "FINAL_RULE_COUNT=${RULE_COUNT}"
echo "FINAL_RULE_EVENT_COUNT=${EVENT_COUNT}"

if [[ "${RULE_COUNT}" != "0" ]]; then
  exit 1
fi

if [[ "${EVENT_COUNT}" != "0" ]]; then
  exit 1
fi

echo "[PASS] ALERT_RULE_DELETE_CASCADE"

echo
echo "===== CLEAN VERIFICATION DATA ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM hazard_alert_rules
      WHERE name LIKE 'Verification %';
    " \
  >/dev/null

VERIFY_RULES="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM hazard_alert_rules
      WHERE name LIKE 'Verification %';
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_ALERT_RULE_COUNT=${VERIFY_RULES}"

if [[ "${VERIFY_RULES}" != "0" ]]; then
  exit 1
fi

echo "[PASS] ALERT_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 7 VERIFICATION PASSED"
echo "========================================================================"
