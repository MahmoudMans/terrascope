package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceReceiptLedgerVerificationResponse(
        boolean valid,

        int entryCount,
        int verifiedEntryCount,

        UUID brokenLedgerEntryId,
        Integer brokenLedgerNumber,

        UUID brokenReceiptId,
        Integer brokenReceiptNumber,

        String message,

        Instant verifiedAt
) {
}
