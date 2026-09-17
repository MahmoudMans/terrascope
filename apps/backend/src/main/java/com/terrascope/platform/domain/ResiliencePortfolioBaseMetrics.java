package com.terrascope.platform.domain;

public record ResiliencePortfolioBaseMetrics(
        int totalIncidentCount,
        int openIncidentCount,
        int closedIncidentCount,

        int certifiedIncidentCount,
        int evidencePackagedIncidentCount,
        int auditLedgerIncidentCount,

        int totalExerciseCount,
        int completedExerciseCount,
        int passedExerciseCount
) {
}
