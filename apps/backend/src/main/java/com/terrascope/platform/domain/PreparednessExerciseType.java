package com.terrascope.platform.domain;

import java.util.Locale;

public enum PreparednessExerciseType {

    TABLETOP,
    FUNCTIONAL,
    FULL_SCALE,
    TECHNICAL,
    COMMUNICATIONS,
    OTHER;

    public static PreparednessExerciseType fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Preparedness exercise type is required."
            );
        }

        try {
            return valueOf(
                    value
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
                            .replace(
                                    '-',
                                    '_'
                            )
                            .replace(
                                    ' ',
                                    '_'
                            )
            );

        } catch (
                IllegalArgumentException exception
        ) {
            throw new IllegalArgumentException(
                    "Unsupported preparedness exercise type '"
                            + value
                            + "'."
            );
        }
    }
}
