package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.PortfolioObjectiveOutcome;

import java.time.Instant;
import java.util.UUID;

public record PortfolioObjectiveOutcomePreview(
        UUID objectiveId,
        String objectiveTitle,

        UUID baselineSnapshotId,
        int baselineSnapshotNumber,

        PortfolioObjectiveOutcome outcome,

        int baselineScore,
        int currentScore,
        int scoreDelta,

        int baselineAssuranceDebt,
        int currentAssuranceDebt,
        int assuranceDebtDelta,

        int certifiedIncidentDelta,
        int validatedImprovementDelta,
        int auditVerifiedIncidentDelta,

        String executiveSummary,

        Instant generatedAt
) {
}
