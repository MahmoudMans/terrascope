package com.terrascope.platform.api.model;

import java.util.List;

public record ResiliencePortfolioReviewHistoryResponse(
        List<ResiliencePortfolioReview> reviews
) {
}
