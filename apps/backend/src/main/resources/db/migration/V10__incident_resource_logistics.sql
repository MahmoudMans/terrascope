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
                'RESOURCE_DELETED'
            )
        );

CREATE TABLE incident_resource_requests (
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

    status VARCHAR(40)
        NOT NULL
        DEFAULT 'REQUESTED',

    requested_quantity INTEGER NOT NULL,

    allocated_quantity INTEGER
        NOT NULL
        DEFAULT 0,

    unit VARCHAR(40) NOT NULL,

    owner_name VARCHAR(120)
        NOT NULL
        DEFAULT 'Unassigned',

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    fulfilled_at TIMESTAMP WITH TIME ZONE,

    cancelled_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_incident_resource_category
        CHECK (
            category IN (
                'PERSONNEL',
                'MEDICAL',
                'TRANSPORT',
                'SHELTER',
                'EQUIPMENT',
                'COMMUNICATIONS',
                'OTHER'
            )
        ),

    CONSTRAINT ck_incident_resource_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(name)
            ) BETWEEN 1 AND 300
        ),

    CONSTRAINT ck_incident_resource_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_incident_resource_status
        CHECK (
            status IN (
                'REQUESTED',
                'PARTIALLY_ALLOCATED',
                'FULFILLED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_incident_resource_requested
        CHECK (
            requested_quantity
            BETWEEN 1 AND 1000000
        ),

    CONSTRAINT ck_incident_resource_allocated
        CHECK (
            allocated_quantity >= 0
            AND allocated_quantity
                <= requested_quantity
        ),

    CONSTRAINT ck_incident_resource_unit
        CHECK (
            CHAR_LENGTH(
                BTRIM(unit)
            ) BETWEEN 1 AND 40
        )
);

CREATE INDEX idx_incident_resources_incident
    ON incident_resource_requests (
        incident_id,
        status,
        updated_at DESC
    );

CREATE INDEX idx_incident_resources_priority
    ON incident_resource_requests (
        priority,
        updated_at DESC
    );

CREATE INDEX idx_incident_resources_shortage
    ON incident_resource_requests (
        incident_id,
        requested_quantity,
        allocated_quantity
    )
    WHERE status IN (
        'REQUESTED',
        'PARTIALLY_ALLOCATED'
    );
