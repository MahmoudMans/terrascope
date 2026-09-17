package com.terrascope.platform.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RegionalHazardPoint(

        @NotBlank
        @Size(max = 200)
        String id,

        @NotBlank
        @Size(max = 40)
        String hazardType,

        @NotBlank
        @Size(max = 400)
        String title,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        Double intensity,

        @NotNull
        Instant observedAt
) {
}
