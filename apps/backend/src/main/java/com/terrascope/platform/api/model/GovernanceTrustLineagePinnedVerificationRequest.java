package com.terrascope.platform.api.model;

public record GovernanceTrustLineagePinnedVerificationRequest(
        String envelopeJson,
        String trustedRootFingerprint
) {
}
