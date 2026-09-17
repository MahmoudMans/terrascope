package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record AlertRule(
        UUID id,
        String name,
        String hazardType,
        double latitude,
        double longitude,
        double radiusKm,
        double minimumIntensity,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
