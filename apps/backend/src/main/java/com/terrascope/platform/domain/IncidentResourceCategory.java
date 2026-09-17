package com.terrascope.platform.domain;

import java.util.Locale;

public enum IncidentResourceCategory {

    PERSONNEL,
    MEDICAL,
    TRANSPORT,
    SHELTER,
    EQUIPMENT,
    COMMUNICATIONS,
    OTHER;

    public static IncidentResourceCategory fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Resource category is required."
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
                    "Unsupported resource category '"
                            + value
                            + "'."
            );
        }
    }
}
