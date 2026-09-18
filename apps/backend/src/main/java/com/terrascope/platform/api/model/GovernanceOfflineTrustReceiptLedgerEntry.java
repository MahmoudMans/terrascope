package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceOfflineTrustReceiptLedgerEntry(
        UUID id,

        int ledgerNumber,

        UUID receiptId,
        int receiptImportNumber,

        String verificationResult,

        String receiptHash,
        String receiptPayloadHash,

        String previousLedgerHash,

        String ledgerVersion,
        String ledgerHash,

        Instant createdAt
) {
}
