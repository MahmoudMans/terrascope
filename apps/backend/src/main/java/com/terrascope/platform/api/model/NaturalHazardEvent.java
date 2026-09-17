package com.terrascope.platform.api.model;

import java.time.Instant;

public record NaturalHazardEvent(
        String id,
        String hazardType,
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
        String detailUrl,
        Double magnitudeValue,
        String magnitudeUnit,
        String magnitudeDescription
) {
}
