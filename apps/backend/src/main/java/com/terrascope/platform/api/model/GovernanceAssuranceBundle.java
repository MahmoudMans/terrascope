package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceBundle(
        UUID id,

        int bundleNumber,

        UUID certificateId,
        int certificateNumber,

        String bundleVersion,
        String bundleHash,

        int signatureCount,
        int trustAnchorCount,
        int retiredAnchorCount,
        int lifecycleEventCount,

        boolean portableVerificationValid,

        String payloadJson,

        Instant createdAt
) {
}
