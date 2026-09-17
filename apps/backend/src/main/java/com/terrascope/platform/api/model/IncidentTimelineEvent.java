package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record IncidentTimelineEvent(
        UUID id,
        UUID incidentId,
        String eventType,
        String message,
        Instant createdAt
) {
}
