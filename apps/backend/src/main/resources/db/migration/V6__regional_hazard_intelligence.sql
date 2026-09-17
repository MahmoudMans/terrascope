CREATE TABLE regional_analysis_runs (
    id UUID PRIMARY KEY,

    region_name VARCHAR(160) NOT NULL,

    center_latitude DOUBLE PRECISION NOT NULL,
    center_longitude DOUBLE PRECISION NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL,

    source_point_count INTEGER NOT NULL,
    matched_event_count INTEGER NOT NULL,

    earthquake_count INTEGER NOT NULL,
    volcano_count INTEGER NOT NULL,
    wildfire_count INTEGER NOT NULL,
    severe_storm_count INTEGER NOT NULL,

    nearest_event_id VARCHAR(200),
    nearest_event_type VARCHAR(40),
    nearest_event_title VARCHAR(400),
    nearest_event_distance_km DOUBLE PRECISION,

    average_distance_km DOUBLE PRECISION NOT NULL,

    activity_index DOUBLE PRECISION NOT NULL,
    activity_band VARCHAR(20) NOT NULL,

    analyzed_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT ck_regional_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(region_name)
            ) BETWEEN 1 AND 160
        ),

    CONSTRAINT ck_regional_latitude
        CHECK (
            center_latitude
            BETWEEN -90 AND 90
        ),

    CONSTRAINT ck_regional_longitude
        CHECK (
            center_longitude
            BETWEEN -180 AND 180
        ),

    CONSTRAINT ck_regional_radius
        CHECK (
            radius_km > 0
            AND radius_km <= 2000
        ),

    CONSTRAINT ck_regional_counts
        CHECK (
            source_point_count >= 0
            AND matched_event_count >= 0
            AND matched_event_count <= source_point_count
            AND earthquake_count >= 0
            AND volcano_count >= 0
            AND wildfire_count >= 0
            AND severe_storm_count >= 0
        ),

    CONSTRAINT ck_regional_average_distance
        CHECK (
            average_distance_km >= 0
        ),

    CONSTRAINT ck_regional_activity_index
        CHECK (
            activity_index
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_regional_activity_band
        CHECK (
            activity_band IN (
                'QUIET',
                'GUARDED',
                'ELEVATED',
                'HIGH',
                'CRITICAL'
            )
        )
);

CREATE INDEX idx_regional_analysis_time
    ON regional_analysis_runs (
        analyzed_at DESC
    );

CREATE INDEX idx_regional_analysis_activity
    ON regional_analysis_runs (
        activity_index DESC,
        analyzed_at DESC
    );

CREATE INDEX idx_regional_analysis_region
    ON regional_analysis_runs (
        region_name,
        analyzed_at DESC
    );
