package com.terrascope.platform.api.model;

import java.time.Instant;

public record LiveFeedUpdate(
        int liveFeedCount,
        Instant refreshedAt
) {
}
