CREATE TABLE resilience_portfolio_snapshots (
    id UUID PRIMARY KEY,

    snapshot_number INTEGER NOT NULL,

    score INTEGER NOT NULL,

    band VARCHAR(40) NOT NULL,

    total_incident_count INTEGER NOT NULL,

    open_incident_count INTEGER NOT NULL,

    closed_incident_count INTEGER NOT NULL,

    certified_incident_count INTEGER NOT NULL,

    evidence_packaged_incident_count INTEGER NOT NULL,

    audit_ledger_incident_count INTEGER NOT NULL,

    audit_verified_incident_count INTEGER NOT NULL,

    completed_improvement_count INTEGER NOT NULL,

    validated_improvement_count INTEGER NOT NULL,

    untested_improvement_count INTEGER NOT NULL,

    needs_revalidation_count INTEGER NOT NULL,

    failed_improvement_count INTEGER NOT NULL,

    open_corrective_action_count INTEGER NOT NULL,

    total_exercise_count INTEGER NOT NULL,

    completed_exercise_count INTEGER NOT NULL,

    passed_exercise_count INTEGER NOT NULL,

    average_latest_readiness_score DOUBLE PRECISION NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_resilience_portfolio_snapshot_number
        UNIQUE (
            snapshot_number
        ),

    CONSTRAINT ck_resilience_portfolio_score
        CHECK (
            score
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_resilience_portfolio_band
        CHECK (
            band IN (
                'FOUNDATIONAL',
                'DEVELOPING',
                'ASSURED',
                'HIGH_ASSURANCE'
            )
        ),

    CONSTRAINT ck_resilience_portfolio_counts
        CHECK (
            total_incident_count >= 0
            AND open_incident_count >= 0
            AND closed_incident_count >= 0

            AND certified_incident_count >= 0
            AND evidence_packaged_incident_count >= 0
            AND audit_ledger_incident_count >= 0
            AND audit_verified_incident_count >= 0

            AND completed_improvement_count >= 0
            AND validated_improvement_count >= 0
            AND untested_improvement_count >= 0
            AND needs_revalidation_count >= 0
            AND failed_improvement_count >= 0

            AND open_corrective_action_count >= 0

            AND total_exercise_count >= 0
            AND completed_exercise_count >= 0
            AND passed_exercise_count >= 0
        ),

    CONSTRAINT ck_resilience_portfolio_readiness
        CHECK (
            average_latest_readiness_score
            BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_resilience_portfolio_snapshot_created
    ON resilience_portfolio_snapshots (
        created_at DESC
    );

CREATE INDEX idx_resilience_portfolio_snapshot_score
    ON resilience_portfolio_snapshots (
        score DESC,
        created_at DESC
    );
