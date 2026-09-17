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

                'AFTER_ACTION_REVIEW_CREATED'
            )
        );

CREATE TABLE incident_after_action_reviews (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    review_number INTEGER NOT NULL,

    incident_title VARCHAR(400) NOT NULL,

    hazard_type VARCHAR(40) NOT NULL,

    incident_severity VARCHAR(30) NOT NULL,

    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,

    closed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    duration_minutes BIGINT NOT NULL,

    closure_status VARCHAR(30) NOT NULL,

    closure_ready BOOLEAN NOT NULL,

    final_command_score INTEGER NOT NULL,

    final_command_band VARCHAR(30) NOT NULL,

    peak_command_score INTEGER NOT NULL,

    sitrep_count INTEGER NOT NULL,

    timeline_event_count INTEGER NOT NULL,

    action_count INTEGER NOT NULL,

    resource_request_count INTEGER NOT NULL,

    evacuation_zone_count INTEGER NOT NULL,

    shelter_count INTEGER NOT NULL,

    lifeline_asset_count INTEGER NOT NULL,

    damage_assessment_count INTEGER NOT NULL,

    unresolved_item_count INTEGER NOT NULL,

    executive_summary VARCHAR(5000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_after_action_review_number
        UNIQUE (
            incident_id,
            review_number
        ),

    CONSTRAINT ck_after_action_review_number
        CHECK (
            review_number >= 1
        ),

    CONSTRAINT ck_after_action_severity
        CHECK (
            incident_severity IN (
                'LOW',
                'MODERATE',
                'HIGH',
                'SEVERE',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_after_action_closure_status
        CHECK (
            closure_status IN (
                'READY',
                'BLOCKED'
            )
        ),

    CONSTRAINT ck_after_action_final_score
        CHECK (
            final_command_score
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_after_action_peak_score
        CHECK (
            peak_command_score
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_after_action_command_band
        CHECK (
            final_command_band IN (
                'STABLE',
                'GUARDED',
                'ELEVATED',
                'SEVERE',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_after_action_nonnegative
        CHECK (
            duration_minutes >= 0
            AND sitrep_count >= 0
            AND timeline_event_count >= 0
            AND action_count >= 0
            AND resource_request_count >= 0
            AND evacuation_zone_count >= 0
            AND shelter_count >= 0
            AND lifeline_asset_count >= 0
            AND damage_assessment_count >= 0
            AND unresolved_item_count >= 0
        )
);

CREATE INDEX idx_after_action_incident_history
    ON incident_after_action_reviews (
        incident_id,
        review_number DESC
    );

CREATE INDEX idx_after_action_readiness
    ON incident_after_action_reviews (
        closure_status,
        peak_command_score DESC,
        created_at DESC
    );
