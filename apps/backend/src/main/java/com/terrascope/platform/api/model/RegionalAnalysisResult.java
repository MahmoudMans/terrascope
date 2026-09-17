package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.RegionalActivityBand;

import java.time.Instant;
import java.util.UUID;

public record RegionalAnalysisResult(
        UUID id,
        String regionName,
        double latitude,
        double longitude,
        double radiusKm,
        int sourcePointCount,
        int matchedEventCount,
        RegionalHazardBreakdown breakdown,
        RegionalNearestEvent nearestEvent,
        double averageDistanceKm,
        double activityIndex,
        RegionalActivityBand activityBand,
        Instant analyzedAt
) {
}
