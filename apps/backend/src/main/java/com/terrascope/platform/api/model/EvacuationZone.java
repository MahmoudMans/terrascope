package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.EvacuationZoneStatus;
import com.terrascope.platform.domain.IncidentActionPriority;

import java.time.Instant;
import java.util.UUID;

public record EvacuationZone(
        UUID id,
        UUID incidentId,
        String name,
        String description,
        IncidentActionPriority priority,
        EvacuationZoneStatus status,
        int populationAtRisk,
        int evacuatedPopulation,
        String owner,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        Instant cancelledAt
) {
}
