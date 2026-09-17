package com.terrascope.platform.api.model;

import java.util.List;

public record AlertDashboardResponse(
        int ruleCount,
        int enabledRuleCount,
        int alertCount,
        int newAlertCount,
        int acknowledgedAlertCount,
        List<AlertRule> rules,
        List<AlertEvent> alerts
) {
}
