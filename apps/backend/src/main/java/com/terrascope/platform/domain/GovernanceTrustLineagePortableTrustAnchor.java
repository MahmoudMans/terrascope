package com.terrascope.platform.domain;

import java.time.Instant;

public record GovernanceTrustLineagePortableTrustAnchor(
        String keyId,
        String algorithm,

        String publicKeyX509Base64,
        String publicKeyFingerprint,

        String status,

        String supersededByKeyId,
        String retirementReason,

        Instant createdAt,
        Instant lastUsedAt,
        Instant retiredAt
) {
}
