package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record EvidencePackageVerificationResponse(
        UUID incidentId,
        UUID packageId,

        boolean valid,

        String manifestVersion,
        String storedHash,
        String recomputedHash,

        Instant verifiedAt
) {
}
