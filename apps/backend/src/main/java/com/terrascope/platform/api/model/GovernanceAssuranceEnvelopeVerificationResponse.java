package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceEnvelopeVerificationResponse(
        boolean valid,

        UUID sourceBundleId,
        Integer sourceBundleNumber,

        String envelopeVersion,
        String bundleVersion,

        String envelopeHash,
        String bundleHash,

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
