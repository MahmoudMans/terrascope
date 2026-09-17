package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.ClosureReadinessStatus;
import com.terrascope.platform.domain.CommandPressureBand;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentClosureReadinessResponse(
        UUID incidentId,

        ClosureReadinessStatus status,
        boolean ready,

        int commandScore,
        CommandPressureBand commandBand,

        int openActionCount,
        int resourceShortageCount,
        int activeEvacuationZoneCount,
        int activeShelterCount,
        int disruptedLifelineCount,
        int unresolvedDamageCount,

        int unresolvedItemCount,

        List<ClosureBlocker> blockers,

        Instant generatedAt
) {
}
