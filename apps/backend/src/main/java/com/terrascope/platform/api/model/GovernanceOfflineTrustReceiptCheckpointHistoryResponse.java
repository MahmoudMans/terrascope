package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceOfflineTrustReceiptCheckpointHistoryResponse(
        List<GovernanceOfflineTrustReceiptCheckpoint> checkpoints
) {
}
