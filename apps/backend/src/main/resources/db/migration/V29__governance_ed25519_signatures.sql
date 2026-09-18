CREATE TABLE resilience_governance_trust_anchors (
    key_id VARCHAR(120) PRIMARY KEY,

    algorithm VARCHAR(30) NOT NULL,

    public_key_x509_base64 TEXT NOT NULL,

    public_key_fingerprint VARCHAR(64) NOT NULL,

    status VARCHAR(20)
        NOT NULL
        DEFAULT 'ACTIVE',

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    last_used_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    retired_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_governance_trust_anchor_fingerprint
        UNIQUE (
            public_key_fingerprint
        ),

    CONSTRAINT ck_governance_trust_anchor_algorithm
        CHECK (
            algorithm = 'ED25519'
        ),

    CONSTRAINT ck_governance_trust_anchor_status
        CHECK (
            status IN (
                'ACTIVE',
                'RETIRED'
            )
        ),

    CONSTRAINT ck_governance_trust_anchor_fingerprint
        CHECK (
            public_key_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_trust_anchor_public_key
        CHECK (
            CHAR_LENGTH(
                BTRIM(
                    public_key_x509_base64
                )
            ) >= 1
        )
);

CREATE TABLE resilience_governance_closure_signatures (
    id UUID PRIMARY KEY,

    certificate_id UUID NOT NULL
        REFERENCES resilience_governance_closure_certificates(id)
        ON DELETE CASCADE,

    certificate_number INTEGER NOT NULL,

    key_id VARCHAR(120) NOT NULL
        REFERENCES resilience_governance_trust_anchors(key_id)
        ON DELETE RESTRICT,

    algorithm VARCHAR(30) NOT NULL,

    public_key_fingerprint VARCHAR(64) NOT NULL,

    signature_version VARCHAR(100) NOT NULL,

    certificate_hash VARCHAR(64) NOT NULL,

    dossier_hash VARCHAR(64) NOT NULL,

    signature_base64 TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_governance_signature_certificate
        UNIQUE (
            certificate_id
        ),

    CONSTRAINT ck_governance_signature_certificate_number
        CHECK (
            certificate_number >= 1
        ),

    CONSTRAINT ck_governance_signature_algorithm
        CHECK (
            algorithm = 'ED25519'
        ),

    CONSTRAINT ck_governance_signature_public_fingerprint
        CHECK (
            public_key_fingerprint
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_signature_certificate_hash
        CHECK (
            certificate_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_signature_dossier_hash
        CHECK (
            dossier_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_signature_value
        CHECK (
            CHAR_LENGTH(
                BTRIM(
                    signature_base64
                )
            ) >= 1
        )
);

CREATE INDEX idx_governance_signature_certificate_number
    ON resilience_governance_closure_signatures (
        certificate_number
    );

CREATE INDEX idx_governance_signature_key
    ON resilience_governance_closure_signatures (
        key_id,
        created_at DESC
    );

CREATE INDEX idx_governance_signature_fingerprint
    ON resilience_governance_closure_signatures (
        public_key_fingerprint
    );
