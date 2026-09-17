package com.terrascope.platform.api.model;

import java.util.List;

public record ResiliencePortfolioHistoryResponse(
        List<ResiliencePortfolioSnapshot> snapshots
) {
}
