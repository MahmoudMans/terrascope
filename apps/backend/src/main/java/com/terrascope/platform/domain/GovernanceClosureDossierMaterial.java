package com.terrascope.platform.domain;

import java.util.UUID;

public record GovernanceClosureDossierMaterial(
        UUID benefitSnapshotId,
        int benefitSnapshotNumber,

        BenefitRealizationBand band,
        boolean closureReady,

        int totalObjectiveCount,
        int completedObjectiveCount,
        int validatedObjectiveCount,
        int achievedCount,

        double validationCoveragePct,
        double realizedBenefitRatePct,

        double averageScoreDelta,
        double averageAssuranceDebtDelta
) {
}
