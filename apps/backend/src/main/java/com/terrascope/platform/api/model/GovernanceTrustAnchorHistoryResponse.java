package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceTrustAnchorHistoryResponse(
        List<GovernanceTrustAnchorView> anchors,
        List<GovernanceTrustAnchorEvent> events
) {
}
