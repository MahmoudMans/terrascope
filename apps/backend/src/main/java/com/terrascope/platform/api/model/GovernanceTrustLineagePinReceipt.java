package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceTrustLineagePinReceipt(
        UUID id,

        int receiptNumber,

        UUID sourcePackageId,
        Integer sourcePackageNumber,

        String envelopeHash,
        String packageHash,

        String trustedRootFingerprint,
        String presentedRootFingerprint,

        String verificationResult,

        boolean packageHashValid,
        boolean canonicalPayloadValid,
        boolean lineageValid,
        boolean rootPinValid,

        int transitionCount,
        int verifiedTransitionCount,
        int trustAnchorCount,

        String message,

        Instant verifiedAt
) {
}
