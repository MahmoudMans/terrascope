package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.BenefitRealizationBand;

import java.time.Instant;
import java.util.UUID;

public record BenefitRealizationSnapshot(
        UUID id,
        int snapshotNumber,

        BenefitRealizationBand band,
        boolean closureReady,

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

        double validationCoveragePct,
        double realizedBenefitRatePct,

        double averageScoreDelta,
        double averageAssuranceDebtDelta,

        Instant createdAt
) {
}
