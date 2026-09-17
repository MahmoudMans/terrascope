package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.CommandPressureBand;

import java.time.Instant;
import java.util.UUID;

public record CommandPulseSnapshot(
        UUID id,
        UUID incidentId,

        int score,
        CommandPressureBand band,
        int blockerCount,

        int openActionCount,
        int overdueActionCount,
        int blockedActionCount,

        int resourceShortageCount,

        long evacuationRemaining,
        long shelterRemainingCapacity,

        int lifelineDisruptedCount,
        int criticalLifelineDisruptionCount,
        long lifelineAffectedPopulation,

        double unresolvedDamageLossMusd,
        int criticalDamageCount,
        double averageRepairProgressPct,

        Instant createdAt
) {
}
