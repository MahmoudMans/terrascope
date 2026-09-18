package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceSignatureVerificationResponse(
        boolean valid,

        int signatureCount,
        int verifiedSignatureCount,

        UUID brokenSignatureId,
        UUID brokenCertificateId,
        Integer brokenCertificateNumber,

        String message,

        Instant verifiedAt
) {
}
