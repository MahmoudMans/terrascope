package com.terrascope.platform.service;

import com.terrascope.platform.api.model.ImprovementValidationRecord;
import com.terrascope.platform.api.model.ResilienceAuditLedgerVerificationResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioHistoryResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.api.model.ResilienceValidationBoardResponse;
import com.terrascope.platform.domain.ImprovementValidationStatus;
import com.terrascope.platform.domain.PortfolioAssuranceBand;
import com.terrascope.platform.domain.ResiliencePortfolioBaseMetrics;
import com.terrascope.platform.repository.ResiliencePortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ResiliencePortfolioService {

    private final ResiliencePortfolioRepository repository;
    private final ResilienceValidationService validationService;
    private final ResilienceAuditLedgerService auditLedgerService;

    public ResiliencePortfolioService(
            ResiliencePortfolioRepository repository,
            ResilienceValidationService validationService,
            ResilienceAuditLedgerService auditLedgerService
    ) {
        this.repository = repository;
        this.validationService = validationService;
        this.auditLedgerService = auditLedgerService;
    }

    public ResiliencePortfolioResponse portfolio() {
        ResiliencePortfolioBaseMetrics base =
                repository.loadBaseMetrics();

        int completedImprovementCount =
                0;

        int validatedImprovementCount =
                0;

        int untestedImprovementCount =
                0;

        int needsRevalidationCount =
                0;

        int failedImprovementCount =
                0;

        int openCorrectiveActionCount =
                0;

        long readinessTotal =
                0;

        int readinessCount =
                0;

        for (
                UUID incidentId
                : repository.findClosedIncidentIds()
        ) {
            ResilienceValidationBoardResponse validation =
                    validationService.board(
                            incidentId
                    );

            completedImprovementCount +=
                    validation.completedImprovementCount();

            validatedImprovementCount +=
                    validation.validatedCount();

            untestedImprovementCount +=
                    validation.untestedCount();

            needsRevalidationCount +=
                    validation.needsRevalidationCount();

            failedImprovementCount +=
                    validation.failedCount();

            openCorrectiveActionCount +=
                    validation.openCorrectiveActionCount()
                    + validation.inProgressCorrectiveActionCount();

            for (
                    ImprovementValidationRecord record
                    : validation.validations()
            ) {
                if (
                        record.latestReadinessScore()
                        != null
                ) {
                    readinessTotal +=
                            record.latestReadinessScore();

                    readinessCount++;
                }
            }
        }

        int auditVerifiedIncidentCount =
                0;

        for (
                UUID incidentId
                : repository.findAuditLedgerIncidentIds()
        ) {
            ResilienceAuditLedgerVerificationResponse verification =
                    auditLedgerService.verify(
                            incidentId
                    );

            if (
                    verification.valid()
            ) {
                auditVerifiedIncidentCount++;
            }
        }

        double averageLatestReadinessScore =
                readinessCount == 0
                        ? 0.0
                        : (
                            Math.round(
                                    (
                                        (
                                            (double) readinessTotal
                                        )
                                        / readinessCount
                                    )
                                    * 10.0
                            )
                            / 10.0
                        );

        int score =
                calculateScore(
                        base,
                        auditVerifiedIncidentCount,

                        completedImprovementCount,
                        validatedImprovementCount,
                        untestedImprovementCount,
                        needsRevalidationCount,
                        failedImprovementCount,

                        openCorrectiveActionCount
                );

        return new ResiliencePortfolioResponse(
                score,
                PortfolioAssuranceBand.fromScore(
                        score
                ),

                base.totalIncidentCount(),
                base.openIncidentCount(),
                base.closedIncidentCount(),

                base.certifiedIncidentCount(),
                base.evidencePackagedIncidentCount(),
                base.auditLedgerIncidentCount(),
                auditVerifiedIncidentCount,

                completedImprovementCount,
                validatedImprovementCount,
                untestedImprovementCount,
                needsRevalidationCount,
                failedImprovementCount,

                openCorrectiveActionCount,

                base.totalExerciseCount(),
                base.completedExerciseCount(),
                base.passedExerciseCount(),

                averageLatestReadinessScore,

                Instant.now()
        );
    }

    public ResiliencePortfolioHistoryResponse history() {
        return new ResiliencePortfolioHistoryResponse(
                repository.findHistory()
        );
    }

    @Transactional
    public ResiliencePortfolioSnapshot createSnapshot() {
        ResiliencePortfolioResponse portfolio =
                portfolio();

        return repository.saveSnapshot(
                UUID.randomUUID(),
                repository.nextSnapshotNumber(),
                portfolio
        );
    }

    int calculateScore(
            ResiliencePortfolioBaseMetrics base,
            int auditVerifiedIncidentCount,

            int completedImprovementCount,
            int validatedImprovementCount,
            int untestedImprovementCount,
            int needsRevalidationCount,
            int failedImprovementCount,

            int openCorrectiveActionCount
    ) {
        int score =
                0;

        score +=
                weightedRatio(
                        35,
                        base.certifiedIncidentCount(),
                        base.closedIncidentCount()
                );

        score +=
                weightedRatio(
                        20,
                        base.evidencePackagedIncidentCount(),
                        base.closedIncidentCount()
                );

        score +=
                weightedRatio(
                        15,
                        auditVerifiedIncidentCount,
                        base.closedIncidentCount()
                );

        score +=
                weightedRatio(
                        20,
                        validatedImprovementCount,
                        completedImprovementCount
                );

        score +=
                weightedRatio(
                        10,
                        base.passedExerciseCount(),
                        base.completedExerciseCount()
                );

        int assuranceDebt =
                untestedImprovementCount * 2
                + needsRevalidationCount * 3
                + failedImprovementCount * 5
                + openCorrectiveActionCount * 2;

        int penalty =
                Math.min(
                        20,
                        assuranceDebt
                );

        return Math.max(
                0,
                Math.min(
                        100,
                        score - penalty
                )
        );
    }

    private int weightedRatio(
            int weight,
            int numerator,
            int denominator
    ) {
        if (
                denominator <= 0
        ) {
            return 0;
        }

        double ratio =
                Math.min(
                        1.0,
                        Math.max(
                                0.0,
                                (
                                    (double)
                                    numerator
                                )
                                / denominator
                        )
                );

        return (
                int
                ) Math.round(
                        weight * ratio
                );
    }
}
