package com.terrascope.platform.domain;

import java.util.Locale;

public enum PortfolioObjectiveStatus {

    PLANNED,
    IN_PROGRESS,
    BLOCKED,
    COMPLETED,
    CANCELLED;

    public static PortfolioObjectiveStatus fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Portfolio objective status is required."
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
                    "Unsupported portfolio objective status '"
                            + value
                            + "'."
            );
        }
    }

    public boolean mutable() {
        return this == PLANNED
                || this == IN_PROGRESS
                || this == BLOCKED;
    }

    public boolean terminal() {
        return this == COMPLETED
                || this == CANCELLED;
    }
}
