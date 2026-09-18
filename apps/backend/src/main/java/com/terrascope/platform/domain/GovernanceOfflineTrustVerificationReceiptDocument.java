package com.terrascope.platform.domain;

public record GovernanceOfflineTrustVerificationReceiptDocument(
        String verifierVersion,

        String sourcePackageId,
        Integer sourcePackageNumber,

        String envelopeJson,
        String envelopeHash,
        String packageHash,

        String trustedRootFingerprint,
        String presentedRootFingerprint,

        boolean valid,

        boolean packageHashValid,
        boolean canonicalPayloadValid,
        boolean lineageValid,
        boolean rootPinValid,

        int transitionCount,
        int verifiedTransitionCount,
        int trustAnchorCount,

        String rootKeyId,
        String terminalKeyId,

        String message,
        String verifiedAt,

        String receiptHash
) {
}
