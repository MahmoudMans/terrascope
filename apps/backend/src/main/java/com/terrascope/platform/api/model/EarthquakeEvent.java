package com.terrascope.platform.api.model;

import java.time.Instant;

public record EarthquakeEvent(
        String id,
        double magnitude,
        String place,
        Instant occurredAt,
        Instant updatedAt,
        double longitude,
        double latitude,
        double depthKm,
        int significance,
        Integer feltReports,
        boolean tsunamiPotential,
        String alertLevel,
        String reviewStatus,
        String detailUrl
) {
}
