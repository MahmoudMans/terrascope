package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ImpactSimulationRequest;
import com.terrascope.platform.domain.HazardType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactModelTest {

    private final ImpactModel model =
            new ImpactModel();

    @Test
    void highIntensityEarthquakeProducesMaterialImpact() {
        ImpactModel.ModelResult result =
                model.calculate(
                        request(
                                "earthquakes",
                                8.4,
                                150.0,
                                2_000_000L,
                                45.0,
                                55.0
                        ),
                        HazardType.EARTHQUAKE
                );

        assertThat(
                result.affectedPopulation()
        ).isGreaterThan(
                200_000L
        );

        assertThat(
                result.displacedPopulation()
        ).isGreaterThan(0L);

        assertThat(
                result.estimatedEconomicLossMusd()
        ).isGreaterThan(0.0);

        assertThat(
                result.compositeRiskScore()
        ).isBetween(
                0.0,
                100.0
        );
    }

    @Test
    void strongerIntensityIncreasesRisk() {
        ImpactModel.ModelResult lower =
                model.calculate(
                        request(
                                "wildfires",
                                3.0,
                                80.0,
                                800_000L,
                                50.0,
                                60.0
                        ),
                        HazardType.WILDFIRE
                );

        ImpactModel.ModelResult higher =
                model.calculate(
                        request(
                                "wildfires",
                                8.0,
                                80.0,
                                800_000L,
                                50.0,
                                60.0
                        ),
                        HazardType.WILDFIRE
                );

        assertThat(
                higher.compositeRiskScore()
        ).isGreaterThan(
                lower.compositeRiskScore()
        );

        assertThat(
                higher.affectedPopulation()
        ).isGreaterThan(
                lower.affectedPopulation()
        );
    }

    @Test
    void strongerResilienceReducesRisk() {
        ImpactModel.ModelResult weak =
                model.calculate(
                        request(
                                "severe-storms",
                                7.5,
                                120.0,
                                1_000_000L,
                                20.0,
                                30.0
                        ),
                        HazardType.SEVERE_STORM
                );

        ImpactModel.ModelResult strong =
                model.calculate(
                        request(
                                "severe-storms",
                                7.5,
                                120.0,
                                1_000_000L,
                                90.0,
                                90.0
                        ),
                        HazardType.SEVERE_STORM
                );

        assertThat(
                strong.compositeRiskScore()
        ).isLessThan(
                weak.compositeRiskScore()
        );

        assertThat(
                strong.infrastructureDisruptionPct()
        ).isLessThan(
                weak.infrastructureDisruptionPct()
        );
    }

    @Test
    void displacedPopulationNeverExceedsAffected() {
        ImpactModel.ModelResult result =
                model.calculate(
                        request(
                                "volcanoes",
                                10.0,
                                1000.0,
                                10_000_000L,
                                0.0,
                                0.0
                        ),
                        HazardType.VOLCANO
                );

        assertThat(
                result.displacedPopulation()
        ).isLessThanOrEqualTo(
                result.affectedPopulation()
        );
    }

    private ImpactSimulationRequest request(
            String hazardType,
            double intensity,
            double radiusKm,
            long population,
            double resilience,
            double readiness
    ) {
        return new ImpactSimulationRequest(
                "Test scenario",
                hazardType,
                36.8,
                10.2,
                radiusKm,
                intensity,
                population,
                resilience,
                readiness
        );
    }
}
