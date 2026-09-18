CREATE TABLE resilience_governance_assurance_bundles (
    id UUID PRIMARY KEY,

    bundle_number INTEGER NOT NULL,

    certificate_id UUID NOT NULL
        REFERENCES resilience_governance_closure_certificates(id)
        ON DELETE RESTRICT,

    certificate_number INTEGER NOT NULL,

    bundle_version VARCHAR(100) NOT NULL,

    bundle_hash VARCHAR(64) NOT NULL,

    signature_count INTEGER NOT NULL,

    trust_anchor_count INTEGER NOT NULL,

    retired_anchor_count INTEGER NOT NULL,

    lifecycle_event_count INTEGER NOT NULL,

    portable_verification_valid BOOLEAN NOT NULL,

    payload_json TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_governance_assurance_bundle_number
        UNIQUE (
            bundle_number
        ),

    CONSTRAINT ck_governance_assurance_bundle_number
        CHECK (
            bundle_number >= 1
        ),

    CONSTRAINT ck_governance_assurance_bundle_hash
        CHECK (
            bundle_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_assurance_bundle_counts
        CHECK (
            signature_count >= 1

            AND trust_anchor_count >= 1

            AND retired_anchor_count >= 0

            AND retired_anchor_count
                <= trust_anchor_count

            AND lifecycle_event_count >= 0
        ),

    CONSTRAINT ck_governance_assurance_bundle_verified
        CHECK (
            portable_verification_valid = TRUE
        ),

    CONSTRAINT ck_governance_assurance_bundle_payload
        CHECK (
            CHAR_LENGTH(
                BTRIM(
                    payload_json
                )
            ) >= 2
        )
);

CREATE INDEX idx_governance_assurance_bundle_certificate
    ON resilience_governance_assurance_bundles (
        certificate_id,
        bundle_number DESC
    );

CREATE INDEX idx_governance_assurance_bundle_hash
    ON resilience_governance_assurance_bundles (
        bundle_hash
    );

CREATE INDEX idx_governance_assurance_bundle_created
    ON resilience_governance_assurance_bundles (
        created_at DESC
    );
