package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CommandBlocker;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class SitrepNarrativeBuilder {

    public String buildExecutiveSummary(
            IncidentCase incident,
            IncidentCommandPulseResponse pulse
    ) {
        return String.format(
                Locale.ROOT,
                "Incident '%s' is currently %s at %s severity. "
                        + "Unified command pressure is %d/100 (%s). "
                        + "Operational workload includes %d open actions, "
                        + "%d overdue actions, %d blocked actions, and "
                        + "%d active resource shortages. "
                        + "The evacuation backlog is %d people with %d "
                        + "remaining shelter spaces. "
                        + "Critical infrastructure has %d disrupted lifeline "
                        + "assets affecting %d people. "
                        + "Unresolved damage is estimated at %.1f MUSD with "
                        + "%d critical damage assessments and %.1f%% average "
                        + "repair progress.",
                incident.title(),
                incident.status().name(),
                incident.severity().name(),
                pulse.score(),
                pulse.band().name(),
                pulse.openActionCount(),
                pulse.overdueActionCount(),
                pulse.blockedActionCount(),
                pulse.resourceShortageCount(),
                pulse.evacuationRemaining(),
                pulse.shelterRemainingCapacity(),
                pulse.lifelineDisruptedCount(),
                pulse.lifelineAffectedPopulation(),
                pulse.unresolvedDamageLossMusd(),
                pulse.criticalDamageCount(),
                pulse.averageRepairProgressPct()
        );
    }

    public List<String> buildPriorities(
            IncidentCommandPulseResponse pulse
    ) {
        if (
                pulse.blockers().isEmpty()
        ) {
            return List.of(
                    "Maintain monitoring and readiness; no scored operational blockers are active."
            );
        }

        return pulse
                .blockers()
                .stream()
                .limit(3)
                .map(
                        this::formatPriority
                )
                .toList();
    }

    private String formatPriority(
            CommandBlocker blocker
    ) {
        return blocker.label()
                + ": "
                + blocker.value()
                + " [pressure +"
                + blocker.weight()
                + "]";
    }
}
