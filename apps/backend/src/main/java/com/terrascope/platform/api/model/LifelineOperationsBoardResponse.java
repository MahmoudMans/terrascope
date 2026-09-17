package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record LifelineOperationsBoardResponse(
        UUID incidentId,

        int totalCount,
        int operationalCount,
        int degradedCount,
        int outageCount,
        int restoredCount,

        int disruptedCount,
        int criticalDisruptionCount,

        long affectedPopulation,
        int etaTrackedCount,

        List<LifelineAsset> assets
) {
}
