package com.terrascope.platform.api.model;

import java.time.Instant;

public record GovernanceSigningReadinessResponse(
        boolean ready,

        String algorithm,
        String keyId,
        String publicKeyFingerprint,

        boolean trustAnchorRegistered,

        String message,

        Instant generatedAt
) {
}
