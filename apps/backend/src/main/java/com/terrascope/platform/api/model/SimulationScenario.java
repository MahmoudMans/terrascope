package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record SimulationScenario(
        UUID id,
        String name,
        String hazardType,
        double latitude,
        double longitude,
        double radiusKm,
        double intensity,
        long exposedPopulation,
        double infrastructureResiliencePct,
        double emergencyReadinessPct,
        Instant createdAt
) {
}
