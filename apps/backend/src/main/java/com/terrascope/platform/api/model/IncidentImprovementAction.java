package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.ImprovementCategory;
import com.terrascope.platform.domain.IncidentActionPriority;

import java.time.Instant;
import java.util.UUID;

public record IncidentImprovementAction(
        UUID id,
        UUID incidentId,
        UUID afterActionReviewId,

        ImprovementCategory category,

        String title,
        String description,

        IncidentActionPriority priority,
        ImprovementActionStatus status,

        String owner,

        Instant dueAt,

        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
