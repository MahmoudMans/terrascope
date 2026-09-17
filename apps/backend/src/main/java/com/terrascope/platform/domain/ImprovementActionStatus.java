package com.terrascope.platform.domain;

import java.util.Locale;

public enum ImprovementActionStatus {

    OPEN,
    IN_PROGRESS,
    COMPLETED,
    DISMISSED;

    public static ImprovementActionStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Improvement action status is required."
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
                    "Unsupported improvement action status '"
                            + value
                            + "'."
            );
        }
    }

    public boolean active() {
        return this == OPEN
                || this == IN_PROGRESS;
    }
}
