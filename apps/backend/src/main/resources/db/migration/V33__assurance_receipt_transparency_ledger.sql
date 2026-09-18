CREATE TABLE resilience_governance_assurance_receipt_ledger (
    id UUID PRIMARY KEY,

    ledger_number INTEGER NOT NULL,

    receipt_id UUID NOT NULL
        REFERENCES resilience_governance_assurance_verification_receipts(id)
        ON DELETE RESTRICT,

    receipt_number INTEGER NOT NULL,

    verification_result VARCHAR(20) NOT NULL,

    envelope_hash VARCHAR(64) NOT NULL,

    bundle_hash VARCHAR(64),

    receipt_payload_hash VARCHAR(64) NOT NULL,

    previous_ledger_hash VARCHAR(64),

    ledger_version VARCHAR(100) NOT NULL,

    ledger_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_assurance_receipt_ledger_number
        UNIQUE (
            ledger_number
        ),

    CONSTRAINT uq_assurance_receipt_ledger_receipt
        UNIQUE (
            receipt_id
        ),

    CONSTRAINT ck_assurance_receipt_ledger_number
        CHECK (
            ledger_number >= 1
        ),

    CONSTRAINT ck_assurance_receipt_ledger_receipt_number
        CHECK (
            receipt_number >= 1
        ),

    CONSTRAINT ck_assurance_receipt_ledger_result
        CHECK (
            verification_result IN (
                'VALID',
                'INVALID'
            )
        ),

    CONSTRAINT ck_assurance_receipt_ledger_envelope_hash
        CHECK (
            envelope_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_assurance_receipt_ledger_bundle_hash
        CHECK (
            bundle_hash IS NULL
            OR bundle_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_assurance_receipt_payload_hash
        CHECK (
            receipt_payload_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_assurance_receipt_previous_ledger_hash
        CHECK (
            previous_ledger_hash IS NULL
            OR previous_ledger_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_assurance_receipt_ledger_hash
        CHECK (
            ledger_hash
            ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_assurance_receipt_ledger_created
    ON resilience_governance_assurance_receipt_ledger (
        created_at DESC
    );

CREATE INDEX idx_assurance_receipt_ledger_hash
    ON resilience_governance_assurance_receipt_ledger (
        ledger_hash
    );

CREATE INDEX idx_assurance_receipt_payload_hash
    ON resilience_governance_assurance_receipt_ledger (
        receipt_payload_hash
    );

CREATE INDEX idx_assurance_receipt_ledger_result
    ON resilience_governance_assurance_receipt_ledger (
        verification_result,
        created_at DESC
    );
