package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.ResilienceCertificationStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResilienceCertification(
        UUID id,
        UUID incidentId,
        int certificationNumber,

        ResilienceCertificationStatus status,

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

        String executiveSummary,

        Instant createdAt
) {
}
