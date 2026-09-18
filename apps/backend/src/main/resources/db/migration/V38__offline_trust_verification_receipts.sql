CREATE TABLE resilience_governance_offline_trust_verification_receipts (
    id UUID PRIMARY KEY,

    import_number INTEGER NOT NULL,

    verifier_version VARCHAR(100) NOT NULL,

    receipt_hash VARCHAR(64) NOT NULL,

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

    root_key_id VARCHAR(120),

    terminal_key_id VARCHAR(120),

    offline_verified_at TIMESTAMP WITH TIME ZONE NOT NULL,

    imported_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_offline_trust_receipt_import_number
        UNIQUE (
            import_number
        ),

    CONSTRAINT uq_offline_trust_receipt_hash
        UNIQUE (
            receipt_hash
        ),

    CONSTRAINT ck_offline_trust_receipt_import_number
        CHECK (
            import_number >= 1
        ),

    CONSTRAINT ck_offline_trust_receipt_hash
        CHECK (
            receipt_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_envelope_hash
        CHECK (
            envelope_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_package_hash
        CHECK (
            package_hash IS NULL

            OR package_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_trusted_root
        CHECK (
            trusted_root_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_presented_root
        CHECK (
            presented_root_fingerprint IS NULL

            OR presented_root_fingerprint
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_result
        CHECK (
            verification_result IN (
                'VALID',
                'INVALID'
            )
        ),

    CONSTRAINT ck_offline_trust_receipt_counts
        CHECK (
            transition_count >= 0

            AND verified_transition_count >= 0

            AND verified_transition_count
                <= transition_count

            AND trust_anchor_count >= 0
        )
);

CREATE INDEX idx_offline_trust_receipt_imported
    ON resilience_governance_offline_trust_verification_receipts (
        imported_at DESC
    );

CREATE INDEX idx_offline_trust_receipt_result
    ON resilience_governance_offline_trust_verification_receipts (
        verification_result,
        imported_at DESC
    );

CREATE INDEX idx_offline_trust_receipt_root
    ON resilience_governance_offline_trust_verification_receipts (
        trusted_root_fingerprint,
        imported_at DESC
    );
