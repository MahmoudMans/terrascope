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
                'SHELTER_DELETED'
            )
        );

CREATE TABLE incident_evacuation_zones (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    name VARCHAR(300) NOT NULL,

    description VARCHAR(2000)
        NOT NULL
        DEFAULT '',

    priority VARCHAR(30) NOT NULL,

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'MONITORING',

    population_at_risk INTEGER NOT NULL,

    evacuated_population INTEGER
        NOT NULL
        DEFAULT 0,

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    completed_at TIMESTAMP WITH TIME ZONE,

    cancelled_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_evacuation_zone_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(name)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_evacuation_zone_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_evacuation_zone_status
        CHECK (
            status IN (
                'MONITORING',
                'EVACUATING',
                'CLEARED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_evacuation_population
        CHECK (
            population_at_risk
            BETWEEN 1 AND 100000000
        ),

    CONSTRAINT ck_evacuation_progress
        CHECK (
            evacuated_population >= 0
            AND evacuated_population
                <= population_at_risk
        )
);

CREATE TABLE incident_shelters (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    name VARCHAR(300) NOT NULL,

    address VARCHAR(500)
        NOT NULL
        DEFAULT '',

    capacity INTEGER NOT NULL,

    occupancy INTEGER
        NOT NULL
        DEFAULT 0,

    status VARCHAR(30)
        NOT NULL
        DEFAULT 'OPEN',

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    closed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_shelter_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(name)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_shelter_capacity
        CHECK (
            capacity
            BETWEEN 1 AND 10000000
        ),

    CONSTRAINT ck_shelter_occupancy
        CHECK (
            occupancy >= 0
            AND occupancy <= capacity
        ),

    CONSTRAINT ck_shelter_status
        CHECK (
            status IN (
                'OPEN',
                'FULL',
                'CLOSED'
            )
        )
);

CREATE INDEX idx_evacuation_zones_incident
    ON incident_evacuation_zones (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_evacuation_zones_priority
    ON incident_evacuation_zones (
        priority,
        updated_at DESC
    );

CREATE INDEX idx_shelters_incident
    ON incident_shelters (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_shelters_capacity
    ON incident_shelters (
        incident_id,
        capacity,
        occupancy
    );
