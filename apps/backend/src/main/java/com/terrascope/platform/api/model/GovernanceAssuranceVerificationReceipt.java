package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceVerificationReceipt(
        UUID id,

        int receiptNumber,

        UUID sourceBundleId,
        Integer sourceBundleNumber,

        String envelopeVersion,
        String bundleVersion,

        String envelopeHash,
        String bundleHash,

        String verificationResult,

        boolean hashValid,
        boolean canonicalPayloadValid,
        boolean portableVerificationValid,

        int signatureCount,
        int verifiedSignatureCount,

        int trustAnchorCount,
        int retiredAnchorCount,
        int lifecycleEventCount,

        String message,

        Instant verifiedAt
) {
}
