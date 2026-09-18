package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record PortfolioObjectiveEvent(
        UUID id,
        UUID objectiveId,

        String eventType,
        String detail,

        Instant createdAt
) {
}
