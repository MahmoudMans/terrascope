package com.terrascope.platform.api.model;

import java.util.List;

public record WatchlistFeedResponse(
        WatchlistSummary summary,
        List<WatchlistEntry> entries
) {
}
