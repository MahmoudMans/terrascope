package com.terrascope.platform.api.model;

public record SimulationHistoryItem(
        SimulationScenario scenario,
        ImpactSimulationResult result
) {
}
