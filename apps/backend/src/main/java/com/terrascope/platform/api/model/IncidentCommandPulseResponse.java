package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.CommandPressureBand;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentCommandPulseResponse(
        UUID incidentId,

        int score,
        CommandPressureBand band,

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

        List<CommandBlocker> blockers,

        Instant generatedAt
) {
}
