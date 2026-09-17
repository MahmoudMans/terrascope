package com.terrascope.platform.domain;

public enum ImprovementValidationStatus {

    UNTESTED,
    VALIDATED,
    NEEDS_REVALIDATION,
    FAILED;

    public static ImprovementValidationStatus derive(
            PreparednessValidationResult latestResult,
            int openCorrectiveActionCount
    ) {
        if (
                latestResult == null
                || latestResult
                == PreparednessValidationResult.NOT_ASSESSED
        ) {
            return UNTESTED;
        }

        if (
                latestResult
                == PreparednessValidationResult.FAILED
        ) {
            return FAILED;
        }

        if (
                latestResult
                == PreparednessValidationResult.PARTIAL
        ) {
            return NEEDS_REVALIDATION;
        }

        if (
                openCorrectiveActionCount > 0
        ) {
            return NEEDS_REVALIDATION;
        }

        return VALIDATED;
    }
}
