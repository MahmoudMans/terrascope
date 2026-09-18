package com.terrascope.platform.domain;

public record GovernanceTrustAnchorTransitionMaterial(
        int transitionNumber,

        String outgoingKeyId,
        String outgoingPublicKeyFingerprint,

        String replacementKeyId,
        String replacementPublicKeyFingerprint,

        String algorithm,

        String reason,

        String previousTransitionHash
) {
}
