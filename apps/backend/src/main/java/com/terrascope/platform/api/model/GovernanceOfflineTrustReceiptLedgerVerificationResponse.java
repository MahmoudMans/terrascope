package com.terrascope.platform.api.model;

import java.time.Instant;

public record GovernanceOfflineTrustReceiptLedgerVerificationResponse(
        boolean valid,

        int ledgerEntryCount,
        int verifiedEntryCount,

        Integer brokenLedgerNumber,

        String headLedgerHash,

        String message,

        Instant verifiedAt
) {
}
