package com.terrascope.platform.service;

import com.terrascope.platform.api.model.PortfolioObjective;
import com.terrascope.platform.api.model.PortfolioObjectiveOutcomePreview;
import com.terrascope.platform.api.model.PortfolioObjectiveValidation;
import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.domain.PortfolioAssuranceBand;
import com.terrascope.platform.domain.PortfolioObjectiveOutcome;
import com.terrascope.platform.domain.PortfolioObjectiveOutcomeBaseline;
import com.terrascope.platform.domain.PortfolioObjectivePriority;
import com.terrascope.platform.domain.PortfolioObjectiveStatus;
import com.terrascope.platform.repository.PortfolioObjectiveOutcomeRepository;
import com.terrascope.platform.repository.ResiliencePortfolioObjectiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioObjectiveOutcomeServiceTest {

    private PortfolioObjectiveOutcomeRepository repository;
    private ResiliencePortfolioObjectiveRepository objectiveRepository;
    private ResiliencePortfolioService portfolioService;

    private PortfolioObjectiveOutcomeService service;

    private UUID objectiveId;
    private UUID reviewId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        PortfolioObjectiveOutcomeRepository.class
                );

        objectiveRepository =
                mock(
                        ResiliencePortfolioObjectiveRepository.class
                );

        portfolioService =
                mock(
                        ResiliencePortfolioService.class
                );

        service =
                new PortfolioObjectiveOutcomeService(
                        repository,
                        objectiveRepository,
                        portfolioService
                );

        objectiveId =
                UUID.randomUUID();

        reviewId =
                UUID.randomUUID();
    }

    @Test
    void incompleteObjectiveRejectsOutcomeValidation() {
        when(
                objectiveRepository.findById(
                        objectiveId
                )
        ).thenReturn(
                Optional.of(
                        objective(
                                PortfolioObjectiveStatus.IN_PROGRESS
                        )
                )
        );

        assertThatThrownBy(() ->
                service.preview(
                        objectiveId
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void unchangedPortfolioProducesPartialPreview() {
        whenCompletedObjective();

        PortfolioObjectiveOutcomeBaseline baseline =
                baseline(
                        50,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0
                );

        when(
                repository.findBaselineForReview(
                        reviewId
                )
        ).thenReturn(
                Optional.of(
                        baseline
                )
        );

        when(
                portfolioService.portfolio()
        ).thenReturn(
                portfolio(
                        50,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0
                )
        );

        PortfolioObjectiveOutcomePreview preview =
                service.preview(
                        objectiveId
                );

        assertThat(
                preview.outcome()
        ).isEqualTo(
                PortfolioObjectiveOutcome.PARTIAL
        );

        assertThat(
                preview.scoreDelta()
        ).isZero();

        assertThat(
                preview.assuranceDebtDelta()
        ).isZero();
    }

    @Test
    void portfolioImprovementProducesAchievedPreview() {
        whenCompletedObjective();

        when(
                repository.findBaselineForReview(
                        reviewId
                )
        ).thenReturn(
                Optional.of(
                        baseline(
                                40,
                                0,
                                0,
                                0,
                                1,
                                0,
                                0,
                                0
                        )
                )
        );

        when(
                portfolioService.portfolio()
        ).thenReturn(
                portfolio(
                        75,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0
                )
        );

        PortfolioObjectiveOutcomePreview preview =
                service.preview(
                        objectiveId
                );

        assertThat(
                preview.outcome()
        ).isEqualTo(
                PortfolioObjectiveOutcome.ACHIEVED
        );

        assertThat(
                preview.scoreDelta()
        ).isEqualTo(35);

        assertThat(
                preview.certifiedIncidentDelta()
        ).isEqualTo(1);
    }

    @Test
    void validationPersistsSnapshotAndObjectiveHistory() {
        PortfolioObjective objective =
                objective(
                        PortfolioObjectiveStatus.COMPLETED
                );

        when(
                objectiveRepository.findById(
                        objectiveId
                )
        ).thenReturn(
                Optional.of(
                        objective
                )
        );

        PortfolioObjectiveOutcomeBaseline baseline =
                baseline(
                        40,
                        0,
                        0,
                        0,
                        1,
                        0,
                        0,
                        0
                );

        when(
                repository.findBaselineForReview(
                        reviewId
                )
        ).thenReturn(
                Optional.of(
                        baseline
                )
        );

        ResiliencePortfolioSnapshot current =
                snapshot(
                        2,
                        75,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0
                );

        when(
                portfolioService.createSnapshot()
        ).thenReturn(
                current
        );

        when(
                repository.nextValidationNumber(
                        objectiveId
                )
        ).thenReturn(1);

        when(
                repository.saveValidation(
                        any(),
                        eq(objectiveId),
                        eq(1),
                        eq(baseline),
                        eq(current),
                        eq(
                                PortfolioObjectiveOutcome.ACHIEVED
                        ),
                        eq(0),
                        any()
                )
        ).thenReturn(
                new PortfolioObjectiveValidation(
                        UUID.randomUUID(),

                        objectiveId,
                        1,

                        baseline.snapshotId(),
                        baseline.snapshotNumber(),

                        current.id(),
                        current.snapshotNumber(),

                        PortfolioObjectiveOutcome.ACHIEVED,

                        baseline.score(),
                        current.score(),
                        35,

                        baseline.assuranceDebt(),
                        0,
                        -2,

                        1,
                        1,
                        1,

                        "Outcome achieved.",

                        Instant.now()
                )
        );

        PortfolioObjectiveValidation validation =
                service.validate(
                        objectiveId
                );

        assertThat(
                validation.outcome()
        ).isEqualTo(
                PortfolioObjectiveOutcome.ACHIEVED
        );

        verify(
                portfolioService
        ).createSnapshot();

        verify(
                objectiveRepository
        ).appendEvent(
                any(),
                eq(objectiveId),
                eq(
                        "OBJECTIVE_OUTCOME_VALIDATED"
                ),
                any()
        );
    }

    private void whenCompletedObjective() {
        when(
                objectiveRepository.findById(
                        objectiveId
                )
        ).thenReturn(
                Optional.of(
                        objective(
                                PortfolioObjectiveStatus.COMPLETED
                        )
                )
        );
    }

    private PortfolioObjective objective(
            PortfolioObjectiveStatus status
    ) {
        Instant now =
                Instant.now();

        return new PortfolioObjective(
                objectiveId,

                reviewId,
                1,

                "Raise enterprise audit coverage",
                "Governance objective.",

                PortfolioObjectivePriority.HIGH,
                status,

                "Executive Resilience Office",

                now.plusSeconds(
                        86400
                ),

                status
                        == PortfolioObjectiveStatus.COMPLETED
                        ? 100
                        : 50,

                "Raise verified coverage.",

                status
                        == PortfolioObjectiveStatus.COMPLETED
                        ? "Governance completion evidence."
                        : "",

                now,
                now,

                status
                        == PortfolioObjectiveStatus.COMPLETED
                        ? now
                        : null
        );
    }

    private PortfolioObjectiveOutcomeBaseline baseline(
            int score,
            int certified,
            int auditVerified,
            int validated,
            int untested,
            int revalidation,
            int failed,
            int openCorrective
    ) {
        return new PortfolioObjectiveOutcomeBaseline(
                UUID.randomUUID(),
                1,

                score,

                certified,
                auditVerified,
                validated,

                untested,
                revalidation,
                failed,
                openCorrective
        );
    }

    private ResiliencePortfolioResponse portfolio(
            int score,
            int certified,
            int auditVerified,
            int validated,
            int untested,
            int revalidation,
            int failed,
            int openCorrective
    ) {
        return new ResiliencePortfolioResponse(
                score,
                PortfolioAssuranceBand.fromScore(
                        score
                ),

                1,
                0,
                1,

                certified,
                certified,
                auditVerified,
                auditVerified,

                validated + untested + revalidation + failed,
                validated,
                untested,
                revalidation,
                failed,

                openCorrective,

                1,
                1,
                validated > 0
                        ? 1
                        : 0,

                validated > 0
                        ? 95.0
                        : 0.0,

                Instant.now()
        );
    }

    private ResiliencePortfolioSnapshot snapshot(
            int snapshotNumber,
            int score,
            int certified,
            int auditVerified,
            int validated,
            int untested,
            int revalidation,
            int failed,
            int openCorrective
    ) {
        return new ResiliencePortfolioSnapshot(
                UUID.randomUUID(),
                snapshotNumber,

                score,
                PortfolioAssuranceBand.fromScore(
                        score
                ),

                1,
                0,
                1,

                certified,
                certified,
                auditVerified,
                auditVerified,

                validated + untested + revalidation + failed,
                validated,
                untested,
                revalidation,
                failed,

                openCorrective,

                1,
                1,
                validated > 0
                        ? 1
                        : 0,

                validated > 0
                        ? 95.0
                        : 0.0,

                Instant.now()
        );
    }
}
