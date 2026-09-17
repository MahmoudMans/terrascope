package com.terrascope.platform.domain;

import java.util.UUID;

public record ImprovementValidationProjection(
        UUID improvementActionId,
        String title,
        IncidentActionPriority priority,

        int totalExerciseCount,

        UUID latestExerciseId,
        PreparednessValidationResult latestValidationResult,
        Integer latestReadinessScore,

        int openCorrectiveActionCount
) {
}
