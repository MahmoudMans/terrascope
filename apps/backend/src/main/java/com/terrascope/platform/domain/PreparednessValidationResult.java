package com.terrascope.platform.domain;

import java.util.Locale;

public enum PreparednessValidationResult {

    NOT_ASSESSED,
    PASSED,
    PARTIAL,
    FAILED;

    public static PreparednessValidationResult fromValue(
            String value
    ) {
        if (
                value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Preparedness validation result is required."
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
                    "Unsupported preparedness validation result '"
                            + value
                            + "'."
            );
        }
    }

    public boolean assessed() {
        return this != NOT_ASSESSED;
    }
}
