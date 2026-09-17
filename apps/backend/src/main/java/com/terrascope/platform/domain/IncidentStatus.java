package com.terrascope.platform.domain;

import java.util.Locale;

public enum IncidentStatus {

    OPEN,
    MONITORING,
    RESPONDING,
    STABILIZED,
    CLOSED;

    public static IncidentStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Incident status is required."
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
                    "Unsupported incident status '"
                            + value
                            + "'."
            );
        }
    }
}
