ALTER TABLE incident_timeline_events
    DROP CONSTRAINT ck_incident_timeline_type;

ALTER TABLE incident_timeline_events
    ADD CONSTRAINT ck_incident_timeline_type
        CHECK (
            event_type IN (
                'INCIDENT_OPENED',
                'STATUS_CHANGED',
                'SEVERITY_CHANGED',
                'OWNER_CHANGED',
                'NOTE_ADDED',
                'ACTION_CREATED',
                'ACTION_STATUS_CHANGED',
                'ACTION_PRIORITY_CHANGED',
                'ACTION_OWNER_CHANGED',
                'ACTION_DUE_CHANGED',
                'ACTION_DELETED'
            )
        );

CREATE TABLE incident_action_items (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    title VARCHAR(300) NOT NULL,

    description VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    priority VARCHAR(30)
        NOT NULL,

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'TODO',

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    due_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_incident_action_title
        CHECK (
            CHAR_LENGTH(
                BTRIM(title)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_incident_action_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_incident_action_status
        CHECK (
            status IN (
                'TODO',
                'IN_PROGRESS',
                'BLOCKED',
                'DONE'
            )
        )
);

CREATE INDEX idx_incident_actions_incident
    ON incident_action_items (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_incident_actions_due
    ON incident_action_items (
        due_at
    )
    WHERE due_at IS NOT NULL;

CREATE INDEX idx_incident_actions_priority
    ON incident_action_items (
        priority,
        updated_at DESC
    );
