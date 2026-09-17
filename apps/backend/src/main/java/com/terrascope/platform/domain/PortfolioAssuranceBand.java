package com.terrascope.platform.domain;

public enum PortfolioAssuranceBand {

    FOUNDATIONAL,
    DEVELOPING,
    ASSURED,
    HIGH_ASSURANCE;

    public static PortfolioAssuranceBand fromScore(
            int score
    ) {
        if (
                score < 0
                || score > 100
        ) {
            throw new IllegalArgumentException(
                    "Portfolio assurance score must be between 0 and 100."
            );
        }

        if (
                score >= 80
        ) {
            return HIGH_ASSURANCE;
        }

        if (
                score >= 60
        ) {
            return ASSURED;
        }

        if (
                score >= 40
        ) {
            return DEVELOPING;
        }

        return FOUNDATIONAL;
    }
}
