package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CommandBlocker;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.api.model.IncidentSitrepHistoryResponse;
import com.terrascope.platform.api.model.IncidentSitrepPreview;
import com.terrascope.platform.api.model.IncidentSituationReport;
import com.terrascope.platform.domain.CommandPressureBand;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.IncidentSituationReportRepository;
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

class IncidentSituationReportServiceTest {

    private IncidentSituationReportRepository repository;
    private IncidentResponseRepository incidentRepository;
    private IncidentCommandPulseService commandPulseService;
    private IncidentSituationReportService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        IncidentSituationReportRepository.class
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
                new IncidentSituationReportService(
                        repository,
                        incidentRepository,
                        commandPulseService,
                        new SitrepNarrativeBuilder()
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

        when(
                commandPulseService.pulse(
                        incidentId
                )
        ).thenReturn(
                pulse()
        );
    }

    @Test
    void previewCombinesIncidentAndCommandPulse() {
        IncidentSitrepPreview preview =
                service.preview(
                        incidentId
                );

        assertThat(
                preview.incidentId()
        ).isEqualTo(
                incidentId
        );

        assertThat(
                preview.commandScore()
        ).isEqualTo(49);

        assertThat(
                preview.commandBand()
        ).isEqualTo(
                CommandPressureBand.ELEVATED
        );

        assertThat(
                preview.priorities()
        ).hasSize(3);

        assertThat(
                preview.executiveSummary()
        ).contains(
                "Verification Incident"
        );
    }

    @Test
    void createPersistsSequentialReportAndWritesTimeline() {
        when(
                repository.nextReportNumber(
                        incidentId
                )
        ).thenReturn(3);

        when(
                repository.saveReport(
                        any(),
                        eq(incidentId),
                        eq(3),
                        any()
                )
        ).thenReturn(
                report(
                        3,
                        IncidentStatus.RESPONDING
                )
        );

        IncidentSituationReport report =
                service.createReport(
                        incidentId
                );

        assertThat(
                report.reportNumber()
        ).isEqualTo(3);

        verify(
                repository
        ).lockIncident(
                incidentId
        );

        verify(
                repository
        ).saveReport(
                any(),
                eq(incidentId),
                eq(3),
                any()
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq("SITREP_CREATED"),
                any()
        );
    }

    @Test
    void historyReturnsStoredReports() {
        when(
                repository.findHistory(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        report(
                                2,
                                IncidentStatus.RESPONDING
                        ),
                        report(
                                1,
                                IncidentStatus.OPEN
                        )
                )
        );

        IncidentSitrepHistoryResponse response =
                service.history(
                        incidentId
                );

        assertThat(
                response.reports()
        ).hasSize(2);

        assertThat(
                response.reports()
                        .get(0)
                        .reportNumber()
        ).isEqualTo(2);
    }

    @Test
    void closedIncidentCanCaptureFinalSituationReport() {
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

        when(
                repository.nextReportNumber(
                        incidentId
                )
        ).thenReturn(7);

        when(
                repository.saveReport(
                        any(),
                        eq(incidentId),
                        eq(7),
                        any()
                )
        ).thenReturn(
                report(
                        7,
                        IncidentStatus.CLOSED
                )
        );

        IncidentSituationReport report =
                service.createReport(
                        incidentId
                );

        assertThat(
                report.reportNumber()
        ).isEqualTo(7);

        assertThat(
                report.incidentStatus()
        ).isEqualTo(
                IncidentStatus.CLOSED
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
                "Verification Incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.SEVERE,
                status,
                "Incident Command",
                0,
                now,
                now,
                status == IncidentStatus.CLOSED
                        ? now
                        : null
        );
    }

    private IncidentCommandPulseResponse pulse() {
        return new IncidentCommandPulseResponse(
                incidentId,

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
                        )
                ),

                Instant.parse(
                        "2026-09-17T12:00:00Z"
                )
        );
    }

    private IncidentSituationReport report(
            int reportNumber,
            IncidentStatus status
    ) {
        return new IncidentSituationReport(
                UUID.randomUUID(),
                incidentId,
                reportNumber,

                "Verification Incident",
                status,
                IncidentSeverity.SEVERE,

                49,
                CommandPressureBand.ELEVATED,

                "Verification executive summary.",

                List.of(
                        "Priority one",
                        "Priority two",
                        "Priority three"
                ),

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

                Instant.parse(
                        "2026-09-17T12:00:00Z"
                )
        );
    }
}
