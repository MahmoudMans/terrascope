package com.terrascope.platform.domain;

import java.util.Locale;

public enum EarthquakePeriod {

    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month");

    private final String feedSuffix;

    EarthquakePeriod(String feedSuffix) {
        this.feedSuffix = feedSuffix;
    }

    public String feedSuffix() {
        return feedSuffix;
    }

    public static EarthquakePeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }

        try {
            return EarthquakePeriod.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported period '" + value
                            + "'. Expected one of: hour, day, week, month."
            );
        }
    }
}
