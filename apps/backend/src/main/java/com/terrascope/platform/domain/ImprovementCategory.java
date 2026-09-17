package com.terrascope.platform.domain;

import java.util.Locale;

public enum ImprovementCategory {

    PEOPLE,
    PROCESS,
    TECHNOLOGY,
    COMMUNICATIONS,
    LOGISTICS,
    INFRASTRUCTURE,
    OTHER;

    public static ImprovementCategory fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Improvement category is required."
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
                    "Unsupported improvement category '"
                            + value
                            + "'."
            );
        }
    }
}
