CREATE TABLE resilience_governance_trust_lineage_pin_receipts (
    id UUID PRIMARY KEY,

    receipt_number INTEGER NOT NULL,

    source_package_id UUID,

    source_package_number INTEGER,

    envelope_hash VARCHAR(64) NOT NULL,

    package_hash VARCHAR(64),

    trusted_root_fingerprint VARCHAR(64) NOT NULL,

    presented_root_fingerprint VARCHAR(64),

    verification_result VARCHAR(20) NOT NULL,

    package_hash_valid BOOLEAN NOT NULL,

    canonical_payload_valid BOOLEAN NOT NULL,

    lineage_valid BOOLEAN NOT NULL,

    root_pin_valid BOOLEAN NOT NULL,

    transition_count INTEGER NOT NULL,

    verified_transition_count INTEGER NOT NULL,

    trust_anchor_count INTEGER NOT NULL,

    message TEXT NOT NULL,

    verified_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_trust_lineage_pin_receipt_number
        UNIQUE (
            receipt_number
        ),

    CONSTRAINT ck_trust_lineage_pin_receipt_number
        CHECK (
            receipt_number >= 1
        ),

    CONSTRAINT ck_trust_lineage_pin_envelope_hash
        CHECK (
            envelope_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_trust_lineage_pin_package_hash
        CHECK (
            package_hash IS NULL

            OR package_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_trust_lineage_pin_trusted_root
        CHECK (
            trusted_root_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_trust_lineage_pin_presented_root
        CHECK (
            presented_root_fingerprint IS NULL

            OR presented_root_fingerprint
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_trust_lineage_pin_result
        CHECK (
            verification_result IN (
                'VALID',
                'INVALID'
            )
        ),

    CONSTRAINT ck_trust_lineage_pin_counts
        CHECK (
            transition_count >= 0

            AND verified_transition_count >= 0

            AND verified_transition_count
                <= transition_count

            AND trust_anchor_count >= 0
        )
);

CREATE INDEX idx_trust_lineage_pin_receipt_verified
    ON resilience_governance_trust_lineage_pin_receipts (
        verified_at DESC
    );

CREATE INDEX idx_trust_lineage_pin_receipt_result
    ON resilience_governance_trust_lineage_pin_receipts (
        verification_result,
        verified_at DESC
    );

CREATE INDEX idx_trust_lineage_pin_receipt_root
    ON resilience_governance_trust_lineage_pin_receipts (
        trusted_root_fingerprint,
        verified_at DESC
    );
