package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CertificationBlocker;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentResilienceCertification;
import com.terrascope.platform.api.model.ResilienceCertificationHistoryResponse;
import com.terrascope.platform.api.model.ResilienceCertificationReadinessResponse;
import com.terrascope.platform.api.model.ResilienceValidationBoardResponse;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.ResilienceCertificationMetrics;
import com.terrascope.platform.domain.ResilienceCertificationStatus;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.ResilienceCertificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResilienceCertificationService {

    private static final double MINIMUM_READINESS_SCORE =
            80.0;

    private final ResilienceCertificationRepository repository;
    private final IncidentResponseRepository incidentRepository;
    private final ResilienceValidationService validationService;

    public ResilienceCertificationService(
            ResilienceCertificationRepository repository,
            IncidentResponseRepository incidentRepository,
            ResilienceValidationService validationService
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.validationService = validationService;
    }

    public ResilienceCertificationReadinessResponse readiness(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        ResilienceValidationBoardResponse validation =
                validationService.board(
                        incidentId
                );

        ResilienceCertificationMetrics metrics =
                repository.loadMetrics(
                        incidentId
                );

        List<CertificationBlocker> blockers =
                new ArrayList<>();

        if (
                incident.status()
                != IncidentStatus.CLOSED
        ) {
            blockers.add(
                    blocker(
                            "INCIDENT_NOT_CLOSED",
                            "Incident must be closed",
                            incident.status().name()
                    )
            );
        }

        if (
                metrics.afterActionReviewCount()
                == 0
        ) {
            blockers.add(
                    blocker(
                            "AAR_MISSING",
                            "After-action review is required",
                            "0"
                    )
            );
        }

        if (
                validation.completedImprovementCount()
                == 0
        ) {
            blockers.add(
                    blocker(
                            "COMPLETED_IMPROVEMENT_MISSING",
                            "At least one completed improvement action is required",
                            "0"
                    )
            );
        }

        if (
                validation.untestedCount()
                > 0
        ) {
            blockers.add(
                    blocker(
                            "UNTESTED_IMPROVEMENTS",
                            "Completed improvements remain untested",
                            Integer.toString(
                                    validation.untestedCount()
                            )
                    )
            );
        }

        if (
                validation.needsRevalidationCount()
                > 0
        ) {
            blockers.add(
                    blocker(
                            "REVALIDATION_REQUIRED",
                            "Improvements require revalidation",
                            Integer.toString(
                                    validation.needsRevalidationCount()
                            )
                    )
            );
        }

        if (
                validation.failedCount()
                > 0
        ) {
            blockers.add(
                    blocker(
                            "FAILED_IMPROVEMENTS",
                            "Improvements have failed validation",
                            Integer.toString(
                                    validation.failedCount()
                            )
                    )
            );
        }

        int openCorrectiveActionCount =
                validation.openCorrectiveActionCount()
                + validation.inProgressCorrectiveActionCount();

        if (
                openCorrectiveActionCount > 0
        ) {
            blockers.add(
                    blocker(
                            "OPEN_CORRECTIVE_ACTIONS",
                            "Preparedness corrective actions remain open",
                            Integer.toString(
                                    openCorrectiveActionCount
                            )
                    )
            );
        }

        if (
                validation.completedImprovementCount()
                > 0
                && validation.averageLatestReadinessScore()
                < MINIMUM_READINESS_SCORE
        ) {
            blockers.add(
                    blocker(
                            "READINESS_SCORE_BELOW_THRESHOLD",
                            "Average latest readiness score is below certification threshold",
                            String.format(
                                    Locale.ROOT,
                                    "%.1f/100",
                                    validation.averageLatestReadinessScore()
                            )
                    )
            );
        }

        boolean ready =
                blockers.isEmpty();

        return new ResilienceCertificationReadinessResponse(
                incidentId,

                ResilienceCertificationStatus.fromReady(
                        ready
                ),

                ready,

                validation.completedImprovementCount(),

                validation.validatedCount(),
                validation.untestedCount(),
                validation.needsRevalidationCount(),
                validation.failedCount(),

                openCorrectiveActionCount,

                metrics.totalExerciseCount(),
                metrics.passedExerciseCount(),

                validation.averageLatestReadinessScore(),

                metrics.afterActionReviewCount(),
                metrics.sitrepCount(),

                List.copyOf(
                        blockers
                ),

                Instant.now()
        );
    }

    public ResilienceCertificationHistoryResponse history(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        return new ResilienceCertificationHistoryResponse(
                incidentId,
                repository.findHistory(
                        incidentId
                )
        );
    }

    @Transactional
    public IncidentResilienceCertification certify(
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
                    "Resilience certification requires a CLOSED incident."
            );
        }

        repository.lockIncident(
                incidentId
        );

        ResilienceCertificationReadinessResponse readiness =
                readiness(
                        incidentId
                );

        if (
                !readiness.ready()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Incident resilience assurance is not ready for certification."
            );
        }

        int certificationNumber =
                repository.nextCertificationNumber(
                        incidentId
                );

        String executiveSummary =
                buildExecutiveSummary(
                        incident,
                        readiness,
                        certificationNumber
                );

        IncidentResilienceCertification certification =
                repository.saveCertification(
                        UUID.randomUUID(),
                        certificationNumber,
                        readiness,
                        executiveSummary
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESILIENCE_CERTIFICATION_CREATED",
                "Resilience certification #"
                        + certification.certificationNumber()
                        + " issued with "
                        + certification.validatedCount()
                        + " validated improvements and average readiness "
                        + String.format(
                                Locale.ROOT,
                                "%.1f",
                                certification.averageLatestReadinessScore()
                        )
                        + "/100."
        );

        return certification;
    }

    private String buildExecutiveSummary(
            IncidentCase incident,
            ResilienceCertificationReadinessResponse readiness,
            int certificationNumber
    ) {
        return String.format(
                Locale.ROOT,
                "Resilience certification #%d confirms that incident '%s' "
                        + "completed the post-incident assurance lifecycle. "
                        + "%d completed improvement actions are validated, "
                        + "with %d untested, %d requiring revalidation, and "
                        + "%d failed. No active preparedness corrective actions "
                        + "remain. The latest validation evidence includes %d "
                        + "preparedness exercises, %d passed exercises, an "
                        + "average latest readiness score of %.1f/100, %d "
                        + "after-action reviews, and %d situation reports.",
                certificationNumber,
                incident.title(),
                readiness.completedImprovementCount(),
                readiness.untestedCount(),
                readiness.needsRevalidationCount(),
                readiness.failedCount(),
                readiness.totalExerciseCount(),
                readiness.passedExerciseCount(),
                readiness.averageLatestReadinessScore(),
                readiness.afterActionReviewCount(),
                readiness.sitrepCount()
        );
    }

    private CertificationBlocker blocker(
            String code,
            String label,
            String value
    ) {
        return new CertificationBlocker(
                code,
                label,
                value
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
