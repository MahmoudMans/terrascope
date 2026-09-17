package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.CorrectiveActionStatus;
import com.terrascope.platform.domain.IncidentActionPriority;

import java.time.Instant;
import java.util.UUID;

public record PreparednessCorrectiveAction(
        UUID id,
        UUID incidentId,
        UUID exerciseId,
        UUID improvementActionId,

        String title,
        String description,

        IncidentActionPriority priority,
        CorrectiveActionStatus status,

        String owner,

        Instant dueAt,

        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
