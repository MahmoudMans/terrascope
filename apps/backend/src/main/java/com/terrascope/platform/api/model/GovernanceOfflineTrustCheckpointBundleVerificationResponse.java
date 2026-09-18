package com.terrascope.platform.api.model;

import java.time.Instant;

public record GovernanceOfflineTrustCheckpointBundleVerificationResponse(
        boolean valid,

        boolean bundleHashValid,

        boolean canonicalPayloadValid,

        boolean ledgerValid,

        boolean checkpointValid,

        boolean signingKeyPinValid,

        int ledgerEntryCount,

        int verifiedLedgerEntryCount,

        Integer checkpointNumber,

        Integer coveredLedgerNumber,

        String bundleHash,

        String checkpointHash,

        String message,

        Instant verifiedAt
) {
}
