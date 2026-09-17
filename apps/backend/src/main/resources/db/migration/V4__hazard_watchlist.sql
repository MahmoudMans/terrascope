CREATE TABLE hazard_watchlist_entries (
    id UUID PRIMARY KEY,

    source_event_id VARCHAR(200) NOT NULL,
    hazard_type VARCHAR(40) NOT NULL,

    title VARCHAR(400) NOT NULL,
    description VARCHAR(4000) NOT NULL DEFAULT '',

    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,

    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    source_provider VARCHAR(80) NOT NULL,
    source_url VARCHAR(1000) NOT NULL DEFAULT '',

    magnitude_value DOUBLE PRECISION,
    magnitude_unit VARCHAR(40) NOT NULL DEFAULT '',

    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    notes VARCHAR(1000) NOT NULL DEFAULT '',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_hazard_watchlist_source_event
        UNIQUE (
            hazard_type,
            source_event_id
        ),

    CONSTRAINT ck_hazard_watchlist_type
        CHECK (
            hazard_type IN (
                'earthquakes',
                'volcanoes',
                'wildfires',
                'severe-storms'
            )
        ),

    CONSTRAINT ck_hazard_watchlist_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT ck_hazard_watchlist_latitude
        CHECK (
            latitude BETWEEN -90 AND 90
        ),

    CONSTRAINT ck_hazard_watchlist_longitude
        CHECK (
            longitude BETWEEN -180 AND 180
        )
);

CREATE INDEX idx_hazard_watchlist_priority_created
    ON hazard_watchlist_entries (
        priority,
        created_at DESC
    );

CREATE INDEX idx_hazard_watchlist_hazard_created
    ON hazard_watchlist_entries (
        hazard_type,
        created_at DESC
    );

CREATE INDEX idx_hazard_watchlist_observed
    ON hazard_watchlist_entries (
        observed_at DESC
    );
