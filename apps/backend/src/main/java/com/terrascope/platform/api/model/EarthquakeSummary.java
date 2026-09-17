package com.terrascope.platform.api.model;

import java.time.Instant;

public record EarthquakeSummary(
        int eventCount,
        double strongestMagnitude,
        double averageDepthKm,
        int significantEventCount,
        int tsunamiPotentialCount,
        Instant latestEventAt
) {
}
