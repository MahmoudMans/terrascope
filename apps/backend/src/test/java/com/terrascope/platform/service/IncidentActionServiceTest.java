package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateIncidentActionRequest;
import com.terrascope.platform.api.model.IncidentActionBoardResponse;
import com.terrascope.platform.api.model.IncidentActionItem;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.UpdateIncidentActionStatusRequest;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentActionStatus;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentActionRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentActionServiceTest {

    private IncidentActionRepository actionRepository;
    private IncidentResponseRepository incidentRepository;
    private IncidentActionService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        actionRepository =
                mock(
                        IncidentActionRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new IncidentActionService(
                        actionRepository,
                        incidentRepository
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
    void boardSummarizesActionsAndOverdueItems() {
        when(
                actionRepository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        action(
                                IncidentActionStatus.TODO,
                                Instant.parse(
                                        "2020-01-01T00:00:00Z"
                                )
                        ),
                        action(
                                IncidentActionStatus.IN_PROGRESS,
                                null
                        ),
                        action(
                                IncidentActionStatus.BLOCKED,
                                null
                        ),
                        action(
                                IncidentActionStatus.DONE,
                                Instant.parse(
                                        "2020-01-01T00:00:00Z"
                                )
                        )
                )
        );

        IncidentActionBoardResponse board =
                service.board(
                        incidentId
                );

        assertThat(
                board.totalCount()
        ).isEqualTo(4);

        assertThat(
                board.todoCount()
        ).isEqualTo(1);

        assertThat(
                board.inProgressCount()
        ).isEqualTo(1);

        assertThat(
                board.blockedCount()
        ).isEqualTo(1);

        assertThat(
                board.doneCount()
        ).isEqualTo(1);

        assertThat(
                board.overdueCount()
        ).isEqualTo(1);
    }

    @Test
    void createWritesIncidentTimeline() {
        CreateIncidentActionRequest request =
                new CreateIncidentActionRequest(
                        "Secure perimeter",
                        "Establish field perimeter.",
                        "HIGH",
                        "Ops Alpha",
                        null
                );

        IncidentActionItem created =
                action(
                        IncidentActionStatus.TODO,
                        null
                );

        when(
                actionRepository.create(
                        any(),
                        eq(incidentId),
                        eq(request),
                        eq(
                                IncidentActionPriority.HIGH
                        ),
                        eq("Ops Alpha")
                )
        ).thenReturn(
                created
        );

        when(
                actionRepository.findAll(
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
                eq("ACTION_CREATED"),
                any()
        );
    }

    @Test
    void statusUpdateWritesTimeline() {
        IncidentActionItem before =
                action(
                        IncidentActionStatus.TODO,
                        null
                );

        when(
                actionRepository.findById(
                        incidentId,
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                actionRepository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        action(
                                IncidentActionStatus.IN_PROGRESS,
                                null
                        )
                )
        );

        service.updateStatus(
                incidentId,
                before.id(),
                new UpdateIncidentActionStatusRequest(
                        "IN_PROGRESS"
                )
        );

        verify(
                actionRepository
        ).updateStatus(
                incidentId,
                before.id(),
                IncidentActionStatus.IN_PROGRESS
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("ACTION_STATUS_CHANGED"),
                any()
        );
    }

    @Test
    void deleteWritesTimeline() {
        IncidentActionItem before =
                action(
                        IncidentActionStatus.BLOCKED,
                        null
                );

        when(
                actionRepository.findById(
                        incidentId,
                        before.id()
                )
        ).thenReturn(
                Optional.of(
                        before
                )
        );

        when(
                actionRepository.delete(
                        incidentId,
                        before.id()
                )
        ).thenReturn(1);

        when(
                actionRepository.findAll(
                        incidentId
                )
        ).thenReturn(
                List.of()
        );

        service.delete(
                incidentId,
                before.id()
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("ACTION_DELETED"),
                any()
        );
    }

    private IncidentCase incident(
            IncidentStatus status
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentCase(
                incidentId,
                null,
                "source-1",
                "earthquakes",
                "Test incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.HIGH,
                status,
                "Ops Alpha",
                0,
                now,
                now,
                null
        );
    }

    private IncidentActionItem action(
            IncidentActionStatus status,
            Instant dueAt
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentActionItem(
                UUID.randomUUID(),
                incidentId,
                "Test action",
                "",
                IncidentActionPriority.HIGH,
                status,
                "Ops Alpha",
                dueAt,
                now,
                now,
                status == IncidentActionStatus.DONE
                        ? now
                        : null
        );
    }
}
