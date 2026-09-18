package com.terrascope.platform.domain;

public enum BenefitRealizationBand {

    UNPROVEN,
    EMERGING,
    REALIZING,
    REALIZED;

    public static BenefitRealizationBand derive(
            int completedObjectiveCount,
            int validatedObjectiveCount,
            int notAchievedCount,
            double validationCoveragePct,
            double realizedBenefitRatePct,
            boolean closureReady
    ) {
        if (
                completedObjectiveCount == 0
                || validatedObjectiveCount == 0
        ) {
            return UNPROVEN;
        }

        if (
                notAchievedCount > 0
                || validationCoveragePct < 50.0
        ) {
            return EMERGING;
        }

        if (
                closureReady
                && realizedBenefitRatePct >= 80.0
        ) {
            return REALIZED;
        }

        return REALIZING;
    }
}
