package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.PortfolioObjectivePriority;
import com.terrascope.platform.domain.PortfolioObjectiveStatus;

import java.time.Instant;
import java.util.UUID;

public record PortfolioObjective(
        UUID id,

        UUID portfolioReviewId,
        int portfolioReviewNumber,

        String title,
        String description,

        PortfolioObjectivePriority priority,
        PortfolioObjectiveStatus status,

        String owner,

        Instant targetAt,

        int progressPct,

        String successMetric,
        String completionEvidence,

        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
