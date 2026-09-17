package com.terrascope.platform.service;

import com.terrascope.platform.api.model.IncidentAfterActionReview;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentClosureReadinessResponse;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.ClosureReadinessStatus;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentClosureReviewMetrics;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentClosureReviewRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentClosureReviewServiceTest {

    private IncidentClosureReviewRepository repository;
    private IncidentResponseRepository incidentRepository;
    private IncidentCommandPulseService commandPulseService;
    private IncidentClosureReviewService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        IncidentClosureReviewRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        commandPulseService =
                mock(
                        IncidentCommandPulseService.class
                );

        service =
                new IncidentClosureReviewService(
                        repository,
                        incidentRepository,
                        commandPulseService,
                        new AfterActionNarrativeBuilder()
                );

        incidentId =
                UUID.randomUUID();

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident(
                                IncidentStatus.RESPONDING
                        )
                )
        );
    }

    @Test
    void emptyOperationalStateIsReadyForClosure() {
        when(
                commandPulseService.pulse(
                        incidentId
                )
        ).thenReturn(
                pulse(
                        0
                )
        );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                metrics(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                )
        );

        IncidentClosureReadinessResponse readiness =
                service.readiness(
                        incidentId
                );

        assertThat(
                readiness.ready()
        ).isTrue();

        assertThat(
                readiness.status()
        ).isEqualTo(
                ClosureReadinessStatus.READY
        );

        assertThat(
                readiness.unresolvedItemCount()
        ).isZero();

        assertThat(
                readiness.blockers()
        ).isEmpty();
    }

    @Test
    void unresolvedOperationalStateIsBlocked() {
        when(
                commandPulseService.pulse(
                        incidentId
                )
        ).thenReturn(
                pulse(
                        49
                )
        );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                metrics(
                        1,
                        1,
                        1,
                        1,
                        1,
                        1,
                        49
                )
        );

        IncidentClosureReadinessResponse readiness =
                service.readiness(
                        incidentId
                );

        assertThat(
                readiness.ready()
        ).isFalse();

        assertThat(
                readiness.status()
        ).isEqualTo(
                ClosureReadinessStatus.BLOCKED
        );

        assertThat(
                readiness.unresolvedItemCount()
        ).isEqualTo(6);

        assertThat(
                readiness.blockers()
        ).hasSize(7);
    }

    @Test
    void activeIncidentCannotCreateAfterActionReview() {
        assertThatThrownBy(() ->
                service.createReview(
                        incidentId
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void closedIncidentCreatesPersistentReviewAndTimelineEvidence() {
        IncidentCase closedIncident =
                incident(
                        IncidentStatus.CLOSED
                );

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        closedIncident
                )
        );

        when(
                commandPulseService.pulse(
                        incidentId
                )
        ).thenReturn(
                pulse(
                        0
                )
        );

        IncidentClosureReviewMetrics metrics =
                metrics(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        49
                );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                metrics
        );

        when(
                repository.nextReviewNumber(
                        incidentId
                )
        ).thenReturn(1);

        when(
                repository.saveReview(
                        any(),
                        eq(1),
                        eq(closedIncident),
                        any(),
                        any(),
                        eq(metrics),
                        eq(49),
                        any(Long.class),
                        any()
                )
        ).thenReturn(
                review()
        );

        IncidentAfterActionReview review =
                service.createReview(
                        incidentId
                );

        assertThat(
                review.reviewNumber()
        ).isEqualTo(1);

        assertThat(
                review.peakCommandScore()
        ).isEqualTo(49);

        verify(
                repository
        ).lockIncident(
                incidentId
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq(
                        "AFTER_ACTION_REVIEW_CREATED"
                ),
                any()
        );
    }

    private IncidentCase incident(
            IncidentStatus status
    ) {
        Instant opened =
                Instant.parse(
                        "2026-09-17T10:00:00Z"
                );

        Instant updated =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        Instant closed =
                status == IncidentStatus.CLOSED
                        ? updated
                        : null;

        return new IncidentCase(
                incidentId,
                null,
                "source-1",
                "earthquakes",
                "Verification Incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.SEVERE,
                status,
                "Command",
                0,
                opened,
                updated,
                closed
        );
    }

    private IncidentCommandPulseResponse pulse(
            int score
    ) {
        return new IncidentCommandPulseResponse(
                incidentId,

                score,

                score == 0
                        ? CommandPressureBand.STABLE
                        : CommandPressureBand.ELEVATED,

                0,
                score > 0 ? 1 : 0,
                score > 0 ? 1 : 0,

                score > 0 ? 1 : 0,

                score > 0 ? 10000 : 0,
                score > 0 ? 90 : 0,

                score > 0 ? 1 : 0,
                score > 0 ? 1 : 0,
                score > 0 ? 5000 : 0,

                score > 0 ? 25.5 : 0,
                score > 0 ? 1 : 0,
                score > 0 ? 0 : 100,

                List.of(),

                Instant.now()
        );
    }

    private IncidentClosureReviewMetrics metrics(
            int actions,
            int shortages,
            int evacuations,
            int shelters,
            int lifelines,
            int damage,
            int peak
    ) {
        return new IncidentClosureReviewMetrics(
                actions,
                shortages,
                evacuations,
                shelters,
                lifelines,
                damage,

                2,
                20,

                1,
                1,
                1,
                1,
                1,
                1,

                peak
        );
    }

    private IncidentAfterActionReview review() {
        Instant opened =
                Instant.parse(
                        "2026-09-17T10:00:00Z"
                );

        Instant closed =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentAfterActionReview(
                UUID.randomUUID(),
                incidentId,
                1,

                "Verification Incident",
                "earthquakes",
                IncidentSeverity.SEVERE,

                opened,
                closed,
                120,

                ClosureReadinessStatus.READY,
                true,

                0,
                CommandPressureBand.STABLE,
                49,

                2,
                20,

                1,
                1,
                1,
                1,
                1,
                1,

                0,

                "After-action summary.",

                Instant.now()
        );
    }
}
