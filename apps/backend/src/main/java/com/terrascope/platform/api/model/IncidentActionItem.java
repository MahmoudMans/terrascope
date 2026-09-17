package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentActionStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentActionItem(
        UUID id,
        UUID incidentId,
        String title,
        String description,
        IncidentActionPriority priority,
        IncidentActionStatus status,
        String owner,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
