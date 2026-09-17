package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record ResilienceAuditReceipt(
        UUID id,
        UUID incidentId,

        int receiptNumber,

        UUID evidencePackageId,
        int evidencePackageNumber,

        String packageManifestVersion,
        String packageManifestHash,

        String previousReceiptHash,

        String ledgerVersion,
        String ledgerHash,

        Instant createdAt
) {
}
