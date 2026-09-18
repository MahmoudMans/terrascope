package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceTrustAnchorTransitionVerificationResponse(
        boolean valid,

        int transitionCount,
        int verifiedTransitionCount,

        UUID brokenTransitionId,
        Integer brokenTransitionNumber,

        String brokenOutgoingKeyId,
        String brokenReplacementKeyId,

        String message,

        Instant verifiedAt
) {
}
