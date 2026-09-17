package com.terrascope.platform.api.model;

public record AlertEvaluationResponse(
        int evaluatedRuleCount,
        int evaluatedPointCount,
        int newlyTriggeredCount,
        AlertDashboardResponse dashboard
) {
}
