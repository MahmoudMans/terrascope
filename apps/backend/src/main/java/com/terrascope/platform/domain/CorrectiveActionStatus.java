package com.terrascope.platform.domain;

import java.util.Locale;

public enum CorrectiveActionStatus {

    OPEN,
    IN_PROGRESS,
    COMPLETED,
    WAIVED;

    public static CorrectiveActionStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Corrective action status is required."
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
                    "Unsupported corrective action status '"
                            + value
                            + "'."
            );
        }
    }

    public boolean active() {
        return this == OPEN
                || this == IN_PROGRESS;
    }

    public boolean terminal() {
        return this == COMPLETED
                || this == WAIVED;
    }
}
