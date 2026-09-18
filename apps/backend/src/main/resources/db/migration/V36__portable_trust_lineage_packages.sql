CREATE TABLE resilience_governance_trust_lineage_packages (
    id UUID PRIMARY KEY,

    package_number INTEGER NOT NULL,

    package_version VARCHAR(100) NOT NULL,

    transition_count INTEGER NOT NULL,

    trust_anchor_count INTEGER NOT NULL,

    first_transition_number INTEGER NOT NULL,

    last_transition_number INTEGER NOT NULL,

    lineage_head_hash VARCHAR(64) NOT NULL,

    payload_json TEXT NOT NULL,

    package_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_governance_trust_lineage_package_number
        UNIQUE (
            package_number
        ),

    CONSTRAINT uq_governance_trust_lineage_package_hash
        UNIQUE (
            package_hash
        ),

    CONSTRAINT ck_governance_trust_lineage_package_number
        CHECK (
            package_number >= 1
        ),

    CONSTRAINT ck_governance_trust_lineage_transition_count
        CHECK (
            transition_count >= 1
        ),

    CONSTRAINT ck_governance_trust_lineage_anchor_count
        CHECK (
            trust_anchor_count >= 2
        ),

    CONSTRAINT ck_governance_trust_lineage_transition_range
        CHECK (
            first_transition_number >= 1

            AND last_transition_number
                >= first_transition_number

            AND transition_count
                =
                last_transition_number
                - first_transition_number
                + 1
        ),

    CONSTRAINT ck_governance_trust_lineage_head_hash
        CHECK (
            lineage_head_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_trust_lineage_payload
        CHECK (
            CHAR_LENGTH(
                BTRIM(
                    payload_json
                )
            ) >= 2
        ),

    CONSTRAINT ck_governance_trust_lineage_package_hash
        CHECK (
            package_hash
            ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_governance_trust_lineage_package_created
    ON resilience_governance_trust_lineage_packages (
        created_at DESC
    );

CREATE INDEX idx_governance_trust_lineage_package_head
    ON resilience_governance_trust_lineage_packages (
        lineage_head_hash
    );
