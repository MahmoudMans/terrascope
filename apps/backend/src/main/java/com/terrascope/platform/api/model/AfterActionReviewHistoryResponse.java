package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record AfterActionReviewHistoryResponse(
        UUID incidentId,
        List<IncidentAfterActionReview> reviews
) {
}
