CREATE TABLE resilience_governance_offline_trust_checkpoint_bundles (
    id UUID PRIMARY KEY,

    bundle_number INTEGER NOT NULL,

    source_checkpoint_id UUID NOT NULL,

    source_checkpoint_number INTEGER NOT NULL,

    covered_ledger_number INTEGER NOT NULL,

    checkpoint_hash VARCHAR(64) NOT NULL,

    signing_key_id VARCHAR(120) NOT NULL,

    signing_public_key_fingerprint VARCHAR(64) NOT NULL,

    bundle_version VARCHAR(100) NOT NULL,

    payload_json TEXT NOT NULL,

    bundle_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_offline_trust_checkpoint_bundle_number
        UNIQUE (
            bundle_number
        ),

    CONSTRAINT uq_offline_trust_checkpoint_bundle_source
        UNIQUE (
            source_checkpoint_id
        ),

    CONSTRAINT uq_offline_trust_checkpoint_bundle_hash
        UNIQUE (
            bundle_hash
        ),

    CONSTRAINT ck_offline_trust_checkpoint_bundle_number
        CHECK (
            bundle_number >= 1
        ),

    CONSTRAINT ck_offline_trust_checkpoint_source_number
        CHECK (
            source_checkpoint_number >= 1
        ),

    CONSTRAINT ck_offline_trust_checkpoint_ledger_number
        CHECK (
            covered_ledger_number >= 1
        ),

    CONSTRAINT ck_offline_trust_checkpoint_hash
        CHECK (
            checkpoint_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_checkpoint_key_fingerprint
        CHECK (
            signing_public_key_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_checkpoint_bundle_hash
        CHECK (
            bundle_hash
            ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_offline_trust_checkpoint_bundle_created
    ON resilience_governance_offline_trust_checkpoint_bundles (
        created_at DESC
    );

INSERT INTO resilience_governance_sequence_allocators (
    allocator_key,
    next_value,
    allocator_version
)
VALUES (
    'OFFLINE_TRUST_CHECKPOINT_BUNDLE_NUMBER_V1',
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
