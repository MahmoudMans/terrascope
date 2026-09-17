package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ImprovementValidationRecord;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.IncidentResilienceCertification;
import com.terrascope.platform.api.model.ResilienceCertificationReadinessResponse;
import com.terrascope.platform.api.model.ResilienceValidationBoardResponse;
import com.terrascope.platform.domain.ImprovementValidationStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.PreparednessValidationResult;
import com.terrascope.platform.domain.ResilienceCertificationMetrics;
import com.terrascope.platform.domain.ResilienceCertificationStatus;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.ResilienceCertificationRepository;
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

class ResilienceCertificationServiceTest {

    private ResilienceCertificationRepository repository;
    private IncidentResponseRepository incidentRepository;
    private ResilienceValidationService validationService;
    private ResilienceCertificationService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        ResilienceCertificationRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        validationService =
                mock(
                        ResilienceValidationService.class
                );

        service =
                new ResilienceCertificationService(
                        repository,
                        incidentRepository,
                        validationService
                );

        incidentId =
                UUID.randomUUID();
    }

    @Test
    void openIncidentIsBlocked() {
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
                validationService.board(
                        incidentId
                )
        ).thenReturn(
                emptyValidationBoard()
        );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                new ResilienceCertificationMetrics(
                        0,
                        0,
                        0,
                        0
                )
        );

        ResilienceCertificationReadinessResponse readiness =
                service.readiness(
                        incidentId
                );

        assertThat(
                readiness.ready()
        ).isFalse();

        assertThat(
                readiness.status()
        ).isEqualTo(
                ResilienceCertificationStatus.BLOCKED
        );

        assertThat(
                readiness.blockers()
        ).extracting(
                blocker ->
                        blocker.code()
        ).contains(
                "INCIDENT_NOT_CLOSED"
        );
    }

    @Test
    void untestedImprovementBlocksCertification() {
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
                validationService.board(
                        incidentId
                )
        ).thenReturn(
                validationBoard(
                        0,
                        1,
                        0,
                        0,
                        0.0
                )
        );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                new ResilienceCertificationMetrics(
                        1,
                        1,
                        0,
                        0
                )
        );

        ResilienceCertificationReadinessResponse readiness =
                service.readiness(
                        incidentId
                );

        assertThat(
                readiness.ready()
        ).isFalse();

        assertThat(
                readiness.blockers()
        ).extracting(
                blocker ->
                        blocker.code()
        ).contains(
                "UNTESTED_IMPROVEMENTS"
        );
    }

    @Test
    void fullyValidatedProgramIsReady() {
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
                validationService.board(
                        incidentId
                )
        ).thenReturn(
                validationBoard(
                        1,
                        0,
                        0,
                        0,
                        95.0
                )
        );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                new ResilienceCertificationMetrics(
                        1,
                        2,
                        2,
                        1
                )
        );

        ResilienceCertificationReadinessResponse readiness =
                service.readiness(
                        incidentId
                );

        assertThat(
                readiness.ready()
        ).isTrue();

        assertThat(
                readiness.status()
        ).isEqualTo(
                ResilienceCertificationStatus.READY
        );

        assertThat(
                readiness.blockers()
        ).isEmpty();
    }

    @Test
    void certificationPersistsSnapshotAndTimelineEvidence() {
        IncidentCase incident =
                incident(
                        IncidentStatus.CLOSED
                );

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident
                )
        );

        when(
                validationService.board(
                        incidentId
                )
        ).thenReturn(
                validationBoard(
                        1,
                        0,
                        0,
                        0,
                        95.0
                )
        );

        when(
                repository.loadMetrics(
                        incidentId
                )
        ).thenReturn(
                new ResilienceCertificationMetrics(
                        1,
                        2,
                        2,
                        1
                )
        );

        when(
                repository.nextCertificationNumber(
                        incidentId
                )
        ).thenReturn(1);

        when(
                repository.saveCertification(
                        any(),
                        eq(1),
                        any(),
                        any()
                )
        ).thenReturn(
                certification()
        );

        IncidentResilienceCertification certification =
                service.certify(
                        incidentId
                );

        assertThat(
                certification.status()
        ).isEqualTo(
                ResilienceCertificationStatus.CERTIFIED
        );

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
                        "RESILIENCE_CERTIFICATION_CREATED"
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
                status,
                "Command",
                0,
                opened,
                closed,
                status == IncidentStatus.CLOSED
                        ? closed
                        : null
        );
    }

    private ResilienceValidationBoardResponse emptyValidationBoard() {
        return new ResilienceValidationBoardResponse(
                incidentId,

                0,

                0,
                0,
                0,
                0,

                0,
                0,
                0,
                0,
                0,
                0,

                0.0,

                List.of(),
                List.of()
        );
    }

    private ResilienceValidationBoardResponse validationBoard(
            int validated,
            int untested,
            int revalidation,
            int failed,
            double score
    ) {
        ImprovementValidationStatus status =
                validated == 1
                        ? ImprovementValidationStatus.VALIDATED
                        : untested == 1
                        ? ImprovementValidationStatus.UNTESTED
                        : revalidation == 1
                        ? ImprovementValidationStatus.NEEDS_REVALIDATION
                        : ImprovementValidationStatus.FAILED;

        PreparednessValidationResult result =
                validated == 1
                        ? PreparednessValidationResult.PASSED
                        : untested == 1
                        ? null
                        : revalidation == 1
                        ? PreparednessValidationResult.PARTIAL
                        : PreparednessValidationResult.FAILED;

        Integer readinessScore =
                result == null
                        ? null
                        : (int) score;

        return new ResilienceValidationBoardResponse(
                incidentId,

                1,

                untested,
                validated,
                revalidation,
                failed,

                0,
                0,
                0,
                0,
                0,
                0,

                score,

                List.of(
                        new ImprovementValidationRecord(
                                UUID.randomUUID(),
                                "Improvement",
                                IncidentActionPriority.HIGH,
                                result == null
                                        ? 0
                                        : 1,
                                result == null
                                        ? null
                                        : UUID.randomUUID(),
                                result,
                                readinessScore,
                                0,
                                status
                        )
                ),

                List.of()
        );
    }

    private IncidentResilienceCertification certification() {
        return new IncidentResilienceCertification(
                UUID.randomUUID(),
                incidentId,
                1,

                ResilienceCertificationStatus.CERTIFIED,

                1,

                1,
                0,
                0,
                0,

                0,

                2,
                1,

                95.0,

                1,
                2,

                "Certified.",

                Instant.now()
        );
    }
}
