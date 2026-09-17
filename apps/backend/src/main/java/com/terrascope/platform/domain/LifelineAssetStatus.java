package com.terrascope.platform.domain;

import java.util.Locale;

public enum LifelineAssetStatus {

    OPERATIONAL,
    DEGRADED,
    OUTAGE,
    RESTORED;

    public static LifelineAssetStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Lifeline status is required."
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
                    "Unsupported lifeline status '"
                            + value
                            + "'."
            );
        }
    }

    public boolean disrupted() {
        return this == DEGRADED
                || this == OUTAGE;
    }
}
