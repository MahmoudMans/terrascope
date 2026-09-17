package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.List;

public record EarthquakeFeedResponse(
        String source,
        boolean live,
        Instant generatedAt,
        String period,
        EarthquakeFilters filters,
        int sourceEventCount,
        int matchedEventCount,
        int returnedEventCount,
        EarthquakeSummary summary,
        List<EarthquakeEvent> events
) {
}
