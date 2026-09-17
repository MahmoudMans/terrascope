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
                'PREPAREDNESS_EXERCISE_DELETED',

                'PREPAREDNESS_CORRECTIVE_ACTION_CREATED',
                'PREPAREDNESS_CORRECTIVE_STATUS_CHANGED',
                'PREPAREDNESS_CORRECTIVE_OWNER_CHANGED',
                'PREPAREDNESS_CORRECTIVE_DUE_CHANGED',
                'PREPAREDNESS_CORRECTIVE_ACTION_DELETED',

                'RESILIENCE_CERTIFICATION_CREATED'
            )
        );

CREATE TABLE incident_resilience_certifications (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    certification_number INTEGER NOT NULL,

    status VARCHAR(30) NOT NULL,

    completed_improvement_count INTEGER NOT NULL,

    validated_count INTEGER NOT NULL,

    untested_count INTEGER NOT NULL,

    needs_revalidation_count INTEGER NOT NULL,

    failed_count INTEGER NOT NULL,

    open_corrective_action_count INTEGER NOT NULL,

    total_exercise_count INTEGER NOT NULL,

    passed_exercise_count INTEGER NOT NULL,

    average_latest_readiness_score DOUBLE PRECISION NOT NULL,

    after_action_review_count INTEGER NOT NULL,

    sitrep_count INTEGER NOT NULL,

    executive_summary VARCHAR(5000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_resilience_certification_number
        UNIQUE (
            incident_id,
            certification_number
        ),

    CONSTRAINT ck_resilience_certification_number
        CHECK (
            certification_number >= 1
        ),

    CONSTRAINT ck_resilience_certification_status
        CHECK (
            status = 'CERTIFIED'
        ),

    CONSTRAINT ck_resilience_certification_counts
        CHECK (
            completed_improvement_count >= 0
            AND validated_count >= 0
            AND untested_count >= 0
            AND needs_revalidation_count >= 0
            AND failed_count >= 0
            AND open_corrective_action_count >= 0
            AND total_exercise_count >= 0
            AND passed_exercise_count >= 0
            AND after_action_review_count >= 0
            AND sitrep_count >= 0
        ),

    CONSTRAINT ck_resilience_certification_score
        CHECK (
            average_latest_readiness_score
            BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_resilience_certification_incident
    ON incident_resilience_certifications (
        incident_id,
        certification_number DESC
    );

CREATE INDEX idx_resilience_certification_created
    ON incident_resilience_certifications (
        created_at DESC
    );
