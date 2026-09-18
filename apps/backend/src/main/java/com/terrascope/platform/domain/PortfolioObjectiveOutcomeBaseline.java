package com.terrascope.platform.domain;

import java.util.UUID;

public record PortfolioObjectiveOutcomeBaseline(
        UUID snapshotId,
        int snapshotNumber,

        int score,

        int certifiedIncidentCount,
        int auditVerifiedIncidentCount,
        int validatedImprovementCount,

        int untestedImprovementCount,
        int needsRevalidationCount,
        int failedImprovementCount,
        int openCorrectiveActionCount
) {
    public int assuranceDebt() {
        return (
                untestedImprovementCount * 2
                + needsRevalidationCount * 3
                + failedImprovementCount * 5
                + openCorrectiveActionCount * 2
        );
    }
}
