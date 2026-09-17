package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record ResilienceValidationBoardResponse(
        UUID incidentId,

        int completedImprovementCount,

        int untestedCount,
        int validatedCount,
        int needsRevalidationCount,
        int failedCount,

        int totalCorrectiveActionCount,
        int openCorrectiveActionCount,
        int inProgressCorrectiveActionCount,
        int completedCorrectiveActionCount,
        int waivedCorrectiveActionCount,
        int overdueCorrectiveActionCount,

        double averageLatestReadinessScore,

        List<ImprovementValidationRecord> validations,
        List<PreparednessCorrectiveAction> correctiveActions
) {
}
