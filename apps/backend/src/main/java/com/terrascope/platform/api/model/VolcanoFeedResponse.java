package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.List;

public record VolcanoFeedResponse(
        String source,
        boolean live,
        Instant generatedAt,
        String status,
        int days,
        String search,
        int sourceEventCount,
        int matchedEventCount,
        int returnedEventCount,
        VolcanoSummary summary,
        List<VolcanoEvent> events
) {
}
