package com.terrascope.platform.domain;

import java.util.UUID;

public record GovernanceAssurancePortableEnvelope(
        String envelopeVersion,

        UUID sourceBundleId,
        int sourceBundleNumber,

        String bundleVersion,
        String bundleHash,

        String payloadJson
) {
}
