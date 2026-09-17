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
                'LIFELINE_ASSET_DELETED'
            )
        );

CREATE TABLE incident_lifeline_assets (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    category VARCHAR(40) NOT NULL,

    name VARCHAR(300) NOT NULL,

    description VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    priority VARCHAR(30) NOT NULL,

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'OPERATIONAL',

    affected_population INTEGER
        NOT NULL
        DEFAULT 0,

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    restoration_eta TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    restored_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_lifeline_category
        CHECK (
            category IN (
                'POWER',
                'WATER',
                'HEALTHCARE',
                'TRANSPORT',
                'COMMUNICATIONS',
                'FUEL',
                'OTHER'
            )
        ),

    CONSTRAINT ck_lifeline_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(name)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_lifeline_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_lifeline_status
        CHECK (
            status IN (
                'OPERATIONAL',
                'DEGRADED',
                'OUTAGE',
                'RESTORED'
            )
        ),

    CONSTRAINT ck_lifeline_affected_population
        CHECK (
            affected_population
            BETWEEN 0 AND 100000000
        )
);

CREATE INDEX idx_lifeline_incident
    ON incident_lifeline_assets (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_lifeline_category
    ON incident_lifeline_assets (
        category,
        status,
        updated_at DESC
    );

CREATE INDEX idx_lifeline_disruption
    ON incident_lifeline_assets (
        incident_id,
        priority,
        affected_population DESC
    )
    WHERE status IN (
        'DEGRADED',
        'OUTAGE'
    );

CREATE INDEX idx_lifeline_restoration_eta
    ON incident_lifeline_assets (
        restoration_eta
    )
    WHERE restoration_eta IS NOT NULL;
