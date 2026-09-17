package com.terrascope.platform.domain;

public record CommandPulseMetrics(
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
        double averageRepairProgressPct
) {
}
