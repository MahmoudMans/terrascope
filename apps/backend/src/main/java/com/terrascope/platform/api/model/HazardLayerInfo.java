package com.terrascope.platform.api.model;

public record HazardLayerInfo(
        String id,
        String label,
        String source,
        String status,
        boolean available,
        boolean defaultVisible
) {
}
