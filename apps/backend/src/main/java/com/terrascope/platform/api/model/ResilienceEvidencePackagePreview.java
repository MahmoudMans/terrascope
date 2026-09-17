package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record ResilienceEvidencePackagePreview(
        UUID incidentId,

        UUID certificationId,
        int certificationNumber,
        double certificationReadinessScore,
        int certificationCount,

        int afterActionReviewCount,
        int sitrepCount,
        int improvementCount,
        int exerciseCount,
        int correctiveActionCount,
        int timelineEventCount,

        String manifestVersion,
        String manifestHash,

        String executiveSummary,

        Instant generatedAt
) {
}
