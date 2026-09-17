package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AddIncidentNoteRequest;
import com.terrascope.platform.api.model.CreateIncidentRequest;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentDashboardResponse;
import com.terrascope.platform.api.model.IncidentTimelineEvent;
import com.terrascope.platform.api.model.UpdateIncidentStatusRequest;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentResponseServiceTest {

    private IncidentResponseRepository repository;
    private IncidentResponseService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        IncidentResponseRepository.class
                );

        service =
                new IncidentResponseService(
                        repository
                );
    }

    @Test
    void createIsIdempotentByHazardAndSource() {
        IncidentCase existing =
                incident(
                        IncidentSeverity.HIGH,
                        IncidentStatus.OPEN
                );

        when(
                repository.findBySource(
                        "earthquakes",
                        "source-1"
                )
        ).thenReturn(
                Optional.of(
                        existing
                )
        );

        when(
                repository.findById(
                        existing.id()
                )
        ).thenReturn(
                Optional.of(
                        existing
                )
        );

        when(
                repository.timeline(
                        existing.id()
                )
        ).thenReturn(
                List.of()
        );

        service.create(
                request()
        );

        verify(
                repository,
                never()
        ).create(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void dashboardSummarizesOperationalState() {
        when(
                repository.findAll()
        ).thenReturn(
                List.of(
                        incident(
                                IncidentSeverity.CRITICAL,
                                IncidentStatus.RESPONDING
                        ),
                        incident(
                                IncidentSeverity.HIGH,
                                IncidentStatus.MONITORING
                        ),
                        incident(
                                IncidentSeverity.MODERATE,
                                IncidentStatus.CLOSED
                        )
                )
        );

        IncidentDashboardResponse response =
                service.dashboard();

        assertThat(
                response.totalCount()
        ).isEqualTo(3);

        assertThat(
                response.activeCount()
        ).isEqualTo(2);

        assertThat(
                response.respondingCount()
        ).isEqualTo(1);

        assertThat(
                response.criticalCount()
        ).isEqualTo(1);

        assertThat(
                response.closedCount()
        ).isEqualTo(1);
    }

    @Test
    void statusChangeWritesTimeline() {
        IncidentCase before =
                incident(
                        IncidentSeverity.HIGH,
                        IncidentStatus.OPEN
                );

        IncidentCase after =
                copyWithStatus(
                        before,
                        IncidentStatus.RESPONDING
                );

        when(
                repository.findById(
                        before.id()
                )
        ).thenReturn(
                Optional.of(before),
                Optional.of(after)
        );

        when(
                repository.updateStatus(
                        before.id(),
                        IncidentStatus.RESPONDING
                )
        ).thenReturn(
                after
        );

        when(
                repository.timeline(
                        before.id()
                )
        ).thenReturn(
                List.of()
        );

        service.updateStatus(
                before.id(),
                new UpdateIncidentStatusRequest(
                        "RESPONDING"
                )
        );

        verify(
                repository
        ).appendTimeline(
                any(),
                eq(before.id()),
                eq("STATUS_CHANGED"),
                any()
        );
    }

    @Test
    void noteWritesTimeline() {
        IncidentCase incident =
                incident(
                        IncidentSeverity.HIGH,
                        IncidentStatus.MONITORING
                );

        when(
                repository.findById(
                        incident.id()
                )
        ).thenReturn(
                Optional.of(incident)
        );

        when(
                repository.timeline(
                        incident.id()
                )
        ).thenReturn(
                List.of(
                        new IncidentTimelineEvent(
                                UUID.randomUUID(),
                                incident.id(),
                                "NOTE_ADDED",
                                "Operational note",
                                Instant.parse(
                                        "2026-09-17T12:00:00Z"
                                )
                        )
                )
        );

        service.addNote(
                incident.id(),
                new AddIncidentNoteRequest(
                        "Operational note"
                )
        );

        verify(
                repository
        ).appendTimeline(
                any(),
                eq(incident.id()),
                eq("NOTE_ADDED"),
                eq("Operational note")
        );
    }

    private CreateIncidentRequest request() {
        return new CreateIncidentRequest(
                null,
                "source-1",
                "earthquakes",
                "Test earthquake",
                "Test incident",
                36.8,
                10.2,
                "HIGH",
                "Unassigned"
        );
    }

    private IncidentCase incident(
            IncidentSeverity severity,
            IncidentStatus status
    ) {
        Instant now =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentCase(
                UUID.randomUUID(),
                null,
                "source-1",
                "earthquakes",
                "Test earthquake",
                "Test incident",
                36.8,
                10.2,
                severity,
                status,
                "Unassigned",
                0,
                now,
                now,
                status == IncidentStatus.CLOSED
                        ? now
                        : null
        );
    }

    private IncidentCase copyWithStatus(
            IncidentCase incident,
            IncidentStatus status
    ) {
        return new IncidentCase(
                incident.id(),
                incident.alertEventId(),
                incident.sourceEventId(),
                incident.hazardType(),
                incident.title(),
                incident.description(),
                incident.latitude(),
                incident.longitude(),
                incident.severity(),
                status,
                incident.owner(),
                incident.timelineEventCount(),
                incident.openedAt(),
                incident.updatedAt(),
                status == IncidentStatus.CLOSED
                        ? incident.updatedAt()
                        : null
        );
    }
}
