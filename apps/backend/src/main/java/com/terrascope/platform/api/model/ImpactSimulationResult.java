package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.SimulationRiskBand;

import java.time.Instant;
import java.util.UUID;

public record ImpactSimulationResult(
        UUID id,
        UUID scenarioId,
        long affectedPopulation,
        long displacedPopulation,
        double estimatedEconomicLossMusd,
        double infrastructureDisruptionPct,
        double responsePressurePct,
        double compositeRiskScore,
        SimulationRiskBand riskBand,
        String methodologyVersion,
        Instant createdAt
) {
}
