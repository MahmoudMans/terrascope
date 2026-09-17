package com.terrascope.platform.domain;

public enum PortfolioTrendDirection {

    BASELINE,
    IMPROVING,
    STABLE,
    DECLINING;

    public static PortfolioTrendDirection derive(
            boolean hasPreviousSnapshot,
            int scoreDelta,
            int assuranceDebtDelta
    ) {
        if (
                !hasPreviousSnapshot
        ) {
            return BASELINE;
        }

        if (
                scoreDelta >= 5
        ) {
            return IMPROVING;
        }

        if (
                scoreDelta <= -5
        ) {
            return DECLINING;
        }

        if (
                assuranceDebtDelta <= -3
                && scoreDelta >= 0
        ) {
            return IMPROVING;
        }

        if (
                assuranceDebtDelta >= 3
                && scoreDelta <= 0
        ) {
            return DECLINING;
        }

        return STABLE;
    }
}
