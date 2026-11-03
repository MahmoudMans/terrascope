package com.terrascope.platform.api.model;

import java.time.Instant;

public record AutomationStatus(
        boolean enabled,
        boolean evaluatesAlerts,
        long intervalSeconds,
        Instant lastRunAt,
        boolean lastRunSucceeded,
        int lastPointCount,
        int lastNewAlertCount,
        int connectedClients
) {
}
