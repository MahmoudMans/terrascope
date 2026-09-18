package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdatePortfolioObjectiveProgressRequest(

        @NotNull
        @Min(0)
        @Max(99)
        Integer progressPct
) {
}
