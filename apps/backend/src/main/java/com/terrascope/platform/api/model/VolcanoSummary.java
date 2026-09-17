package com.terrascope.platform.api.model;

import java.time.Instant;

public record VolcanoSummary(
        int eventCount,
        int totalObservationCount,
        int sourceReferenceCount,
        Instant latestObservationAt
) {
}
