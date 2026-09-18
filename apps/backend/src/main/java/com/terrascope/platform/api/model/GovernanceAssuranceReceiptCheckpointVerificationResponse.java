package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceReceiptCheckpointVerificationResponse(
        boolean valid,

        int checkpointCount,
        int verifiedCheckpointCount,

        UUID brokenCheckpointId,
        Integer brokenCheckpointNumber,

        Integer brokenLedgerNumber,

        String message,

        Instant verifiedAt
) {
}
