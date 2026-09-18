CREATE TABLE resilience_governance_trust_anchor_transitions (
    id UUID PRIMARY KEY,

    transition_number INTEGER NOT NULL,

    outgoing_key_id VARCHAR(120) NOT NULL
        REFERENCES resilience_governance_trust_anchors(key_id)
        ON DELETE RESTRICT,

    outgoing_public_key_fingerprint VARCHAR(64) NOT NULL,

    replacement_key_id VARCHAR(120) NOT NULL
        REFERENCES resilience_governance_trust_anchors(key_id)
        ON DELETE RESTRICT,

    replacement_public_key_fingerprint VARCHAR(64) NOT NULL,

    algorithm VARCHAR(20) NOT NULL,

    reason VARCHAR(1000) NOT NULL,

    transition_version VARCHAR(100) NOT NULL,

    signature_base64 TEXT NOT NULL,

    previous_transition_hash VARCHAR(64),

    transition_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL,

    CONSTRAINT uq_governance_trust_transition_number
        UNIQUE (
            transition_number
        ),

    CONSTRAINT uq_governance_trust_transition_outgoing_key
        UNIQUE (
            outgoing_key_id
        ),

    CONSTRAINT ck_governance_trust_transition_number
        CHECK (
            transition_number >= 1
        ),

    CONSTRAINT ck_governance_trust_transition_distinct_keys
        CHECK (
            outgoing_key_id
            <> replacement_key_id
        ),

    CONSTRAINT ck_governance_trust_transition_algorithm
        CHECK (
            algorithm = 'ED25519'
        ),

    CONSTRAINT ck_governance_trust_transition_reason
        CHECK (
            CHAR_LENGTH(
                BTRIM(
                    reason
                )
            ) >= 1
        ),

    CONSTRAINT ck_governance_trust_transition_outgoing_fingerprint
        CHECK (
            outgoing_public_key_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_trust_transition_replacement_fingerprint
        CHECK (
            replacement_public_key_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_trust_transition_previous_hash
        CHECK (
            previous_transition_hash IS NULL
            OR previous_transition_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_trust_transition_hash
        CHECK (
            transition_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_trust_transition_signature
        CHECK (
            CHAR_LENGTH(
                BTRIM(
                    signature_base64
                )
            ) >= 1
        )
);

CREATE INDEX idx_governance_trust_transition_replacement
    ON resilience_governance_trust_anchor_transitions (
        replacement_key_id,
        transition_number DESC
    );

CREATE INDEX idx_governance_trust_transition_hash
    ON resilience_governance_trust_anchor_transitions (
        transition_hash
    );

CREATE INDEX idx_governance_trust_transition_created
    ON resilience_governance_trust_anchor_transitions (
        created_at DESC
    );
