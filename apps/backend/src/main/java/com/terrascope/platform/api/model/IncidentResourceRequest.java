package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentResourceCategory;
import com.terrascope.platform.domain.IncidentResourceStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResourceRequest(
        UUID id,
        UUID incidentId,
        IncidentResourceCategory category,
        String name,
        String description,
        IncidentActionPriority priority,
        IncidentResourceStatus status,
        int requestedQuantity,
        int allocatedQuantity,
        String unit,
        String owner,
        Instant createdAt,
        Instant updatedAt,
        Instant fulfilledAt,
        Instant cancelledAt
) {
}
