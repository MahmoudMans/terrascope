package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.AlertEventStatus;

import java.time.Instant;
import java.util.UUID;

public record AlertEvent(
        UUID id,
        UUID ruleId,
        String ruleName,
        String sourceEventId,
        String hazardType,
        String title,
        double latitude,
        double longitude,
        Double intensity,
        Instant observedAt,
        double distanceKm,
        AlertEventStatus status,
        Instant triggeredAt,
        Instant updatedAt
) {
}
