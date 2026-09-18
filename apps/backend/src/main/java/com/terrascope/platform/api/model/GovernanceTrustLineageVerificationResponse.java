package com.terrascope.platform.api.model;

import java.time.Instant;

public record GovernanceTrustLineageVerificationResponse(
        boolean valid,

        boolean hashValid,
        boolean canonicalPayloadValid,
        boolean lineageValid,

        int transitionCount,
        int verifiedTransitionCount,

        int trustAnchorCount,

        String rootKeyId,
        String terminalKeyId,

        String message,

        Instant verifiedAt
) {
}
