package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioReview;
import com.terrascope.platform.api.model.ResiliencePortfolioReviewPreview;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.domain.PortfolioAssuranceBand;
import com.terrascope.platform.domain.PortfolioTrendDirection;
import com.terrascope.platform.repository.ResiliencePortfolioRepository;
import com.terrascope.platform.repository.ResiliencePortfolioReviewRepository;
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

class ResiliencePortfolioReviewServiceTest {

    private ResiliencePortfolioService portfolioService;
    private ResiliencePortfolioRepository portfolioRepository;
    private ResiliencePortfolioReviewRepository reviewRepository;

    private ResiliencePortfolioReviewService service;

    @BeforeEach
    void setUp() {
        portfolioService =
                mock(
                        ResiliencePortfolioService.class
                );

        portfolioRepository =
                mock(
                        ResiliencePortfolioRepository.class
                );

        reviewRepository =
                mock(
                        ResiliencePortfolioReviewRepository.class
                );

        service =
                new ResiliencePortfolioReviewService(
                        portfolioService,
                        portfolioRepository,
                        reviewRepository
                );
    }

    @Test
    void noPriorSnapshotCreatesBaselinePreview() {
        when(
                portfolioService.portfolio()
        ).thenReturn(
                portfolio(
                        45,
                        1,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0
                )
        );

        when(
                portfolioRepository.findHistory()
        ).thenReturn(
                List.of()
        );

        ResiliencePortfolioReviewPreview preview =
                service.preview();

        assertThat(
                preview.trend()
        ).isEqualTo(
                PortfolioTrendDirection.BASELINE
        );

        assertThat(
                preview.previousSnapshotId()
        ).isNull();

        assertThat(
                preview.scoreDelta()
        ).isZero();
    }

    @Test
    void scoreGrowthCreatesImprovingPreview() {
        ResiliencePortfolioSnapshot previous =
                snapshot(
                        1,
                        40,
                        0,
                        0,
                        1,
                        0,
                        0,
                        0,
                        0
                );

        when(
                portfolioRepository.findHistory()
        ).thenReturn(
                List.of(
                        previous
                )
        );

        when(
                portfolioService.portfolio()
        ).thenReturn(
                portfolio(
                        55,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0
                )
        );

        ResiliencePortfolioReviewPreview preview =
                service.preview();

        assertThat(
                preview.trend()
        ).isEqualTo(
                PortfolioTrendDirection.IMPROVING
        );

        assertThat(
                preview.scoreDelta()
        ).isEqualTo(15);

        assertThat(
                preview.assuranceDebtDelta()
        ).isEqualTo(-2);
    }

    @Test
    void debtGrowthCanCreateDecliningPreview() {
        ResiliencePortfolioSnapshot previous =
                snapshot(
                        1,
                        50,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0
                );

        when(
                portfolioRepository.findHistory()
        ).thenReturn(
                List.of(
                        previous
                )
        );

        when(
                portfolioService.portfolio()
        ).thenReturn(
                portfolio(
                        50,
                        1,
                        1,
                        0,
                        1,
                        0,
                        0,
                        0
                )
        );

        ResiliencePortfolioReviewPreview preview =
                service.preview();

        assertThat(
                preview.trend()
        ).isEqualTo(
                PortfolioTrendDirection.DECLINING
        );

        assertThat(
                preview.assuranceDebtDelta()
        ).isEqualTo(3);
    }

    @Test
    void reviewCapturesCurrentSnapshotAndHistoryEvidence() {
        ResiliencePortfolioSnapshot previous =
                snapshot(
                        1,
                        40,
                        0,
                        0,
                        1,
                        0,
                        0,
                        0,
                        0
                );

        ResiliencePortfolioSnapshot current =
                snapshot(
                        2,
                        55,
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0
                );

        when(
                portfolioRepository.findHistory()
        ).thenReturn(
                List.of(
                        previous
                )
        );

        when(
                portfolioService.createSnapshot()
        ).thenReturn(
                current
        );

        when(
                reviewRepository.nextReviewNumber()
        ).thenReturn(1);

        when(
                reviewRepository.saveReview(
                        any(),
                        eq(1),
                        eq(current),
                        eq(previous),
                        any()
                )
        ).thenAnswer(
                invocation -> {
                    ResiliencePortfolioReviewPreview preview =
                            invocation.getArgument(
                                    4
                            );

                    return new ResiliencePortfolioReview(
                            UUID.randomUUID(),
                            1,

                            current.id(),
                            current.snapshotNumber(),

                            previous.id(),
                            previous.snapshotNumber(),

                            preview.trend(),

                            preview.currentScore(),
                            preview.previousScore(),
                            preview.scoreDelta(),

                            preview.currentAssuranceDebt(),
                            preview.previousAssuranceDebt(),
                            preview.assuranceDebtDelta(),

                            preview.certifiedIncidentDelta(),
                            preview.validatedImprovementDelta(),
                            preview.auditVerifiedIncidentDelta(),

                            preview.executiveSummary(),

                            Instant.now()
                    );
                }
        );

        ResiliencePortfolioReview review =
                service.createReview();

        assertThat(
                review.reviewNumber()
        ).isEqualTo(1);

        assertThat(
                review.currentSnapshotNumber()
        ).isEqualTo(2);

        assertThat(
                review.previousSnapshotNumber()
        ).isEqualTo(1);

        verify(
                portfolioService
        ).createSnapshot();

        verify(
                reviewRepository
        ).saveReview(
                any(),
                eq(1),
                eq(current),
                eq(previous),
                any()
        );
    }

    private ResiliencePortfolioResponse portfolio(
            int score,
            int certified,
            int auditVerified,
            int untested,
            int revalidation,
            int failed,
            int openCorrective,
            int validated
    ) {
        return new ResiliencePortfolioResponse(
                score,
                PortfolioAssuranceBand.fromScore(
                        score
                ),

                2,
                0,
                2,

                certified,
                certified,
                auditVerified,
                auditVerified,

                1,
                validated,
                untested,
                revalidation,
                failed,

                openCorrective,

                1,
                1,
                1,

                validated > 0
                        ? 95.0
                        : 0.0,

                Instant.now()
        );
    }

    private ResiliencePortfolioSnapshot snapshot(
            int number,
            int score,
            int certified,
            int auditVerified,
            int untested,
            int revalidation,
            int failed,
            int openCorrective,
            int validated
    ) {
        return new ResiliencePortfolioSnapshot(
                UUID.randomUUID(),
                number,

                score,
                PortfolioAssuranceBand.fromScore(
                        score
                ),

                2,
                0,
                2,

                certified,
                certified,
                auditVerified,
                auditVerified,

                1,
                validated,
                untested,
                revalidation,
                failed,

                openCorrective,

                1,
                1,
                1,

                validated > 0
                        ? 95.0
                        : 0.0,

                Instant.now()
        );
    }
}
