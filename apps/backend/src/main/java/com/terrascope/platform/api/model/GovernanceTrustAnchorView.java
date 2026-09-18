package com.terrascope.platform.api.model;

import java.time.Instant;

public record GovernanceTrustAnchorView(
        String keyId,

        String algorithm,

        String publicKeyFingerprint,

        String status,

        String supersededByKeyId,
        String retirementReason,

        Instant createdAt,
        Instant lastUsedAt,
        Instant retiredAt
) {
}
