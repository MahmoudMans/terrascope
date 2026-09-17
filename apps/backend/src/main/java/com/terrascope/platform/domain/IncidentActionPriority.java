package com.terrascope.platform.domain;

import java.util.Locale;

public enum IncidentActionPriority {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static IncidentActionPriority fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Action priority is required."
            );
        }

        try {
            return valueOf(
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
                    "Unsupported action priority '"
                            + value
                            + "'."
            );
        }
    }
}
