package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.PortfolioTrendDirection;

import java.time.Instant;
import java.util.UUID;

public record ResiliencePortfolioReviewPreview(
        UUID previousSnapshotId,
        Integer previousSnapshotNumber,

        PortfolioTrendDirection trend,

        int currentScore,
        Integer previousScore,
        int scoreDelta,

        int currentAssuranceDebt,
        Integer previousAssuranceDebt,
        int assuranceDebtDelta,

        int currentCertifiedIncidentCount,
        int certifiedIncidentDelta,

        int currentValidatedImprovementCount,
        int validatedImprovementDelta,

        int currentAuditVerifiedIncidentCount,
        int auditVerifiedIncidentDelta,

        String executiveSummary,

        Instant generatedAt
) {
}
