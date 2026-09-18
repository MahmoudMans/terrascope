package com.terrascope.platform.api.model;

import com.terrascope.platform.domain.BenefitRealizationBand;

import java.time.Instant;
import java.util.UUID;

public record GovernanceClosureCertificate(
        UUID id,

        int certificateNumber,

        UUID benefitSnapshotId,
        int benefitSnapshotNumber,

        BenefitRealizationBand band,
        boolean closureReady,

        int totalObjectiveCount,
        int completedObjectiveCount,
        int validatedObjectiveCount,
        int achievedCount,

        double validationCoveragePct,
        double realizedBenefitRatePct,

        double averageScoreDelta,
        double averageAssuranceDebtDelta,

        String previousCertificateHash,

        String dossierVersion,
        String dossierHash,

        String certificateVersion,
        String certificateHash,

        String executiveSummary,

        Instant createdAt
) {
}
