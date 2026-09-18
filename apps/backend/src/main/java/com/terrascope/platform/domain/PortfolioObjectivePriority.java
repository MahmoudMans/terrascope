package com.terrascope.platform.domain;

import java.util.Locale;

public enum PortfolioObjectivePriority {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static PortfolioObjectivePriority fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Portfolio objective priority is required."
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
                    "Unsupported portfolio objective priority '"
                            + value
                            + "'."
            );
        }
    }
}
