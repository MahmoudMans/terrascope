package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceClosureVerificationResponse(
        boolean valid,

        int certificateCount,
        int verifiedCertificateCount,

        UUID brokenCertificateId,
        Integer brokenCertificateNumber,

        String message,

        Instant verifiedAt
) {
}
