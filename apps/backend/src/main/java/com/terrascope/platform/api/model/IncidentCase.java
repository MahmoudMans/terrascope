package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentCase(
        UUID id,
        UUID alertEventId,
        String sourceEventId,
        String hazardType,
        String title,
        String description,
        double latitude,
        double longitude,
        IncidentSeverity severity,
        IncidentStatus status,
        String owner,
        int timelineEventCount,
        Instant openedAt,
        Instant updatedAt,
        Instant closedAt
) {
}
