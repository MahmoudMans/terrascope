package com.terrascope.platform.domain;

import java.util.UUID;

public record ResilienceAuditLedgerMaterial(
        UUID incidentId,

        int receiptNumber,

        UUID evidencePackageId,
        int evidencePackageNumber,

        String packageManifestVersion,
        String packageManifestHash,

        String previousReceiptHash
) {
}
