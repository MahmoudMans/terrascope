package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceTrustLineagePackage(
        UUID id,

        int packageNumber,

        String packageVersion,

        int transitionCount,
        int trustAnchorCount,

        int firstTransitionNumber,
        int lastTransitionNumber,

        String lineageHeadHash,

        String payloadJson,
        String packageHash,

        Instant createdAt
) {
}
