package com.terrascope.platform.api.model;

public record VerifyGovernanceOfflineTrustCheckpointBundleRequest(
        String envelopeJson,

        String trustedSigningKeyFingerprint
) {
}
