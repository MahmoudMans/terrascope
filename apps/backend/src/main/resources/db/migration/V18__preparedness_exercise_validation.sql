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
                'IMPROVEMENT_ACTION_DELETED',

                'PREPAREDNESS_EXERCISE_CREATED',
                'PREPAREDNESS_EXERCISE_STATUS_CHANGED',
                'PREPAREDNESS_EXERCISE_OWNER_CHANGED',
                'PREPAREDNESS_EXERCISE_SCHEDULE_CHANGED',
                'PREPAREDNESS_EXERCISE_ASSESSED',
                'PREPAREDNESS_EXERCISE_DELETED'
            )
        );

CREATE TABLE incident_preparedness_exercises (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    improvement_action_id UUID NOT NULL
        REFERENCES incident_improvement_actions(id)
        ON DELETE CASCADE,

    exercise_type VARCHAR(40) NOT NULL,

    title VARCHAR(300) NOT NULL,

    description VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'PLANNED',

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    scheduled_at TIMESTAMP WITH TIME ZONE,

    performed_at TIMESTAMP WITH TIME ZONE,

    validation_result VARCHAR(30)
        NOT NULL
        DEFAULT 'NOT_ASSESSED',

    readiness_score INTEGER,

    evidence_summary VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    findings VARCHAR(3000)
        NOT NULL
        DEFAULT '',

    corrective_action VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_preparedness_exercise_type
        CHECK (
            exercise_type IN (
                'TABLETOP',
                'FUNCTIONAL',
                'FULL_SCALE',
                'TECHNICAL',
                'COMMUNICATIONS',
                'OTHER'
            )
        ),

    CONSTRAINT ck_preparedness_exercise_title
        CHECK (
            CHAR_LENGTH(
                BTRIM(title)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_preparedness_exercise_status
        CHECK (
            status IN (
                'PLANNED',
                'IN_PROGRESS',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_preparedness_validation_result
        CHECK (
            validation_result IN (
                'NOT_ASSESSED',
                'PASSED',
                'PARTIAL',
                'FAILED'
            )
        ),

    CONSTRAINT ck_preparedness_readiness_score
        CHECK (
            readiness_score IS NULL
            OR readiness_score
                BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_preparedness_assessment_consistency
        CHECK (
            (
                validation_result = 'NOT_ASSESSED'
                AND readiness_score IS NULL
            )
            OR
            (
                validation_result <> 'NOT_ASSESSED'
                AND readiness_score IS NOT NULL
            )
        )
);

CREATE INDEX idx_preparedness_incident
    ON incident_preparedness_exercises (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_preparedness_improvement
    ON incident_preparedness_exercises (
        improvement_action_id,
        updated_at DESC
    );

CREATE INDEX idx_preparedness_result
    ON incident_preparedness_exercises (
        validation_result,
        readiness_score DESC,
        updated_at DESC
    );

CREATE INDEX idx_preparedness_schedule
    ON incident_preparedness_exercises (
        scheduled_at
    )
    WHERE scheduled_at IS NOT NULL
      AND status IN (
          'PLANNED',
          'IN_PROGRESS'
      );
