package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceOfflineTrustVerificationReceipt(
        UUID id,

        int importNumber,

        String verifierVersion,

        String receiptHash,

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

        String rootKeyId,
        String terminalKeyId,

        Instant offlineVerifiedAt,
        Instant importedAt
) {
}
