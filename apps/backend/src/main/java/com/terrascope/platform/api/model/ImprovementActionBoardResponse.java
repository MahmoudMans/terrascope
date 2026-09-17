package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record ImprovementActionBoardResponse(
        UUID incidentId,

        int totalCount,
        int openCount,
        int inProgressCount,
        int completedCount,
        int dismissedCount,
        int overdueCount,

        List<IncidentImprovementAction> actions
) {
}
