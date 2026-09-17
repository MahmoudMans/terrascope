CREATE TABLE resilience_portfolio_reviews (
    id UUID PRIMARY KEY,

    review_number INTEGER NOT NULL,

    current_snapshot_id UUID NOT NULL
        REFERENCES resilience_portfolio_snapshots(id)
        ON DELETE CASCADE,

    current_snapshot_number INTEGER NOT NULL,

    previous_snapshot_id UUID
        REFERENCES resilience_portfolio_snapshots(id)
        ON DELETE SET NULL,

    previous_snapshot_number INTEGER,

    trend VARCHAR(30) NOT NULL,

    current_score INTEGER NOT NULL,

    previous_score INTEGER,

    score_delta INTEGER NOT NULL,

    current_assurance_debt INTEGER NOT NULL,

    previous_assurance_debt INTEGER,

    assurance_debt_delta INTEGER NOT NULL,

    certified_incident_delta INTEGER NOT NULL,

    validated_improvement_delta INTEGER NOT NULL,

    audit_verified_incident_delta INTEGER NOT NULL,

    executive_summary VARCHAR(5000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_resilience_portfolio_review_number
        UNIQUE (
            review_number
        ),

    CONSTRAINT uq_resilience_portfolio_current_snapshot
        UNIQUE (
            current_snapshot_id
        ),

    CONSTRAINT ck_resilience_portfolio_review_number
        CHECK (
            review_number >= 1
        ),

    CONSTRAINT ck_resilience_portfolio_review_trend
        CHECK (
            trend IN (
                'BASELINE',
                'IMPROVING',
                'STABLE',
                'DECLINING'
            )
        ),

    CONSTRAINT ck_resilience_portfolio_review_scores
        CHECK (
            current_score BETWEEN 0 AND 100
            AND (
                previous_score IS NULL
                OR previous_score BETWEEN 0 AND 100
            )
        ),

    CONSTRAINT ck_resilience_portfolio_review_debt
        CHECK (
            current_assurance_debt >= 0
            AND (
                previous_assurance_debt IS NULL
                OR previous_assurance_debt >= 0
            )
        )
);

CREATE INDEX idx_resilience_portfolio_review_created
    ON resilience_portfolio_reviews (
        created_at DESC
    );

CREATE INDEX idx_resilience_portfolio_review_trend
    ON resilience_portfolio_reviews (
        trend,
        created_at DESC
    );
