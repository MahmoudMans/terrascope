package com.terrascope.platform.domain;

public record GovernanceAssuranceReceiptCheckpointMaterial(
        int checkpointNumber,

        int coveredLedgerNumber,
        String coveredLedgerHash,

        int entryCount,
        int validReceiptCount,
        int invalidReceiptCount,

        String keyId,
        String algorithm,
        String publicKeyFingerprint,

        String previousCheckpointHash
) {
}
