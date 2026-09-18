package com.terrascope.platform.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GovernanceAssuranceBundlePayload(
        String bundleVersion,

        CertificateEvidence certificate,

        List<SignatureEvidence> signatures,

        List<TrustAnchorEvidence> trustAnchors,

        List<TrustAnchorEventEvidence> lifecycleEvents
) {

    public GovernanceAssuranceBundlePayload {
        signatures =
                List.copyOf(
                        signatures
                );

        trustAnchors =
                List.copyOf(
                        trustAnchors
                );

        lifecycleEvents =
                List.copyOf(
                        lifecycleEvents
                );
    }

    public record CertificateEvidence(
            UUID id,

            int certificateNumber,

            UUID benefitSnapshotId,
            int benefitSnapshotNumber,

            String band,
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

    public record SignatureEvidence(
            UUID id,

            UUID certificateId,
            int certificateNumber,

            String keyId,
            String algorithm,

            String publicKeyFingerprint,

            String signatureVersion,

            String certificateHash,
            String dossierHash,

            String signatureBase64,

            Instant createdAt
    ) {
    }

    public record TrustAnchorEvidence(
            String keyId,

            String algorithm,

            String publicKeyX509Base64,
            String publicKeyFingerprint,

            String status,

            String supersededByKeyId,
            String retirementReason,

            Instant createdAt,
            Instant lastUsedAt,
            Instant retiredAt
    ) {
    }

    public record TrustAnchorEventEvidence(
            UUID id,

            String keyId,

            String eventType,

            String replacementKeyId,

            String detail,

            Instant createdAt
    ) {
    }
}
