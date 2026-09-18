package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.PortfolioObjectiveOutcome;

import java.time.Instant;
import java.util.UUID;

public record PortfolioObjectiveValidation(
        UUID id,

        UUID objectiveId,
        int validationNumber,

        UUID baselineSnapshotId,
        int baselineSnapshotNumber,

        UUID validationSnapshotId,
        int validationSnapshotNumber,

        PortfolioObjectiveOutcome outcome,

        int baselineScore,
        int validationScore,
        int scoreDelta,

        int baselineAssuranceDebt,
        int validationAssuranceDebt,
        int assuranceDebtDelta,

        int certifiedIncidentDelta,
        int validatedImprovementDelta,
        int auditVerifiedIncidentDelta,

        String executiveSummary,

        Instant createdAt
) {
}
