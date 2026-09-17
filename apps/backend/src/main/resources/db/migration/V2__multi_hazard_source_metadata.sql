ALTER TABLE saved_disaster_events
    ADD COLUMN source_provider VARCHAR(80),
    ADD COLUMN source_url VARCHAR(1000),
    ADD COLUMN event_status VARCHAR(40) NOT NULL DEFAULT 'OBSERVED';

CREATE INDEX idx_saved_disaster_source_provider
    ON saved_disaster_events(source_provider);

CREATE INDEX idx_saved_disaster_event_status
    ON saved_disaster_events(event_status);
