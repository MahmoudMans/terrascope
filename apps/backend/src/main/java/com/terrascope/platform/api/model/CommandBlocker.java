package com.terrascope.platform.api.model;

public record CommandBlocker(
        String code,
        String label,
        String value,
        int weight
) {
}
