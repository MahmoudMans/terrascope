package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.DamageAssetCategory;
import com.terrascope.platform.domain.DamageRecoveryStatus;
import com.terrascope.platform.domain.DamageSeverity;

import java.time.Instant;
import java.util.UUID;

public record DamageAssessment(
        UUID id,
        UUID incidentId,
        DamageAssetCategory category,
        String assetName,
        String description,
        DamageSeverity severity,
        DamageRecoveryStatus status,
        int affectedUnits,
        double estimatedLossMusd,
        String owner,
        Instant recoveryEta,
        int repairProgressPct,
        Instant createdAt,
        Instant updatedAt,
        Instant verifiedAt,
        Instant restoredAt
) {
}
