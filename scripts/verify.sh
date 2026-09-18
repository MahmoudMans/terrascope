#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.."
  pwd
)"

cd "${ROOT_DIR}"

BACKEND_PID=""

BACKEND_LOG="/tmp/terrascope-step41-backend.log"

ALLOCATOR_CONCURRENCY_KEY="STEP41_CONCURRENCY_PROBE"

ALLOCATOR_MAP=(
  "GOV_ASSURANCE_BUNDLES_BUNDLE_NUMBER_V1|resilience_governance_assurance_bundles|bundle_number"
  "GOV_ASSURANCE_RECEIPT_CHECKPOINTS_CHECKPOINT_NUMBER_V1|resilience_governance_assurance_receipt_checkpoints|checkpoint_number"
  "GOV_ASSURANCE_RECEIPT_LEDGER_LEDGER_NUMBER_V1|resilience_governance_assurance_receipt_ledger|ledger_number"
  "GOV_ASSURANCE_VERIFICATION_RECEIPTS_RECEIPT_NUMBER_V1|resilience_governance_assurance_verification_receipts|receipt_number"
  "GOV_CLOSURE_CERTIFICATES_CERTIFICATE_NUMBER_V1|resilience_governance_closure_certificates|certificate_number"
  "GOV_TRUST_ANCHOR_TRANSITIONS_TRANSITION_NUMBER_V1|resilience_governance_trust_anchor_transitions|transition_number"
  "GOV_TRUST_LINEAGE_PACKAGES_PACKAGE_NUMBER_V1|resilience_governance_trust_lineage_packages|package_number"
  "GOV_TRUST_LINEAGE_PIN_RECEIPTS_RECEIPT_NUMBER_V1|resilience_governance_trust_lineage_pin_receipts|receipt_number"
)

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

wait_backend() {
  for attempt in $(seq 1 45); do
    if curl \
        --fail \
        --silent \
        "http://localhost:8087/actuator/health" \
        >/dev/null 2>&1; then

      return 0
    fi

    if [[ -n "${BACKEND_PID}" ]] \
       && ! kill -0 \
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

start_backend() {
  (
    cd apps/backend

    mvn spring-boot:run
  ) >> "${BACKEND_LOG}" 2>&1 &

  BACKEND_PID=$!

  wait_backend
}

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 41 VERIFICATION"
echo "========================================================================"

docker compose config >/dev/null

docker compose up -d postgres

echo "[PASS] COMPOSE_CONFIGURATION"

(
  cd apps/backend
  mvn test
)

echo "[PASS] BACKEND_TESTS"

(
  cd apps/backend
  mvn -DskipTests package
)

(
  cd apps/web
  npx eslint . --max-warnings=0
  npm run typecheck
  npm run build
)

echo "[PASS] BUILD_QUALITY"

rm -f "${BACKEND_LOG}"

start_backend

echo "[PASS] STEP41_RUNTIME_READY"

curl \
  --fail \
  --silent \
  "http://localhost:8087/api/v1/status" \
  >/tmp/terrascope-step41-status.json

python3 - <<'PY'
import json

payload = json.load(
    open(
        "/tmp/terrascope-step41-status.json",
        encoding="utf-8",
    )
)

assert payload["version"] == "0.41.0"

print(
    "[PASS] STATUS_ENDPOINT"
)
PY

for VERSION in $(seq 1 41); do
  COUNT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version='${VERSION}'
          AND success=TRUE;
        " \
      | tr -d '[:space:]'
  )"

  echo "FLYWAY_V${VERSION}_COUNT=${COUNT}"

  [[ "${COUNT}" == "1" ]]
done

echo "[PASS] FLYWAY_V41_RUNTIME"

echo
echo "===== ALLOCATOR BACKFILL AUTHORITY ====="

for SPEC in "${ALLOCATOR_MAP[@]}"; do
  IFS='|' read -r KEY TABLE COLUMN <<< "${SPEC}"

  DB_NEXT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT next_value
        FROM resilience_governance_sequence_allocators
        WHERE allocator_key=
              '${KEY}';
        " \
      | tr -d '[:space:]'
  )"

  EXPECTED_NEXT="$(
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT
            COALESCE(
                MAX(${COLUMN}),
                0
            ) + 1
        FROM ${TABLE};
        " \
      | tr -d '[:space:]'
  )"

  echo "ALLOCATOR_KEY=${KEY}"
  echo "ALLOCATOR_TABLE=${TABLE}"
  echo "ALLOCATOR_COLUMN=${COLUMN}"
  echo "ALLOCATOR_DB_NEXT=${DB_NEXT}"
  echo "ALLOCATOR_EXPECTED_NEXT=${EXPECTED_NEXT}"

  [[ -n "${DB_NEXT}" ]]
  [[ "${DB_NEXT}" == "${EXPECTED_NEXT}" ]]
done

echo "[PASS] ALL_DISCOVERED_ALLOCATORS_BACKFILLED"

echo
echo "===== GENERIC ALLOCATOR CONCURRENCY PROBE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            '${ALLOCATOR_CONCURRENCY_KEY}';

      INSERT INTO resilience_governance_sequence_allocators (
          allocator_key,
          next_value,
          allocator_version
      )
      VALUES (
          '${ALLOCATOR_CONCURRENCY_KEY}',
          1,
          'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'
      );
    " \
  >/dev/null

rm -rf \
  /tmp/terrascope-step41-concurrency

mkdir -p \
  /tmp/terrascope-step41-concurrency

PIDS=()

for INDEX in $(seq 1 24); do
  (
    docker compose exec -T postgres \
      psql \
        -U terrascope \
        -d terrascope \
        -tAc \
        "
        SELECT allocate_governance_sequence(
            '${ALLOCATOR_CONCURRENCY_KEY}'
        );
        " \
      >"/tmp/terrascope-step41-concurrency/${INDEX}.txt"
  ) &

  PIDS+=(
    "$!"
  )
done

for PID in "${PIDS[@]}"; do
  wait "${PID}"
done

python3 - <<'PY'
from pathlib import Path

values = []

for path in sorted(
    Path(
        "/tmp/terrascope-step41-concurrency"
    ).glob(
        "*.txt"
    )
):
    text = path.read_text().strip()

    assert text

    values.append(
        int(
            text
        )
    )

assert len(
    values
) == 24

assert len(
    set(
        values
    )
) == 24

assert sorted(
    values
) == list(
    range(
        1,
        25,
    )
)

print(
    "CONCURRENT_GENERIC_ALLOCATIONS="
    + ",".join(
        map(
            str,
            sorted(
                values
            ),
        )
    )
)

print(
    "[PASS] GENERIC_ALLOCATOR_CONCURRENCY"
)
PY

PROBE_NEXT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            '${ALLOCATOR_CONCURRENCY_KEY}';
      " \
    | tr -d '[:space:]'
)"

echo "CONCURRENCY_PROBE_NEXT=${PROBE_NEXT}"

[[ "${PROBE_NEXT}" == "25" ]]

echo "[PASS] GENERIC_ALLOCATOR_POST_CONCURRENCY_STATE"

echo
echo "===== GENERIC ALLOCATOR ROLLBACK PROOF ====="

docker compose exec -T postgres \
  psql \
    -v ON_ERROR_STOP=1 \
    -U terrascope \
    -d terrascope \
<<SQL >/tmp/terrascope-step41-rollback.txt
BEGIN;

SELECT allocate_governance_sequence(
    '${ALLOCATOR_CONCURRENCY_KEY}'
);

ROLLBACK;
SQL

PROBE_AFTER_ROLLBACK="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT next_value
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            '${ALLOCATOR_CONCURRENCY_KEY}';
      " \
    | tr -d '[:space:]'
)"

echo "CONCURRENCY_PROBE_BEFORE_ROLLBACK=25"
echo "CONCURRENCY_PROBE_AFTER_ROLLBACK=${PROBE_AFTER_ROLLBACK}"

[[ "${PROBE_AFTER_ROLLBACK}" == "25" ]]

echo "[PASS] GENERIC_ALLOCATOR_ROLLBACK_SAFETY"

echo
echo "===== UNKNOWN ALLOCATOR REJECTION ====="

set +e

docker compose exec -T postgres \
  psql \
    -v ON_ERROR_STOP=1 \
    -U terrascope \
    -d terrascope \
    -tAc \
    "
    SELECT allocate_governance_sequence(
        'STEP41_UNKNOWN_ALLOCATOR'
    );
    " \
  >/tmp/terrascope-step41-unknown.out \
  2>/tmp/terrascope-step41-unknown.err

UNKNOWN_STATUS=$?

set -e

echo "UNKNOWN_ALLOCATOR_STATUS=${UNKNOWN_STATUS}"

[[ "${UNKNOWN_STATUS}" != "0" ]]

grep \
  -q \
  "Unknown TerraScope governance sequence allocator" \
  /tmp/terrascope-step41-unknown.err

echo "[PASS] UNKNOWN_ALLOCATOR_REJECTION"

echo
echo "===== DATABASE CONTRACT ====="

ALLOCATOR_TABLE_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM information_schema.tables
      WHERE table_schema='public'
        AND table_name=
            'resilience_governance_sequence_allocators';
      " \
    | tr -d '[:space:]'
)"

ALLOCATOR_FUNCTION_COUNT="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM pg_proc
      WHERE proname=
            'allocate_governance_sequence';
      " \
    | tr -d '[:space:]'
)"

echo "ALLOCATOR_TABLE_COUNT=${ALLOCATOR_TABLE_COUNT}"
echo "ALLOCATOR_FUNCTION_COUNT=${ALLOCATOR_FUNCTION_COUNT}"

[[ "${ALLOCATOR_TABLE_COUNT}" == "1" ]]
[[ "${ALLOCATOR_FUNCTION_COUNT}" == "1" ]]

echo "[PASS] STEP41_DATABASE_CONTRACT"

echo
echo "===== CLEAN PROBE ====="

docker compose exec -T postgres \
  psql \
    -U terrascope \
    -d terrascope \
    -c "
      DELETE
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            '${ALLOCATOR_CONCURRENCY_KEY}';
    " \
  >/dev/null

PROBE_REMAINDER="$(
  docker compose exec -T postgres \
    psql \
      -U terrascope \
      -d terrascope \
      -tAc \
      "
      SELECT COUNT(*)
      FROM resilience_governance_sequence_allocators
      WHERE allocator_key=
            '${ALLOCATOR_CONCURRENCY_KEY}';
      " \
    | tr -d '[:space:]'
)"

echo "PROBE_REMAINDER=${PROBE_REMAINDER}"

[[ "${PROBE_REMAINDER}" == "0" ]]

echo "[PASS] STEP41_CLEANUP"

echo
echo "========================================================================"
echo " TERRASCOPE — STEP 41 VERIFICATION PASSED"
echo "========================================================================"
