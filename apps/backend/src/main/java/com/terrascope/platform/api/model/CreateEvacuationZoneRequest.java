package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEvacuationZoneRequest(

        @NotBlank
        @Size(max = 300)
        String name,

        @Size(max = 2000)
        String description,

        @NotBlank
        @Size(max = 30)
        String priority,

        @NotNull
        @Min(1)
        @Max(100000000)
        Integer populationAtRisk,

        @NotNull
        @Min(0)
        @Max(100000000)
        Integer evacuatedPopulation,

        @Size(max = 120)
        String owner
) {
}
