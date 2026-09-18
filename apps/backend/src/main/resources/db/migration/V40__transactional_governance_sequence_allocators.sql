CREATE TABLE resilience_governance_sequence_allocators (
    allocator_key VARCHAR(120) PRIMARY KEY,

    next_value INTEGER NOT NULL,

    allocator_version VARCHAR(100) NOT NULL,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT ck_governance_sequence_allocator_next_value
        CHECK (
            next_value >= 1
        )
);

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'OFFLINE_TRUST_IMPORT_NUMBER_V1',

    COALESCE(
        MAX(import_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_offline_trust_verification_receipts;

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
SELECT
    'OFFLINE_TRUST_LEDGER_NUMBER_V1',

    COALESCE(
        MAX(ledger_number),
        0
    ) + 1,

    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'

FROM resilience_governance_offline_trust_receipt_ledger;

CREATE INDEX idx_governance_sequence_allocator_updated
    ON resilience_governance_sequence_allocators (
        updated_at DESC
    );
