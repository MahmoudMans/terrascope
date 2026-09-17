package com.terrascope.platform.domain;

import java.util.Locale;

public enum ShelterStatus {

    OPEN,
    FULL,
    CLOSED;

    public static ShelterStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Shelter status is required."
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
                    "Unsupported shelter status '"
                            + value
                            + "'."
            );
        }
    }
}
