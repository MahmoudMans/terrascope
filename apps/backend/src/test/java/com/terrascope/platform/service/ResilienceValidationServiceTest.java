package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreatePreparednessCorrectiveActionRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentPreparednessExercise;
import com.terrascope.platform.api.model.PreparednessCorrectiveAction;
import com.terrascope.platform.api.model.ResilienceValidationBoardResponse;
import com.terrascope.platform.api.model.UpdateCorrectiveActionOwnerRequest;
import com.terrascope.platform.domain.CorrectiveActionStatus;
import com.terrascope.platform.domain.ImprovementValidationProjection;
import com.terrascope.platform.domain.ImprovementValidationStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.PreparednessExerciseStatus;
import com.terrascope.platform.domain.PreparednessExerciseType;
import com.terrascope.platform.domain.PreparednessValidationResult;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.PreparednessExerciseRepository;
import com.terrascope.platform.repository.ResilienceValidationRepository;
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

class ResilienceValidationServiceTest {

    private ResilienceValidationRepository repository;
    private PreparednessExerciseRepository preparednessRepository;
    private IncidentResponseRepository incidentRepository;
    private ResilienceValidationService service;

    private UUID incidentId;
    private UUID improvementId;
    private UUID exerciseId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        ResilienceValidationRepository.class
                );

        preparednessRepository =
                mock(
                        PreparednessExerciseRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new ResilienceValidationService(
                        repository,
                        preparednessRepository,
                        incidentRepository
                );

        incidentId =
                UUID.randomUUID();

        improvementId =
                UUID.randomUUID();

        exerciseId =
                UUID.randomUUID();

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident()
                )
        );
    }

    @Test
    void boardSummarizesValidationStates() {
        when(
                repository.findValidationProjections(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        projection(
                                null,
                                null,
                                0
                        ),

                        projection(
                                PreparednessValidationResult.PASSED,
                                90,
                                0
                        ),

                        projection(
                                PreparednessValidationResult.PARTIAL,
                                60,
                                0
                        ),

                        projection(
                                PreparednessValidationResult.FAILED,
                                30,
                                1
                        )
                )
        );

        when(
                repository.findCorrectiveActions(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        correctiveAction(
                                CorrectiveActionStatus.OPEN,
                                Instant.now()
                                        .minusSeconds(
                                                3600
                                        )
                        )
                )
        );

        ResilienceValidationBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.completedImprovementCount()
        ).isEqualTo(4);

        assertThat(
                board.untestedCount()
        ).isEqualTo(1);

        assertThat(
                board.validatedCount()
        ).isEqualTo(1);

        assertThat(
                board.needsRevalidationCount()
        ).isEqualTo(1);

        assertThat(
                board.failedCount()
        ).isEqualTo(1);

        assertThat(
                board.openCorrectiveActionCount()
        ).isEqualTo(1);

        assertThat(
                board.overdueCorrectiveActionCount()
        ).isEqualTo(1);

        assertThat(
                board.averageLatestReadinessScore()
        ).isEqualTo(60.0);
    }

    @Test
    void passedExerciseRejectsCorrectiveActionCreation() {
        when(
                preparednessRepository.findById(
                        incidentId,
                        exerciseId
                )
        ).thenReturn(
                Optional.of(
                        exercise(
                                PreparednessValidationResult.PASSED
                        )
                )
        );

        assertThatThrownBy(() ->
                service.createCorrectiveAction(
                        incidentId,
                        exerciseId,
                        request()
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void failedExerciseCreatesCorrectiveActionAndTimelineEvidence() {
        IncidentPreparednessExercise exercise =
                exercise(
                        PreparednessValidationResult.FAILED
                );

        PreparednessCorrectiveAction created =
                correctiveAction(
                        CorrectiveActionStatus.OPEN,
                        request().dueAt()
                );

        when(
                preparednessRepository.findById(
                        incidentId,
                        exerciseId
                )
        ).thenReturn(
                Optional.of(
                        exercise
                )
        );

        when(
                repository.createCorrectiveAction(
                        any(),
                        eq(incidentId),
                        eq(exerciseId),
                        eq(improvementId),
                        eq(request()),
                        eq(
                                IncidentActionPriority.HIGH
                        ),
                        eq(
                                "Preparedness Office"
                        )
                )
        ).thenReturn(
                created
        );

        when(
                repository.findValidationProjections(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        projection(
                                PreparednessValidationResult.FAILED,
                                35,
                                1
                        )
                )
        );

        when(
                repository.findCorrectiveActions(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        created
                )
        );

        service.createCorrectiveAction(
                incidentId,
                exerciseId,
                request()
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq(
                        "PREPAREDNESS_CORRECTIVE_ACTION_CREATED"
                ),
                any()
        );
    }

    @Test
    void completedCorrectiveActionIsImmutable() {
        PreparednessCorrectiveAction completed =
                correctiveAction(
                        CorrectiveActionStatus.COMPLETED,
                        null
                );

        when(
                repository.findCorrectiveAction(
                        incidentId,
                        completed.id()
                )
        ).thenReturn(
                Optional.of(
                        completed
                )
        );

        assertThatThrownBy(() ->
                service.updateCorrectiveOwner(
                        incidentId,
                        completed.id(),
                        new UpdateCorrectiveActionOwnerRequest(
                                "New Owner"
                        )
                )
        ).isInstanceOf(
                ResponseStatusException.class
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
                incidentId,
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

    private ImprovementValidationProjection projection(
            PreparednessValidationResult result,
            Integer score,
            int openCorrectiveCount
    ) {
        return new ImprovementValidationProjection(
                UUID.randomUUID(),
                "Improvement action",
                IncidentActionPriority.HIGH,

                result == null
                        ? 0
                        : 1,

                result == null
                        ? null
                        : UUID.randomUUID(),

                result,
                score,

                openCorrectiveCount
        );
    }

    private IncidentPreparednessExercise exercise(
            PreparednessValidationResult result
    ) {
        Instant now =
                Instant.now();

        return new IncidentPreparednessExercise(
                exerciseId,
                incidentId,
                improvementId,

                PreparednessExerciseType.TABLETOP,

                "Validation Exercise",
                "",

                PreparednessExerciseStatus.COMPLETED,

                "Preparedness Office",

                now,
                now,

                result,

                result == PreparednessValidationResult.PASSED
                        ? 90
                        : 35,

                "Evidence",
                "Findings",
                "Corrective follow-up",

                now,
                now,
                now
        );
    }

    private CreatePreparednessCorrectiveActionRequest request() {
        return new CreatePreparednessCorrectiveActionRequest(
                "Tighten command handoff timing",
                "Address validation findings before the next exercise.",
                "HIGH",
                "Preparedness Office",
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                )
        );
    }

    private PreparednessCorrectiveAction correctiveAction(
            CorrectiveActionStatus status,
            Instant dueAt
    ) {
        Instant now =
                Instant.now();

        return new PreparednessCorrectiveAction(
                UUID.randomUUID(),
                incidentId,
                exerciseId,
                improvementId,

                "Tighten command handoff timing",
                "",

                IncidentActionPriority.HIGH,
                status,

                "Preparedness Office",

                dueAt,

                now,
                now,

                status == CorrectiveActionStatus.COMPLETED
                        ? now
                        : null
        );
    }
}
