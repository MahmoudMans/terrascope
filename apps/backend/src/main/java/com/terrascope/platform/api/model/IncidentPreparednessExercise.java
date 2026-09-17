package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.PreparednessExerciseStatus;
import com.terrascope.platform.domain.PreparednessExerciseType;
import com.terrascope.platform.domain.PreparednessValidationResult;

import java.time.Instant;
import java.util.UUID;

public record IncidentPreparednessExercise(
        UUID id,
        UUID incidentId,
        UUID improvementActionId,

        PreparednessExerciseType exerciseType,

        String title,
        String description,

        PreparednessExerciseStatus status,

        String owner,

        Instant scheduledAt,
        Instant performedAt,

        PreparednessValidationResult validationResult,
        Integer readinessScore,

        String evidenceSummary,
        String findings,
        String correctiveAction,

        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
