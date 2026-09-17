package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.ClosureReadinessStatus;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentSeverity;

import java.time.Instant;
import java.util.UUID;

public record IncidentAfterActionReview(
        UUID id,
        UUID incidentId,
        int reviewNumber,

        String incidentTitle,
        String hazardType,
        IncidentSeverity incidentSeverity,

        Instant openedAt,
        Instant closedAt,
        long durationMinutes,

        ClosureReadinessStatus closureReadinessStatus,
        boolean closureReady,

        int finalCommandScore,
        CommandPressureBand finalCommandBand,
        int peakCommandScore,

        int sitrepCount,
        int timelineEventCount,

        int actionCount,
        int resourceRequestCount,
        int evacuationZoneCount,
        int shelterCount,
        int lifelineAssetCount,
        int damageAssessmentCount,

        int unresolvedItemCount,

        String executiveSummary,

        Instant createdAt
) {
}
