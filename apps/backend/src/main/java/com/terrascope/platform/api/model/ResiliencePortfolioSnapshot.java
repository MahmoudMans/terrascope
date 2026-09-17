package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.PortfolioAssuranceBand;

import java.time.Instant;
import java.util.UUID;

public record ResiliencePortfolioSnapshot(
        UUID id,
        int snapshotNumber,

        int score,
        PortfolioAssuranceBand band,

        int totalIncidentCount,
        int openIncidentCount,
        int closedIncidentCount,

        int certifiedIncidentCount,
        int evidencePackagedIncidentCount,
        int auditLedgerIncidentCount,
        int auditVerifiedIncidentCount,

        int completedImprovementCount,
        int validatedImprovementCount,
        int untestedImprovementCount,
        int needsRevalidationCount,
        int failedImprovementCount,

        int openCorrectiveActionCount,

        int totalExerciseCount,
        int completedExerciseCount,
        int passedExerciseCount,

        double averageLatestReadinessScore,

        Instant createdAt
) {
}
