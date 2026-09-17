package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.ImprovementValidationStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.PreparednessValidationResult;

import java.util.UUID;

public record ImprovementValidationRecord(
        UUID improvementActionId,
        String title,
        IncidentActionPriority priority,

        int totalExerciseCount,

        UUID latestExerciseId,
        PreparednessValidationResult latestValidationResult,
        Integer latestReadinessScore,

        int openCorrectiveActionCount,

        ImprovementValidationStatus validationStatus
) {
}
