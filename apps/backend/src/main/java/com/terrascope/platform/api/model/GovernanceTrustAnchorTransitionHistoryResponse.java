package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceTrustAnchorTransitionHistoryResponse(
        List<GovernanceTrustAnchorTransition> transitions
) {
}
