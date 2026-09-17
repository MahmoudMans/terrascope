package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ImpactSimulationRequest;
import com.terrascope.platform.domain.HazardType;
import com.terrascope.platform.domain.SimulationRiskBand;
import org.springframework.stereotype.Component;

@Component
public class ImpactModel {

    public ModelResult calculate(
            ImpactSimulationRequest request,
            HazardType hazardType
    ) {
        double intensity =
                clamp(
                        request.intensity()
                                / 10.0,
                        0.0,
                        1.0
                );

        double radiusFactor =
                Math.pow(
                        clamp(
                                request.radiusKm()
                                        / 300.0,
                                0.02,
                                1.0
                        ),
                        0.62
                );

        double resilience =
                clamp(
                        request.infrastructureResiliencePct()
                                / 100.0,
                        0.0,
                        1.0
                );

        double readiness =
                clamp(
                        request.emergencyReadinessPct()
                                / 100.0,
                        0.0,
                        1.0
                );

        double hazardMultiplier =
                hazardMultiplier(
                        hazardType
                );

        double vulnerability =
                clamp(
                        1.0
                                - (
                                resilience
                                        * 0.56
                        )
                                - (
                                readiness
                                        * 0.24
                        ),
                        0.12,
                        1.0
                );

        double affectedRatio =
                clamp(
                        intensity
                                * hazardMultiplier
                                * (
                                0.45
                                        + radiusFactor
                                        * 0.55
                        )
                                * (
                                0.38
                                        + vulnerability
                                        * 0.62
                        ),
                        0.0,
                        0.96
                );

        long affectedPopulation =
                Math.round(
                        request.exposedPopulation()
                                * affectedRatio
                );

        double displacementRatio =
                displacementRatio(
                        hazardType
                )
                        * (
                        0.45
                                + intensity
                                * 0.55
                )
                        * (
                        0.55
                                + vulnerability
                                * 0.45
                );

        long displacedPopulation =
                Math.min(
                        affectedPopulation,
                        Math.round(
                                affectedPopulation
                                        * displacementRatio
                        )
                );

        double infrastructureDisruption =
                clamp(
                        100.0
                                * intensity
                                * hazardMultiplier
                                * (
                                0.35
                                        + radiusFactor
                                        * 0.35
                                        + vulnerability
                                        * 0.30
                        ),
                        0.0,
                        100.0
                );

        double responsePressure =
                clamp(
                        affectedRatio
                                * 58.0
                                + (
                                1.0
                                        - readiness
                        )
                                * 31.0
                                + intensity
                                * 18.0,
                        0.0,
                        100.0
                );

        double lossPerAffectedPerson =
                lossPerAffectedPerson(
                        hazardType
                );

        double estimatedEconomicLossMusd =
                Math.max(
                        0.0,
                        (
                                affectedPopulation
                                        * lossPerAffectedPerson
                                        * (
                                        0.65
                                                + infrastructureDisruption
                                                / 100.0
                                        )
                        )
                                / 1_000_000.0
                );

        double populationImpactScore =
                affectedRatio
                        * 100.0;

        double compositeRiskScore =
                clamp(
                        populationImpactScore
                                * 0.33
                                + infrastructureDisruption
                                * 0.27
                                + responsePressure
                                * 0.25
                                + intensity
                                * 100.0
                                * 0.15,
                        0.0,
                        100.0
                );

        return new ModelResult(
                affectedPopulation,
                displacedPopulation,
                round2(
                        estimatedEconomicLossMusd
                ),
                round2(
                        infrastructureDisruption
                ),
                round2(
                        responsePressure
                ),
                round2(
                        compositeRiskScore
                ),
                SimulationRiskBand.fromScore(
                        compositeRiskScore
                )
        );
    }

    private double hazardMultiplier(
            HazardType hazardType
    ) {
        return switch (hazardType) {
            case EARTHQUAKE -> 1.12;
            case VOLCANO -> 1.20;
            case WILDFIRE -> 0.88;
            case SEVERE_STORM -> 0.96;
        };
    }

    private double displacementRatio(
            HazardType hazardType
    ) {
        return switch (hazardType) {
            case EARTHQUAKE -> 0.27;
            case VOLCANO -> 0.34;
            case WILDFIRE -> 0.23;
            case SEVERE_STORM -> 0.19;
        };
    }

    private double lossPerAffectedPerson(
            HazardType hazardType
    ) {
        return switch (hazardType) {
            case EARTHQUAKE -> 8200.0;
            case VOLCANO -> 9100.0;
            case WILDFIRE -> 6400.0;
            case SEVERE_STORM -> 5900.0;
        };
    }

    private double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(
                        maximum,
                        value
                )
        );
    }

    private double round2(
            double value
    ) {
        return Math.round(
                value
                        * 100.0
        )
                / 100.0;
    }

    public record ModelResult(
            long affectedPopulation,
            long displacedPopulation,
            double estimatedEconomicLossMusd,
            double infrastructureDisruptionPct,
            double responsePressurePct,
            double compositeRiskScore,
            SimulationRiskBand riskBand
    ) {
    }
}
