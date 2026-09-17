package com.terrascope.platform.api.model;

import java.time.Instant;

public record WatchlistSummary(
        int totalCount,
        int criticalCount,
        int highCount,
        int earthquakeCount,
        int volcanoCount,
        int wildfireCount,
        int severeStormCount,
        Instant latestSavedAt
) {
}
