CREATE TABLE resilience_governance_assurance_verification_receipts (
    id UUID PRIMARY KEY,

    receipt_number INTEGER NOT NULL,

    source_bundle_id UUID,

    source_bundle_number INTEGER,

    envelope_version VARCHAR(100),

    bundle_version VARCHAR(100),

    envelope_hash VARCHAR(64) NOT NULL,

    bundle_hash VARCHAR(64),

    verification_result VARCHAR(20) NOT NULL,

    hash_valid BOOLEAN NOT NULL,

    canonical_payload_valid BOOLEAN NOT NULL,

    portable_verification_valid BOOLEAN NOT NULL,

    signature_count INTEGER NOT NULL,

    verified_signature_count INTEGER NOT NULL,

    trust_anchor_count INTEGER NOT NULL,

    retired_anchor_count INTEGER NOT NULL,

    lifecycle_event_count INTEGER NOT NULL,

    message VARCHAR(2000) NOT NULL,

    verified_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_governance_assurance_verification_receipt_number
        UNIQUE (
            receipt_number
        ),

    CONSTRAINT ck_governance_assurance_verification_receipt_number
        CHECK (
            receipt_number >= 1
        ),

    CONSTRAINT ck_governance_assurance_verification_source_number
        CHECK (
            source_bundle_number IS NULL
            OR source_bundle_number >= 1
        ),

    CONSTRAINT ck_governance_assurance_verification_envelope_hash
        CHECK (
            envelope_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_assurance_verification_bundle_hash
        CHECK (
            bundle_hash IS NULL
            OR bundle_hash ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_assurance_verification_result
        CHECK (
            verification_result IN (
                'VALID',
                'INVALID'
            )
        ),

    CONSTRAINT ck_governance_assurance_verification_counts
        CHECK (
            signature_count >= 0

            AND verified_signature_count >= 0

            AND verified_signature_count
                <= signature_count

            AND trust_anchor_count >= 0

            AND retired_anchor_count >= 0

            AND retired_anchor_count
                <= trust_anchor_count

            AND lifecycle_event_count >= 0
        ),

    CONSTRAINT ck_governance_assurance_valid_receipt
        CHECK (
            verification_result = 'INVALID'

            OR (
                source_bundle_id IS NOT NULL

                AND source_bundle_number IS NOT NULL

                AND envelope_version IS NOT NULL

                AND bundle_version IS NOT NULL

                AND bundle_hash IS NOT NULL

                AND hash_valid = TRUE

                AND canonical_payload_valid = TRUE

                AND portable_verification_valid = TRUE

                AND signature_count >= 1

                AND verified_signature_count
                    = signature_count

                AND trust_anchor_count >= 1
            )
        )
);

CREATE INDEX idx_governance_assurance_verification_result
    ON resilience_governance_assurance_verification_receipts (
        verification_result,
        verified_at DESC
    );

CREATE INDEX idx_governance_assurance_verification_bundle_hash
    ON resilience_governance_assurance_verification_receipts (
        bundle_hash
    )
    WHERE bundle_hash IS NOT NULL;

CREATE INDEX idx_governance_assurance_verification_envelope_hash
    ON resilience_governance_assurance_verification_receipts (
        envelope_hash
    );
