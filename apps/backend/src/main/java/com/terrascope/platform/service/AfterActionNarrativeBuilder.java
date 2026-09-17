package com.terrascope.platform.service;

import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentClosureReadinessResponse;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.IncidentClosureReviewMetrics;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AfterActionNarrativeBuilder {

    public String build(
            IncidentCase incident,
            IncidentClosureReadinessResponse readiness,
            IncidentCommandPulseResponse pulse,
            IncidentClosureReviewMetrics metrics,
            int peakCommandScore,
            long durationMinutes
    ) {
        return String.format(
                Locale.ROOT,
                "Incident '%s' closed after %d minutes at %s severity. "
                        + "Closure readiness was %s with %d unresolved tracked items. "
                        + "Final command pressure was %d/100 (%s), while the highest "
                        + "recorded command pressure was %d/100. "
                        + "The operational record contains %d actions, %d resource "
                        + "requests, %d evacuation zones, %d shelters, %d lifeline "
                        + "assets, and %d damage assessments. "
                        + "The incident generated %d persisted situation reports and "
                        + "%d timeline events before this after-action review.",
                incident.title(),
                durationMinutes,
                incident.severity().name(),
                readiness.status().name(),
                readiness.unresolvedItemCount(),
                pulse.score(),
                pulse.band().name(),
                peakCommandScore,
                metrics.actionCount(),
                metrics.resourceRequestCount(),
                metrics.evacuationZoneCount(),
                metrics.shelterCount(),
                metrics.lifelineAssetCount(),
                metrics.damageAssessmentCount(),
                metrics.sitrepCount(),
                metrics.timelineEventCount()
        );
    }
}
