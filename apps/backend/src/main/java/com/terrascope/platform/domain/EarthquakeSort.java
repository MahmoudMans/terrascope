package com.terrascope.platform.domain;

import java.util.Locale;

public enum EarthquakeSort {

    TIME,
    MAGNITUDE,
    DEPTH;

    public static EarthquakeSort from(String value) {
        if (value == null || value.isBlank()) {
            return TIME;
        }

        try {
            return EarthquakeSort.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported sort '" + value
                            + "'. Expected one of: time, magnitude, depth."
            );
        }
    }
}
