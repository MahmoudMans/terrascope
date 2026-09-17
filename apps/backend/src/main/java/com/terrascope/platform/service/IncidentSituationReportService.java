package com.terrascope.platform.service;

import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.api.model.IncidentSitrepHistoryResponse;
import com.terrascope.platform.api.model.IncidentSitrepPreview;
import com.terrascope.platform.api.model.IncidentSituationReport;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.IncidentSituationReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentSituationReportService {

    private final IncidentSituationReportRepository repository;
    private final IncidentResponseRepository incidentRepository;
    private final IncidentCommandPulseService commandPulseService;
    private final SitrepNarrativeBuilder narrativeBuilder;

    public IncidentSituationReportService(
            IncidentSituationReportRepository repository,
            IncidentResponseRepository incidentRepository,
            IncidentCommandPulseService commandPulseService,
            SitrepNarrativeBuilder narrativeBuilder
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.commandPulseService = commandPulseService;
        this.narrativeBuilder = narrativeBuilder;
    }

    public IncidentSitrepPreview preview(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        IncidentCommandPulseResponse pulse =
                commandPulseService.pulse(
                        incidentId
                );

        return buildPreview(
                incident,
                pulse
        );
    }

    public IncidentSitrepHistoryResponse history(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        return new IncidentSitrepHistoryResponse(
                incidentId,
                repository.findHistory(
                        incidentId
                )
        );
    }

    @Transactional
    public IncidentSituationReport createReport(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        repository.lockIncident(
                incidentId
        );

        IncidentCommandPulseResponse pulse =
                commandPulseService.pulse(
                        incidentId
                );

        IncidentSitrepPreview preview =
                buildPreview(
                        incident,
                        pulse
                );

        int reportNumber =
                repository.nextReportNumber(
                        incidentId
                );

        IncidentSituationReport report =
                repository.saveReport(
                        UUID.randomUUID(),
                        incidentId,
                        reportNumber,
                        preview
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "SITREP_CREATED",
                "Situation report #"
                        + report.reportNumber()
                        + " captured with command pressure "
                        + report.commandScore()
                        + "/100 ("
                        + report.commandBand().name()
                        + ")."
        );

        return report;
    }

    private IncidentSitrepPreview buildPreview(
            IncidentCase incident,
            IncidentCommandPulseResponse pulse
    ) {
        String summary =
                narrativeBuilder
                        .buildExecutiveSummary(
                                incident,
                                pulse
                        );

        List<String> priorities =
                narrativeBuilder
                        .buildPriorities(
                                pulse
                        );

        return new IncidentSitrepPreview(
                incident.id(),

                incident.title(),
                incident.status(),
                incident.severity(),

                pulse.score(),
                pulse.band(),

                summary,

                priorities,

                pulse.openActionCount(),
                pulse.overdueActionCount(),
                pulse.blockedActionCount(),

                pulse.resourceShortageCount(),

                pulse.evacuationRemaining(),
                pulse.shelterRemainingCapacity(),

                pulse.lifelineDisruptedCount(),
                pulse.criticalLifelineDisruptionCount(),
                pulse.lifelineAffectedPopulation(),

                pulse.unresolvedDamageLossMusd(),
                pulse.criticalDamageCount(),
                pulse.averageRepairProgressPct(),

                Instant.now()
        );
    }

    private IncidentCase requireIncident(
            UUID incidentId
    ) {
        return incidentRepository
                .findById(
                        incidentId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Incident "
                                        + incidentId
                                        + " was not found."
                        )
                );
    }
}
