package com.terrascope.platform.service;

import com.terrascope.platform.api.model.BenefitRealizationHistoryResponse;
import com.terrascope.platform.api.model.BenefitRealizationResponse;
import com.terrascope.platform.api.model.BenefitRealizationSnapshot;
import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.BenefitRealizationMetrics;
import com.terrascope.platform.repository.BenefitRealizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class BenefitRealizationService {

    private final BenefitRealizationRepository repository;

    public BenefitRealizationService(
            BenefitRealizationRepository repository
    ) {
        this.repository = repository;
    }

    public BenefitRealizationResponse realization() {
        BenefitRealizationMetrics metrics =
                repository.loadMetrics();

        double validationCoveragePct =
                percentage(
                        metrics.validatedObjectiveCount(),
                        metrics.completedObjectiveCount()
                );

        double realizedBenefitRatePct =
                percentage(
                        metrics.achievedCount(),
                        metrics.validatedObjectiveCount()
                );

        boolean closureReady =
                metrics.totalObjectiveCount() > 0
                && metrics.activeObjectiveCount() == 0
                && metrics.completedObjectiveCount() > 0

                && metrics.unvalidatedCompletedObjectiveCount()
                   == 0

                && metrics.partialCount()
                   == 0

                && metrics.notAchievedCount()
                   == 0

                && metrics.achievedCount()
                   == metrics.completedObjectiveCount();

        BenefitRealizationBand band =
                BenefitRealizationBand.derive(
                        metrics.completedObjectiveCount(),
                        metrics.validatedObjectiveCount(),
                        metrics.notAchievedCount(),
                        validationCoveragePct,
                        realizedBenefitRatePct,
                        closureReady
                );

        return new BenefitRealizationResponse(
                band,
                closureReady,

                metrics.totalObjectiveCount(),

                metrics.plannedObjectiveCount(),
                metrics.inProgressObjectiveCount(),
                metrics.blockedObjectiveCount(),
                metrics.completedObjectiveCount(),
                metrics.cancelledObjectiveCount(),

                metrics.validatedObjectiveCount(),
                metrics.unvalidatedCompletedObjectiveCount(),

                metrics.achievedCount(),
                metrics.partialCount(),
                metrics.notAchievedCount(),

                validationCoveragePct,
                realizedBenefitRatePct,

                rounded(
                        metrics.averageScoreDelta()
                ),

                rounded(
                        metrics.averageAssuranceDebtDelta()
                ),

                Instant.now()
        );
    }

    public BenefitRealizationHistoryResponse history() {
        return new BenefitRealizationHistoryResponse(
                repository.findHistory()
        );
    }

    @Transactional
    public BenefitRealizationSnapshot createSnapshot() {
        BenefitRealizationResponse realization =
                realization();

        return repository.saveSnapshot(
                UUID.randomUUID(),
                repository.nextSnapshotNumber(),
                realization
        );
    }

    private double percentage(
            int numerator,
            int denominator
    ) {
        if (
                denominator <= 0
        ) {
            return 0.0;
        }

        return rounded(
                (
                    (double) numerator
                    / denominator
                )
                * 100.0
        );
    }

    private double rounded(
            double value
    ) {
        return Math.round(
                value
                * 10.0
        ) / 10.0;
    }
}
