CREATE TABLE saved_disaster_events (
    id UUID PRIMARY KEY,
    external_event_id VARCHAR(160) NOT NULL,
    disaster_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    magnitude DOUBLE PRECISION,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_saved_disaster_external_event
        UNIQUE (external_event_id, disaster_type)
);

CREATE TABLE simulation_scenarios (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    disaster_type VARCHAR(40) NOT NULL,
    region_name VARCHAR(160),
    severity INTEGER,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE simulation_runs (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    outcome JSONB NOT NULL DEFAULT '{}'::jsonb,

    CONSTRAINT fk_simulation_run_scenario
        FOREIGN KEY (scenario_id)
        REFERENCES simulation_scenarios(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_saved_disaster_event_occurred
    ON saved_disaster_events(occurred_at DESC);

CREATE INDEX idx_saved_disaster_event_type
    ON saved_disaster_events(disaster_type);

CREATE INDEX idx_simulation_scenario_created
    ON simulation_scenarios(created_at DESC);
