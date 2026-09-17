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

                'SITREP_CREATED'
            )
        );

CREATE TABLE incident_situation_reports (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    report_number INTEGER NOT NULL,

    incident_title VARCHAR(400) NOT NULL,

    incident_status VARCHAR(30) NOT NULL,

    incident_severity VARCHAR(30) NOT NULL,

    command_score INTEGER NOT NULL,

    command_band VARCHAR(30) NOT NULL,

    executive_summary VARCHAR(4000) NOT NULL,

    priority_one VARCHAR(500),

    priority_two VARCHAR(500),

    priority_three VARCHAR(500),

    open_action_count INTEGER NOT NULL,

    overdue_action_count INTEGER NOT NULL,

    blocked_action_count INTEGER NOT NULL,

    resource_shortage_count INTEGER NOT NULL,

    evacuation_remaining BIGINT NOT NULL,

    shelter_remaining_capacity BIGINT NOT NULL,

    lifeline_disrupted_count INTEGER NOT NULL,

    critical_lifeline_disruption_count INTEGER NOT NULL,

    lifeline_affected_population BIGINT NOT NULL,

    unresolved_damage_loss_musd DOUBLE PRECISION NOT NULL,

    critical_damage_count INTEGER NOT NULL,

    average_repair_progress_pct DOUBLE PRECISION NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_incident_sitrep_number
        UNIQUE (
            incident_id,
            report_number
        ),

    CONSTRAINT ck_sitrep_report_number
        CHECK (
            report_number >= 1
        ),

    CONSTRAINT ck_sitrep_incident_status
        CHECK (
            incident_status IN (
                'OPEN',
                'MONITORING',
                'RESPONDING',
                'STABILIZED',
                'CLOSED'
            )
        ),

    CONSTRAINT ck_sitrep_incident_severity
        CHECK (
            incident_severity IN (
                'LOW',
                'MODERATE',
                'HIGH',
                'SEVERE',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_sitrep_command_score
        CHECK (
            command_score
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_sitrep_command_band
        CHECK (
            command_band IN (
                'STABLE',
                'GUARDED',
                'ELEVATED',
                'SEVERE',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_sitrep_metrics_nonnegative
        CHECK (
            open_action_count >= 0
            AND overdue_action_count >= 0
            AND blocked_action_count >= 0
            AND resource_shortage_count >= 0
            AND evacuation_remaining >= 0
            AND shelter_remaining_capacity >= 0
            AND lifeline_disrupted_count >= 0
            AND critical_lifeline_disruption_count >= 0
            AND lifeline_affected_population >= 0
            AND unresolved_damage_loss_musd >= 0
            AND critical_damage_count >= 0
            AND average_repair_progress_pct
                BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_sitrep_incident_history
    ON incident_situation_reports (
        incident_id,
        report_number DESC
    );

CREATE INDEX idx_sitrep_command_pressure
    ON incident_situation_reports (
        command_band,
        command_score DESC,
        created_at DESC
    );
