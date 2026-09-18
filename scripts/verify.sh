#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step31-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep31Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step31-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step31-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step31-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step31-key2-public.b64"

KEY1_ID="verification-step31-ed25519-v1"
KEY2_ID="verification-step31-ed25519-v2"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi

  rm -f \
    "${KEYGEN_SOURCE}" \
    "${KEY1_PRIVATE_FILE}" \
    "${KEY1_PUBLIC_FILE}" \
    "${KEY2_PRIVATE_FILE}" \
    "${KEY2_PUBLIC_FILE}"
}

trap cleanup EXIT

stop_backend() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true

    BACKEND_PID=""
  fi

  for attempt in $(seq 1 30); do
    if ! curl \
        --silent \
        "http://localhost:8087/actuator/health" \
        >/dev/null 2>&1; then

      return 0
    fi

    sleep 1
  done

  return 1
}

start_backend_with_key() {
  local KEY_ID="$1"
  local PRIVATE_KEY="$2"
  local PUBLIC_KEY="$3"

  {
    echo
    echo "========================================================================"
    echo " START BACKEND WITH SIGNING KEY ${KEY_ID}"
    echo "========================================================================"
  } >> "${BACKEND_LOG}"

  (
    cd apps/backend

    TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID="${KEY_ID}" \
    TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64="${PRIVATE_KEY}" \
    TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64="${PUBLIC_KEY}" \
      mvn spring-boot:run
  ) >> "${BACKEND_LOG}" 2>&1 &

  BACKEND_PID=$!

  for attempt in $(seq 1 45); do
    if curl \
        --fail \
        --silent \
        "http://localhost:8087/actuator/health" \
        >/dev/null 2>&1; then

      return 0
    fi

    if ! kill -0 \
        "${BACKEND_PID}" \
        >/dev/null 2>&1; then

      cat "${BACKEND_LOG}"
      return 1
    fi

    sleep 1
  done

  cat "${BACKEND_LOG}"
  return 1
}

start_backend_without_key() {
  {
    echo
    echo "========================================================================"
    echo " START BACKEND WITHOUT PRIVATE SIGNING KEY"
    echo "========================================================================"
  } >> "${BACKEND_LOG}"

  (
    cd apps/backend

    env \
      -u TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID \
      -u TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64 \
      -u TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64 \
      mvn spring-boot:run
  ) >> "${BACKEND_LOG}" 2>&1 &

  BACKEND_PID=$!

  for attempt in $(seq 1 45); do
    if curl \
        --fail \
        --silent \
        "http://localhost:8087/actuator/health" \
        >/dev/null 2>&1; then

      return 0
    fi

    if ! kill -0 \
        "${BACKEND_PID}" \
        >/dev/null 2>&1; then

      cat "${BACKEND_LOG}"
      return 1
    fi

    sleep 1
  done

  cat "${BACKEND_LOG}"
  return 1
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 31 VERIFICATION"
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

  npx eslint . \
    --max-warnings=0

  npm run typecheck

  npm run build
)

echo "[PASS] FRONTEND_QUALITY"

echo
echo "===== GENERATE TWO LOCAL ED25519 KEYPAIRS ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class TerraScopeStep31Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        KeyPair first =
                generator.generateKeyPair();

        KeyPair second =
                generator.generateKeyPair();

        Files.writeString(
                Path.of(
                        args[0]
                ),
                Base64
                        .getEncoder()
                        .encodeToString(
                                first.getPrivate()
                                        .getEncoded()
                        )
        );

        Files.writeString(
                Path.of(
                        args[1]
                ),
                Base64
                        .getEncoder()
                        .encodeToString(
                                first.getPublic()
                                        .getEncoded()
                        )
        );

        Files.writeString(
                Path.of(
                        args[2]
                ),
                Base64
                        .getEncoder()
                        .encodeToString(
                                second.getPrivate()
                                        .getEncoded()
                        )
        );

        Files.writeString(
                Path.of(
                        args[3]
                ),
                Base64
                        .getEncoder()
                        .encodeToString(
                                second.getPublic()
                                        .getEncoded()
                        )
        );
    }
}
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${KEY1_PRIVATE_FILE}" \
  "${KEY1_PUBLIC_FILE}" \
  "${KEY2_PRIVATE_FILE}" \
  "${KEY2_PUBLIC_FILE}"

KEY1_PRIVATE="$(
  cat "${KEY1_PRIVATE_FILE}"
)"

KEY1_PUBLIC="$(
  cat "${KEY1_PUBLIC_FILE}"
)"

KEY2_PRIVATE="$(
  cat "${KEY2_PRIVATE_FILE}"
)"

KEY2_PUBLIC="$(
  cat "${KEY2_PUBLIC_FILE}"
)"

[[ -n "${KEY1_PRIVATE}" ]]
[[ -n "${KEY1_PUBLIC}" ]]
[[ -n "${KEY2_PRIVATE}" ]]
[[ -n "${KEY2_PUBLIC}" ]]

echo "[PASS] TWO_LOCAL_ED25519_KEYPAIRS"

rm -f "${BACKEND_LOG}"

start_backend_with_key \
  "${KEY1_ID}" \
  "${KEY1_PRIVATE}" \
  "${KEY1_PUBLIC}"

echo "[PASS] BACKEND_RUNTIME_KEY1_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step31-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.31.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 \
  21 22 23 24 25 26 27 28 29 30 31
do
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

echo "[PASS] FLYWAY_V31_RUNTIME"

echo
echo "===== CLEAN STEP 31 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_bundles;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      )
         OR replacement_key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step31-realized';
    " \
  >/dev/null

echo "[PASS] STEP31_PRE_CLEAN"

echo
echo "===== CREATE CLOSURE CERTIFICATE FIXTURE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step31-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["id"])
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["currentSnapshotId"])
PY
)"

cat > \
  /tmp/terrascope-step31-objective.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Publish portable enterprise governance assurance",
  "description": "Step 31 independently verifiable governance assurance bundle fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Closure evidence is portable and independently verifiable without access to the private signing key."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step31-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-objective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["objectives"][0]["id"])
PY
)"

cat > \
  /tmp/terrascope-step31-complete-objective.json \
<<'JSON'
{
  "completionEvidence": "Portable governance assurance export and independent verification controls were implemented."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-complete-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

cat > \
  /tmp/terrascope-step31-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step31-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 31 Portable Assurance Incident",
  "description": "Portable governance bundle verification fixture.",
  "latitude": 36.8065,
  "longitude": 10.1815,
  "severity": "SEVERE",
  "owner": "Incident Command"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step31-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["incident"]["id"])
PY
)"

cat > \
  /tmp/terrascope-step31-close.json \
<<'JSON'
{
  "status": "CLOSED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step31-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["id"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/dev/null

cat > \
  /tmp/terrascope-step31-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "TECHNOLOGY",
  "title": "Validate portable governance assurance",
  "description": "Step 31 portable bundle validation improvement.",
  "priority": "HIGH",
  "owner": "Resilience Office",
  "dueAt": "2030-01-01T00:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step31-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["actions"][0]["id"])
PY
)"

cat > \
  /tmp/terrascope-step31-complete-improvement.json \
<<'JSON'
{
  "status": "COMPLETED"
}
JSON

curl \
  --fail \
  --silent \
  --request PATCH \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-complete-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step31-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TECHNICAL",
  "title": "Portable governance assurance verification exercise",
  "description": "Validate independent cryptographic evidence verification.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step31-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["exercises"][0]["id"])
PY
)"

cat > \
  /tmp/terrascope-step31-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 99,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Portable governance assurance controls were independently exercised.",
  "findings": "No material evidence portability gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-assessment.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises/${EXERCISE_ID}/assess" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-certification" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-evidence/packages" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/resilience-audit-ledger/receipts" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step31-outcome.json

OUTCOME_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-outcome.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["outcome"] == "ACHIEVED"

print(payload["validationSnapshotId"])
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step31-certificate.json

CERTIFICATE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-certificate.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["id"])
PY
)"

CERTIFICATE_NUMBER="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-certificate.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["certificateNumber"])
PY
)"

BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-certificate.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["benefitSnapshotId"])
PY
)"

echo "CERTIFICATE_ID=${CERTIFICATE_ID}"
echo "CERTIFICATE_NUMBER=${CERTIFICATE_NUMBER}"

echo "[PASS] PORTABLE_BUNDLE_CERTIFICATE_FIXTURE"

echo
echo "===== SIGN WITH KEY 1 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step31-signature1.json

SIGNATURE1_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-signature1.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["id"])
PY
)"

echo "[PASS] PORTABLE_BUNDLE_KEY1_SIGNATURE"

echo
echo "===== REGISTER KEY 2 + RETIRE KEY 1 ====="

cat > \
  /tmp/terrascope-step31-register-key2.json \
<<JSON
{
  "keyId": "${KEY2_ID}",
  "publicKeyX509Base64": "${KEY2_PUBLIC}"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-register-key2.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/dev/null

cat > \
  /tmp/terrascope-step31-retire-key1.json \
<<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "reason": "Step 31 assurance bundle rotation continuity verification."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step31-retire-key1.json \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors/${KEY1_ID}/retire" \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step31-key1-historical.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-key1-historical.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True
assert payload["signatureCount"] == 1

print(
    "[PASS] PORTABLE_BUNDLE_RETIRED_KEY_CONTINUITY"
)
PY

echo
echo "===== RESTART WITH KEY 2 ====="

stop_backend

start_backend_with_key \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] BACKEND_RUNTIME_KEY2_READY"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step31-signature2.json

SIGNATURE2_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-signature2.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["id"])
PY
)"

echo "[PASS] PORTABLE_BUNDLE_KEY2_REATTESTATION"

echo
echo "===== PREVIEW PORTABLE ASSURANCE BUNDLE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/preview/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step31-preview.json

python3 \
  - "${CERTIFICATE_ID}" \
  "${CERTIFICATE_NUMBER}" \
<<'PY'
import json
import re
import sys

certificate_id = sys.argv[1]
certificate_number = int(sys.argv[2])

with open(
    "/tmp/terrascope-step31-preview.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificateId"] == certificate_id
assert payload["certificateNumber"] == certificate_number

assert (
    payload["bundleVersion"]
    == "TS-GOVERNANCE-ASSURANCE-BUNDLE-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["bundleHash"],
)

assert payload["signatureCount"] == 2
assert payload["trustAnchorCount"] == 2
assert payload["retiredAnchorCount"] == 1
assert payload["lifecycleEventCount"] >= 5

assert payload["portableVerificationValid"] is True

print(
    f"BUNDLE_PREVIEW_SIGNATURE_COUNT={payload['signatureCount']}"
)

print(
    f"BUNDLE_PREVIEW_TRUST_ANCHOR_COUNT={payload['trustAnchorCount']}"
)

print(
    f"BUNDLE_PREVIEW_RETIRED_ANCHOR_COUNT={payload['retiredAnchorCount']}"
)

print(
    f"BUNDLE_PREVIEW_EVENT_COUNT={payload['lifecycleEventCount']}"
)

print(
    "[PASS] PORTABLE_ASSURANCE_BUNDLE_PREVIEW"
)
PY

echo
echo "===== CREATE PORTABLE ASSURANCE BUNDLE ====="

BUNDLE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step31-bundle.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${CERTIFICATE_ID}"
)"

echo "PORTABLE_ASSURANCE_BUNDLE_HTTP=${BUNDLE_HTTP}"

[[ "${BUNDLE_HTTP}" == "200" ]]

BUNDLE_ID="$(
  python3 - <<'PY'
import json
import re

with open(
    "/tmp/terrascope-step31-bundle.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["bundleNumber"] == 1

assert (
    payload["bundleVersion"]
    == "TS-GOVERNANCE-ASSURANCE-BUNDLE-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["bundleHash"],
)

assert payload["signatureCount"] == 2
assert payload["trustAnchorCount"] == 2
assert payload["retiredAnchorCount"] == 1
assert payload["lifecycleEventCount"] >= 5

assert payload["portableVerificationValid"] is True

assert payload["payloadJson"].startswith("{")
assert payload["payloadJson"].endswith("}")

print(payload["id"])
PY
)"

BUNDLE_HASH="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-bundle.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(payload["bundleHash"])
PY
)"

echo "BUNDLE_ID=${BUNDLE_ID}"
echo "BUNDLE_HASH=${BUNDLE_HASH}"

echo "[PASS] PORTABLE_ASSURANCE_BUNDLE_CREATE"

echo
echo "===== BUNDLE HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles" \
  >/tmp/terrascope-step31-history.json

python3 - "${BUNDLE_ID}" <<'PY'
import json
import sys

bundle_id = sys.argv[1]

with open(
    "/tmp/terrascope-step31-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert len(payload["bundles"]) == 1

assert payload["bundles"][0]["id"] == bundle_id

print(
    "[PASS] PORTABLE_ASSURANCE_BUNDLE_HISTORY"
)
PY

echo
echo "===== RESTART WITHOUT ANY PRIVATE SIGNING KEY ====="

stop_backend

echo "[PASS] SIGNING_BACKEND_STOPPED"

start_backend_without_key

echo "[PASS] INDEPENDENT_VERIFIER_RUNTIME_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signing-readiness" \
  >/tmp/terrascope-step31-no-key-readiness.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-no-key-readiness.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False

assert (
    "not configured"
    in payload["message"].lower()
)

print(
    "[PASS] PRIVATE_SIGNING_KEY_ABSENT"
)
PY

echo
echo "===== VERIFY PORTABLE BUNDLE WITHOUT PRIVATE KEY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${BUNDLE_ID}/verify" \
  >/tmp/terrascope-step31-bundle-verify.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-bundle-verify.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["portableVerificationValid"] is True

assert payload["signatureCount"] == 2
assert payload["verifiedSignatureCount"] == 2

assert (
    "independently"
    in payload["message"].lower()
)

print(
    "[PASS] PRIVATE_KEY_FREE_PORTABLE_VERIFICATION"
)

print(
    "[PASS] PORTABLE_BUNDLE_HASH_AUTHORITY"
)

print(
    "[PASS] PORTABLE_BUNDLE_SIGNATURE_AUTHORITY"
)
PY

echo
echo "===== LIVE SIGNATURE VERIFICATION ALSO NEEDS NO PRIVATE KEY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step31-signature-verify-no-key.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-signature-verify-no-key.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["signatureCount"] == 2
assert payload["verifiedSignatureCount"] == 2

print(
    "[PASS] PUBLIC_TRUST_ONLY_SIGNATURE_VERIFICATION"
)
PY

echo
echo "===== DELIBERATE BUNDLE PAYLOAD TAMPER ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_bundles

      SET payload_json =
          payload_json || ' '

      WHERE id =
            '${BUNDLE_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${BUNDLE_ID}/verify" \
  >/tmp/terrascope-step31-bundle-tampered.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-bundle-tampered.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is False

assert payload["hashValid"] is False

assert (
    "sha-256"
    in payload["message"].lower()
)

print(
    "[PASS] PORTABLE_BUNDLE_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE BUNDLE PAYLOAD ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_assurance_bundles

      SET payload_json =
          LEFT(
              payload_json,
              LENGTH(payload_json) - 1
          )

      WHERE id =
            '${BUNDLE_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/assurance-bundles/${BUNDLE_ID}/verify" \
  >/tmp/terrascope-step31-bundle-restored.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step31-bundle-restored.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["hashValid"] is True
assert payload["canonicalPayloadValid"] is True
assert payload["portableVerificationValid"] is True

assert payload["verifiedSignatureCount"] == 2

print(
    "[PASS] PORTABLE_BUNDLE_RESTORATION_VERIFIED"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

BUNDLE_COLUMNS="$(
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
            'resilience_governance_assurance_bundles';
      " \
    | tr -d '[:space:]'
)"

BUNDLE_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM resilience_governance_assurance_bundles

      WHERE id =
            '${BUNDLE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

CERTIFICATE_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM pg_constraint

      WHERE conrelid =
            'resilience_governance_assurance_bundles'::regclass

        AND contype = 'f'

        AND pg_get_constraintdef(oid)
            LIKE '%resilience_governance_closure_certificates%';
      " \
    | tr -d '[:space:]'
)"

BUNDLE_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM pg_constraint

      WHERE conname =
            'uq_governance_assurance_bundle_number';
      " \
    | tr -d '[:space:]'
)"

BUNDLE_HASH_CHECK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM pg_constraint

      WHERE conname =
            'ck_governance_assurance_bundle_hash';
      " \
    | tr -d '[:space:]'
)"

echo "GOVERNANCE_ASSURANCE_BUNDLE_COLUMN_COUNT=${BUNDLE_COLUMNS}"
echo "GOVERNANCE_ASSURANCE_BUNDLE_ROW_COUNT=${BUNDLE_ROWS}"
echo "GOVERNANCE_ASSURANCE_BUNDLE_CERTIFICATE_FK_COUNT=${CERTIFICATE_FK}"
echo "GOVERNANCE_ASSURANCE_BUNDLE_UNIQUE_COUNT=${BUNDLE_UNIQUE}"
echo "GOVERNANCE_ASSURANCE_BUNDLE_HASH_CHECK_COUNT=${BUNDLE_HASH_CHECK}"

[[ "${BUNDLE_COLUMNS}" == "13" ]]
[[ "${BUNDLE_ROWS}" == "1" ]]
[[ "${CERTIFICATE_FK}" == "1" ]]
[[ "${BUNDLE_UNIQUE}" == "1" ]]
[[ "${BUNDLE_HASH_CHECK}" == "1" ]]

echo "[PASS] PORTABLE_ASSURANCE_BUNDLE_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 31 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_assurance_bundles
      WHERE id =
            '${BUNDLE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchor_events
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      )
         OR replacement_key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM resilience_governance_closure_signatures
      WHERE id IN (
          '${SIGNATURE1_ID}'::uuid,
          '${SIGNATURE2_ID}'::uuid
      );

      DELETE
      FROM resilience_governance_closure_certificates
      WHERE id =
            '${CERTIFICATE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );

      DELETE
      FROM resilience_benefit_realization_snapshots
      WHERE id =
            '${BENEFIT_SNAPSHOT_ID}'::uuid;

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step31-realized';

      DELETE
      FROM resilience_portfolio_reviews
      WHERE id =
            '${REVIEW_ID}'::uuid;

      DELETE
      FROM resilience_portfolio_snapshots
      WHERE id IN (
          '${BASELINE_SNAPSHOT_ID}'::uuid,
          '${OUTCOME_SNAPSHOT_ID}'::uuid
      );
    " \
  >/dev/null

VERIFY_BUNDLE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_assurance_bundles
      WHERE id =
            '${BUNDLE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

VERIFY_CERTIFICATE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_closure_certificates
      WHERE id =
            '${CERTIFICATE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

VERIFY_SIGNATURE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_closure_signatures
      WHERE id IN (
          '${SIGNATURE1_ID}'::uuid,
          '${SIGNATURE2_ID}'::uuid
      );
      " \
    | tr -d '[:space:]'
)"

VERIFY_ANCHOR_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_anchors
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );
      " \
    | tr -d '[:space:]'
)"

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
            'verification-step31-realized';
      " \
    | tr -d '[:space:]'
)"

VERIFY_OBJECTIVE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_portfolio_objectives
      WHERE id =
            '${OBJECTIVE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_ASSURANCE_BUNDLE_COUNT=${VERIFY_BUNDLE_COUNT}"
echo "VERIFICATION_CLOSURE_CERTIFICATE_COUNT=${VERIFY_CERTIFICATE_COUNT}"
echo "VERIFICATION_SIGNATURE_COUNT=${VERIFY_SIGNATURE_COUNT}"
echo "VERIFICATION_TRUST_ANCHOR_COUNT=${VERIFY_ANCHOR_COUNT}"
echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "VERIFICATION_OBJECTIVE_COUNT=${VERIFY_OBJECTIVE_COUNT}"

[[ "${VERIFY_BUNDLE_COUNT}" == "0" ]]
[[ "${VERIFY_CERTIFICATE_COUNT}" == "0" ]]
[[ "${VERIFY_SIGNATURE_COUNT}" == "0" ]]
[[ "${VERIFY_ANCHOR_COUNT}" == "0" ]]
[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_OBJECTIVE_COUNT}" == "0" ]]

echo "[PASS] PORTABLE_ASSURANCE_BUNDLE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 31 VERIFICATION PASSED"
echo "========================================================================"
