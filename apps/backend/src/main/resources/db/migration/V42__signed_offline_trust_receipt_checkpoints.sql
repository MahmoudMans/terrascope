CREATE TABLE resilience_governance_offline_trust_receipt_checkpoints (
    id UUID PRIMARY KEY,

    checkpoint_number INTEGER NOT NULL,

    covered_ledger_number INTEGER NOT NULL,

    covered_ledger_hash VARCHAR(64) NOT NULL,

    entry_count INTEGER NOT NULL,

    valid_receipt_count INTEGER NOT NULL,

    invalid_receipt_count INTEGER NOT NULL,

    key_id VARCHAR(120) NOT NULL,

    algorithm VARCHAR(30) NOT NULL,

    public_key_fingerprint VARCHAR(64) NOT NULL,

    checkpoint_version VARCHAR(100) NOT NULL,

    signature_base64 TEXT NOT NULL,

    previous_checkpoint_hash VARCHAR(64),

    checkpoint_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_offline_trust_receipt_checkpoint_number
        UNIQUE (
            checkpoint_number
        ),

    CONSTRAINT uq_offline_trust_receipt_checkpoint_head_key
        UNIQUE (
            covered_ledger_number,
            key_id
        ),

    CONSTRAINT uq_offline_trust_receipt_checkpoint_hash
        UNIQUE (
            checkpoint_hash
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_number
        CHECK (
            checkpoint_number >= 1
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_ledger
        CHECK (
            covered_ledger_number >= 1
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_counts
        CHECK (
            entry_count >= 1

            AND valid_receipt_count >= 0

            AND invalid_receipt_count >= 0

            AND valid_receipt_count
                + invalid_receipt_count
                = entry_count
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_ledger_hash
        CHECK (
            covered_ledger_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_algorithm
        CHECK (
            algorithm =
            'ED25519'
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_fingerprint
        CHECK (
            public_key_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_previous
        CHECK (
            previous_checkpoint_hash IS NULL

            OR previous_checkpoint_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_checkpoint_hash
        CHECK (
            checkpoint_hash
            ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_offline_trust_receipt_checkpoint_created
    ON resilience_governance_offline_trust_receipt_checkpoints (
        created_at DESC
    );

CREATE INDEX idx_offline_trust_receipt_checkpoint_ledger
    ON resilience_governance_offline_trust_receipt_checkpoints (
        covered_ledger_number DESC
    );

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
VALUES (
    'OFFLINE_TRUST_RECEIPT_CHECKPOINT_NUMBER_V1',
    1,
    'TS-GOVERNANCE-MONOTONIC-ALLOCATOR-V1'
)

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
