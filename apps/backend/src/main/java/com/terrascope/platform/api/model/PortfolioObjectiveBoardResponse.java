package com.terrascope.platform.api.model;

import java.util.List;

public record PortfolioObjectiveBoardResponse(
        int totalCount,

        int plannedCount,
        int inProgressCount,
        int blockedCount,
        int completedCount,
        int cancelledCount,

        int overdueCount,

        double averageProgressPct,

        List<PortfolioObjective> objectives
) {
}
