package com.terrascope.platform.api.model;

import java.time.Instant;

public record GovernanceOfflineTrustReceiptCheckpointVerificationResponse(
        boolean valid,

        int checkpointCount,

        int verifiedCheckpointCount,

        Integer brokenCheckpointNumber,

        String checkpointHeadHash,

        String coveredLedgerHeadHash,

        String message,

        Instant verifiedAt
) {
}
