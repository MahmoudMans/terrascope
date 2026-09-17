#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step13-backend.log"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 13 VERIFICATION"
echo "========================================================================"

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

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step13-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.13.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in 1 2 3 4 5 6 7 8 9 10 11 12 13; do
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

echo "[PASS] FLYWAY_V13_RUNTIME"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step13-incident';
    " \
  >/dev/null

cat > \
  /tmp/terrascope-step13-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step13-incident",
  "hazardType": "earthquakes",
  "title": "Verification damage recovery incident",
  "description": "Step 13 damage assessment and recovery verification.",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "severity": "SEVERE",
  "owner": "Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step13-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step13-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

echo "INCIDENT_ID=${INCIDENT_ID}"
echo "[PASS] DAMAGE_INCIDENT_CREATE"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage" \
  >/tmp/terrascope-step13-empty.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-empty.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["reportedCount"] == 0
assert payload["repairingCount"] == 0
assert payload["restoredCount"] == 0
assert payload["criticalDamageCount"] == 0
assert payload["affectedUnits"] == 0
assert payload["totalEstimatedLossMusd"] == 0
assert payload["unresolvedEstimatedLossMusd"] == 0
assert payload["assessments"] == []

print(
    "[PASS] DAMAGE_BOARD_EMPTY_CONTRACT"
)
PY

cat > \
  /tmp/terrascope-step13-damage.json \
<<'JSON'
{
  "category": "BRIDGE",
  "assetName": "Verification Bridge Alpha",
  "description": "Major structural cracking and deck deformation.",
  "severity": "MAJOR",
  "affectedUnits": 12,
  "estimatedLossMusd": 25.5,
  "owner": "Recovery Alpha",
  "recoveryEta": "2030-01-01T00:00:00Z"
}
JSON

CREATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step13-damage-response.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step13-damage.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage"
)"

echo "DAMAGE_CREATE_HTTP=${CREATE_HTTP}"

[[ "${CREATE_HTTP}" == "200" ]]

ASSESSMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-damage-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 1
assert payload["reportedCount"] == 1
assert payload["verifiedCount"] == 0
assert payload["repairingCount"] == 0
assert payload["restoredCount"] == 0

assert payload["criticalDamageCount"] == 1
assert payload["affectedUnits"] == 12

assert payload["totalEstimatedLossMusd"] == 25.5
assert payload["unresolvedEstimatedLossMusd"] == 25.5
assert payload["averageRepairProgressPct"] == 0

assessment = payload["assessments"][0]

assert assessment["category"] == "BRIDGE"
assert assessment["severity"] == "MAJOR"
assert assessment["status"] == "REPORTED"
assert assessment["affectedUnits"] == 12
assert assessment["estimatedLossMusd"] == 25.5
assert assessment["repairProgressPct"] == 0
assert assessment["verifiedAt"] is None
assert assessment["restoredAt"] is None

print(
    assessment["id"]
)
PY
)"

echo "ASSESSMENT_ID=${ASSESSMENT_ID}"

echo "[PASS] DAMAGE_ASSESSMENT_CREATE"
echo "[PASS] DAMAGE_BACKLOG_INTELLIGENCE"

cat > \
  /tmp/terrascope-step13-progress-unverified.json \
<<'JSON'
{
  "repairProgressPct": 25
}
JSON

UNVERIFIED_PROGRESS_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step13-unverified-progress-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step13-progress-unverified.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/progress"
)"

echo "UNVERIFIED_PROGRESS_HTTP=${UNVERIFIED_PROGRESS_HTTP}"

[[ "${UNVERIFIED_PROGRESS_HTTP}" == "409" ]]

echo "[PASS] DAMAGE_VERIFICATION_GUARD"

VERIFY_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step13-verify-response.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/verify"
)"

echo "DAMAGE_VERIFY_HTTP=${VERIFY_HTTP}"

[[ "${VERIFY_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-verify-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["reportedCount"] == 0
assert payload["verifiedCount"] == 1

assessment = payload["assessments"][0]

assert assessment["status"] == "VERIFIED"
assert assessment["verifiedAt"] is not None

print(
    "[PASS] DAMAGE_VERIFICATION"
)
PY

cat > \
  /tmp/terrascope-step13-owner.json \
<<'JSON'
{
  "owner": "Reconstruction Team"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step13-owner.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/owner" \
  >/tmp/terrascope-step13-owner-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-owner-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["assessments"][0]["owner"]
    == "Reconstruction Team"
)

print(
    "[PASS] DAMAGE_OWNER_UPDATE"
)
PY

cat > \
  /tmp/terrascope-step13-eta.json \
<<'JSON'
{
  "recoveryEta": "2031-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step13-eta.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/recovery-eta" \
  >/tmp/terrascope-step13-eta-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-eta-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["assessments"][0]["recoveryEta"]
    == "2031-01-01T00:00:00Z"
)

print(
    "[PASS] DAMAGE_ETA_UPDATE"
)
PY

cat > \
  /tmp/terrascope-step13-severity.json \
<<'JSON'
{
  "severity": "DESTROYED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step13-severity.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/severity" \
  >/tmp/terrascope-step13-severity-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-severity-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert (
    payload["assessments"][0]["severity"]
    == "DESTROYED"
)

assert payload["criticalDamageCount"] == 1

print(
    "[PASS] DAMAGE_SEVERITY_UPDATE"
)
PY

cat > \
  /tmp/terrascope-step13-progress-45.json \
<<'JSON'
{
  "repairProgressPct": 45
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step13-progress-45.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/progress" \
  >/tmp/terrascope-step13-progress-45-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-progress-45-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["verifiedCount"] == 0
assert payload["repairingCount"] == 1
assert payload["restoredCount"] == 0

assert payload["averageRepairProgressPct"] == 45

assessment = payload["assessments"][0]

assert assessment["status"] == "REPAIRING"
assert assessment["repairProgressPct"] == 45

print(
    "[PASS] DAMAGE_REPAIR_PROGRESS"
)
PY

cat > \
  /tmp/terrascope-step13-progress-100.json \
<<'JSON'
{
  "repairProgressPct": 100
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step13-progress-100.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/progress" \
  >/tmp/terrascope-step13-progress-100-response.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-progress-100-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["repairingCount"] == 0
assert payload["restoredCount"] == 1

assert payload["criticalDamageCount"] == 0

assert payload["totalEstimatedLossMusd"] == 25.5
assert payload["unresolvedEstimatedLossMusd"] == 0
assert payload["averageRepairProgressPct"] == 100

assessment = payload["assessments"][0]

assert assessment["status"] == "RESTORED"
assert assessment["repairProgressPct"] == 100
assert assessment["restoredAt"] is not None
assert assessment["recoveryEta"] is None

print(
    "[PASS] DAMAGE_AUTO_RESTORATION"
)
PY

cat > \
  /tmp/terrascope-step13-progress-regression.json \
<<'JSON'
{
  "repairProgressPct": 50
}
JSON

RESTORED_MUTATION_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step13-restored-mutation-response.json \
    --write-out '%{http_code}' \
    --request PATCH \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step13-progress-regression.json \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}/progress"
)"

echo "RESTORED_DAMAGE_MUTATION_HTTP=${RESTORED_MUTATION_HTTP}"

[[ "${RESTORED_MUTATION_HTTP}" == "409" ]]

echo "[PASS] RESTORED_DAMAGE_MUTATION_GUARD"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step13-detail.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-detail.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

for expected in (
    "DAMAGE_ASSESSMENT_CREATED",
    "DAMAGE_VERIFIED",
    "DAMAGE_OWNER_CHANGED",
    "DAMAGE_ETA_CHANGED",
    "DAMAGE_SEVERITY_CHANGED",
):
    assert expected in types

assert (
    types.count(
        "DAMAGE_PROGRESS_CHANGED"
    )
    == 2
)

print(
    "[PASS] DAMAGE_TIMELINE_CONTRACT"
)
PY

DAMAGE_COLUMNS="$(
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
            'incident_damage_assessments';
      " \
    | tr -d '[:space:]'
)"

TIMELINE_CONSTRAINT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'incident_timeline_events'::regclass
        AND conname =
            'ck_incident_timeline_type'
        AND pg_get_constraintdef(oid)
            LIKE '%DAMAGE_ASSESSMENT_CREATED%'
        AND pg_get_constraintdef(oid)
            LIKE '%DAMAGE_ASSESSMENT_DELETED%';
      " \
    | tr -d '[:space:]'
)"

echo "DAMAGE_COLUMN_COUNT=${DAMAGE_COLUMNS}"
echo "DAMAGE_TIMELINE_CONSTRAINT_COUNT=${TIMELINE_CONSTRAINT_COUNT}"

[[ "${DAMAGE_COLUMNS}" == "16" ]]
[[ "${TIMELINE_CONSTRAINT_COUNT}" == "1" ]]

echo "[PASS] DAMAGE_DATABASE_CONTRACT"

DELETE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step13-delete-response.json \
    --write-out '%{http_code}' \
    --request DELETE \
    "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/damage/${ASSESSMENT_ID}"
)"

echo "DAMAGE_DELETE_HTTP=${DELETE_HTTP}"

[[ "${DELETE_HTTP}" == "200" ]]

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-delete-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["totalCount"] == 0
assert payload["assessments"] == []

print(
    "[PASS] DAMAGE_ASSESSMENT_DELETE"
)
PY

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}" \
  >/tmp/terrascope-step13-detail-after-delete.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step13-detail-after-delete.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

types = [
    event["eventType"]
    for event in payload["timeline"]
]

assert "DAMAGE_ASSESSMENT_DELETED" in types

print(
    "[PASS] DAMAGE_DELETE_TIMELINE"
)
PY

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step13-incident';
    " \
  >/dev/null

VERIFY_INCIDENT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_cases
      WHERE source_event_id =
            'verification-step13-incident';
      " \
    | tr -d '[:space:]'
)"

ORPHAN_DAMAGE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM incident_damage_assessments damage
      WHERE NOT EXISTS (
          SELECT 1
          FROM incident_cases incident
          WHERE incident.id =
                damage.incident_id
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "ORPHAN_DAMAGE_COUNT=${ORPHAN_DAMAGE_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${ORPHAN_DAMAGE_COUNT}" == "0" ]]

echo "[PASS] DAMAGE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 13 VERIFICATION PASSED"
echo "========================================================================"
