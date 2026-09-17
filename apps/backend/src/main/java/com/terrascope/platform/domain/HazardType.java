package com.terrascope.platform.domain;

import java.util.Arrays;
import java.util.Locale;

public enum HazardType {

    EARTHQUAKE(
            "earthquakes",
            "Earthquakes",
            null
    ),

    VOLCANO(
            "volcanoes",
            "Volcanoes",
            "volcanoes"
    ),

    WILDFIRE(
            "wildfires",
            "Wildfires",
            "wildfires"
    ),

    SEVERE_STORM(
            "severe-storms",
            "Severe storms",
            "severeStorms"
    );

    private final String key;
    private final String label;
    private final String eonetCategory;

    HazardType(
            String key,
            String label,
            String eonetCategory
    ) {
        this.key = key;
        this.label = label;
        this.eonetCategory = eonetCategory;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String eonetCategory() {
        return eonetCategory;
    }

    public boolean hasEonetCategory() {
        return eonetCategory != null;
    }

    public boolean isGenericEonetHazard() {
        return this == WILDFIRE
                || this == SEVERE_STORM;
    }

    public static HazardType fromKey(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Hazard type is required."
            );
        }

        String normalized =
                value
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return Arrays
                .stream(values())
                .filter(type ->
                        type.key.equalsIgnoreCase(
                                normalized
                        )
                        || (
                                type.eonetCategory != null
                                && type.eonetCategory
                                .equalsIgnoreCase(
                                        normalized
                                )
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported hazard type '"
                                        + value
                                        + "'."
                        )
                );
    }
}
