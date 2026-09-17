package com.terrascope.platform.domain;

import java.util.Locale;

public enum EvacuationZoneStatus {

    MONITORING,
    EVACUATING,
    CLEARED,
    CANCELLED;

    public static EvacuationZoneStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Evacuation zone status is required."
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
                    "Unsupported evacuation zone status '"
                            + value
                            + "'."
            );
        }
    }
}
