package com.terrascope.platform.domain;

public record IncidentClosureReviewMetrics(
        int openActionCount,
        int resourceShortageCount,
        int activeEvacuationZoneCount,
        int activeShelterCount,
        int disruptedLifelineCount,
        int unresolvedDamageCount,

        int sitrepCount,
        int timelineEventCount,

        int actionCount,
        int resourceRequestCount,
        int evacuationZoneCount,
        int shelterCount,
        int lifelineAssetCount,
        int damageAssessmentCount,

        int peakCommandScore
) {

    public int unresolvedItemCount() {
        return openActionCount
                + resourceShortageCount
                + activeEvacuationZoneCount
                + activeShelterCount
                + disruptedLifelineCount
                + unresolvedDamageCount;
    }
}
