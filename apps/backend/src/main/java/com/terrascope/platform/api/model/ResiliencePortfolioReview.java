package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.PortfolioTrendDirection;

import java.time.Instant;
import java.util.UUID;

public record ResiliencePortfolioReview(
        UUID id,
        int reviewNumber,

        UUID currentSnapshotId,
        int currentSnapshotNumber,

        UUID previousSnapshotId,
        Integer previousSnapshotNumber,

        PortfolioTrendDirection trend,

        int currentScore,
        Integer previousScore,
        int scoreDelta,

        int currentAssuranceDebt,
        Integer previousAssuranceDebt,
        int assuranceDebtDelta,

        int certifiedIncidentDelta,
        int validatedImprovementDelta,
        int auditVerifiedIncidentDelta,

        String executiveSummary,

        Instant createdAt
) {
}
