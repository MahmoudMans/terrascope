package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceOfflineTrustCheckpointBundleHistoryResponse(
        List<GovernanceOfflineTrustCheckpointBundle> bundles
) {
}
