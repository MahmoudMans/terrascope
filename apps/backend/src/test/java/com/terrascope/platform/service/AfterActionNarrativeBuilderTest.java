package com.terrascope.platform.service;

import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentClosureReadinessResponse;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.ClosureReadinessStatus;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentClosureReviewMetrics;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AfterActionNarrativeBuilderTest {

    private final AfterActionNarrativeBuilder builder =
            new AfterActionNarrativeBuilder();

    @Test
    void summarizesReadyClosure() {
        String summary =
                builder.build(
                        incident(),
                        readiness(
                                true,
                                0
                        ),
                        pulse(
                                0
                        ),
                        metrics(
                                49
                        ),
                        49,
                        120
                );

        assertThat(
                summary
        ).contains(
                "120 minutes"
        );

        assertThat(
                summary
        ).contains(
                "Closure readiness was READY"
        );

        assertThat(
                summary
        ).contains(
                "highest recorded command pressure was 49/100"
        );
    }

    @Test
    void summarizesPrematureClosureWithUnresolvedItems() {
        String summary =
                builder.build(
                        incident(),
                        readiness(
                                false,
                                3
                        ),
                        pulse(
                                40
                        ),
                        metrics(
                                60
                        ),
                        60,
                        90
                );

        assertThat(
                summary
        ).contains(
                "Closure readiness was BLOCKED"
        );

        assertThat(
                summary
        ).contains(
                "3 unresolved tracked items"
        );

        assertThat(
                summary
        ).contains(
                "60/100"
        );
    }

    private IncidentCase incident() {
        Instant opened =
                Instant.parse(
                        "2026-09-17T10:00:00Z"
                );

        Instant closed =
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
                IncidentStatus.CLOSED,
                "Command",
                0,
                opened,
                closed,
                closed
        );
    }

    private IncidentClosureReadinessResponse readiness(
            boolean ready,
            int unresolved
    ) {
        return new IncidentClosureReadinessResponse(
                UUID.randomUUID(),

                ready
                        ? ClosureReadinessStatus.READY
                        : ClosureReadinessStatus.BLOCKED,

                ready,

                ready ? 0 : 40,

                ready
                        ? CommandPressureBand.STABLE
                        : CommandPressureBand.ELEVATED,

                unresolved,
                0,
                0,
                0,
                0,
                0,

                unresolved,

                List.of(),

                Instant.now()
        );
    }

    private IncidentCommandPulseResponse pulse(
            int score
    ) {
        return new IncidentCommandPulseResponse(
                UUID.randomUUID(),

                score,

                score == 0
                        ? CommandPressureBand.STABLE
                        : CommandPressureBand.ELEVATED,

                0,
                0,
                0,

                0,

                0,
                0,

                0,
                0,
                0,

                0,
                0,
                100,

                List.of(),

                Instant.now()
        );
    }

    private IncidentClosureReviewMetrics metrics(
            int peak
    ) {
        return new IncidentClosureReviewMetrics(
                0,
                0,
                0,
                0,
                0,
                0,

                2,
                10,

                1,
                1,
                1,
                1,
                1,
                1,

                peak
        );
    }
}
