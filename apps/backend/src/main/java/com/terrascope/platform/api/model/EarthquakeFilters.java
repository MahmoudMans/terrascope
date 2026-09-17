package com.terrascope.platform.api.model;

public record EarthquakeFilters(
        double minMagnitude,
        double maxDepthKm,
        int limit,
        String search,
        String sort
) {
}
