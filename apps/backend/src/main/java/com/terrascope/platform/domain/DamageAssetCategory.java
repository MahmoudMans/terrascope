package com.terrascope.platform.domain;

import java.util.Locale;

public enum DamageAssetCategory {

    RESIDENTIAL,
    COMMERCIAL,
    PUBLIC_BUILDING,
    ROAD,
    BRIDGE,
    UTILITY,
    HEALTHCARE,
    OTHER;

    public static DamageAssetCategory fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Damage asset category is required."
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
                    "Unsupported damage asset category '"
                            + value
                            + "'."
            );
        }
    }
}
