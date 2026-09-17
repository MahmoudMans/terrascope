package com.terrascope.platform.api.model;

import java.time.Instant;

public record VolcanoEvent(
        String id,
        String title,
        String description,
        Instant observedAt,
        double longitude,
        double latitude,
        int observationCount,
        int sourceCount,
        String status,
        String primarySource,
        String sourceUrl,
        String detailUrl
) {
}
