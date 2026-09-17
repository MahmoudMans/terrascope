package com.terrascope.platform.api.model;

import java.time.Instant;

public record NaturalHazardSummary(
        int eventCount,
        int totalObservationCount,
        int sourceReferenceCount,
        int magnitudeEventCount,
        Instant latestObservationAt
) {
}
