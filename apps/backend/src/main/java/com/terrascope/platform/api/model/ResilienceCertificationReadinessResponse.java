package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.ResilienceCertificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResilienceCertificationReadinessResponse(
        UUID incidentId,

        ResilienceCertificationStatus status,
        boolean ready,

        int completedImprovementCount,

        int validatedCount,
        int untestedCount,
        int needsRevalidationCount,
        int failedCount,

        int openCorrectiveActionCount,

        int totalExerciseCount,
        int passedExerciseCount,

        double averageLatestReadinessScore,

        int afterActionReviewCount,
        int sitrepCount,

        List<CertificationBlocker> blockers,

        Instant generatedAt
) {
}
