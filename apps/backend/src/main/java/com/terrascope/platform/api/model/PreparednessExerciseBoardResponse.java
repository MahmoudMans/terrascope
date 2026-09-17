package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record PreparednessExerciseBoardResponse(
        UUID incidentId,

        int totalCount,
        int plannedCount,
        int inProgressCount,
        int completedCount,
        int cancelledCount,

        int passedCount,
        int partialCount,
        int failedCount,

        int overdueCount,

        double averageReadinessScore,

        List<IncidentPreparednessExercise> exercises
) {
}
