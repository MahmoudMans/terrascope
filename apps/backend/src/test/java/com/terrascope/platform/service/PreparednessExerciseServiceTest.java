package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AssessPreparednessExerciseRequest;
import com.terrascope.platform.api.model.CreatePreparednessExerciseRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentPreparednessExercise;
import com.terrascope.platform.api.model.PreparednessExerciseBoardResponse;
import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.PreparednessExerciseStatus;
import com.terrascope.platform.domain.PreparednessExerciseType;
import com.terrascope.platform.domain.PreparednessValidationResult;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.PreparednessExerciseRepository;
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

class PreparednessExerciseServiceTest {

    private PreparednessExerciseRepository repository;
    private IncidentResponseRepository incidentRepository;
    private PreparednessExerciseService service;

    private UUID incidentId;
    private UUID improvementId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        PreparednessExerciseRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new PreparednessExerciseService(
                        repository,
                        incidentRepository
                );

        incidentId =
                UUID.randomUUID();

        improvementId =
                UUID.randomUUID();

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident(
                                IncidentStatus.CLOSED
                        )
                )
        );
    }

    @Test
    void boardSummarizesValidationPerformance() {
        Instant now =
                Instant.now();

        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        exercise(
                                PreparednessExerciseStatus.PLANNED,
                                PreparednessValidationResult.NOT_ASSESSED,
                                null,
                                now.minusSeconds(
                                        3600
                                )
                        ),

                        exercise(
                                PreparednessExerciseStatus.COMPLETED,
                                PreparednessValidationResult.PASSED,
                                92,
                                now.minusSeconds(
                                        7200
                                )
                        )
                )
        );

        PreparednessExerciseBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.totalCount()
        ).isEqualTo(2);

        assertThat(
                board.plannedCount()
        ).isEqualTo(1);

        assertThat(
                board.completedCount()
        ).isEqualTo(1);

        assertThat(
                board.passedCount()
        ).isEqualTo(1);

        assertThat(
                board.overdueCount()
        ).isEqualTo(1);

        assertThat(
                board.averageReadinessScore()
        ).isEqualTo(92.0);
    }

    @Test
    void incompleteImprovementCannotCreateExercise() {
        when(
                repository.improvementStatus(
                        incidentId,
                        improvementId
                )
        ).thenReturn(
                Optional.of(
                        ImprovementActionStatus.IN_PROGRESS
                )
        );

        assertThatThrownBy(() ->
                service.create(
                        incidentId,
                        createRequest()
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void completedImprovementCreatesExerciseAndTimelineEvidence() {
        CreatePreparednessExerciseRequest request =
                createRequest();

        IncidentPreparednessExercise created =
                exercise(
                        PreparednessExerciseStatus.PLANNED,
                        PreparednessValidationResult.NOT_ASSESSED,
                        null,
                        request.scheduledAt()
                );

        when(
                repository.improvementStatus(
                        incidentId,
                        improvementId
                )
        ).thenReturn(
                Optional.of(
                        ImprovementActionStatus.COMPLETED
                )
        );

        when(
                repository.create(
                        any(),
                        eq(incidentId),
                        eq(request),
                        eq(
                                PreparednessExerciseType.TABLETOP
                        ),
                        eq(
                                "Preparedness Office"
                        )
                )
        ).thenReturn(
                created
        );

        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        created
                )
        );

        service.create(
                incidentId,
                request
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq(
                        "PREPAREDNESS_EXERCISE_CREATED"
                ),
                any()
        );
    }

    @Test
    void assessmentCompletesExerciseAndWritesTimelineEvidence() {
        IncidentPreparednessExercise before =
                exercise(
                        PreparednessExerciseStatus.IN_PROGRESS,
                        PreparednessValidationResult.NOT_ASSESSED,
                        null,
                        Instant.now()
                );

        AssessPreparednessExerciseRequest request =
                new AssessPreparednessExerciseRequest(
                        "PASSED",
                        94,
                        Instant.parse(
                                "2030-01-02T12:00:00Z"
                        ),
                        "Observers confirmed successful execution.",
                        "Minor timing variance.",
                        "Update drill timing guidance."
                );

        IncidentPreparednessExercise after =
                new IncidentPreparednessExercise(
                        before.id(),
                        before.incidentId(),
                        before.improvementActionId(),

                        before.exerciseType(),

                        before.title(),
                        before.description(),

                        PreparednessExerciseStatus.COMPLETED,

                        before.owner(),

                        before.scheduledAt(),
                        request.performedAt(),

                        PreparednessValidationResult.PASSED,
                        94,

                        request.evidenceSummary(),
                        request.findings(),
                        request.correctiveAction(),

                        before.createdAt(),
                        before.updatedAt(),
                        before.updatedAt()
                );

        when(
                repository.findById(
                        incidentId,
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                repository.assess(
                        eq(incidentId),
                        eq(before.id()),
                        eq(
                                PreparednessValidationResult.PASSED
                        ),
                        eq(request.performedAt()),
                        eq(request)
                )
        ).thenReturn(
                after
        );

        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        after
                )
        );

        service.assess(
                incidentId,
                before.id(),
                request
        );

        verify(
                repository
        ).assess(
                eq(incidentId),
                eq(before.id()),
                eq(
                        PreparednessValidationResult.PASSED
                ),
                eq(request.performedAt()),
                eq(request)
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq(
                        "PREPAREDNESS_EXERCISE_ASSESSED"
                ),
                any()
        );
    }

    private CreatePreparednessExerciseRequest createRequest() {
        return new CreatePreparednessExerciseRequest(
                improvementId,
                "TABLETOP",
                "Validate incident command handoff",
                "Test the improvement under a realistic command transition scenario.",
                "Preparedness Office",
                Instant.parse(
                        "2030-01-02T10:00:00Z"
                )
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
                status == IncidentStatus.CLOSED
                        ? updated
                        : null
        );
    }

    private IncidentPreparednessExercise exercise(
            PreparednessExerciseStatus status,
            PreparednessValidationResult result,
            Integer score,
            Instant scheduledAt
    ) {
        Instant now =
                Instant.now();

        return new IncidentPreparednessExercise(
                UUID.randomUUID(),
                incidentId,
                improvementId,

                PreparednessExerciseType.TABLETOP,

                "Validate incident command handoff",
                "",

                status,

                "Preparedness Office",

                scheduledAt,

                status == PreparednessExerciseStatus.COMPLETED
                        ? now
                        : null,

                result,
                score,

                result.assessed()
                        ? "Evidence"
                        : "",

                result.assessed()
                        ? "Findings"
                        : "",

                result.assessed()
                        ? "Follow-up"
                        : "",

                now,
                now,

                status == PreparednessExerciseStatus.COMPLETED
                        ? now
                        : null
        );
    }
}
