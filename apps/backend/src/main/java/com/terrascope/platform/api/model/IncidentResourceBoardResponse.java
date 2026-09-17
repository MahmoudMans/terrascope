package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record IncidentResourceBoardResponse(
        UUID incidentId,
        int totalCount,
        int requestedCount,
        int partiallyAllocatedCount,
        int fulfilledCount,
        int cancelledCount,
        int shortageCount,
        List<IncidentResourceRequest> resources
) {
}
