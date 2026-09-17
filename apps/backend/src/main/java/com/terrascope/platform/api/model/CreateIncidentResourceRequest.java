package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentResourceRequest(

        @NotBlank
        @Size(max = 40)
        String category,

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
        @Max(1000000)
        Integer requestedQuantity,

        @NotBlank
        @Size(max = 40)
        String unit,

        @Size(max = 120)
        String owner
) {
}
