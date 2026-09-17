package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateLifelineImpactRequest(

        @NotNull
        @Min(0)
        @Max(100000000)
        Integer affectedPopulation
) {
}
