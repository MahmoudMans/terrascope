CREATE OR REPLACE FUNCTION allocate_governance_sequence(
    p_allocator_key VARCHAR
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    allocated_value INTEGER;
BEGIN
    UPDATE resilience_governance_sequence_allocators

    SET
        next_value =
            next_value + 1,

        updated_at =
            NOW()

    WHERE allocator_key =
          p_allocator_key

    RETURNING
        next_value - 1

    INTO allocated_value;

    IF allocated_value IS NULL THEN
        RAISE EXCEPTION
            'Unknown TerraScope governance sequence allocator: %',
            p_allocator_key;
    END IF;

    RETURN allocated_value;
END;
$$;

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_ASSURANCE_BUNDLES_BUNDLE_NUMBER_V1',

    COALESCE(
        MAX(bundle_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_assurance_bundles

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_ASSURANCE_RECEIPT_CHECKPOINTS_CHECKPOINT_NUMBER_V1',

    COALESCE(
        MAX(checkpoint_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_assurance_receipt_checkpoints

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_ASSURANCE_RECEIPT_LEDGER_LEDGER_NUMBER_V1',

    COALESCE(
        MAX(ledger_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_assurance_receipt_ledger

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_ASSURANCE_VERIFICATION_RECEIPTS_RECEIPT_NUMBER_V1',

    COALESCE(
        MAX(receipt_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_assurance_verification_receipts

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_CLOSURE_CERTIFICATES_CERTIFICATE_NUMBER_V1',

    COALESCE(
        MAX(certificate_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_closure_certificates

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_TRUST_ANCHOR_TRANSITIONS_TRANSITION_NUMBER_V1',

    COALESCE(
        MAX(transition_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_trust_anchor_transitions

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_TRUST_LINEAGE_PACKAGES_PACKAGE_NUMBER_V1',

    COALESCE(
        MAX(package_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_trust_lineage_packages

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'GOV_TRUST_LINEAGE_PIN_RECEIPTS_RECEIPT_NUMBER_V1',

    COALESCE(
        MAX(receipt_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_trust_lineage_pin_receipts

ON CONFLICT (
    allocator_key
)
DO UPDATE

SET
    next_value =
        GREATEST(
            resilience_governance_sequence_allocators.next_value,
            EXCLUDED.next_value
        ),

    allocator_version =
        EXCLUDED.allocator_version,

    updated_at =
        NOW();

