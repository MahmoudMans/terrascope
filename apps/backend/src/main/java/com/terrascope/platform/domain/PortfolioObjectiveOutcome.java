package com.terrascope.platform.domain;

public enum PortfolioObjectiveOutcome {

    ACHIEVED,
    PARTIAL,
    NOT_ACHIEVED;

    public static PortfolioObjectiveOutcome derive(
            int scoreDelta,
            int assuranceDebtDelta
    ) {
        if (
                scoreDelta <= -5
                || assuranceDebtDelta >= 3
        ) {
            return NOT_ACHIEVED;
        }

        if (
                scoreDelta >= 5
                || assuranceDebtDelta <= -3
        ) {
            return ACHIEVED;
        }

        return PARTIAL;
    }
}
