package com.terrascope.platform.domain;

public enum HazardType {

    EARTHQUAKE(
            "earthquakes",
            "Earthquakes"
    ),

    VOLCANO(
            "volcanoes",
            "Volcanoes"
    ),

    WILDFIRE(
            "wildfires",
            "Wildfires"
    ),

    SEVERE_STORM(
            "severe-storms",
            "Severe storms"
    );

    private final String key;
    private final String label;

    HazardType(
            String key,
            String label
    ) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }
}
