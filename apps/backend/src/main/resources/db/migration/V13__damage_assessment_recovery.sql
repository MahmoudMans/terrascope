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
                'DAMAGE_ASSESSMENT_DELETED'
            )
        );

CREATE TABLE incident_damage_assessments (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    category VARCHAR(40) NOT NULL,

    asset_name VARCHAR(300) NOT NULL,

    description VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    severity VARCHAR(30) NOT NULL,

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'REPORTED',

    affected_units INTEGER
        NOT NULL
        DEFAULT 0,

    estimated_loss_musd DOUBLE PRECISION
        NOT NULL
        DEFAULT 0,

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    recovery_eta TIMESTAMP WITH TIME ZONE,

    repair_progress_pct INTEGER
        NOT NULL
        DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    verified_at TIMESTAMP WITH TIME ZONE,

    restored_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_damage_category
        CHECK (
            category IN (
                'RESIDENTIAL',
                'COMMERCIAL',
                'PUBLIC_BUILDING',
                'ROAD',
                'BRIDGE',
                'UTILITY',
                'HEALTHCARE',
                'OTHER'
            )
        ),

    CONSTRAINT ck_damage_asset_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(asset_name)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_damage_severity
        CHECK (
            severity IN (
                'MINOR',
                'MODERATE',
                'MAJOR',
                'DESTROYED'
            )
        ),

    CONSTRAINT ck_damage_status
        CHECK (
            status IN (
                'REPORTED',
                'VERIFIED',
                'REPAIRING',
                'RESTORED'
            )
        ),

    CONSTRAINT ck_damage_affected_units
        CHECK (
            affected_units
            BETWEEN 0 AND 100000000
        ),

    CONSTRAINT ck_damage_loss
        CHECK (
            estimated_loss_musd >= 0
        ),

    CONSTRAINT ck_damage_progress
        CHECK (
            repair_progress_pct
            BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_damage_incident
    ON incident_damage_assessments (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_damage_severity
    ON incident_damage_assessments (
        severity,
        updated_at DESC
    );

CREATE INDEX idx_damage_recovery_backlog
    ON incident_damage_assessments (
        incident_id,
        severity,
        estimated_loss_musd DESC
    )
    WHERE status <> 'RESTORED';

CREATE INDEX idx_damage_recovery_eta
    ON incident_damage_assessments (
        recovery_eta
    )
    WHERE recovery_eta IS NOT NULL;
