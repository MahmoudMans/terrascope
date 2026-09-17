#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step10-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

docker compose config >/dev/null
echo "[PASS] COMPOSE_CONFIGURATION"

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

(
  cd apps/backend
  mvn test
)

echo "[PASS] BACKEND_TESTS"

(
  cd apps/backend
  mvn -DskipTests package
)

echo "[PASS] BACKEND_PACKAGE"

(
  cd apps/web
  npx eslint . --max-warnings=0
  npm run typecheck
  npm run build
)

echo "[PASS] FRONTEND_QUALITY"

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
      >/dev/null 2>&1; then

    echo "[PASS] BACKEND_RUNTIME_READY"
    break
  fi

  if ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    cat "${BACKEND_LOG}"
    exit 1
  fi

  sleep 1
done

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step10-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.10.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10; do
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

  [[ "${COUNT}" == "1" ]]
done

echo "[PASS] FLYWAY_V10_RUNTIME"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step10-incident';
    " \
  >/dev/null

cat > \
  /tmp/terrascope-step10-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step10-incident",
  "hazardType": "wildfires",
  "title": "Verification logistics incident",
  "description": "Step 10 logistics verification.",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "severity": "HIGH",
  "owner": "Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step10-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step10-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "[PASS] LOGISTICS_INCIDENT_CREATE"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources" \
  >/tmp/terrascope-step10-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["shortageCount"] == 0

print(
    "[PASS] RESOURCE_BOARD_EMPTY_CONTRACT"
)
PY

cat > \
  /tmp/terrascope-step10-resource.json \
<<'JSON'
{
  "category": "MEDICAL",
  "name": "Trauma kits",
  "description": "Deploy field trauma kits.",
  "priority": "HIGH",
  "requestedQuantity": 20,
  "unit": "kits",
  "owner": "Medical Alpha"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step10-resource.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources" \
  >/tmp/terrascope-step10-resource-response.json

RESOURCE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-resource-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["requestedCount"] == 1
assert payload["shortageCount"] == 1

resource = payload["resources"][0]

assert resource["requestedQuantity"] == 20
assert resource["allocatedQuantity"] == 0
assert resource["status"] == "REQUESTED"

print(resource["id"])
PY
)"

echo "[PASS] RESOURCE_CREATE"
echo "[PASS] RESOURCE_SHORTAGE_DETECTION"

cat > /tmp/terrascope-step10-partial.json <<'JSON'
{"allocatedQuantity":8}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step10-partial.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources/${RESOURCE_ID}/allocation" \
  >/tmp/terrascope-step10-partial-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-partial-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

resource = payload["resources"][0]

assert resource["status"] == "PARTIALLY_ALLOCATED"
assert resource["allocatedQuantity"] == 8
assert payload["shortageCount"] == 1

print(
    "[PASS] RESOURCE_PARTIAL_ALLOCATION"
)
PY

cat > /tmp/terrascope-step10-full.json <<'JSON'
{"allocatedQuantity":20}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step10-full.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources/${RESOURCE_ID}/allocation" \
  >/tmp/terrascope-step10-full-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-full-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

resource = payload["resources"][0]

assert resource["status"] == "FULFILLED"
assert resource["allocatedQuantity"] == 20
assert resource["fulfilledAt"] is not None
assert payload["shortageCount"] == 0

print(
    "[PASS] RESOURCE_FULFILLMENT"
)
PY

cat > /tmp/terrascope-step10-invalid.json <<'JSON'
{"allocatedQuantity":21}
JSON

INVALID_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step10-invalid-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step10-invalid.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources/${RESOURCE_ID}/allocation"
)"

echo "INVALID_ALLOCATION_HTTP=${INVALID_HTTP}"

[[ "${INVALID_HTTP}" == "400" ]]

echo "[PASS] RESOURCE_ALLOCATION_BOUNDARY"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources/${RESOURCE_ID}/cancel" \
  >/tmp/terrascope-step10-cancel-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-cancel-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["resources"][0]["status"] == "CANCELLED"

print(
    "[PASS] RESOURCE_CANCEL"
)
PY

CANCELLED_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step10-cancelled-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step10-partial.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources/${RESOURCE_ID}/allocation"
)"

echo "CANCELLED_RESOURCE_MUTATION_HTTP=${CANCELLED_HTTP}"

[[ "${CANCELLED_HTTP}" == "409" ]]

echo "[PASS] CANCELLED_RESOURCE_MUTATION_GUARD"

curl \
  --fail \
  --silent \
  --request DELETE \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resources/${RESOURCE_ID}" \
  >/tmp/terrascope-step10-delete-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-delete-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0

print(
    "[PASS] RESOURCE_DELETE"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step10-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step10-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "RESOURCE_REQUESTED" in types
assert "RESOURCE_ALLOCATION_CHANGED" in types
assert "RESOURCE_CANCELLED" in types
assert "RESOURCE_DELETED" in types

print(
    "[PASS] RESOURCE_TIMELINE_CONTRACT"
)
PY

RESOURCE_COLUMNS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name =
            'incident_resource_requests';
      " \
    | tr -d '[:space:]'
)"

echo "RESOURCE_COLUMN_COUNT=${RESOURCE_COLUMNS}"

[[ "${RESOURCE_COLUMNS}" == "15" ]]

echo "[PASS] RESOURCE_DATABASE_CONTRACT"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step10-incident';
    " \
  >/dev/null

VERIFY_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_cases
      WHERE source_event_id =
            'verification-step10-incident';
      " \
    | tr -d '[:space:]'
)"

[[ "${VERIFY_COUNT}" == "0" ]]

echo "[PASS] RESOURCE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 10 VERIFICATION PASSED"
echo "========================================================================"
