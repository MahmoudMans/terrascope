package com.terrascope.platform.domain;

import java.util.Locale;

public enum DamageSeverity {

    MINOR,
    MODERATE,
    MAJOR,
    DESTROYED;

    public static DamageSeverity fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Damage severity is required."
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
                    "Unsupported damage severity '"
                            + value
                            + "'."
            );
        }
    }

    public boolean critical() {
        return this == MAJOR
                || this == DESTROYED;
    }
}
