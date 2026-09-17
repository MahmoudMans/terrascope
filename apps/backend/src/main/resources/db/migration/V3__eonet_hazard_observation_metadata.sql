ALTER TABLE saved_disaster_events
    ADD COLUMN last_observed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN observation_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN magnitude_value DOUBLE PRECISION,
    ADD COLUMN magnitude_unit VARCHAR(40);

ALTER TABLE saved_disaster_events
    ADD CONSTRAINT ck_saved_disaster_observation_count
        CHECK (observation_count >= 1);

CREATE INDEX idx_saved_disaster_last_observed
    ON saved_disaster_events(last_observed_at DESC);

CREATE INDEX idx_saved_disaster_magnitude
    ON saved_disaster_events(magnitude_value)
    WHERE magnitude_value IS NOT NULL;
