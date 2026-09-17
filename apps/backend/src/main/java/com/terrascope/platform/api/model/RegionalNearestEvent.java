package com.terrascope.platform.api.model;

public record RegionalNearestEvent(
        String id,
        String hazardType,
        String title,
        double distanceKm
) {
}
