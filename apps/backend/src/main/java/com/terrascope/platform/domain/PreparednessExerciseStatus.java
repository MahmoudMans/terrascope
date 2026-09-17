package com.terrascope.platform.domain;

import java.util.Locale;

public enum PreparednessExerciseStatus {

    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static PreparednessExerciseStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Preparedness exercise status is required."
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
                    "Unsupported preparedness exercise status '"
                            + value
                            + "'."
            );
        }
    }

    public boolean mutable() {
        return this == PLANNED
                || this == IN_PROGRESS;
    }
}
