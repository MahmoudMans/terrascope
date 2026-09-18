ALTER TABLE resilience_governance_trust_anchors
    ADD COLUMN superseded_by_key_id VARCHAR(120),

    ADD COLUMN retirement_reason VARCHAR(1000)
        NOT NULL
        DEFAULT '';

ALTER TABLE resilience_governance_trust_anchors
    ADD CONSTRAINT fk_governance_trust_anchor_replacement

        FOREIGN KEY (
            superseded_by_key_id
        )

        REFERENCES resilience_governance_trust_anchors(
            key_id
        )

        ON DELETE RESTRICT;

ALTER TABLE resilience_governance_trust_anchors
    ADD CONSTRAINT ck_governance_trust_anchor_lifecycle
        CHECK (
            (
                status = 'ACTIVE'

                AND retired_at IS NULL

                AND superseded_by_key_id IS NULL

                AND retirement_reason = ''
            )

            OR

            (
                status = 'RETIRED'

                AND retired_at IS NOT NULL

                AND CHAR_LENGTH(
                    BTRIM(
                        retirement_reason
                    )
                ) >= 1
            )
        );

ALTER TABLE resilience_governance_closure_signatures
    DROP CONSTRAINT uq_governance_signature_certificate;

ALTER TABLE resilience_governance_closure_signatures
    ADD CONSTRAINT uq_governance_signature_certificate_key
        UNIQUE (
            certificate_id,
            key_id
        );

CREATE TABLE resilience_governance_trust_anchor_events (
    id UUID PRIMARY KEY,

    key_id VARCHAR(120) NOT NULL
        REFERENCES resilience_governance_trust_anchors(key_id)
        ON DELETE CASCADE,

    event_type VARCHAR(40) NOT NULL,

    replacement_key_id VARCHAR(120)
        REFERENCES resilience_governance_trust_anchors(key_id)
        ON DELETE RESTRICT,

    detail VARCHAR(2000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT ck_governance_trust_anchor_event_type
        CHECK (
            event_type IN (
                'REGISTERED',
                'SIGNATURE_ISSUED',
                'RETIRED'
            )
        )
);

CREATE INDEX idx_governance_trust_anchor_event_key
    ON resilience_governance_trust_anchor_events (
        key_id,
        created_at DESC
    );

CREATE INDEX idx_governance_trust_anchor_event_replacement
    ON resilience_governance_trust_anchor_events (
        replacement_key_id,
        created_at DESC
    )
    WHERE replacement_key_id IS NOT NULL;

CREATE INDEX idx_governance_trust_anchor_status
    ON resilience_governance_trust_anchors (
        status,
        created_at DESC
    );
