package com.terrascope.platform.domain;

import java.util.Locale;

public enum IncidentSeverity {

    LOW,
    MODERATE,
    HIGH,
    SEVERE,
    CRITICAL;

    public static IncidentSeverity fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Incident severity is required."
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
                    "Unsupported incident severity '"
                            + value
                            + "'."
            );
        }
    }
}
