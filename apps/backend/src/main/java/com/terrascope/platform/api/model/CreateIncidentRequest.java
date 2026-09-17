package com.terrascope.platform.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateIncidentRequest(

        UUID alertEventId,

        @NotBlank
        @Size(max = 200)
        String sourceEventId,

        @NotBlank
        @Size(max = 40)
        String hazardType,

        @NotBlank
        @Size(max = 400)
        String title,

        @Size(max = 2000)
        String description,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @NotBlank
        @Size(max = 30)
        String severity,

        @Size(max = 120)
        String owner
) {
}
