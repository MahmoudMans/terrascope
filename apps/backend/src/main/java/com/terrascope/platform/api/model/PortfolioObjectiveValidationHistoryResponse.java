package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record PortfolioObjectiveValidationHistoryResponse(
        UUID objectiveId,
        List<PortfolioObjectiveValidation> validations
) {
}
