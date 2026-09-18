package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceOfflineTrustCheckpointBundle(
        UUID id,

        int bundleNumber,

        UUID sourceCheckpointId,

        int sourceCheckpointNumber,

        int coveredLedgerNumber,

        String checkpointHash,

        String signingKeyId,

        String signingPublicKeyFingerprint,

        String bundleVersion,

        String payloadJson,

        String bundleHash,

        Instant createdAt
) {
}
