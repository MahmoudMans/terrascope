package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceAssuranceReceiptCheckpointHistoryResponse(
        List<GovernanceAssuranceReceiptCheckpoint> checkpoints
) {
}
