package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceReceiptLedgerEntry(
        UUID id,

        int ledgerNumber,

        UUID receiptId,
        int receiptNumber,

        String verificationResult,

        String envelopeHash,
        String bundleHash,

        String receiptPayloadHash,

        String previousLedgerHash,

        String ledgerVersion,
        String ledgerHash,

        Instant createdAt
) {
}
