package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAssuranceReceiptCheckpoint(
        UUID id,

        int checkpointNumber,

        int coveredLedgerNumber,
        String coveredLedgerHash,

        int entryCount,
        int validReceiptCount,
        int invalidReceiptCount,

        String keyId,
        String algorithm,
        String publicKeyFingerprint,

        String checkpointVersion,

        String signatureBase64,

        String previousCheckpointHash,
        String checkpointHash,

        Instant createdAt
) {
}
