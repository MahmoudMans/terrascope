package com.terrascope.platform.domain;

public record GovernanceOfflineTrustCheckpointPortableEnvelope(
        String envelopeVersion,

        String bundleHash,

        String payloadJson
) {
}
