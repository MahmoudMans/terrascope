package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.List;

public record NaturalHazardFeedResponse(
        String hazardType,
        String source,
        boolean live,
        Instant generatedAt,
        String status,
        int days,
        String search,
        int sourceEventCount,
        int matchedEventCount,
        int returnedEventCount,
        NaturalHazardSummary summary,
        List<NaturalHazardEvent> events
) {
}
