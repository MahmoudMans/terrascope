package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentSituationReport(
        UUID id,
        UUID incidentId,
        int reportNumber,

        String incidentTitle,
        IncidentStatus incidentStatus,
        IncidentSeverity incidentSeverity,

        int commandScore,
        CommandPressureBand commandBand,

        String executiveSummary,

        List<String> priorities,

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
