package com.terrascope.platform.service;

import com.terrascope.platform.api.model.PortfolioObjective;
import com.terrascope.platform.api.model.PortfolioObjectiveOutcomePreview;
import com.terrascope.platform.api.model.PortfolioObjectiveValidation;
import com.terrascope.platform.api.model.PortfolioObjectiveValidationHistoryResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioResponse;
import com.terrascope.platform.api.model.ResiliencePortfolioSnapshot;
import com.terrascope.platform.domain.PortfolioObjectiveOutcome;
import com.terrascope.platform.domain.PortfolioObjectiveOutcomeBaseline;
import com.terrascope.platform.domain.PortfolioObjectiveStatus;
import com.terrascope.platform.repository.PortfolioObjectiveOutcomeRepository;
import com.terrascope.platform.repository.ResiliencePortfolioObjectiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PortfolioObjectiveOutcomeService {

    private final PortfolioObjectiveOutcomeRepository repository;
    private final ResiliencePortfolioObjectiveRepository objectiveRepository;
    private final ResiliencePortfolioService portfolioService;

    public PortfolioObjectiveOutcomeService(
            PortfolioObjectiveOutcomeRepository repository,
            ResiliencePortfolioObjectiveRepository objectiveRepository,
            ResiliencePortfolioService portfolioService
    ) {
        this.repository = repository;
        this.objectiveRepository = objectiveRepository;
        this.portfolioService = portfolioService;
    }

    public PortfolioObjectiveOutcomePreview preview(
            UUID objectiveId
    ) {
        PortfolioObjective objective =
                requireCompletedObjective(
                        objectiveId
                );

        PortfolioObjectiveOutcomeBaseline baseline =
                requireBaseline(
                        objective
                );

        ResiliencePortfolioResponse current =
                portfolioService.portfolio();

        return previewFromLive(
                objective,
                baseline,
                current
        );
    }

    public PortfolioObjectiveValidationHistoryResponse history(
            UUID objectiveId
    ) {
        requireObjective(
                objectiveId
        );

        return new PortfolioObjectiveValidationHistoryResponse(
                objectiveId,
                repository.findHistory(
                        objectiveId
                )
        );
    }

    @Transactional
    public PortfolioObjectiveValidation validate(
            UUID objectiveId
    ) {
        PortfolioObjective objective =
                requireCompletedObjective(
                        objectiveId
                );

        PortfolioObjectiveOutcomeBaseline baseline =
                requireBaseline(
                        objective
                );

        ResiliencePortfolioSnapshot currentSnapshot =
                portfolioService.createSnapshot();

        int currentDebt =
                assuranceDebt(
                        currentSnapshot.untestedImprovementCount(),
                        currentSnapshot.needsRevalidationCount(),
                        currentSnapshot.failedImprovementCount(),
                        currentSnapshot.openCorrectiveActionCount()
                );

        int scoreDelta =
                currentSnapshot.score()
                - baseline.score();

        int debtDelta =
                currentDebt
                - baseline.assuranceDebt();

        PortfolioObjectiveOutcome outcome =
                PortfolioObjectiveOutcome.derive(
                        scoreDelta,
                        debtDelta
                );

        String summary =
                buildSummary(
                        objective,
                        baseline,

                        currentSnapshot.score(),
                        currentDebt,

                        currentSnapshot.certifiedIncidentCount(),
                        currentSnapshot.validatedImprovementCount(),
                        currentSnapshot.auditVerifiedIncidentCount(),

                        outcome
                );

        PortfolioObjectiveValidation validation =
                repository.saveValidation(
                        UUID.randomUUID(),
                        objectiveId,
                        repository.nextValidationNumber(
                                objectiveId
                        ),
                        baseline,
                        currentSnapshot,
                        outcome,
                        currentDebt,
                        summary
                );

        objectiveRepository.appendEvent(
                UUID.randomUUID(),
                objectiveId,
                "OBJECTIVE_OUTCOME_VALIDATED",
                "Outcome validation #"
                        + validation.validationNumber()
                        + " classified strategic objective '"
                        + objective.title()
                        + "' as "
                        + validation.outcome().name()
                        + " with portfolio score delta "
                        + signed(
                                validation.scoreDelta()
                        )
                        + " and assurance debt delta "
                        + signed(
                                validation.assuranceDebtDelta()
                        )
                        + "."
        );

        return validation;
    }

    private PortfolioObjectiveOutcomePreview previewFromLive(
            PortfolioObjective objective,
            PortfolioObjectiveOutcomeBaseline baseline,
            ResiliencePortfolioResponse current
    ) {
        int currentDebt =
                assuranceDebt(
                        current.untestedImprovementCount(),
                        current.needsRevalidationCount(),
                        current.failedImprovementCount(),
                        current.openCorrectiveActionCount()
                );

        int scoreDelta =
                current.score()
                - baseline.score();

        int debtDelta =
                currentDebt
                - baseline.assuranceDebt();

        int certifiedDelta =
                current.certifiedIncidentCount()
                - baseline.certifiedIncidentCount();

        int validatedDelta =
                current.validatedImprovementCount()
                - baseline.validatedImprovementCount();

        int auditDelta =
                current.auditVerifiedIncidentCount()
                - baseline.auditVerifiedIncidentCount();

        PortfolioObjectiveOutcome outcome =
                PortfolioObjectiveOutcome.derive(
                        scoreDelta,
                        debtDelta
                );

        String summary =
                buildSummary(
                        objective,
                        baseline,

                        current.score(),
                        currentDebt,

                        current.certifiedIncidentCount(),
                        current.validatedImprovementCount(),
                        current.auditVerifiedIncidentCount(),

                        outcome
                );

        return new PortfolioObjectiveOutcomePreview(
                objective.id(),
                objective.title(),

                baseline.snapshotId(),
                baseline.snapshotNumber(),

                outcome,

                baseline.score(),
                current.score(),
                scoreDelta,

                baseline.assuranceDebt(),
                currentDebt,
                debtDelta,

                certifiedDelta,
                validatedDelta,
                auditDelta,

                summary,

                Instant.now()
        );
    }

    private String buildSummary(
            PortfolioObjective objective,
            PortfolioObjectiveOutcomeBaseline baseline,

            int currentScore,
            int currentDebt,

            int currentCertifiedIncidentCount,
            int currentValidatedImprovementCount,
            int currentAuditVerifiedIncidentCount,

            PortfolioObjectiveOutcome outcome
    ) {
        int scoreDelta =
                currentScore
                - baseline.score();

        int debtDelta =
                currentDebt
                - baseline.assuranceDebt();

        int certifiedDelta =
                currentCertifiedIncidentCount
                - baseline.certifiedIncidentCount();

        int validatedDelta =
                currentValidatedImprovementCount
                - baseline.validatedImprovementCount();

        int auditDelta =
                currentAuditVerifiedIncidentCount
                - baseline.auditVerifiedIncidentCount();

        return String.format(
                Locale.ROOT,
                "Strategic objective '%s' is classified %s against portfolio "
                        + "snapshot #%d. Portfolio score moved from %d/100 to %d/100 "
                        + "(delta %+d). Assurance debt moved from %d to %d "
                        + "(delta %+d). Certified incidents changed by %+d, "
                        + "validated improvements by %+d, and verified audit-ledger "
                        + "coverage by %+d.",
                objective.title(),
                outcome.name(),
                baseline.snapshotNumber(),
                baseline.score(),
                currentScore,
                scoreDelta,
                baseline.assuranceDebt(),
                currentDebt,
                debtDelta,
                certifiedDelta,
                validatedDelta,
                auditDelta
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

    private String signed(
            int value
    ) {
        return value > 0
                ? "+" + value
                : Integer.toString(
                        value
                );
    }

    private PortfolioObjectiveOutcomeBaseline requireBaseline(
            PortfolioObjective objective
    ) {
        return repository
                .findBaselineForReview(
                        objective.portfolioReviewId()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "The strategic objective source review has no portfolio baseline snapshot."
                        )
                );
    }

    private PortfolioObjective requireCompletedObjective(
            UUID objectiveId
    ) {
        PortfolioObjective objective =
                requireObjective(
                        objectiveId
                );

        if (
                objective.status()
                != PortfolioObjectiveStatus.COMPLETED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Strategic objective outcome validation requires a COMPLETED objective."
            );
        }

        if (
                objective.completionEvidence()
                        .isBlank()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Strategic objective outcome validation requires completion evidence."
            );
        }

        return objective;
    }

    private PortfolioObjective requireObjective(
            UUID objectiveId
    ) {
        return objectiveRepository
                .findById(
                        objectiveId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Strategic objective "
                                        + objectiveId
                                        + " was not found."
                        )
                );
    }
}
