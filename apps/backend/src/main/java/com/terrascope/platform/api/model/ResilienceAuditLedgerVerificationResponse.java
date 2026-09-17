package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record ResilienceAuditLedgerVerificationResponse(
        UUID incidentId,

        boolean valid,

        int receiptCount,
        int verifiedReceiptCount,

        UUID brokenReceiptId,
        Integer brokenReceiptNumber,

        String message,

        Instant verifiedAt
) {
}
