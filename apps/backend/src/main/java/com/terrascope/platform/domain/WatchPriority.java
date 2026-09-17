package com.terrascope.platform.domain;

import java.util.Locale;

public enum WatchPriority {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static WatchPriority fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            return MEDIUM;
        }

        try {
            return WatchPriority.valueOf(
                    value
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );

        } catch (
                IllegalArgumentException exception
        ) {
            throw new IllegalArgumentException(
                    "Unsupported watch priority '"
                            + value
                            + "'. Expected one of: "
                            + "LOW, MEDIUM, HIGH, CRITICAL."
            );
        }
    }
}
