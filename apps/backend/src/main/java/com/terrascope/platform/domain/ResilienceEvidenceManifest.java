package com.terrascope.platform.domain;

import java.util.UUID;

public record ResilienceEvidenceManifest(
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
        int timelineEventCount
) {

    public boolean hasCertification() {
        return certificationId != null
                && certificationNumber > 0;
    }
}
