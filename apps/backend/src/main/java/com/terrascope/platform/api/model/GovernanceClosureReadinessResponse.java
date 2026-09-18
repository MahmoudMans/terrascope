package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.BenefitRealizationBand;

import java.time.Instant;
import java.util.List;

public record GovernanceClosureReadinessResponse(
        boolean ready,

        BenefitRealizationBand band,

        int totalObjectiveCount,
        int completedObjectiveCount,
        int validatedObjectiveCount,
        int achievedCount,

        double validationCoveragePct,
        double realizedBenefitRatePct,

        List<String> blockers,

        Instant generatedAt
) {
}
