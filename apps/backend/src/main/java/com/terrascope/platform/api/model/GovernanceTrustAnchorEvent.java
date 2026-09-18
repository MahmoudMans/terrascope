package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceTrustAnchorEvent(
        UUID id,

        String keyId,

        String eventType,

        String replacementKeyId,

        String detail,

        Instant createdAt
) {
}
