package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ImprovementValidationRecord;
import com.terrascope.platform.api.model.ResilienceAuditLedgerVerificationResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.api.model.ResilienceValidationBoardResponse;
import com.terrascope.platform.domain.ImprovementValidationStatus;
import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.PortfolioAssuranceBand;
import com.terrascope.platform.domain.PreparednessValidationResult;
import com.terrascope.platform.domain.ResiliencePortfolioBaseMetrics;
import com.terrascope.platform.repository.ResiliencePortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResiliencePortfolioServiceTest {

    private ResiliencePortfolioRepository repository;
    private ResilienceValidationService validationService;
    private ResilienceAuditLedgerService auditLedgerService;
    private ResiliencePortfolioService service;

    private UUID firstIncidentId;
    private UUID secondIncidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        ResiliencePortfolioRepository.class
                );

        validationService =
                mock(
                        ResilienceValidationService.class
                );

        auditLedgerService =
                mock(
                        ResilienceAuditLedgerService.class
                );

        service =
                new ResiliencePortfolioService(
                        repository,
                        validationService,
                        auditLedgerService
                );

        firstIncidentId =
                UUID.randomUUID();

        secondIncidentId =
                UUID.randomUUID();
    }

    @Test
    void emptyPortfolioIsFoundational() {
        when(
                repository.loadBaseMetrics()
        ).thenReturn(
                new ResiliencePortfolioBaseMetrics(
                        0,
                        0,
                        0,

                        0,
                        0,
                        0,

                        0,
                        0,
                        0
                )
        );

        when(
                repository.findClosedIncidentIds()
        ).thenReturn(
                List.of()
        );

        when(
                repository.findAuditLedgerIncidentIds()
        ).thenReturn(
                List.of()
        );

        ResiliencePortfolioResponse portfolio =
                service.portfolio();

        assertThat(
                portfolio.score()
        ).isZero();

        assertThat(
                portfolio.band()
        ).isEqualTo(
                PortfolioAssuranceBand.FOUNDATIONAL
        );
    }

    @Test
    void computesDeterministicPortfolioScore() {
        when(
                repository.loadBaseMetrics()
        ).thenReturn(
                new ResiliencePortfolioBaseMetrics(
                        3,
                        1,
                        2,

                        1,
                        1,
                        1,

                        2,
                        2,
                        1
                )
        );

        when(
                repository.findClosedIncidentIds()
        ).thenReturn(
                List.of(
                        firstIncidentId,
                        secondIncidentId
                )
        );

        when(
                validationService.board(
                        firstIncidentId
                )
        ).thenReturn(
                validationBoard(
                        firstIncidentId,
                        ImprovementValidationStatus.VALIDATED,
                        95
                )
        );

        when(
                validationService.board(
                        secondIncidentId
                )
        ).thenReturn(
                validationBoard(
                        secondIncidentId,
                        ImprovementValidationStatus.UNTESTED,
                        null
                )
        );

        when(
                repository.findAuditLedgerIncidentIds()
        ).thenReturn(
                List.of(
                        firstIncidentId
                )
        );

        when(
                auditLedgerService.verify(
                        firstIncidentId
                )
        ).thenReturn(
                new ResilienceAuditLedgerVerificationResponse(
                        firstIncidentId,
                        true,
                        2,
                        2,
                        null,
                        null,
                        "Audit ledger integrity verified.",
                        Instant.now()
                )
        );

        ResiliencePortfolioResponse portfolio =
                service.portfolio();

        assertThat(
                portfolio.score()
        ).isEqualTo(49);

        assertThat(
                portfolio.band()
        ).isEqualTo(
                PortfolioAssuranceBand.DEVELOPING
        );

        assertThat(
                portfolio.completedImprovementCount()
        ).isEqualTo(2);

        assertThat(
                portfolio.validatedImprovementCount()
        ).isEqualTo(1);

        assertThat(
                portfolio.untestedImprovementCount()
        ).isEqualTo(1);

        assertThat(
                portfolio.auditVerifiedIncidentCount()
        ).isEqualTo(1);

        assertThat(
                portfolio.averageLatestReadinessScore()
        ).isEqualTo(95.0);
    }

    @Test
    void invalidAuditLedgerDoesNotCountAsVerified() {
        when(
                repository.loadBaseMetrics()
        ).thenReturn(
                new ResiliencePortfolioBaseMetrics(
                        1,
                        0,
                        1,

                        1,
                        1,
                        1,

                        1,
                        1,
                        1
                )
        );

        when(
                repository.findClosedIncidentIds()
        ).thenReturn(
                List.of(
                        firstIncidentId
                )
        );

        when(
                validationService.board(
                        firstIncidentId
                )
        ).thenReturn(
                validationBoard(
                        firstIncidentId,
                        ImprovementValidationStatus.VALIDATED,
                        95
                )
        );

        when(
                repository.findAuditLedgerIncidentIds()
        ).thenReturn(
                List.of(
                        firstIncidentId
                )
        );

        when(
                auditLedgerService.verify(
                        firstIncidentId
                )
        ).thenReturn(
                new ResilienceAuditLedgerVerificationResponse(
                        firstIncidentId,
                        false,
                        2,
                        1,
                        UUID.randomUUID(),
                        2,
                        "Broken ledger.",
                        Instant.now()
                )
        );

        ResiliencePortfolioResponse portfolio =
                service.portfolio();

        assertThat(
                portfolio.auditLedgerIncidentCount()
        ).isEqualTo(1);

        assertThat(
                portfolio.auditVerifiedIncidentCount()
        ).isZero();
    }

    @Test
    void snapshotPersistsCurrentPortfolio() {
        ResiliencePortfolioBaseMetrics base =
                new ResiliencePortfolioBaseMetrics(
                        0,
                        0,
                        0,

                        0,
                        0,
                        0,

                        0,
                        0,
                        0
                );

        when(
                repository.loadBaseMetrics()
        ).thenReturn(
                base
        );

        when(
                repository.findClosedIncidentIds()
        ).thenReturn(
                List.of()
        );

        when(
                repository.findAuditLedgerIncidentIds()
        ).thenReturn(
                List.of()
        );

        when(
                repository.nextSnapshotNumber()
        ).thenReturn(1);

        when(
                repository.saveSnapshot(
                        any(),
                        eq(1),
                        any()
                )
        ).thenAnswer(
                invocation -> {
                    ResiliencePortfolioResponse portfolio =
                            invocation.getArgument(
                                    2
                            );

                    return new ResiliencePortfolioSnapshot(
                            UUID.randomUUID(),
                            1,

                            portfolio.score(),
                            portfolio.band(),

                            portfolio.totalIncidentCount(),
                            portfolio.openIncidentCount(),
                            portfolio.closedIncidentCount(),

                            portfolio.certifiedIncidentCount(),
                            portfolio.evidencePackagedIncidentCount(),
                            portfolio.auditLedgerIncidentCount(),
                            portfolio.auditVerifiedIncidentCount(),

                            portfolio.completedImprovementCount(),
                            portfolio.validatedImprovementCount(),
                            portfolio.untestedImprovementCount(),
                            portfolio.needsRevalidationCount(),
                            portfolio.failedImprovementCount(),

                            portfolio.openCorrectiveActionCount(),

                            portfolio.totalExerciseCount(),
                            portfolio.completedExerciseCount(),
                            portfolio.passedExerciseCount(),

                            portfolio.averageLatestReadinessScore(),

                            Instant.now()
                    );
                }
        );

        ResiliencePortfolioSnapshot snapshot =
                service.createSnapshot();

        assertThat(
                snapshot.snapshotNumber()
        ).isEqualTo(1);

        verify(
                repository
        ).saveSnapshot(
                any(),
                eq(1),
                any()
        );
    }

    private ResilienceValidationBoardResponse validationBoard(
            UUID incidentId,
            ImprovementValidationStatus status,
            Integer score
    ) {
        PreparednessValidationResult result =
                switch (status) {
                    case VALIDATED ->
                            PreparednessValidationResult.PASSED;

                    case NEEDS_REVALIDATION ->
                            PreparednessValidationResult.PARTIAL;

                    case FAILED ->
                            PreparednessValidationResult.FAILED;

                    case UNTESTED ->
                            null;
                };

        return new ResilienceValidationBoardResponse(
                incidentId,

                1,

                status == ImprovementValidationStatus.UNTESTED
                        ? 1
                        : 0,

                status == ImprovementValidationStatus.VALIDATED
                        ? 1
                        : 0,

                status == ImprovementValidationStatus.NEEDS_REVALIDATION
                        ? 1
                        : 0,

                status == ImprovementValidationStatus.FAILED
                        ? 1
                        : 0,

                0,
                0,
                0,
                0,
                0,
                0,

                score == null
                        ? 0.0
                        : score.doubleValue(),

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
                                score,

                                0,

                                status
                        )
                ),

                List.of()
        );
    }
}
