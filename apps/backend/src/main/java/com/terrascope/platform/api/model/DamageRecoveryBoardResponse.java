package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record DamageRecoveryBoardResponse(
        UUID incidentId,

        int totalCount,
        int reportedCount,
        int verifiedCount,
        int repairingCount,
        int restoredCount,

        int criticalDamageCount,

        long affectedUnits,

        double totalEstimatedLossMusd,
        double unresolvedEstimatedLossMusd,

        double averageRepairProgressPct,

        List<DamageAssessment> assessments
) {
}
