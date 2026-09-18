#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""
BACKEND_LOG="/tmp/terrascope-step29-backend.log"

KEY_ID="verification-step29-ed25519"

PRIVATE_KEY_FILE="/tmp/terrascope-step29-private.b64"
PUBLIC_KEY_FILE="/tmp/terrascope-step29-public.b64"
KEYGEN_SOURCE="/tmp/TerraScopeStep29Keygen.java"

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi

  rm -f \
    "${PRIVATE_KEY_FILE}" \
    "${PUBLIC_KEY_FILE}" \
    "${KEYGEN_SOURCE}"
}

trap cleanup EXIT

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 29 VERIFICATION"
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
echo "===== GENERATE LOCAL ED25519 KEYPAIR ====="

cat > "${KEYGEN_SOURCE}" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class TerraScopeStep29Keygen {

    public static void main(
            String[] args
    ) throws Exception {
        KeyPair pair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        String privateKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPrivate()
                                        .getEncoded()
                        );

        String publicKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPublic()
                                        .getEncoded()
                        );

        Files.writeString(
                Path.of(
                        args[0]
                ),
                privateKey
        );

        Files.writeString(
                Path.of(
                        args[1]
                ),
                publicKey
        );
    }
}
JAVA

java \
  "${KEYGEN_SOURCE}" \
  "${PRIVATE_KEY_FILE}" \
  "${PUBLIC_KEY_FILE}"

PRIVATE_KEY="$(
  cat "${PRIVATE_KEY_FILE}"
)"

PUBLIC_KEY="$(
  cat "${PUBLIC_KEY_FILE}"
)"

[[ -n "${PRIVATE_KEY}" ]]
[[ -n "${PUBLIC_KEY}" ]]

echo "[PASS] LOCAL_ED25519_KEYPAIR"

rm -f "${BACKEND_LOG}"

(
  cd apps/backend

  TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID="${KEY_ID}" \
  TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64="${PRIVATE_KEY}" \
  TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64="${PUBLIC_KEY}" \
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
  >/tmp/terrascope-step29-status.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-status.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["application"] == "TerraScope"
assert payload["version"] == "0.29.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in \
  1 2 3 4 5 6 7 8 9 10 \
  11 12 13 14 15 16 17 18 19 20 \
  21 22 23 24 25 26 27 28 29
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

echo "[PASS] FLYWAY_V29_RUNTIME"

echo
echo "===== CLEAN STEP 29 TRUST FIXTURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_closure_signatures
      WHERE key_id =
            '${KEY_ID}';

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id =
            '${KEY_ID}';

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step29-realized';
    " \
  >/dev/null

echo "[PASS] STEP29_PRE_CLEAN"

echo
echo "===== SIGNING READINESS BEFORE TRUST REGISTRATION ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signing-readiness" \
  >/tmp/terrascope-step29-signing-readiness.json

PUBLIC_FINGERPRINT="$(
  python3 - "${KEY_ID}" <<'PY'
import json
import re
import sys

key_id = sys.argv[1]

with open(
    "/tmp/terrascope-step29-signing-readiness.json",
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

echo "PUBLIC_KEY_FINGERPRINT=${PUBLIC_FINGERPRINT}"

echo "[PASS] GOVERNANCE_SIGNING_READINESS"

echo
echo "===== CREATE BASELINE REVIEW ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/reviews" \
  >/tmp/terrascope-step29-review.json

REVIEW_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-review.json",
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
    "/tmp/terrascope-step29-review.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["currentSnapshotId"]
)
PY
)"

echo "REVIEW_ID=${REVIEW_ID}"
echo "BASELINE_SNAPSHOT_ID=${BASELINE_SNAPSHOT_ID}"

echo "[PASS] GOVERNANCE_SIGNATURE_REVIEW_FIXTURE"

echo
echo "===== CREATE + COMPLETE STRATEGIC OBJECTIVE ====="

cat > \
  /tmp/terrascope-step29-objective.json \
<<JSON
{
  "portfolioReviewId": "${REVIEW_ID}",
  "title": "Establish cryptographically signed governance closure",
  "description": "Step 29 Ed25519 trust-anchor verification fixture.",
  "priority": "CRITICAL",
  "owner": "Executive Resilience Council",
  "targetAt": "2035-12-31T23:59:59Z",
  "successMetric": "Enterprise governance closure certificate is independently verifiable with an Ed25519 signature."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step29-objective.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives" \
  >/tmp/terrascope-step29-objective-response.json

OBJECTIVE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-objective-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["objectives"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step29-complete.json \
<<'JSON'
{
  "completionEvidence": "Enterprise signing controls were implemented and approved for cryptographic closure verification."
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step29-complete.json \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/complete" \
  >/dev/null

echo "OBJECTIVE_ID=${OBJECTIVE_ID}"

echo "[PASS] GOVERNANCE_SIGNATURE_OBJECTIVE_FIXTURE"

echo
echo "===== CREATE REALIZED BENEFIT FIXTURE ====="

cat > \
  /tmp/terrascope-step29-incident.json \
<<'JSON'
{
  "alertEventId": null,
  "sourceEventId": "verification-step29-realized",
  "hazardType": "earthquakes",
  "title": "Verification Step 29 Signed Governance Incident",
  "description": "Ed25519 governance signing verification fixture.",
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
  --data @/tmp/terrascope-step29-incident.json \
  "http://localhost:8087/api/v1/incidents" \
  >/tmp/terrascope-step29-incident-response.json

INCIDENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-incident-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["incident"]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step29-close.json \
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
  --data @/tmp/terrascope-step29-close.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/status" \
  >/dev/null

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/after-action-reviews" \
  >/tmp/terrascope-step29-aar.json

AAR_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-aar.json",
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
  /tmp/terrascope-step29-improvement.json \
<<JSON
{
  "afterActionReviewId": "${AAR_ID}",
  "category": "TECHNOLOGY",
  "title": "Validate signed governance assurance",
  "description": "Step 29 Ed25519 assurance fixture improvement.",
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
  --data @/tmp/terrascope-step29-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements" \
  >/tmp/terrascope-step29-improvement-response.json

IMPROVEMENT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-improvement-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["actions"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step29-complete-improvement.json \
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
  --data @/tmp/terrascope-step29-complete-improvement.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/improvements/${IMPROVEMENT_ID}/status" \
  >/dev/null

cat > \
  /tmp/terrascope-step29-exercise.json \
<<JSON
{
  "improvementActionId": "${IMPROVEMENT_ID}",
  "exerciseType": "TECHNICAL",
  "title": "Ed25519 governance assurance exercise",
  "description": "Validate cryptographic governance closure controls.",
  "owner": "Preparedness Office",
  "scheduledAt": "2030-02-01T10:00:00Z"
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step29-exercise.json \
  "http://localhost:8087/api/v1/incidents/${INCIDENT_ID}/preparedness-exercises" \
  >/tmp/terrascope-step29-exercise-response.json

EXERCISE_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-exercise-response.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["exercises"][0]["id"]
)
PY
)"

cat > \
  /tmp/terrascope-step29-assessment.json \
<<'JSON'
{
  "result": "PASSED",
  "readinessScore": 99,
  "performedAt": "2030-02-01T12:00:00Z",
  "evidenceSummary": "Ed25519 governance closure controls successfully validated.",
  "findings": "No material cryptographic governance assurance gaps remained.",
  "correctiveAction": ""
}
JSON

curl \
  --fail \
  --silent \
  --request POST \
  --header 'Content-Type: application/json' \
  --data @/tmp/terrascope-step29-assessment.json \
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

echo "[PASS] GOVERNANCE_SIGNATURE_REALIZED_FIXTURE"

echo
echo "===== CAPTURE ACHIEVED OUTCOME ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/objectives/${OBJECTIVE_ID}/outcomes" \
  >/tmp/terrascope-step29-outcome.json

OUTCOME_SNAPSHOT_ID="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-outcome.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["outcome"] == "ACHIEVED"
assert payload["scoreDelta"] >= 5

print(
    payload["validationSnapshotId"]
)
PY
)"

echo "OUTCOME_SNAPSHOT_ID=${OUTCOME_SNAPSHOT_ID}"

echo "[PASS] GOVERNANCE_SIGNATURE_ACHIEVED_OUTCOME"

echo
echo "===== ISSUE GOVERNANCE CLOSURE CERTIFICATE ====="

curl \
  --fail \
  --silent \
  --request POST \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/certificates" \
  >/tmp/terrascope-step29-certificate.json

CERTIFICATE_ID="$(
  python3 - <<'PY'
import json
import re

with open(
    "/tmp/terrascope-step29-certificate.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["band"] == "REALIZED"
assert payload["closureReady"] is True

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["certificateHash"],
)

print(
    payload["id"]
)
PY
)"

CERTIFICATE_NUMBER="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-certificate.json",
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
    "/tmp/terrascope-step29-certificate.json",
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
echo "BENEFIT_SNAPSHOT_ID=${BENEFIT_SNAPSHOT_ID}"

echo "[PASS] GOVERNANCE_SIGNATURE_CERTIFICATE_FIXTURE"

echo
echo "===== VERIFY UNSIGNED CLOSURE CHAIN ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify" \
  >/tmp/terrascope-step29-closure-verify.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-closure-verify.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True
assert payload["certificateCount"] >= 1

print(
    "[PASS] GOVERNANCE_SIGNATURE_CLOSURE_CHAIN_AUTHORITY"
)
PY

echo
echo "===== SIGN CERTIFICATE ====="

SIGN_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step29-signature.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}"
)"

echo "GOVERNANCE_ED25519_SIGNATURE_HTTP=${SIGN_HTTP}"

[[ "${SIGN_HTTP}" == "200" ]]

SIGNATURE_ID="$(
  python3 \
    - "${CERTIFICATE_ID}" \
    "${CERTIFICATE_NUMBER}" \
    "${KEY_ID}" \
    "${PUBLIC_FINGERPRINT}" \
<<'PY'
import base64
import json
import re
import sys

certificate_id = sys.argv[1]
certificate_number = int(
    sys.argv[2]
)
key_id = sys.argv[3]
fingerprint = sys.argv[4]

with open(
    "/tmp/terrascope-step29-signature.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["certificateId"] == certificate_id
assert payload["certificateNumber"] == certificate_number

assert payload["keyId"] == key_id
assert payload["algorithm"] == "ED25519"

assert payload["publicKeyFingerprint"] == fingerprint

assert (
    payload["signatureVersion"]
    == "TS-GOVERNANCE-CLOSURE-ED25519-V1"
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["certificateHash"],
)

assert re.fullmatch(
    r"[0-9a-f]{64}",
    payload["dossierHash"],
)

signature = base64.b64decode(
    payload["signatureBase64"]
)

assert len(signature) == 64

print(
    payload["id"]
)
PY
)"

ORIGINAL_SIGNATURE="$(
  python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-signature.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

print(
    payload["signatureBase64"]
)
PY
)"

echo "SIGNATURE_ID=${SIGNATURE_ID}"

echo "[PASS] GOVERNANCE_ED25519_SIGNATURE_CREATE"

echo
echo "===== TRUST ANCHOR REGISTERED ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signing-readiness" \
  >/tmp/terrascope-step29-signing-ready-after.json

python3 - "${PUBLIC_FINGERPRINT}" <<'PY'
import json
import sys

fingerprint = sys.argv[1]

with open(
    "/tmp/terrascope-step29-signing-ready-after.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["ready"] is True
assert payload["trustAnchorRegistered"] is True
assert payload["publicKeyFingerprint"] == fingerprint

print(
    "[PASS] GOVERNANCE_TRUST_ANCHOR_REGISTRATION"
)
PY

echo
echo "===== DUPLICATE SIGNATURE GUARD ====="

DUPLICATE_HTTP="$(
  curl \
    --silent \
    --output /tmp/terrascope-step29-duplicate-signature.json \
    --write-out '%{http_code}' \
    --request POST \
    "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures/${CERTIFICATE_ID}"
)"

echo "DUPLICATE_SIGNATURE_HTTP=${DUPLICATE_HTTP}"

[[ "${DUPLICATE_HTTP}" == "409" ]]

echo "[PASS] GOVERNANCE_SIGNATURE_DUPLICATE_GUARD"

echo
echo "===== VERIFY ED25519 SIGNATURE ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step29-verify-signatures.json

python3 \
  - "${CERTIFICATE_NUMBER}" \
<<'PY'
import json
import sys

certificate_number = int(
    sys.argv[1]
)

with open(
    "/tmp/terrascope-step29-verify-signatures.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True

assert payload["signatureCount"] == 1
assert payload["verifiedSignatureCount"] == 1

assert payload["brokenSignatureId"] is None
assert payload["brokenCertificateId"] is None
assert payload["brokenCertificateNumber"] is None

assert (
    "verified"
    in payload["message"].lower()
)

print(
    "[PASS] GOVERNANCE_ED25519_SIGNATURE_INTEGRITY"
)
PY

echo
echo "===== SIGNATURE HISTORY ====="

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/signatures" \
  >/tmp/terrascope-step29-signature-history.json

python3 \
  - "${SIGNATURE_ID}" \
  "${CERTIFICATE_ID}" \
  "${PUBLIC_FINGERPRINT}" \
<<'PY'
import json
import sys

signature_id = sys.argv[1]
certificate_id = sys.argv[2]
fingerprint = sys.argv[3]

with open(
    "/tmp/terrascope-step29-signature-history.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert len(
    payload["signatures"]
) == 1

signature = payload["signatures"][0]

assert signature["id"] == signature_id
assert signature["certificateId"] == certificate_id
assert signature["publicKeyFingerprint"] == fingerprint

print(
    "[PASS] GOVERNANCE_SIGNATURE_HISTORY"
)
PY

echo
echo "===== DELIBERATE SIGNATURE TAMPER ====="

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
            '${SIGNATURE_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step29-tampered.json

python3 \
  - "${SIGNATURE_ID}" \
  "${CERTIFICATE_ID}" \
  "${CERTIFICATE_NUMBER}" \
<<'PY'
import json
import sys

signature_id = sys.argv[1]
certificate_id = sys.argv[2]
certificate_number = int(
    sys.argv[3]
)

with open(
    "/tmp/terrascope-step29-tampered.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is False

assert payload["signatureCount"] == 1
assert payload["verifiedSignatureCount"] == 0

assert payload["brokenSignatureId"] == signature_id
assert payload["brokenCertificateId"] == certificate_id
assert payload["brokenCertificateNumber"] == certificate_number

assert (
    "verification failed"
    in payload["message"].lower()
)

print(
    "[PASS] GOVERNANCE_ED25519_TAMPER_DETECTION"
)
PY

echo
echo "===== RESTORE SIGNATURE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      UPDATE resilience_governance_closure_signatures

      SET signature_base64 =
          '${ORIGINAL_SIGNATURE}'

      WHERE id =
            '${SIGNATURE_ID}'::uuid;
    " \
  >/dev/null

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/resilience-portfolio/governance-closure/verify-signatures" \
  >/tmp/terrascope-step29-restored.json

python3 - <<'PY'
import json

with open(
    "/tmp/terrascope-step29-restored.json",
    encoding="utf-8",
) as handle:
    payload = json.load(handle)

assert payload["valid"] is True
assert payload["verifiedSignatureCount"] == 1

print(
    "[PASS] GOVERNANCE_ED25519_RESTORATION_VERIFIED"
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
      WHERE key_id =
            '${KEY_ID}';
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
      WHERE id =
            '${SIGNATURE_ID}'::uuid;
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
            'resilience_governance_closure_signatures'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_governance_closure_certificates%';
      " \
    | tr -d '[:space:]'
)"

ANCHOR_FK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conrelid =
            'resilience_governance_closure_signatures'::regclass
        AND contype = 'f'
        AND pg_get_constraintdef(oid)
            LIKE '%resilience_governance_trust_anchors%';
      " \
    | tr -d '[:space:]'
)"

SIGNATURE_UNIQUE="$(
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

ANCHOR_FINGERPRINT_UNIQUE="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_constraint
      WHERE conname =
            'uq_governance_trust_anchor_fingerprint';
      " \
    | tr -d '[:space:]'
)"

echo "GOVERNANCE_TRUST_ANCHOR_COLUMN_COUNT=${ANCHOR_COLUMNS}"
echo "GOVERNANCE_SIGNATURE_COLUMN_COUNT=${SIGNATURE_COLUMNS}"
echo "GOVERNANCE_TRUST_ANCHOR_ROW_COUNT=${ANCHOR_ROWS}"
echo "GOVERNANCE_SIGNATURE_ROW_COUNT=${SIGNATURE_ROWS}"
echo "GOVERNANCE_SIGNATURE_CERTIFICATE_FK_COUNT=${CERTIFICATE_FK}"
echo "GOVERNANCE_SIGNATURE_ANCHOR_FK_COUNT=${ANCHOR_FK}"
echo "GOVERNANCE_SIGNATURE_CERTIFICATE_UNIQUE_COUNT=${SIGNATURE_UNIQUE}"
echo "GOVERNANCE_ANCHOR_FINGERPRINT_UNIQUE_COUNT=${ANCHOR_FINGERPRINT_UNIQUE}"

[[ "${ANCHOR_COLUMNS}" == "8" ]]
[[ "${SIGNATURE_COLUMNS}" == "11" ]]

[[ "${ANCHOR_ROWS}" == "1" ]]
[[ "${SIGNATURE_ROWS}" == "1" ]]

[[ "${CERTIFICATE_FK}" == "1" ]]
[[ "${ANCHOR_FK}" == "1" ]]

[[ "${SIGNATURE_UNIQUE}" == "1" ]]
[[ "${ANCHOR_FINGERPRINT_UNIQUE}" == "1" ]]

echo "[PASS] GOVERNANCE_SIGNATURE_DATABASE_CONTRACT"

echo
echo "===== CLEAN STEP 29 FIXTURES ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_closure_signatures
      WHERE id =
            '${SIGNATURE_ID}'::uuid;

      DELETE
      FROM resilience_governance_closure_certificates
      WHERE id =
            '${CERTIFICATE_ID}'::uuid;

      DELETE
      FROM resilience_governance_trust_anchors
      WHERE key_id =
            '${KEY_ID}';

      DELETE
      FROM resilience_benefit_realization_snapshots
      WHERE id =
            '${BENEFIT_SNAPSHOT_ID}'::uuid;

      DELETE
      FROM incident_cases
      WHERE source_event_id =
            'verification-step29-realized';

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
            'verification-step29-realized';
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
      WHERE id =
            '${SIGNATURE_ID}'::uuid;
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
      WHERE key_id =
            '${KEY_ID}';
      " \
    | tr -d '[:space:]'
)"

VERIFY_BENEFIT_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_benefit_realization_snapshots
      WHERE id =
            '${BENEFIT_SNAPSHOT_ID}'::uuid;
      " \
    | tr -d '[:space:]'
)"

echo "VERIFICATION_INCIDENT_COUNT=${VERIFY_INCIDENT_COUNT}"
echo "VERIFICATION_OBJECTIVE_COUNT=${VERIFY_OBJECTIVE_COUNT}"
echo "VERIFICATION_CLOSURE_CERTIFICATE_COUNT=${VERIFY_CERTIFICATE_COUNT}"
echo "VERIFICATION_SIGNATURE_COUNT=${VERIFY_SIGNATURE_COUNT}"
echo "VERIFICATION_TRUST_ANCHOR_COUNT=${VERIFY_ANCHOR_COUNT}"
echo "VERIFICATION_BENEFIT_SNAPSHOT_COUNT=${VERIFY_BENEFIT_COUNT}"

[[ "${VERIFY_INCIDENT_COUNT}" == "0" ]]
[[ "${VERIFY_OBJECTIVE_COUNT}" == "0" ]]
[[ "${VERIFY_CERTIFICATE_COUNT}" == "0" ]]
[[ "${VERIFY_SIGNATURE_COUNT}" == "0" ]]
[[ "${VERIFY_ANCHOR_COUNT}" == "0" ]]
[[ "${VERIFY_BENEFIT_COUNT}" == "0" ]]

echo "[PASS] GOVERNANCE_SIGNATURE_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 29 VERIFICATION PASSED"
echo "========================================================================"
