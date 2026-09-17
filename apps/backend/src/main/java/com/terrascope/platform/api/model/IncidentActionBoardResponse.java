package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record IncidentActionBoardResponse(
        UUID incidentId,
        int totalCount,
        int todoCount,
        int inProgressCount,
        int blockedCount,
        int doneCount,
        int overdueCount,
        List<IncidentActionItem> actions
) {
}
