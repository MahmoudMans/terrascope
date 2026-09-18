package com.terrascope.platform.domain;

import java.time.Instant;

public record GovernanceTrustAnchor(
        String keyId,
        String algorithm,

        String publicKeyX509Base64,
        String publicKeyFingerprint,

        String status,

        Instant createdAt,
        Instant lastUsedAt,
        Instant retiredAt
) {
}
