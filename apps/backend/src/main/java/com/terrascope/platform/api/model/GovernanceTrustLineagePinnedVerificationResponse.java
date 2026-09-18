package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceTrustLineagePinnedVerificationResponse(
        UUID sourcePackageId,
        Integer sourcePackageNumber,

        String envelopeHash,
        String packageHash,

        boolean valid,

        boolean packageHashValid,
        boolean canonicalPayloadValid,
        boolean lineageValid,
        boolean rootPinValid,

        int transitionCount,
        int verifiedTransitionCount,
        int trustAnchorCount,

        String trustedRootFingerprint,
        String presentedRootFingerprint,

        String rootKeyId,
        String terminalKeyId,

        String message,

        Instant verifiedAt
) {
}
