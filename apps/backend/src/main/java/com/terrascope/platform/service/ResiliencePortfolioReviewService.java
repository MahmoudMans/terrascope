package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ResiliencePortfolioHistoryResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioReview;
import com.terrascope.platform.api.model.ResiliencePortfolioReviewHistoryResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioReviewPreview;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.domain.PortfolioTrendDirection;
import com.terrascope.platform.repository.ResiliencePortfolioRepository;
import com.terrascope.platform.repository.ResiliencePortfolioReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResiliencePortfolioReviewService {

    private final ResiliencePortfolioService portfolioService;
    private final ResiliencePortfolioRepository portfolioRepository;
    private final ResiliencePortfolioReviewRepository reviewRepository;

    public ResiliencePortfolioReviewService(
            ResiliencePortfolioService portfolioService,
            ResiliencePortfolioRepository portfolioRepository,
            ResiliencePortfolioReviewRepository reviewRepository
    ) {
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.reviewRepository = reviewRepository;
    }

    public ResiliencePortfolioReviewPreview preview() {
        ResiliencePortfolioResponse current =
                portfolioService.portfolio();

        List<ResiliencePortfolioSnapshot> history =
                portfolioRepository.findHistory();

        ResiliencePortfolioSnapshot previous =
                history.isEmpty()
                        ? null
                        : history.getFirst();

        return previewFromLive(
                current,
                previous
        );
    }

    public ResiliencePortfolioReviewHistoryResponse history() {
        return new ResiliencePortfolioReviewHistoryResponse(
                reviewRepository.findHistory()
        );
    }

    @Transactional
    public ResiliencePortfolioReview createReview() {
        List<ResiliencePortfolioSnapshot> before =
                portfolioRepository.findHistory();

        ResiliencePortfolioSnapshot previous =
                before.isEmpty()
                        ? null
                        : before.getFirst();

        ResiliencePortfolioSnapshot current =
                portfolioService.createSnapshot();

        ResiliencePortfolioReviewPreview preview =
                previewFromSnapshots(
                        current,
                        previous
                );

        return reviewRepository.saveReview(
                UUID.randomUUID(),
                reviewRepository.nextReviewNumber(),
                current,
                previous,
                preview
        );
    }

    private ResiliencePortfolioReviewPreview previewFromLive(
            ResiliencePortfolioResponse current,
            ResiliencePortfolioSnapshot previous
    ) {
        int currentDebt =
                assuranceDebt(
                        current.untestedImprovementCount(),
                        current.needsRevalidationCount(),
                        current.failedImprovementCount(),
                        current.openCorrectiveActionCount()
                );

        return buildPreview(
                previous,

                current.score(),
                currentDebt,

                current.certifiedIncidentCount(),
                current.validatedImprovementCount(),
                current.auditVerifiedIncidentCount()
        );
    }

    private ResiliencePortfolioReviewPreview previewFromSnapshots(
            ResiliencePortfolioSnapshot current,
            ResiliencePortfolioSnapshot previous
    ) {
        int currentDebt =
                assuranceDebt(
                        current.untestedImprovementCount(),
                        current.needsRevalidationCount(),
                        current.failedImprovementCount(),
                        current.openCorrectiveActionCount()
                );

        return buildPreview(
                previous,

                current.score(),
                currentDebt,

                current.certifiedIncidentCount(),
                current.validatedImprovementCount(),
                current.auditVerifiedIncidentCount()
        );
    }

    private ResiliencePortfolioReviewPreview buildPreview(
            ResiliencePortfolioSnapshot previous,

            int currentScore,
            int currentDebt,

            int currentCertifiedIncidentCount,
            int currentValidatedImprovementCount,
            int currentAuditVerifiedIncidentCount
    ) {
        boolean hasPrevious =
                previous != null;

        Integer previousScore =
                hasPrevious
                        ? previous.score()
                        : null;

        Integer previousDebt =
                hasPrevious
                        ? assuranceDebt(
                                previous.untestedImprovementCount(),
                                previous.needsRevalidationCount(),
                                previous.failedImprovementCount(),
                                previous.openCorrectiveActionCount()
                        )
                        : null;

        int scoreDelta =
                hasPrevious
                        ? currentScore
                          - previous.score()
                        : 0;

        int debtDelta =
                hasPrevious
                        ? currentDebt
                          - previousDebt
                        : 0;

        int certifiedDelta =
                hasPrevious
                        ? currentCertifiedIncidentCount
                          - previous.certifiedIncidentCount()
                        : 0;

        int validatedDelta =
                hasPrevious
                        ? currentValidatedImprovementCount
                          - previous.validatedImprovementCount()
                        : 0;

        int auditDelta =
                hasPrevious
                        ? currentAuditVerifiedIncidentCount
                          - previous.auditVerifiedIncidentCount()
                        : 0;

        PortfolioTrendDirection trend =
                PortfolioTrendDirection.derive(
                        hasPrevious,
                        scoreDelta,
                        debtDelta
                );

        String summary =
                buildSummary(
                        previous,
                        trend,

                        currentScore,
                        previousScore,
                        scoreDelta,

                        currentDebt,
                        previousDebt,
                        debtDelta,

                        certifiedDelta,
                        validatedDelta,
                        auditDelta
                );

        return new ResiliencePortfolioReviewPreview(
                hasPrevious
                        ? previous.id()
                        : null,

                hasPrevious
                        ? previous.snapshotNumber()
                        : null,

                trend,

                currentScore,
                previousScore,
                scoreDelta,

                currentDebt,
                previousDebt,
                debtDelta,

                currentCertifiedIncidentCount,
                certifiedDelta,

                currentValidatedImprovementCount,
                validatedDelta,

                currentAuditVerifiedIncidentCount,
                auditDelta,

                summary,

                Instant.now()
        );
    }

    private int assuranceDebt(
            int untestedImprovementCount,
            int needsRevalidationCount,
            int failedImprovementCount,
            int openCorrectiveActionCount
    ) {
        return (
                untestedImprovementCount * 2
                + needsRevalidationCount * 3
                + failedImprovementCount * 5
                + openCorrectiveActionCount * 2
        );
    }

    private String buildSummary(
            ResiliencePortfolioSnapshot previous,
            PortfolioTrendDirection trend,

            int currentScore,
            Integer previousScore,
            int scoreDelta,

            int currentDebt,
            Integer previousDebt,
            int debtDelta,

            int certifiedDelta,
            int validatedDelta,
            int auditDelta
    ) {
        if (
                previous == null
        ) {
            return String.format(
                    Locale.ROOT,
                    "Portfolio baseline established at %d/100 with assurance debt %d. "
                            + "No prior portfolio snapshot is available for trend comparison.",
                    currentScore,
                    currentDebt
            );
        }

        return String.format(
                Locale.ROOT,
                "Portfolio trend is %s. Score moved from %d/100 to %d/100 "
                        + "(delta %+d). Assurance debt moved from %d to %d "
                        + "(delta %+d). Certified incidents changed by %+d, "
                        + "validated improvements by %+d, and verified audit "
                        + "ledgers by %+d versus portfolio snapshot #%d.",
                trend.name(),
                previousScore,
                currentScore,
                scoreDelta,
                previousDebt,
                currentDebt,
                debtDelta,
                certifiedDelta,
                validatedDelta,
                auditDelta,
                previous.snapshotNumber()
        );
    }
}
