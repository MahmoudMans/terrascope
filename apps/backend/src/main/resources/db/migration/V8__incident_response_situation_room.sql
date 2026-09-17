CREATE TABLE incident_cases (
    id UUID PRIMARY KEY,

    alert_event_id UUID
        REFERENCES hazard_alert_events(id)
        ON DELETE SET NULL,

    source_event_id VARCHAR(200) NOT NULL,

    hazard_type VARCHAR(40) NOT NULL,

    title VARCHAR(400) NOT NULL,

    description VARCHAR(2000) NOT NULL DEFAULT '',

    event_latitude DOUBLE PRECISION NOT NULL,
    event_longitude DOUBLE PRECISION NOT NULL,

    severity VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',

    owner_name VARCHAR(120) NOT NULL DEFAULT 'Unassigned',

    opened_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    closed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_incident_source
        UNIQUE (
            hazard_type,
            source_event_id
        ),

    CONSTRAINT uq_incident_alert_event
        UNIQUE (
            alert_event_id
        ),

    CONSTRAINT ck_incident_hazard
        CHECK (
            hazard_type IN (
                'earthquakes',
                'volcanoes',
                'wildfires',
                'severe-storms'
            )
        ),

    CONSTRAINT ck_incident_latitude
        CHECK (
            event_latitude
            BETWEEN -90 AND 90
        ),

    CONSTRAINT ck_incident_longitude
        CHECK (
            event_longitude
            BETWEEN -180 AND 180
        ),

    CONSTRAINT ck_incident_severity
        CHECK (
            severity IN (
                'LOW',
                'MODERATE',
                'HIGH',
                'SEVERE',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_incident_status
        CHECK (
            status IN (
                'OPEN',
                'MONITORING',
                'RESPONDING',
                'STABILIZED',
                'CLOSED'
            )
        )
);

CREATE TABLE incident_timeline_events (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    event_type VARCHAR(40) NOT NULL,

    message VARCHAR(2000) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT ck_incident_timeline_type
        CHECK (
            event_type IN (
                'INCIDENT_OPENED',
                'STATUS_CHANGED',
                'SEVERITY_CHANGED',
                'OWNER_CHANGED',
                'NOTE_ADDED'
            )
        ),

    CONSTRAINT ck_incident_timeline_message
        CHECK (
            CHAR_LENGTH(
                BTRIM(message)
            ) BETWEEN 1 AND 2000
        )
);

CREATE INDEX idx_incident_status
    ON incident_cases (
        status,
        updated_at DESC
    );

CREATE INDEX idx_incident_severity
    ON incident_cases (
        severity,
        updated_at DESC
    );

CREATE INDEX idx_incident_updated
    ON incident_cases (
        updated_at DESC
    );

CREATE INDEX idx_incident_timeline
    ON incident_timeline_events (
        incident_id,
        created_at DESC
    );
