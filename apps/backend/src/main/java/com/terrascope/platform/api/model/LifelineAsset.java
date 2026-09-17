package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.LifelineAssetCategory;
import com.terrascope.platform.domain.LifelineAssetStatus;

import java.time.Instant;
import java.util.UUID;

public record LifelineAsset(
        UUID id,
        UUID incidentId,
        LifelineAssetCategory category,
        String name,
        String description,
        IncidentActionPriority priority,
        LifelineAssetStatus status,
        int affectedPopulation,
        String owner,
        Instant restorationEta,
        Instant createdAt,
        Instant updatedAt,
        Instant restoredAt
) {
}
