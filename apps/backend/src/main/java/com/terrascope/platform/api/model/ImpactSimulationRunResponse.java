package com.terrascope.platform.api.model;

public record ImpactSimulationRunResponse(
        SimulationScenario scenario,
        ImpactSimulationResult result
) {
}
