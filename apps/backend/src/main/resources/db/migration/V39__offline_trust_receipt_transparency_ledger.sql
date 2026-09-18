CREATE TABLE resilience_governance_offline_trust_receipt_ledger (
    id UUID PRIMARY KEY,

    ledger_number INTEGER NOT NULL,

    receipt_id UUID NOT NULL,

    receipt_import_number INTEGER NOT NULL,

    verification_result VARCHAR(20) NOT NULL,

    receipt_hash VARCHAR(64) NOT NULL,

    receipt_payload_hash VARCHAR(64) NOT NULL,

    previous_ledger_hash VARCHAR(64),

    ledger_version VARCHAR(100) NOT NULL,

    ledger_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT fk_offline_trust_receipt_ledger_receipt
        FOREIGN KEY (
            receipt_id
        )
        REFERENCES resilience_governance_offline_trust_verification_receipts (
            id
        ),

    CONSTRAINT uq_offline_trust_receipt_ledger_number
        UNIQUE (
            ledger_number
        ),

    CONSTRAINT uq_offline_trust_receipt_ledger_receipt
        UNIQUE (
            receipt_id
        ),

    CONSTRAINT uq_offline_trust_receipt_ledger_hash
        UNIQUE (
            ledger_hash
        ),

    CONSTRAINT ck_offline_trust_receipt_ledger_number
        CHECK (
            ledger_number >= 1
        ),

    CONSTRAINT ck_offline_trust_receipt_import_number
        CHECK (
            receipt_import_number >= 1
        ),

    CONSTRAINT ck_offline_trust_receipt_ledger_result
        CHECK (
            verification_result IN (
                'VALID',
                'INVALID'
            )
        ),

    CONSTRAINT ck_offline_trust_receipt_hash
        CHECK (
            receipt_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_payload_hash
        CHECK (
            receipt_payload_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_previous_hash
        CHECK (
            previous_ledger_hash IS NULL

            OR previous_ledger_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_offline_trust_receipt_ledger_hash
        CHECK (
            ledger_hash
            ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_offline_trust_receipt_ledger_created
    ON resilience_governance_offline_trust_receipt_ledger (
        created_at DESC
    );

CREATE INDEX idx_offline_trust_receipt_ledger_receipt_import
    ON resilience_governance_offline_trust_receipt_ledger (
        receipt_import_number
    );
