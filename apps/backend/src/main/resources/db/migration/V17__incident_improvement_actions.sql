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
                'ACTION_DELETED',

                'RESOURCE_REQUESTED',
                'RESOURCE_ALLOCATION_CHANGED',
                'RESOURCE_PRIORITY_CHANGED',
                'RESOURCE_OWNER_CHANGED',
                'RESOURCE_CANCELLED',
                'RESOURCE_DELETED',

                'EVACUATION_ZONE_CREATED',
                'EVACUATION_PROGRESS_CHANGED',
                'EVACUATION_STATUS_CHANGED',
                'EVACUATION_OWNER_CHANGED',
                'EVACUATION_ZONE_DELETED',

                'SHELTER_CREATED',
                'SHELTER_OCCUPANCY_CHANGED',
                'SHELTER_STATUS_CHANGED',
                'SHELTER_OWNER_CHANGED',
                'SHELTER_DELETED',

                'LIFELINE_ASSET_CREATED',
                'LIFELINE_STATUS_CHANGED',
                'LIFELINE_IMPACT_CHANGED',
                'LIFELINE_OWNER_CHANGED',
                'LIFELINE_ETA_CHANGED',
                'LIFELINE_ASSET_DELETED',

                'DAMAGE_ASSESSMENT_CREATED',
                'DAMAGE_VERIFIED',
                'DAMAGE_SEVERITY_CHANGED',
                'DAMAGE_PROGRESS_CHANGED',
                'DAMAGE_OWNER_CHANGED',
                'DAMAGE_ETA_CHANGED',
                'DAMAGE_ASSESSMENT_DELETED',

                'COMMAND_SNAPSHOT_CREATED',
                'SITREP_CREATED',
                'AFTER_ACTION_REVIEW_CREATED',

                'IMPROVEMENT_ACTION_CREATED',
                'IMPROVEMENT_STATUS_CHANGED',
                'IMPROVEMENT_PRIORITY_CHANGED',
                'IMPROVEMENT_OWNER_CHANGED',
                'IMPROVEMENT_DUE_CHANGED',
                'IMPROVEMENT_ACTION_DELETED'
            )
        );

CREATE TABLE incident_improvement_actions (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    after_action_review_id UUID NOT NULL
        REFERENCES incident_after_action_reviews(id)
        ON DELETE CASCADE,

    category VARCHAR(40) NOT NULL,

    title VARCHAR(300) NOT NULL,

    description VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    priority VARCHAR(30) NOT NULL,

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'OPEN',

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

    CONSTRAINT ck_improvement_category
        CHECK (
            category IN (
                'PEOPLE',
                'PROCESS',
                'TECHNOLOGY',
                'COMMUNICATIONS',
                'LOGISTICS',
                'INFRASTRUCTURE',
                'OTHER'
            )
        ),

    CONSTRAINT ck_improvement_title
        CHECK (
            CHAR_LENGTH(
                BTRIM(title)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_improvement_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_improvement_status
        CHECK (
            status IN (
                'OPEN',
                'IN_PROGRESS',
                'COMPLETED',
                'DISMISSED'
            )
        )
);

CREATE INDEX idx_improvement_incident
    ON incident_improvement_actions (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_improvement_review
    ON incident_improvement_actions (
        after_action_review_id,
        updated_at DESC
    );

CREATE INDEX idx_improvement_priority
    ON incident_improvement_actions (
        priority,
        status,
        updated_at DESC
    );

CREATE INDEX idx_improvement_due
    ON incident_improvement_actions (
        due_at
    )
    WHERE due_at IS NOT NULL
      AND status IN (
          'OPEN',
          'IN_PROGRESS'
      );
