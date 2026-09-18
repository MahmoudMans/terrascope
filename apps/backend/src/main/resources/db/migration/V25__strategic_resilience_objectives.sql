CREATE TABLE resilience_portfolio_objectives (
    id UUID PRIMARY KEY,

    portfolio_review_id UUID NOT NULL
        REFERENCES resilience_portfolio_reviews(id)
        ON DELETE CASCADE,

    portfolio_review_number INTEGER NOT NULL,

    title VARCHAR(300) NOT NULL,

    description VARCHAR(2500)
        NOT NULL
        DEFAULT '',

    priority VARCHAR(30) NOT NULL,

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'PLANNED',

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    target_at TIMESTAMP WITH TIME ZONE,

    progress_pct INTEGER
        NOT NULL
        DEFAULT 0,

    success_metric VARCHAR(1200) NOT NULL,

    completion_evidence VARCHAR(3000)
        NOT NULL
        DEFAULT '',

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_resilience_portfolio_objective_title
        CHECK (
            CHAR_LENGTH(
                BTRIM(title)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_resilience_portfolio_objective_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_resilience_portfolio_objective_status
        CHECK (
            status IN (
                'PLANNED',
                'IN_PROGRESS',
                'BLOCKED',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_resilience_portfolio_objective_progress
        CHECK (
            progress_pct
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_resilience_portfolio_objective_completion
        CHECK (
            (
                status = 'COMPLETED'
                AND progress_pct = 100
                AND completed_at IS NOT NULL
                AND CHAR_LENGTH(
                    BTRIM(
                        completion_evidence
                    )
                ) >= 1
            )
            OR
            (
                status <> 'COMPLETED'
                AND completed_at IS NULL
            )
        )
);

CREATE TABLE resilience_portfolio_objective_events (
    id UUID PRIMARY KEY,

    objective_id UUID NOT NULL
        REFERENCES resilience_portfolio_objectives(id)
        ON DELETE CASCADE,

    event_type VARCHAR(50) NOT NULL,

    detail VARCHAR(2000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT ck_resilience_portfolio_objective_event_type
        CHECK (
            event_type IN (
                'OBJECTIVE_CREATED',
                'OBJECTIVE_STATUS_CHANGED',
                'OBJECTIVE_OWNER_CHANGED',
                'OBJECTIVE_TARGET_CHANGED',
                'OBJECTIVE_PROGRESS_CHANGED',
                'OBJECTIVE_COMPLETED'
            )
        )
);

CREATE INDEX idx_resilience_portfolio_objective_review
    ON resilience_portfolio_objectives (
        portfolio_review_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_resilience_portfolio_objective_status
    ON resilience_portfolio_objectives (
        status,
        priority,
        updated_at DESC
    );

CREATE INDEX idx_resilience_portfolio_objective_target
    ON resilience_portfolio_objectives (
        target_at
    )
    WHERE target_at IS NOT NULL
      AND status IN (
          'PLANNED',
          'IN_PROGRESS',
          'BLOCKED'
      );

CREATE INDEX idx_resilience_portfolio_objective_event_history
    ON resilience_portfolio_objective_events (
        objective_id,
        created_at DESC
    );
