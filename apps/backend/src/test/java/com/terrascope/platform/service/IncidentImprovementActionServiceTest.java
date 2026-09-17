package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateImprovementActionRequest;
import com.terrascope.platform.api.model.ImprovementActionBoardResponse;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentImprovementAction;
import com.terrascope.platform.api.model.UpdateImprovementStatusRequest;
import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.ImprovementCategory;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentImprovementActionRepository;
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

class IncidentImprovementActionServiceTest {

    private IncidentImprovementActionRepository repository;
    private IncidentResponseRepository incidentRepository;
    private IncidentImprovementActionService service;

    private UUID incidentId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        IncidentImprovementActionRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new IncidentImprovementActionService(
                        repository,
                        incidentRepository
                );

        incidentId =
                UUID.randomUUID();

        reviewId =
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
    void boardSummarizesImprovementWorkflow() {
        Instant now =
                Instant.now();

        when(
                repository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        action(
                                ImprovementActionStatus.OPEN,
                                now.minusSeconds(
                                        3600
                                )
                        ),

                        action(
                                ImprovementActionStatus.IN_PROGRESS,
                                now.plusSeconds(
                                        3600
                                )
                        ),

                        action(
                                ImprovementActionStatus.COMPLETED,
                                now.minusSeconds(
                                        3600
                                )
                        ),

                        action(
                                ImprovementActionStatus.DISMISSED,
                                null
                        )
                )
        );

        ImprovementActionBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.totalCount()
        ).isEqualTo(4);

        assertThat(
                board.openCount()
        ).isEqualTo(1);

        assertThat(
                board.inProgressCount()
        ).isEqualTo(1);

        assertThat(
                board.completedCount()
        ).isEqualTo(1);

        assertThat(
                board.dismissedCount()
        ).isEqualTo(1);

        assertThat(
                board.overdueCount()
        ).isEqualTo(1);
    }

    @Test
    void activeIncidentRejectsImprovementCreation() {
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

        assertThatThrownBy(() ->
                service.create(
                        incidentId,
                        request()
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void creationRequiresMatchingReviewAndWritesTimeline() {
        CreateImprovementActionRequest request =
                request();

        IncidentImprovementAction created =
                action(
                        ImprovementActionStatus.OPEN,
                        request.dueAt()
                );

        when(
                repository.reviewBelongsToIncident(
                        incidentId,
                        reviewId
                )
        ).thenReturn(
                true
        );

        when(
                repository.create(
                        any(),
                        eq(incidentId),
                        eq(request),
                        eq(
                                ImprovementCategory.PROCESS
                        ),
                        eq(
                                IncidentActionPriority.HIGH
                        ),
                        eq(
                                "Resilience Office"
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
                        "IMPROVEMENT_ACTION_CREATED"
                ),
                any()
        );
    }

    @Test
    void completionWritesWorkflowEvidence() {
        IncidentImprovementAction before =
                action(
                        ImprovementActionStatus.IN_PROGRESS,
                        null
                );

        IncidentImprovementAction after =
                new IncidentImprovementAction(
                        before.id(),
                        before.incidentId(),
                        before.afterActionReviewId(),
                        before.category(),
                        before.title(),
                        before.description(),
                        before.priority(),
                        ImprovementActionStatus.COMPLETED,
                        before.owner(),
                        before.dueAt(),
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
                repository.updateStatus(
                        incidentId,
                        before.id(),
                        ImprovementActionStatus.COMPLETED
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

        service.updateStatus(
                incidentId,
                before.id(),
                new UpdateImprovementStatusRequest(
                        "COMPLETED"
                )
        );

        verify(
                repository
        ).updateStatus(
                incidentId,
                before.id(),
                ImprovementActionStatus.COMPLETED
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq(
                        "IMPROVEMENT_STATUS_CHANGED"
                ),
                any()
        );
    }

    private CreateImprovementActionRequest request() {
        return new CreateImprovementActionRequest(
                reviewId,
                "PROCESS",
                "Formalize command handoff checklist",
                "Convert the after-action finding into a repeatable checklist.",
                "HIGH",
                "Resilience Office",
                Instant.parse(
                        "2030-01-01T00:00:00Z"
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

    private IncidentImprovementAction action(
            ImprovementActionStatus status,
            Instant dueAt
    ) {
        Instant now =
                Instant.now();

        return new IncidentImprovementAction(
                UUID.randomUUID(),
                incidentId,
                reviewId,

                ImprovementCategory.PROCESS,

                "Formalize command handoff checklist",
                "",

                IncidentActionPriority.HIGH,
                status,

                "Resilience Office",

                dueAt,

                now,
                now,

                status
                        == ImprovementActionStatus.COMPLETED
                        ? now
                        : null
        );
    }
}
