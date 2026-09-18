package com.terrascope.platform.service;

import com.terrascope.platform.api.model.BenefitRealizationResponse;
import com.terrascope.platform.api.model.BenefitRealizationSnapshot;
import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.BenefitRealizationMetrics;
import com.terrascope.platform.repository.BenefitRealizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BenefitRealizationServiceTest {

    private BenefitRealizationRepository repository;
    private BenefitRealizationService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        BenefitRealizationRepository.class
                );

        service =
                new BenefitRealizationService(
                        repository
                );
    }

    @Test
    void emptyPortfolioIsUnproven() {
        when(
                repository.loadMetrics()
        ).thenReturn(
                metrics(
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
                        0.0
                )
        );

        BenefitRealizationResponse response =
                service.realization();

        assertThat(
                response.band()
        ).isEqualTo(
                BenefitRealizationBand.UNPROVEN
        );

        assertThat(
                response.closureReady()
        ).isFalse();

        assertThat(
                response.validationCoveragePct()
        ).isZero();
    }

    @Test
    void partialRealizationIsNotReadyForClosure() {
        when(
                repository.loadMetrics()
        ).thenReturn(
                metrics(
                        1,

                        0,
                        0,
                        0,
                        1,
                        0,

                        1,
                        0,

                        0,
                        1,
                        0,

                        2.0,
                        0.0
                )
        );

        BenefitRealizationResponse response =
                service.realization();

        assertThat(
                response.band()
        ).isEqualTo(
                BenefitRealizationBand.REALIZING
        );

        assertThat(
                response.validationCoveragePct()
        ).isEqualTo(100.0);

        assertThat(
                response.realizedBenefitRatePct()
        ).isZero();

        assertThat(
                response.closureReady()
        ).isFalse();
    }

    @Test
    void fullyAchievedPortfolioIsRealizedAndClosureReady() {
        when(
                repository.loadMetrics()
        ).thenReturn(
                metrics(
                        2,

                        0,
                        0,
                        0,
                        2,
                        0,

                        2,
                        0,

                        2,
                        0,
                        0,

                        12.5,
                        -4.0
                )
        );

        BenefitRealizationResponse response =
                service.realization();

        assertThat(
                response.band()
        ).isEqualTo(
                BenefitRealizationBand.REALIZED
        );

        assertThat(
                response.closureReady()
        ).isTrue();

        assertThat(
                response.validationCoveragePct()
        ).isEqualTo(100.0);

        assertThat(
                response.realizedBenefitRatePct()
        ).isEqualTo(100.0);
    }

    @Test
    void snapshotPersistsCurrentRealization() {
        BenefitRealizationMetrics metrics =
                metrics(
                        1,

                        0,
                        0,
                        0,
                        1,
                        0,

                        1,
                        0,

                        1,
                        0,
                        0,

                        25.0,
                        -2.0
                );

        when(
                repository.loadMetrics()
        ).thenReturn(
                metrics
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
                    BenefitRealizationResponse realization =
                            invocation.getArgument(
                                    2
                            );

                    return new BenefitRealizationSnapshot(
                            UUID.randomUUID(),
                            1,

                            realization.band(),
                            realization.closureReady(),

                            realization.totalObjectiveCount(),

                            realization.plannedObjectiveCount(),
                            realization.inProgressObjectiveCount(),
                            realization.blockedObjectiveCount(),
                            realization.completedObjectiveCount(),
                            realization.cancelledObjectiveCount(),

                            realization.validatedObjectiveCount(),
                            realization.unvalidatedCompletedObjectiveCount(),

                            realization.achievedCount(),
                            realization.partialCount(),
                            realization.notAchievedCount(),

                            realization.validationCoveragePct(),
                            realization.realizedBenefitRatePct(),

                            realization.averageScoreDelta(),
                            realization.averageAssuranceDebtDelta(),

                            Instant.now()
                    );
                }
        );

        BenefitRealizationSnapshot snapshot =
                service.createSnapshot();

        assertThat(
                snapshot.snapshotNumber()
        ).isEqualTo(1);

        assertThat(
                snapshot.band()
        ).isEqualTo(
                BenefitRealizationBand.REALIZED
        );

        verify(
                repository
        ).saveSnapshot(
                any(),
                eq(1),
                any()
        );
    }

    private BenefitRealizationMetrics metrics(
            int total,

            int planned,
            int inProgress,
            int blocked,
            int completed,
            int cancelled,

            int validated,
            int unvalidated,

            int achieved,
            int partial,
            int notAchieved,

            double averageScoreDelta,
            double averageDebtDelta
    ) {
        return new BenefitRealizationMetrics(
                total,

                planned,
                inProgress,
                blocked,
                completed,
                cancelled,

                validated,
                unvalidated,

                achieved,
                partial,
                notAchieved,

                averageScoreDelta,
                averageDebtDelta
        );
    }
}
