package com.terrascope.platform.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegionalAnalysisRequest(

        @NotBlank
        @Size(max = 160)
        String regionName,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @NotNull
        @DecimalMin("1.0")
        @DecimalMax("2000.0")
        Double radiusKm,

        @Valid
        @NotNull
        @Size(max = 2000)
        List<RegionalHazardPoint> points
) {
}
