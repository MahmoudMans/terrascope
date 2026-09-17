package com.terrascope.platform.domain;

import java.util.Locale;

public enum LifelineAssetCategory {

    POWER,
    WATER,
    HEALTHCARE,
    TRANSPORT,
    COMMUNICATIONS,
    FUEL,
    OTHER;

    public static LifelineAssetCategory fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Lifeline category is required."
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
                    "Unsupported lifeline category '"
                            + value
                            + "'."
            );
        }
    }
}
