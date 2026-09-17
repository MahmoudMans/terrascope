package com.terrascope.platform.service;

import com.terrascope.platform.api.model.AfterActionReviewHistoryResponse;
import com.terrascope.platform.api.model.ClosureBlocker;
import com.terrascope.platform.api.model.IncidentAfterActionReview;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentClosureReadinessResponse;
import com.terrascope.platform.api.model.IncidentCommandPulseResponse;
import com.terrascope.platform.domain.ClosureReadinessStatus;
import com.terrascope.platform.domain.IncidentClosureReviewMetrics;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.repository.IncidentClosureReviewRepository;
import com.terrascope.platform.repository.IncidentResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentClosureReviewService {

    private final IncidentClosureReviewRepository repository;
    private final IncidentResponseRepository incidentRepository;
    private final IncidentCommandPulseService commandPulseService;
    private final AfterActionNarrativeBuilder narrativeBuilder;

    public IncidentClosureReviewService(
            IncidentClosureReviewRepository repository,
            IncidentResponseRepository incidentRepository,
            IncidentCommandPulseService commandPulseService,
            AfterActionNarrativeBuilder narrativeBuilder
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.commandPulseService = commandPulseService;
        this.narrativeBuilder = narrativeBuilder;
    }

    public IncidentClosureReadinessResponse readiness(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        IncidentCommandPulseResponse pulse =
                commandPulseService.pulse(
                        incidentId
                );

        IncidentClosureReviewMetrics metrics =
                repository.loadMetrics(
                        incidentId
                );

        return calculateReadiness(
                incidentId,
                pulse,
                metrics
        );
    }

    public AfterActionReviewHistoryResponse history(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        return new AfterActionReviewHistoryResponse(
                incidentId,
                repository.findHistory(
                        incidentId
                )
        );
    }

    @Transactional
    public IncidentAfterActionReview createReview(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        if (
                incident.status()
                != IncidentStatus.CLOSED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "After-action reviews require a CLOSED incident."
            );
        }

        if (
                incident.closedAt()
                == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed incident is missing its closure timestamp."
            );
        }

        repository.lockIncident(
                incidentId
        );

        IncidentCommandPulseResponse pulse =
                commandPulseService.pulse(
                        incidentId
                );

        IncidentClosureReviewMetrics metrics =
                repository.loadMetrics(
                        incidentId
                );

        IncidentClosureReadinessResponse readiness =
                calculateReadiness(
                        incidentId,
                        pulse,
                        metrics
                );

        int peakCommandScore =
                Math.max(
                        metrics.peakCommandScore(),
                        pulse.score()
                );

        long durationMinutes =
                Math.max(
                        0,
                        Duration.between(
                                incident.openedAt(),
                                incident.closedAt()
                        ).toMinutes()
                );

        String executiveSummary =
                narrativeBuilder.build(
                        incident,
                        readiness,
                        pulse,
                        metrics,
                        peakCommandScore,
                        durationMinutes
                );

        int reviewNumber =
                repository.nextReviewNumber(
                        incidentId
                );

        IncidentAfterActionReview review =
                repository.saveReview(
                        UUID.randomUUID(),
                        reviewNumber,
                        incident,
                        readiness,
                        pulse,
                        metrics,
                        peakCommandScore,
                        durationMinutes,
                        executiveSummary
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "AFTER_ACTION_REVIEW_CREATED",
                "After-action review #"
                        + review.reviewNumber()
                        + " captured. Closure readiness: "
                        + review
                        .closureReadinessStatus()
                        .name()
                        + ", final command pressure "
                        + review.finalCommandScore()
                        + "/100, peak pressure "
                        + review.peakCommandScore()
                        + "/100."
        );

        return review;
    }

    IncidentClosureReadinessResponse calculateReadiness(
            UUID incidentId,
            IncidentCommandPulseResponse pulse,
            IncidentClosureReviewMetrics metrics
    ) {
        List<ClosureBlocker> blockers =
                new ArrayList<>();

        addCountBlocker(
                blockers,
                metrics.openActionCount(),
                "OPEN_ACTIONS",
                "Open response actions"
        );

        addCountBlocker(
                blockers,
                metrics.resourceShortageCount(),
                "RESOURCE_SHORTAGES",
                "Unfulfilled resource shortages"
        );

        addCountBlocker(
                blockers,
                metrics.activeEvacuationZoneCount(),
                "ACTIVE_EVACUATIONS",
                "Active evacuation zones"
        );

        addCountBlocker(
                blockers,
                metrics.activeShelterCount(),
                "ACTIVE_SHELTERS",
                "Open or full shelters"
        );

        addCountBlocker(
                blockers,
                metrics.disruptedLifelineCount(),
                "LIFELINE_DISRUPTIONS",
                "Disrupted critical infrastructure assets"
        );

        addCountBlocker(
                blockers,
                metrics.unresolvedDamageCount(),
                "UNRESOLVED_DAMAGE",
                "Unresolved damage assessments"
        );

        if (
                pulse.score() >= 20
        ) {
            blockers.add(
                    new ClosureBlocker(
                            "COMMAND_PRESSURE",
                            "Command pressure remains above stable",
                            pulse.score()
                                    + "/100 "
                                    + pulse.band().name()
                    )
            );
        }

        boolean ready =
                blockers.isEmpty();

        return new IncidentClosureReadinessResponse(
                incidentId,

                ClosureReadinessStatus.fromReady(
                        ready
                ),
                ready,

                pulse.score(),
                pulse.band(),

                metrics.openActionCount(),
                metrics.resourceShortageCount(),
                metrics.activeEvacuationZoneCount(),
                metrics.activeShelterCount(),
                metrics.disruptedLifelineCount(),
                metrics.unresolvedDamageCount(),

                metrics.unresolvedItemCount(),

                List.copyOf(
                        blockers
                ),

                Instant.now()
        );
    }

    private void addCountBlocker(
            List<ClosureBlocker> blockers,
            int count,
            String code,
            String label
    ) {
        if (
                count <= 0
        ) {
            return;
        }

        blockers.add(
                new ClosureBlocker(
                        code,
                        label,
                        Integer.toString(
                                count
                        )
                )
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
