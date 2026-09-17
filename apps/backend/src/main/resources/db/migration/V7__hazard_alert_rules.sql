CREATE TABLE hazard_alert_rules (
    id UUID PRIMARY KEY,

    name VARCHAR(160) NOT NULL,

    hazard_type VARCHAR(40) NOT NULL,

    center_latitude DOUBLE PRECISION NOT NULL,
    center_longitude DOUBLE PRECISION NOT NULL,

    radius_km DOUBLE PRECISION NOT NULL,
    minimum_intensity DOUBLE PRECISION NOT NULL DEFAULT 0,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT ck_alert_rule_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(name)
            ) BETWEEN 1 AND 160
        ),

    CONSTRAINT ck_alert_rule_hazard_type
        CHECK (
            hazard_type IN (
                'all',
                'earthquakes',
                'volcanoes',
                'wildfires',
                'severe-storms'
            )
        ),

    CONSTRAINT ck_alert_rule_latitude
        CHECK (
            center_latitude
            BETWEEN -90 AND 90
        ),

    CONSTRAINT ck_alert_rule_longitude
        CHECK (
            center_longitude
            BETWEEN -180 AND 180
        ),

    CONSTRAINT ck_alert_rule_radius
        CHECK (
            radius_km > 0
            AND radius_km <= 2000
        ),

    CONSTRAINT ck_alert_rule_intensity
        CHECK (
            minimum_intensity >= 0
            AND minimum_intensity <= 1000
        )
);

CREATE TABLE hazard_alert_events (
    id UUID PRIMARY KEY,

    rule_id UUID NOT NULL
        REFERENCES hazard_alert_rules(id)
        ON DELETE CASCADE,

    source_event_id VARCHAR(200) NOT NULL,

    hazard_type VARCHAR(40) NOT NULL,

    title VARCHAR(400) NOT NULL,

    event_latitude DOUBLE PRECISION NOT NULL,
    event_longitude DOUBLE PRECISION NOT NULL,

    intensity DOUBLE PRECISION,

    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    distance_km DOUBLE PRECISION NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'NEW',

    triggered_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_alert_event_rule_source
        UNIQUE (
            rule_id,
            hazard_type,
            source_event_id
        ),

    CONSTRAINT ck_alert_event_hazard
        CHECK (
            hazard_type IN (
                'earthquakes',
                'volcanoes',
                'wildfires',
                'severe-storms'
            )
        ),

    CONSTRAINT ck_alert_event_latitude
        CHECK (
            event_latitude
            BETWEEN -90 AND 90
        ),

    CONSTRAINT ck_alert_event_longitude
        CHECK (
            event_longitude
            BETWEEN -180 AND 180
        ),

    CONSTRAINT ck_alert_event_distance
        CHECK (
            distance_km >= 0
        ),

    CONSTRAINT ck_alert_event_status
        CHECK (
            status IN (
                'NEW',
                'ACKNOWLEDGED',
                'DISMISSED'
            )
        )
);

CREATE INDEX idx_alert_rules_enabled
    ON hazard_alert_rules (
        enabled,
        created_at DESC
    );

CREATE INDEX idx_alert_events_status
    ON hazard_alert_events (
        status,
        triggered_at DESC
    );

CREATE INDEX idx_alert_events_rule
    ON hazard_alert_events (
        rule_id,
        triggered_at DESC
    );
