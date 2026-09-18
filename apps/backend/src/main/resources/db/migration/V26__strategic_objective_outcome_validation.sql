ALTER TABLE resilience_portfolio_objective_events
    DROP CONSTRAINT ck_resilience_portfolio_objective_event_type;

ALTER TABLE resilience_portfolio_objective_events
    ADD CONSTRAINT ck_resilience_portfolio_objective_event_type
        CHECK (
            event_type IN (
                'OBJECTIVE_CREATED',
                'OBJECTIVE_STATUS_CHANGED',
                'OBJECTIVE_OWNER_CHANGED',
                'OBJECTIVE_TARGET_CHANGED',
                'OBJECTIVE_PROGRESS_CHANGED',
                'OBJECTIVE_COMPLETED',
                'OBJECTIVE_OUTCOME_VALIDATED'
            )
        );

CREATE TABLE resilience_portfolio_objective_validations (
    id UUID PRIMARY KEY,

    objective_id UUID NOT NULL
        REFERENCES resilience_portfolio_objectives(id)
        ON DELETE CASCADE,

    validation_number INTEGER NOT NULL,

    baseline_snapshot_id UUID NOT NULL
        REFERENCES resilience_portfolio_snapshots(id)
        ON DELETE CASCADE,

    baseline_snapshot_number INTEGER NOT NULL,

    validation_snapshot_id UUID NOT NULL
        REFERENCES resilience_portfolio_snapshots(id)
        ON DELETE CASCADE,

    validation_snapshot_number INTEGER NOT NULL,

    outcome VARCHAR(30) NOT NULL,

    baseline_score INTEGER NOT NULL,

    validation_score INTEGER NOT NULL,

    score_delta INTEGER NOT NULL,

    baseline_assurance_debt INTEGER NOT NULL,

    validation_assurance_debt INTEGER NOT NULL,

    assurance_debt_delta INTEGER NOT NULL,

    certified_incident_delta INTEGER NOT NULL,

    validated_improvement_delta INTEGER NOT NULL,

    audit_verified_incident_delta INTEGER NOT NULL,

    executive_summary VARCHAR(5000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_portfolio_objective_validation_number
        UNIQUE (
            objective_id,
            validation_number
        ),

    CONSTRAINT uq_portfolio_objective_validation_snapshot
        UNIQUE (
            validation_snapshot_id
        ),

    CONSTRAINT ck_portfolio_objective_validation_number
        CHECK (
            validation_number >= 1
        ),

    CONSTRAINT ck_portfolio_objective_validation_outcome
        CHECK (
            outcome IN (
                'ACHIEVED',
                'PARTIAL',
                'NOT_ACHIEVED'
            )
        ),

    CONSTRAINT ck_portfolio_objective_validation_scores
        CHECK (
            baseline_score BETWEEN 0 AND 100
            AND validation_score BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_portfolio_objective_validation_debt
        CHECK (
            baseline_assurance_debt >= 0
            AND validation_assurance_debt >= 0
        )
);

CREATE INDEX idx_portfolio_objective_validation_objective
    ON resilience_portfolio_objective_validations (
        objective_id,
        validation_number DESC
    );

CREATE INDEX idx_portfolio_objective_validation_outcome
    ON resilience_portfolio_objective_validations (
        outcome,
        created_at DESC
    );

CREATE INDEX idx_portfolio_objective_validation_created
    ON resilience_portfolio_objective_validations (
        created_at DESC
    );
