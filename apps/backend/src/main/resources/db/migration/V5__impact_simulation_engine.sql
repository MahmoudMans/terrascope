DO $$
BEGIN
    IF (
        SELECT COUNT(*)
        FROM simulation_runs
    ) > 0 THEN
        RAISE EXCEPTION
            'simulation_runs must be empty before V5 schema formalization';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM simulation_scenarios
    ) > 0 THEN
        RAISE EXCEPTION
            'simulation_scenarios must be empty before V5 schema formalization';
    END IF;
END
$$;

DROP TABLE simulation_runs;
DROP TABLE simulation_scenarios;

CREATE TABLE simulation_scenarios (
    id UUID PRIMARY KEY,

    name VARCHAR(160) NOT NULL,

    hazard_type VARCHAR(40) NOT NULL,

    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,

    radius_km DOUBLE PRECISION NOT NULL,
    intensity DOUBLE PRECISION NOT NULL,

    exposed_population BIGINT NOT NULL,

    infrastructure_resilience_pct DOUBLE PRECISION NOT NULL,
    emergency_readiness_pct DOUBLE PRECISION NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_simulation_scenario_name
        CHECK (
            CHAR_LENGTH(
                BTRIM(name)
            ) BETWEEN 1 AND 160
        ),

    CONSTRAINT ck_simulation_scenario_hazard
        CHECK (
            hazard_type IN (
                'earthquakes',
                'volcanoes',
                'wildfires',
                'severe-storms'
            )
        ),

    CONSTRAINT ck_simulation_scenario_latitude
        CHECK (
            latitude BETWEEN -90 AND 90
        ),

    CONSTRAINT ck_simulation_scenario_longitude
        CHECK (
            longitude BETWEEN -180 AND 180
        ),

    CONSTRAINT ck_simulation_scenario_radius
        CHECK (
            radius_km > 0
            AND radius_km <= 2000
        ),

    CONSTRAINT ck_simulation_scenario_intensity
        CHECK (
            intensity >= 0
            AND intensity <= 10
        ),

    CONSTRAINT ck_simulation_scenario_population
        CHECK (
            exposed_population >= 0
            AND exposed_population <= 500000000
        ),

    CONSTRAINT ck_simulation_scenario_resilience
        CHECK (
            infrastructure_resilience_pct
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_simulation_scenario_readiness
        CHECK (
            emergency_readiness_pct
            BETWEEN 0 AND 100
        )
);

CREATE TABLE simulation_runs (
    id UUID PRIMARY KEY,

    scenario_id UUID NOT NULL
        REFERENCES simulation_scenarios(id)
        ON DELETE CASCADE,

    affected_population BIGINT NOT NULL,
    displaced_population BIGINT NOT NULL,

    estimated_economic_loss_musd DOUBLE PRECISION NOT NULL,

    infrastructure_disruption_pct DOUBLE PRECISION NOT NULL,
    response_pressure_pct DOUBLE PRECISION NOT NULL,

    composite_risk_score DOUBLE PRECISION NOT NULL,
    risk_band VARCHAR(20) NOT NULL,

    methodology_version VARCHAR(60) NOT NULL
        DEFAULT 'TERRASCOPE_IMPACT_V1',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_simulation_run_population
        CHECK (
            affected_population >= 0
            AND displaced_population >= 0
            AND displaced_population <= affected_population
        ),

    CONSTRAINT ck_simulation_run_loss
        CHECK (
            estimated_economic_loss_musd >= 0
        ),

    CONSTRAINT ck_simulation_run_infrastructure
        CHECK (
            infrastructure_disruption_pct
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_simulation_run_response
        CHECK (
            response_pressure_pct
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_simulation_run_score
        CHECK (
            composite_risk_score
            BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_simulation_run_band
        CHECK (
            risk_band IN (
                'LOW',
                'MODERATE',
                'HIGH',
                'SEVERE',
                'EXTREME'
            )
        ),

    CONSTRAINT ck_simulation_run_methodology
        CHECK (
            methodology_version
            = 'TERRASCOPE_IMPACT_V1'
        )
);

CREATE INDEX idx_simulation_scenarios_created
    ON simulation_scenarios (
        created_at DESC
    );

CREATE INDEX idx_simulation_scenarios_hazard
    ON simulation_scenarios (
        hazard_type,
        created_at DESC
    );

CREATE INDEX idx_simulation_runs_scenario
    ON simulation_runs (
        scenario_id,
        created_at DESC
    );

CREATE INDEX idx_simulation_runs_risk
    ON simulation_runs (
        composite_risk_score DESC,
        created_at DESC
    );
