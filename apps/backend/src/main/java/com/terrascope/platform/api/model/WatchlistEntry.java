package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.WatchPriority;

import java.time.Instant;
import java.util.UUID;

public record WatchlistEntry(
        UUID id,
        String sourceEventId,
        String hazardType,
        String title,
        String description,
        double latitude,
        double longitude,
        Instant observedAt,
        String sourceProvider,
        String sourceUrl,
        Double magnitudeValue,
        String magnitudeUnit,
        WatchPriority priority,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
