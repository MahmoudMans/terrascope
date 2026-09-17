package com.terrascope.platform.api.model;

import java.util.List;

public record IncidentDashboardResponse(
        int totalCount,
        int activeCount,
        int respondingCount,
        int criticalCount,
        int closedCount,
        List<IncidentCase> incidents
) {
}
