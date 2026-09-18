package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record PortfolioObjectiveHistoryResponse(
        UUID objectiveId,
        List<PortfolioObjectiveEvent> events
) {
}
