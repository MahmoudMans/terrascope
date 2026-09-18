CREATE TABLE resilience_governance_closure_certificates (
    id UUID PRIMARY KEY,

    certificate_number INTEGER NOT NULL,

    benefit_snapshot_id UUID NOT NULL
        REFERENCES resilience_benefit_realization_snapshots(id)
        ON DELETE RESTRICT,

    benefit_snapshot_number INTEGER NOT NULL,

    band VARCHAR(30) NOT NULL,

    closure_ready BOOLEAN NOT NULL,

    total_objective_count INTEGER NOT NULL,

    completed_objective_count INTEGER NOT NULL,

    validated_objective_count INTEGER NOT NULL,

    achieved_count INTEGER NOT NULL,

    validation_coverage_pct DOUBLE PRECISION NOT NULL,

    realized_benefit_rate_pct DOUBLE PRECISION NOT NULL,

    average_score_delta DOUBLE PRECISION NOT NULL,

    average_assurance_debt_delta DOUBLE PRECISION NOT NULL,

    previous_certificate_hash VARCHAR(64),

    dossier_version VARCHAR(80) NOT NULL,

    dossier_hash VARCHAR(64) NOT NULL,

    certificate_version VARCHAR(80) NOT NULL,

    certificate_hash VARCHAR(64) NOT NULL,

    executive_summary VARCHAR(5000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_governance_closure_certificate_number
        UNIQUE (
            certificate_number
        ),

    CONSTRAINT uq_governance_closure_benefit_snapshot
        UNIQUE (
            benefit_snapshot_id
        ),

    CONSTRAINT ck_governance_closure_certificate_number
        CHECK (
            certificate_number >= 1
        ),

    CONSTRAINT ck_governance_closure_band
        CHECK (
            band = 'REALIZED'
        ),

    CONSTRAINT ck_governance_closure_ready
        CHECK (
            closure_ready = TRUE
        ),

    CONSTRAINT ck_governance_closure_counts
        CHECK (
            total_objective_count >= 1

            AND completed_objective_count
                = total_objective_count

            AND validated_objective_count
                = completed_objective_count

            AND achieved_count
                = completed_objective_count
        ),

    CONSTRAINT ck_governance_closure_percentages
        CHECK (
            validation_coverage_pct = 100

            AND realized_benefit_rate_pct = 100
        ),

    CONSTRAINT ck_governance_closure_previous_hash
        CHECK (
            previous_certificate_hash IS NULL

            OR previous_certificate_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_closure_dossier_hash
        CHECK (
            dossier_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_governance_closure_certificate_hash
        CHECK (
            certificate_hash
            ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_governance_closure_certificate_created
    ON resilience_governance_closure_certificates (
        created_at DESC
    );

CREATE INDEX idx_governance_closure_certificate_hash
    ON resilience_governance_closure_certificates (
        certificate_hash
    );

CREATE INDEX idx_governance_closure_dossier_hash
    ON resilience_governance_closure_certificates (
        dossier_hash
    );
