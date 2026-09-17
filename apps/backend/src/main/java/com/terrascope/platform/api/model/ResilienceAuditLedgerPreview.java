package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record ResilienceAuditLedgerPreview(
        UUID incidentId,

        int nextReceiptNumber,

        UUID evidencePackageId,
        int evidencePackageNumber,

        String packageManifestVersion,
        String packageManifestHash,

        boolean latestPackageReceipted,

        String previousReceiptHash,

        String ledgerVersion,
        String ledgerHash,

        Instant generatedAt
) {
}
