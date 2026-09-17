package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CommandBlocker;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SitrepNarrativeBuilderTest {

    private final SitrepNarrativeBuilder builder =
            new SitrepNarrativeBuilder();

    @Test
    void buildsDeterministicExecutiveSummary() {
        String summary =
                builder.buildExecutiveSummary(
                        incident(),
                        pulse()
                );

        assertThat(
                summary
        ).contains(
                "Verification Incident"
        );

        assertThat(
                summary
        ).contains(
                "49/100"
        );

        assertThat(
                summary
        ).contains(
                "ELEVATED"
        );

        assertThat(
                summary
        ).contains(
                "25.5 MUSD"
        );
    }

    @Test
    void buildsTopThreePrioritiesFromOrderedBlockers() {
        List<String> priorities =
                builder.buildPriorities(
                        pulse()
                );

        assertThat(
                priorities
        ).hasSize(3);

        assertThat(
                priorities.get(0)
        ).contains(
                "People remaining to evacuate"
        );

        assertThat(
                priorities.get(1)
        ).contains(
                "Shelter capacity below evacuation backlog"
        );

        assertThat(
                priorities.get(2)
        ).contains(
                "Critical infrastructure disruption"
        );
    }

    private IncidentCase incident() {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentCase(
                UUID.randomUUID(),
                null,
                "source-1",
                "earthquakes",
                "Verification Incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.SEVERE,
                IncidentStatus.RESPONDING,
                "Command",
                0,
                now,
                now,
                null
        );
    }

    private IncidentCommandPulseResponse pulse() {
        return new IncidentCommandPulseResponse(
                UUID.randomUUID(),

                49,
                CommandPressureBand.ELEVATED,

                1,
                1,
                1,

                1,

                10000,
                90,

                1,
                1,
                5000,

                25.5,
                1,
                0.0,

                List.of(
                        new CommandBlocker(
                                "EVACUATION_BACKLOG",
                                "People remaining to evacuate",
                                "10000",
                                15
                        ),
                        new CommandBlocker(
                                "SHELTER_CAPACITY_GAP",
                                "Shelter capacity below evacuation backlog",
                                "90 spaces available",
                                10
                        ),
                        new CommandBlocker(
                                "LIFELINE_DISRUPTION",
                                "Critical infrastructure disruption",
                                "1 disrupted / 1 critical",
                                7
                        ),
                        new CommandBlocker(
                                "RECOVERY_BACKLOG",
                                "Unresolved damage recovery",
                                "25.5 MUSD",
                                5
                        )
                ),

                Instant.parse(
                        "2026-09-17T12:00:00Z"
                )
        );
    }
}
