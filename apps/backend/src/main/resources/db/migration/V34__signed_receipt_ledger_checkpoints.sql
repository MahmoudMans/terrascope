CREATE TABLE resilience_governance_assurance_receipt_checkpoints (
    id UUID PRIMARY KEY,

    checkpoint_number INTEGER NOT NULL,

    covered_ledger_number INTEGER NOT NULL,

    covered_ledger_hash VARCHAR(64) NOT NULL,

    entry_count INTEGER NOT NULL,

    valid_receipt_count INTEGER NOT NULL,

    invalid_receipt_count INTEGER NOT NULL,

    key_id VARCHAR(120) NOT NULL
        REFERENCES resilience_governance_trust_anchors(key_id)
        ON DELETE RESTRICT,

    algorithm VARCHAR(20) NOT NULL,

    public_key_fingerprint VARCHAR(64) NOT NULL,

    checkpoint_version VARCHAR(100) NOT NULL,

    signature_base64 TEXT NOT NULL,

    previous_checkpoint_hash VARCHAR(64),

    checkpoint_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT fk_receipt_checkpoint_ledger
        FOREIGN KEY (
            covered_ledger_number
        )
        REFERENCES resilience_governance_assurance_receipt_ledger(
            ledger_number
        )
        ON DELETE RESTRICT,

    CONSTRAINT uq_receipt_checkpoint_number
        UNIQUE (
            checkpoint_number
        ),

    CONSTRAINT uq_receipt_checkpoint_ledger_key
        UNIQUE (
            covered_ledger_number,
            key_id
        ),

    CONSTRAINT ck_receipt_checkpoint_number
        CHECK (
            checkpoint_number >= 1
        ),

    CONSTRAINT ck_receipt_checkpoint_covered_ledger
        CHECK (
            covered_ledger_number >= 1
        ),

    CONSTRAINT ck_receipt_checkpoint_counts
        CHECK (
            entry_count >= 1

            AND valid_receipt_count >= 0

            AND invalid_receipt_count >= 0

            AND valid_receipt_count
                + invalid_receipt_count
                = entry_count
        ),

    CONSTRAINT ck_receipt_checkpoint_algorithm
        CHECK (
            algorithm = 'ED25519'
        ),

    CONSTRAINT ck_receipt_checkpoint_ledger_hash
        CHECK (
            covered_ledger_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_receipt_checkpoint_public_fingerprint
        CHECK (
            public_key_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_receipt_checkpoint_previous_hash
        CHECK (
            previous_checkpoint_hash IS NULL
            OR previous_checkpoint_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_receipt_checkpoint_hash
        CHECK (
            checkpoint_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_receipt_checkpoint_signature
        CHECK (
            CHAR_LENGTH(
                BTRIM(
                    signature_base64
                )
            ) >= 1
        )
);

CREATE INDEX idx_receipt_checkpoint_covered_ledger
    ON resilience_governance_assurance_receipt_checkpoints (
        covered_ledger_number DESC
    );

CREATE INDEX idx_receipt_checkpoint_key
    ON resilience_governance_assurance_receipt_checkpoints (
        key_id,
        created_at DESC
    );

CREATE INDEX idx_receipt_checkpoint_hash
    ON resilience_governance_assurance_receipt_checkpoints (
        checkpoint_hash
    );
