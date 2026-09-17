package com.terrascope.platform.repository;

import com.terrascope.platform.api.model.ImpactSimulationRequest;
import com.terrascope.platform.api.model.ImpactSimulationResult;
import com.terrascope.platform.api.model.SimulationHistoryItem;
import com.terrascope.platform.api.model.SimulationScenario;
import com.terrascope.platform.domain.SimulationRiskBand;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ImpactSimulationRepository {

    private final JdbcClient jdbcClient;

    public ImpactSimulationRepository(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    public SimulationScenario createScenario(
            UUID id,
            ImpactSimulationRequest request,
            String hazardType
    ) {
        return jdbcClient
                .sql("""
                        INSERT INTO simulation_scenarios (
                            id,
                            name,
                            hazard_type,
                            latitude,
                            longitude,
                            radius_km,
                            intensity,
                            exposed_population,
                            infrastructure_resilience_pct,
                            emergency_readiness_pct
                        )
                        VALUES (
                            :id,
                            :name,
                            :hazardType,
                            :latitude,
                            :longitude,
                            :radiusKm,
                            :intensity,
                            :exposedPopulation,
                            :resilience,
                            :readiness
                        )
                        RETURNING
                            id,
                            name,
                            hazard_type,
                            latitude,
                            longitude,
                            radius_km,
                            intensity,
                            exposed_population,
                            infrastructure_resilience_pct,
                            emergency_readiness_pct,
                            created_at
                        """)
                .param(
                        "id",
                        id
                )
                .param(
                        "name",
                        request
                                .name()
                                .trim()
                )
                .param(
                        "hazardType",
                        hazardType
                )
                .param(
                        "latitude",
                        request.latitude()
                )
                .param(
                        "longitude",
                        request.longitude()
                )
                .param(
                        "radiusKm",
                        request.radiusKm()
                )
                .param(
                        "intensity",
                        request.intensity()
                )
                .param(
                        "exposedPopulation",
                        request.exposedPopulation()
                )
                .param(
                        "resilience",
                        request.infrastructureResiliencePct()
                )
                .param(
                        "readiness",
                        request.emergencyReadinessPct()
                )
                .query(
                        this::mapScenario
                )
                .single();
    }

    public ImpactSimulationResult createRun(
            UUID id,
            UUID scenarioId,
            long affectedPopulation,
            long displacedPopulation,
            double estimatedEconomicLossMusd,
            double infrastructureDisruptionPct,
            double responsePressurePct,
            double compositeRiskScore,
            SimulationRiskBand riskBand
    ) {
        return jdbcClient
                .sql("""
                        INSERT INTO simulation_runs (
                            id,
                            scenario_id,
                            affected_population,
                            displaced_population,
                            estimated_economic_loss_musd,
                            infrastructure_disruption_pct,
                            response_pressure_pct,
                            composite_risk_score,
                            risk_band
                        )
                        VALUES (
                            :id,
                            :scenarioId,
                            :affectedPopulation,
                            :displacedPopulation,
                            :estimatedEconomicLossMusd,
                            :infrastructureDisruptionPct,
                            :responsePressurePct,
                            :compositeRiskScore,
                            :riskBand
                        )
                        RETURNING
                            id,
                            scenario_id,
                            affected_population,
                            displaced_population,
                            estimated_economic_loss_musd,
                            infrastructure_disruption_pct,
                            response_pressure_pct,
                            composite_risk_score,
                            risk_band,
                            methodology_version,
                            created_at
                        """)
                .param(
                        "id",
                        id
                )
                .param(
                        "scenarioId",
                        scenarioId
                )
                .param(
                        "affectedPopulation",
                        affectedPopulation
                )
                .param(
                        "displacedPopulation",
                        displacedPopulation
                )
                .param(
                        "estimatedEconomicLossMusd",
                        estimatedEconomicLossMusd
                )
                .param(
                        "infrastructureDisruptionPct",
                        infrastructureDisruptionPct
                )
                .param(
                        "responsePressurePct",
                        responsePressurePct
                )
                .param(
                        "compositeRiskScore",
                        compositeRiskScore
                )
                .param(
                        "riskBand",
                        riskBand.name()
                )
                .query(
                        this::mapResult
                )
                .single();
    }

    public List<SimulationHistoryItem> history() {
        return jdbcClient
                .sql("""
                        SELECT
                            s.id AS scenario_id_value,
                            s.name,
                            s.hazard_type,
                            s.latitude,
                            s.longitude,
                            s.radius_km,
                            s.intensity,
                            s.exposed_population,
                            s.infrastructure_resilience_pct,
                            s.emergency_readiness_pct,
                            s.created_at AS scenario_created_at,

                            r.id AS run_id,
                            r.affected_population,
                            r.displaced_population,
                            r.estimated_economic_loss_musd,
                            r.infrastructure_disruption_pct,
                            r.response_pressure_pct,
                            r.composite_risk_score,
                            r.risk_band,
                            r.methodology_version,
                            r.created_at AS run_created_at

                        FROM simulation_runs r
                        JOIN simulation_scenarios s
                          ON s.id = r.scenario_id

                        ORDER BY
                            r.created_at DESC,
                            r.id DESC

                        LIMIT 100
                        """)
                .query(
                        (resultSet, rowNumber) -> {
                            UUID scenarioId =
                                    resultSet.getObject(
                                            "scenario_id_value",
                                            UUID.class
                                    );

                            SimulationScenario scenario =
                                    new SimulationScenario(
                                            scenarioId,
                                            resultSet.getString(
                                                    "name"
                                            ),
                                            resultSet.getString(
                                                    "hazard_type"
                                            ),
                                            resultSet.getDouble(
                                                    "latitude"
                                            ),
                                            resultSet.getDouble(
                                                    "longitude"
                                            ),
                                            resultSet.getDouble(
                                                    "radius_km"
                                            ),
                                            resultSet.getDouble(
                                                    "intensity"
                                            ),
                                            resultSet.getLong(
                                                    "exposed_population"
                                            ),
                                            resultSet.getDouble(
                                                    "infrastructure_resilience_pct"
                                            ),
                                            resultSet.getDouble(
                                                    "emergency_readiness_pct"
                                            ),
                                            resultSet
                                                    .getObject(
                                                            "scenario_created_at",
                                                            OffsetDateTime.class
                                                    )
                                                    .toInstant()
                                    );

                            ImpactSimulationResult result =
                                    new ImpactSimulationResult(
                                            resultSet.getObject(
                                                    "run_id",
                                                    UUID.class
                                            ),
                                            scenarioId,
                                            resultSet.getLong(
                                                    "affected_population"
                                            ),
                                            resultSet.getLong(
                                                    "displaced_population"
                                            ),
                                            resultSet.getDouble(
                                                    "estimated_economic_loss_musd"
                                            ),
                                            resultSet.getDouble(
                                                    "infrastructure_disruption_pct"
                                            ),
                                            resultSet.getDouble(
                                                    "response_pressure_pct"
                                            ),
                                            resultSet.getDouble(
                                                    "composite_risk_score"
                                            ),
                                            SimulationRiskBand.valueOf(
                                                    resultSet.getString(
                                                            "risk_band"
                                                    )
                                            ),
                                            resultSet.getString(
                                                    "methodology_version"
                                            ),
                                            resultSet
                                                    .getObject(
                                                            "run_created_at",
                                                            OffsetDateTime.class
                                                    )
                                                    .toInstant()
                                    );

                            return new SimulationHistoryItem(
                                    scenario,
                                    result
                            );
                        }
                )
                .list();
    }

    public int deleteScenario(
            UUID scenarioId
    ) {
        return jdbcClient
                .sql("""
                        DELETE
                        FROM simulation_scenarios
                        WHERE id = :scenarioId
                        """)
                .param(
                        "scenarioId",
                        scenarioId
                )
                .update();
    }

    private SimulationScenario mapScenario(
            java.sql.ResultSet resultSet,
            int rowNumber
    ) throws java.sql.SQLException {
        return new SimulationScenario(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getString(
                        "name"
                ),
                resultSet.getString(
                        "hazard_type"
                ),
                resultSet.getDouble(
                        "latitude"
                ),
                resultSet.getDouble(
                        "longitude"
                ),
                resultSet.getDouble(
                        "radius_km"
                ),
                resultSet.getDouble(
                        "intensity"
                ),
                resultSet.getLong(
                        "exposed_population"
                ),
                resultSet.getDouble(
                        "infrastructure_resilience_pct"
                ),
                resultSet.getDouble(
                        "emergency_readiness_pct"
                ),
                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }

    private ImpactSimulationResult mapResult(
            java.sql.ResultSet resultSet,
            int rowNumber
    ) throws java.sql.SQLException {
        return new ImpactSimulationResult(
                resultSet.getObject(
                        "id",
                        UUID.class
                ),
                resultSet.getObject(
                        "scenario_id",
                        UUID.class
                ),
                resultSet.getLong(
                        "affected_population"
                ),
                resultSet.getLong(
                        "displaced_population"
                ),
                resultSet.getDouble(
                        "estimated_economic_loss_musd"
                ),
                resultSet.getDouble(
                        "infrastructure_disruption_pct"
                ),
                resultSet.getDouble(
                        "response_pressure_pct"
                ),
                resultSet.getDouble(
                        "composite_risk_score"
                ),
                SimulationRiskBand.valueOf(
                        resultSet.getString(
                                "risk_band"
                        )
                ),
                resultSet.getString(
                        "methodology_version"
                ),
                resultSet
                        .getObject(
                                "created_at",
                                OffsetDateTime.class
                        )
                        .toInstant()
        );
    }
}
