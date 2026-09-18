CREATE TABLE resilience_benefit_realization_snapshots (
    id UUID PRIMARY KEY,

    snapshot_number INTEGER NOT NULL,

    band VARCHAR(30) NOT NULL,

    closure_ready BOOLEAN NOT NULL,

    total_objective_count INTEGER NOT NULL,

    planned_objective_count INTEGER NOT NULL,

    in_progress_objective_count INTEGER NOT NULL,

    blocked_objective_count INTEGER NOT NULL,

    completed_objective_count INTEGER NOT NULL,

    cancelled_objective_count INTEGER NOT NULL,

    validated_objective_count INTEGER NOT NULL,

    unvalidated_completed_objective_count INTEGER NOT NULL,

    achieved_count INTEGER NOT NULL,

    partial_count INTEGER NOT NULL,

    not_achieved_count INTEGER NOT NULL,

    validation_coverage_pct DOUBLE PRECISION NOT NULL,

    realized_benefit_rate_pct DOUBLE PRECISION NOT NULL,

    average_score_delta DOUBLE PRECISION NOT NULL,

    average_assurance_debt_delta DOUBLE PRECISION NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_benefit_realization_snapshot_number
        UNIQUE (
            snapshot_number
        ),

    CONSTRAINT ck_benefit_realization_snapshot_number
        CHECK (
            snapshot_number >= 1
        ),

    CONSTRAINT ck_benefit_realization_band
        CHECK (
            band IN (
                'UNPROVEN',
                'EMERGING',
                'REALIZING',
                'REALIZED'
            )
        ),

    CONSTRAINT ck_benefit_realization_counts
        CHECK (
            total_objective_count >= 0

            AND planned_objective_count >= 0
            AND in_progress_objective_count >= 0
            AND blocked_objective_count >= 0
            AND completed_objective_count >= 0
            AND cancelled_objective_count >= 0

            AND validated_objective_count >= 0
            AND unvalidated_completed_objective_count >= 0

            AND achieved_count >= 0
            AND partial_count >= 0
            AND not_achieved_count >= 0
        ),

    CONSTRAINT ck_benefit_realization_percentages
        CHECK (
            validation_coverage_pct
            BETWEEN 0 AND 100

            AND realized_benefit_rate_pct
            BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_benefit_realization_created
    ON resilience_benefit_realization_snapshots (
        created_at DESC
    );

CREATE INDEX idx_benefit_realization_band
    ON resilience_benefit_realization_snapshots (
        band,
        created_at DESC
    );

CREATE INDEX idx_benefit_realization_closure
    ON resilience_benefit_realization_snapshots (
        closure_ready,
        created_at DESC
    );
