package com.terrascope.platform.domain;

public record BenefitRealizationMetrics(
        int totalObjectiveCount,

        int plannedObjectiveCount,
        int inProgressObjectiveCount,
        int blockedObjectiveCount,
        int completedObjectiveCount,
        int cancelledObjectiveCount,

        int validatedObjectiveCount,
        int unvalidatedCompletedObjectiveCount,

        int achievedCount,
        int partialCount,
        int notAchievedCount,

        double averageScoreDelta,
        double averageAssuranceDebtDelta
) {
    public int activeObjectiveCount() {
        return plannedObjectiveCount
                + inProgressObjectiveCount
                + blockedObjectiveCount;
    }
}
