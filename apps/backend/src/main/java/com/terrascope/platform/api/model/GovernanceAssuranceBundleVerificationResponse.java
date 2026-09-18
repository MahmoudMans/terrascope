package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceBundleVerificationResponse(
        boolean valid,

        UUID bundleId,
        int bundleNumber,

        boolean hashValid,
        boolean canonicalPayloadValid,
        boolean portableVerificationValid,

        int signatureCount,
        int verifiedSignatureCount,

        String message,

        Instant verifiedAt
) {
}
