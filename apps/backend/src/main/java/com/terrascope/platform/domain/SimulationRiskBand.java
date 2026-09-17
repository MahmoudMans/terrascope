package com.terrascope.platform.domain;

public enum SimulationRiskBand {

    LOW,
    MODERATE,
    HIGH,
    SEVERE,
    EXTREME;

    public static SimulationRiskBand fromScore(
            double score
    ) {
        if (score >= 85.0) {
            return EXTREME;
        }

        if (score >= 70.0) {
            return SEVERE;
        }

        if (score >= 50.0) {
            return HIGH;
        }

        if (score >= 30.0) {
            return MODERATE;
        }

        return LOW;
    }
}
