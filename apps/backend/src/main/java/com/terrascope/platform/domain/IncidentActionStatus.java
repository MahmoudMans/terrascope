package com.terrascope.platform.domain;

import java.util.Locale;

public enum IncidentActionStatus {

    TODO,
    IN_PROGRESS,
    BLOCKED,
    DONE;

    public static IncidentActionStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Action status is required."
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
                    "Unsupported action status '"
                            + value
                            + "'."
            );
        }
    }
}
