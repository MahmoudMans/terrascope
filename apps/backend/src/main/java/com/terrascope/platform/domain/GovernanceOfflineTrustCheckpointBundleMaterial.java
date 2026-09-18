package com.terrascope.platform.domain;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;

import java.util.List;

public record GovernanceOfflineTrustCheckpointBundleMaterial(
        String bundleVersion,

        GovernanceOfflineTrustReceiptCheckpoint checkpoint,

        List<GovernanceOfflineTrustReceiptLedgerEntry> ledgerEntries,

        String signingPublicKeyX509Base64,

        String signingPublicKeyFingerprint
) {
}
