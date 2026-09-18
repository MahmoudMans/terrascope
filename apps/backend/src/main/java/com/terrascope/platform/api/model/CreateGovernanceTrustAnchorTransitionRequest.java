package com.terrascope.platform.api.model;

public record CreateGovernanceTrustAnchorTransitionRequest(
        String replacementKeyId,
        String replacementPublicKeyX509Base64,
        String reason
) {
}
