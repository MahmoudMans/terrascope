package com.terrascope.platform.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ImpactSimulationRequest(

        @NotBlank
        @Size(max = 160)
        String name,

        @NotBlank
        @Size(max = 40)
        String hazardType,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @NotNull
        @DecimalMin("0.1")
        @DecimalMax("2000.0")
        Double radiusKm,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        Double intensity,

        @NotNull
        @Min(0)
        @Max(500000000)
        Long exposedPopulation,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        Double infrastructureResiliencePct,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        Double emergencyReadinessPct
) {
}
