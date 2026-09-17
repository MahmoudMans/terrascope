package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateEvacuationProgressRequest(

        @NotNull
        @Min(0)
        Integer evacuatedPopulation
) {
}
