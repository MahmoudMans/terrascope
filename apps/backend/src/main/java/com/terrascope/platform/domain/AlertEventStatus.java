package com.terrascope.platform.domain;

import java.util.Locale;

public enum AlertEventStatus {

    NEW,
    ACKNOWLEDGED,
    DISMISSED;

    public static AlertEventStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Alert status is required."
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
                    "Unsupported alert status '"
                            + value
                            + "'. Expected NEW, "
                            + "ACKNOWLEDGED or DISMISSED."
            );
        }
    }
}
