package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceTrustAnchorTransition(
        UUID id,

        int transitionNumber,

        String outgoingKeyId,
        String outgoingPublicKeyFingerprint,

        String replacementKeyId,
        String replacementPublicKeyFingerprint,

        String algorithm,

        String reason,

        String transitionVersion,

        String signatureBase64,

        String previousTransitionHash,
        String transitionHash,

        Instant createdAt
) {
}
