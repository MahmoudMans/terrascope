CREATE TABLE activity_log (
    id UUID PRIMARY KEY,

    occurred_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    actor_id UUID,

    actor_username VARCHAR(64) NOT NULL,

    actor_display_name VARCHAR(120) NOT NULL,

    actor_role VARCHAR(16),

    http_method VARCHAR(8) NOT NULL,

    route VARCHAR(300) NOT NULL,

    action VARCHAR(200) NOT NULL,

    entity_type VARCHAR(80),

    entity_id VARCHAR(120),

    status_code INTEGER NOT NULL,

    CONSTRAINT ck_activity_log_actor_role
        CHECK (
            actor_role IS NULL
            OR actor_role IN (
                'VIEWER',
                'OPERATOR',
                'ADMIN'
            )
        )
);

CREATE INDEX idx_activity_log_occurred_at
    ON activity_log (
        occurred_at DESC,
        id DESC
    );

CREATE INDEX idx_activity_log_actor
    ON activity_log (
        actor_username,
        occurred_at DESC
    );
