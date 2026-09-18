#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step30-backend.log"

KEYGEN_SOURCE="/tmp/TerraScopeStep30Keygen.java"

KEY1_PRIVATE_FILE="/tmp/terrascope-step30-key1-private.b64"
KEY1_PUBLIC_FILE="/tmp/terrascope-step30-key1-public.b64"

KEY2_PRIVATE_FILE="/tmp/terrascope-step30-key2-private.b64"
KEY2_PUBLIC_FILE="/tmp/terrascope-step30-key2-public.b64"

KEY1_ID="verification-step30-ed25519-v1"
KEY2_ID="verification-step30-ed25519-v2"

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

start_backend() {
  local KEY_ID="$1"
  local PRIVATE_KEY="$2"
  local PUBLIC_KEY="$3"

  {
    echo
    echo "========================================================================"
    echo " START BACKEND WITH ${KEY_ID}"
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

    if [[ "${attempt}" -eq 45 ]]; then
      cat "${BACKEND_LOG}"
      return 1
    fi

    sleep 1
  done
}

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

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 30 VERIFICATION"
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

public class TerraScopeStep30Keygen {

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

[[ "${KEY1_PUBLIC}" != "${KEY2_PUBLIC}" ]]

echo "[PASS] TWO_LOCAL_ED25519_KEYPAIRS"

rm -f "${BACKEND_LOG}"

start_backend \
  "${KEY1_ID}" \
  "${KEY1_PRIVATE}" \
  "${KEY1_PUBLIC}"

echo "[PASS] BACKEND_RUNTIME_KEY1_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step30-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.30.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 \
  21 22 23 24 25 26 27 28 29 30
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

echo "[PASS] FLYWAY_V30_RUNTIME"

echo
echo "===== CLEAN STEP 30 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
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
            'verification-step30-realized';
    " \
  >/dev/null

echo "[PASS] STEP30_PRE_CLEAN"

echo
echo "===== KEY 1 SIGNING READINESS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signing-readiness" \
  >/tmp/terrascope-step30-key1-readiness.json

KEY1_FINGERPRINT="$(
  python3 - "${KEY1_ID}" <<'PY'
import json
import re
import sys

key_id = sys.argv[1]

with open(
    "/tmp/terrascope-step30-key1-readiness.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is True

assert payload["algorithm"] == "ED25519"

assert payload["keyId"] == key_id

assert payload["trustAnchorRegistered"] is False

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["publicKeyFingerprint"],
)

print(
    payload["publicKeyFingerprint"]
)
PY
)"

echo "KEY1_FINGERPRINT=${KEY1_FINGERPRINT}"

echo "[PASS] KEY1_SIGNING_READINESS"

echo
echo "===== CREATE GOVERNANCE CLOSURE FIXTURE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step30-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

BASELINE_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["currentSnapshotId"]
)
PY
)"

cat > \
  /tmp/terrascope-step30-objective.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Validate governance signing-key rotation",
  "description": "Step 30 trust-anchor rotation and historical signature continuity fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Governance certificates remain verifiable through an Ed25519 trust-anchor rotation."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step30-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step30-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-objective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["objectives"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step30-complete-objective.json \
<<'JSON'
{
  "completionEvidence": "Governance trust-anchor rotation controls were implemented for lifecycle validation."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step30-complete-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

cat > \
  /tmp/terrascope-step30-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step30-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 30 Trust Rotation Incident",
  "description": "Trust-anchor rotation verification fixture.",
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
  --data @/tmp/terrascope-step30-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step30-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step30-close.json \
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
  --data @/tmp/terrascope-step30-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step30-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-aar.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/sitrep/reports" \
  >/dev/null

cat > \
  /tmp/terrascope-step30-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "TECHNOLOGY",
  "title": "Exercise governance key rotation assurance",
  "description": "Step 30 key rotation validation improvement.",
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
  --data @/tmp/terrascope-step30-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step30-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step30-complete-improvement.json \
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
  --data @/tmp/terrascope-step30-complete-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step30-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TECHNICAL",
  "title": "Governance trust rotation exercise",
  "description": "Validate trust-anchor rotation and signature continuity.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step30-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step30-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step30-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 99,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Trust-anchor rotation and signature continuity controls successfully validated.",
  "findings": "No material signing-key lifecycle gap remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step30-assessment.json \
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
  >/tmp/terrascope-step30-outcome.json

OUTCOME_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-outcome.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["outcome"] == "ACHIEVED"

print(
    payload["validationSnapshotId"]
)
PY
)"

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step30-certificate.json

CERTIFICATE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-certificate.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["id"]
)
PY
)"

CERTIFICATE_NUMBER="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-certificate.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["certificateNumber"]
)
PY
)"

BENEFIT_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-certificate.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["benefitSnapshotId"]
)
PY
)"

echo "CERTIFICATE_ID=${CERTIFICATE_ID}"
echo "CERTIFICATE_NUMBER=${CERTIFICATE_NUMBER}"

echo "[PASS] TRUST_ROTATION_CERTIFICATE_FIXTURE"

echo
echo "===== SIGN CERTIFICATE WITH KEY 1 ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}" \
  >/tmp/terrascope-step30-signature-key1.json

SIGNATURE1_ID="$(
  python3 \
    - "${KEY1_ID}" \
    "${CERTIFICATE_ID}" \
<<'PY'
import json
import sys

key_id = sys.argv[1]
certificate_id = sys.argv[2]

with open(
    "/tmp/terrascope-step30-signature-key1.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["keyId"] == key_id
assert payload["certificateId"] == certificate_id

print(
    payload["id"]
)
PY
)"

SIGNATURE1_BASE64="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-signature-key1.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["signatureBase64"]
)
PY
)"

echo "SIGNATURE1_ID=${SIGNATURE1_ID}"

echo "[PASS] KEY1_CERTIFICATE_SIGNATURE"

echo
echo "===== REGISTER REPLACEMENT KEY 2 ====="

cat > \
  /tmp/terrascope-step30-register-key2.json \
<<JSON
{
  "keyId": "${KEY2_ID}",
  "publicKeyX509Base64": "${KEY2_PUBLIC}"
}
JSON

REGISTER_KEY2_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step30-key2-anchor.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step30-register-key2.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors"
)"

echo "REGISTER_KEY2_HTTP=${REGISTER_KEY2_HTTP}"

[[ "${REGISTER_KEY2_HTTP}" == "200" ]]

KEY2_FINGERPRINT="$(
  python3 - "${KEY2_ID}" <<'PY'
import json
import re
import sys

key_id = sys.argv[1]

with open(
    "/tmp/terrascope-step30-key2-anchor.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["keyId"] == key_id
assert payload["algorithm"] == "ED25519"
assert payload["status"] == "ACTIVE"

assert payload["supersededByKeyId"] is None
assert payload["retiredAt"] is None

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["publicKeyFingerprint"],
)

print(
    payload["publicKeyFingerprint"]
)
PY
)"

echo "KEY2_FINGERPRINT=${KEY2_FINGERPRINT}"

[[ "${KEY1_FINGERPRINT}" != "${KEY2_FINGERPRINT}" ]]

echo "[PASS] REPLACEMENT_TRUST_ANCHOR_REGISTRATION"

echo
echo "===== RETIRE KEY 1 ====="

cat > \
  /tmp/terrascope-step30-retire-key1.json \
<<JSON
{
  "replacementKeyId": "${KEY2_ID}",
  "reason": "Scheduled governance signing-key rotation after successful replacement-anchor registration."
}
JSON

RETIRE_KEY1_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step30-key1-retired.json \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data @/tmp/terrascope-step30-retire-key1.json \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors/${KEY1_ID}/retire"
)"

echo "RETIRE_KEY1_HTTP=${RETIRE_KEY1_HTTP}"

[[ "${RETIRE_KEY1_HTTP}" == "200" ]]

python3 \
  - "${KEY1_ID}" \
  "${KEY2_ID}" \
<<'PY'
import json
import sys

key1 = sys.argv[1]
key2 = sys.argv[2]

with open(
    "/tmp/terrascope-step30-key1-retired.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["keyId"] == key1

assert payload["status"] == "RETIRED"

assert payload["supersededByKeyId"] == key2

assert payload["retiredAt"] is not None

assert (
    "rotation"
    in payload["retirementReason"].lower()
)

print(
    "[PASS] KEY1_RETIREMENT"
)
PY

echo
echo "===== RETIRED KEY CANNOT SIGN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signing-readiness" \
  >/tmp/terrascope-step30-key1-retired-readiness.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-key1-retired-readiness.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is False

assert (
    "retired"
    in payload["message"].lower()
)

print(
    "[PASS] RETIRED_KEY_SIGNING_BLOCK"
)
PY

echo
echo "===== HISTORICAL KEY1 SIGNATURE STILL VERIFIES ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step30-retired-key-verify.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-retired-key-verify.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["signatureCount"] == 1
assert payload["verifiedSignatureCount"] == 1

print(
    "[PASS] HISTORICAL_SIGNATURE_CONTINUITY"
)
PY

echo
echo "===== RESTART BACKEND WITH KEY 2 ====="

stop_backend

echo "[PASS] KEY1_BACKEND_STOPPED"

start_backend \
  "${KEY2_ID}" \
  "${KEY2_PRIVATE}" \
  "${KEY2_PUBLIC}"

echo "[PASS] BACKEND_RUNTIME_KEY2_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signing-readiness" \
  >/tmp/terrascope-step30-key2-readiness.json

python3 \
  - "${KEY2_ID}" \
  "${KEY2_FINGERPRINT}" \
<<'PY'
import json
import sys

key_id = sys.argv[1]
fingerprint = sys.argv[2]

with open(
    "/tmp/terrascope-step30-key2-readiness.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is True

assert payload["keyId"] == key_id

assert payload["publicKeyFingerprint"] == fingerprint

assert payload["trustAnchorRegistered"] is True

print(
    "[PASS] KEY2_SIGNING_READINESS"
)
PY

echo
echo "===== RE-ATTEST SAME CERTIFICATE WITH KEY 2 ====="

KEY2_SIGN_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step30-signature-key2.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}"
)"

echo "KEY2_SIGNATURE_HTTP=${KEY2_SIGN_HTTP}"

[[ "${KEY2_SIGN_HTTP}" == "200" ]]

SIGNATURE2_ID="$(
  python3 \
    - "${KEY2_ID}" \
    "${CERTIFICATE_ID}" \
<<'PY'
import json
import sys

key_id = sys.argv[1]
certificate_id = sys.argv[2]

with open(
    "/tmp/terrascope-step30-signature-key2.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["keyId"] == key_id

assert payload["certificateId"] == certificate_id

print(
    payload["id"]
)
PY
)"

SIGNATURE2_BASE64="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-signature-key2.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["signatureBase64"]
)
PY
)"

echo "SIGNATURE2_ID=${SIGNATURE2_ID}"

[[ "${SIGNATURE1_ID}" != "${SIGNATURE2_ID}" ]]
[[ "${SIGNATURE1_BASE64}" != "${SIGNATURE2_BASE64}" ]]

echo "[PASS] REPLACEMENT_KEY_REATTESTATION"

echo
echo "===== KEY2 DUPLICATE GUARD ====="

KEY2_DUPLICATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step30-key2-duplicate.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}"
)"

echo "KEY2_DUPLICATE_HTTP=${KEY2_DUPLICATE_HTTP}"

[[ "${KEY2_DUPLICATE_HTTP}" == "409" ]]

echo "[PASS] PER_KEY_CERTIFICATE_DUPLICATE_GUARD"

echo
echo "===== VERIFY BOTH SIGNATURE GENERATIONS ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step30-two-key-verify.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-two-key-verify.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["signatureCount"] == 2
assert payload["verifiedSignatureCount"] == 2

print(
    "[PASS] DUAL_GENERATION_SIGNATURE_VERIFICATION"
)
PY

echo
echo "===== TRUST-ANCHOR LIFECYCLE HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/trust-anchors" \
  >/tmp/terrascope-step30-lifecycle.json

python3 \
  - "${KEY1_ID}" \
  "${KEY2_ID}" \
  "${KEY1_FINGERPRINT}" \
  "${KEY2_FINGERPRINT}" \
<<'PY'
import json
import sys

key1 = sys.argv[1]
key2 = sys.argv[2]
fingerprint1 = sys.argv[3]
fingerprint2 = sys.argv[4]

with open(
    "/tmp/terrascope-step30-lifecycle.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

anchors = {
    anchor["keyId"]: anchor
    for anchor in payload["anchors"]
}

assert key1 in anchors
assert key2 in anchors

assert anchors[key1]["status"] == "RETIRED"
assert anchors[key1]["supersededByKeyId"] == key2
assert anchors[key1]["publicKeyFingerprint"] == fingerprint1

assert anchors[key2]["status"] == "ACTIVE"
assert anchors[key2]["supersededByKeyId"] is None
assert anchors[key2]["publicKeyFingerprint"] == fingerprint2

events = payload["events"]

types_for_key1 = [
    event["eventType"]
    for event in events
    if event["keyId"] == key1
]

types_for_key2 = [
    event["eventType"]
    for event in events
    if event["keyId"] == key2
]

assert "REGISTERED" in types_for_key1
assert "SIGNATURE_ISSUED" in types_for_key1
assert "RETIRED" in types_for_key1

assert "REGISTERED" in types_for_key2
assert "SIGNATURE_ISSUED" in types_for_key2

retirement_event = next(
    event
    for event in events
    if event["keyId"] == key1
    and event["eventType"] == "RETIRED"
)

assert retirement_event["replacementKeyId"] == key2

print(
    f"TRUST_ANCHOR_EVENT_COUNT={len(events)}"
)

print(
    "[PASS] TRUST_ANCHOR_LIFECYCLE_HISTORY"
)
PY

echo
echo "===== DELIBERATE RETIRED SIGNATURE TAMPER ====="

ZERO_SIGNATURE="$(
  python3 - <<'PY'
import base64

print(
    base64.b64encode(
        bytes(64)
    ).decode()
)
PY
)"

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_closure_signatures

      SET signature_base64 =
          '${ZERO_SIGNATURE}'

      WHERE id =
            '${SIGNATURE1_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step30-tampered.json

python3 \
  - "${SIGNATURE1_ID}" \
<<'PY'
import json
import sys

signature_id = sys.argv[1]

with open(
    "/tmp/terrascope-step30-tampered.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is False

assert payload["signatureCount"] == 2

assert payload["brokenSignatureId"] == signature_id

assert (
    "verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] RETIRED_SIGNATURE_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE RETIRED SIGNATURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_closure_signatures

      SET signature_base64 =
          '${SIGNATURE1_BASE64}'

      WHERE id =
            '${SIGNATURE1_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step30-restored.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step30-restored.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["signatureCount"] == 2
assert payload["verifiedSignatureCount"] == 2

print(
    "[PASS] ROTATION_SIGNATURE_RESTORATION"
)
PY

echo
echo "===== DATABASE CONTRACT ====="

ANCHOR_COLUMNS="$(
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
            'resilience_governance_trust_anchors';
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

        AND table_name =
            'resilience_governance_trust_anchor_events';
      " \
    | tr -d '[:space:]'
)"

SIGNATURE_COLUMNS="$(
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
            'resilience_governance_closure_signatures';
      " \
    | tr -d '[:space:]'
)"

ANCHOR_ROWS="$(
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

SIGNATURE_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM resilience_governance_closure_signatures

      WHERE certificate_id =
            '${CERTIFICATE_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

EVENT_ROWS="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM resilience_governance_trust_anchor_events

      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );
      " \
    | tr -d '[:space:]'
)"

NEW_SIGNATURE_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM pg_constraint

      WHERE conname =
            'uq_governance_signature_certificate_key';
      " \
    | tr -d '[:space:]'
)"

OLD_SIGNATURE_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM pg_constraint

      WHERE conname =
            'uq_governance_signature_certificate';
      " \
    | tr -d '[:space:]'
)"

REPLACEMENT_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM pg_constraint

      WHERE conname =
            'fk_governance_trust_anchor_replacement';
      " \
    | tr -d '[:space:]'
)"

LIFECYCLE_CHECK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)

      FROM pg_constraint

      WHERE conname =
            'ck_governance_trust_anchor_lifecycle';
      " \
    | tr -d '[:space:]'
)"

echo "TRUST_ANCHOR_COLUMN_COUNT=${ANCHOR_COLUMNS}"
echo "TRUST_ANCHOR_EVENT_COLUMN_COUNT=${EVENT_COLUMNS}"
echo "GOVERNANCE_SIGNATURE_COLUMN_COUNT=${SIGNATURE_COLUMNS}"
echo "TRUST_ANCHOR_ROW_COUNT=${ANCHOR_ROWS}"
echo "GOVERNANCE_SIGNATURE_ROW_COUNT=${SIGNATURE_ROWS}"
echo "TRUST_ANCHOR_EVENT_ROW_COUNT=${EVENT_ROWS}"
echo "NEW_SIGNATURE_UNIQUE_COUNT=${NEW_SIGNATURE_UNIQUE}"
echo "OLD_SIGNATURE_UNIQUE_COUNT=${OLD_SIGNATURE_UNIQUE}"
echo "TRUST_ANCHOR_REPLACEMENT_FK_COUNT=${REPLACEMENT_FK}"
echo "TRUST_ANCHOR_LIFECYCLE_CHECK_COUNT=${LIFECYCLE_CHECK}"

[[ "${ANCHOR_COLUMNS}" == "10" ]]
[[ "${EVENT_COLUMNS}" == "6" ]]
[[ "${SIGNATURE_COLUMNS}" == "11" ]]

[[ "${ANCHOR_ROWS}" == "2" ]]
[[ "${SIGNATURE_ROWS}" == "2" ]]
[[ "${EVENT_ROWS}" -ge "5" ]]

[[ "${NEW_SIGNATURE_UNIQUE}" == "1" ]]
[[ "${OLD_SIGNATURE_UNIQUE}" == "0" ]]

[[ "${REPLACEMENT_FK}" == "1" ]]
[[ "${LIFECYCLE_CHECK}" == "1" ]]

echo "[PASS] TRUST_ROTATION_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 30 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
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
            'verification-step30-realized';

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
            'verification-step30-realized';
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

VERIFY_EVENT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_trust_anchor_events
      WHERE key_id IN (
          '${KEY1_ID}',
          '${KEY2_ID}'
      );
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "VERIFICATION_OBJECTIVE_COUNT=${VERIFY_OBJECTIVE_COUNT}"
echo "VERIFICATION_CLOSURE_CERTIFICATE_COUNT=${VERIFY_CERTIFICATE_COUNT}"
echo "VERIFICATION_SIGNATURE_COUNT=${VERIFY_SIGNATURE_COUNT}"
echo "VERIFICATION_TRUST_ANCHOR_COUNT=${VERIFY_ANCHOR_COUNT}"
echo "VERIFICATION_TRUST_EVENT_COUNT=${VERIFY_EVENT_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_OBJECTIVE_COUNT}" == "0" ]]
[[ "${VERIFY_CERTIFICATE_COUNT}" == "0" ]]
[[ "${VERIFY_SIGNATURE_COUNT}" == "0" ]]
[[ "${VERIFY_ANCHOR_COUNT}" == "0" ]]
[[ "${VERIFY_EVENT_COUNT}" == "0" ]]

echo "[PASS] TRUST_ROTATION_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 30 VERIFICATION PASSED"
echo "========================================================================"
