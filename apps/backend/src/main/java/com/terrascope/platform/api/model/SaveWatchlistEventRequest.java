package com.terrascope.platform.api.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record SaveWatchlistEventRequest(

        @NotBlank
        @Size(max = 200)
        String sourceEventId,

        @NotBlank
        @Size(max = 40)
        String hazardType,

        @NotBlank
        @Size(max = 400)
        String title,

        @Size(max = 4000)
        String description,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @NotNull
        Instant observedAt,

        @NotBlank
        @Size(max = 80)
        String sourceProvider,

        @Size(max = 1000)
        String sourceUrl,

        Double magnitudeValue,

        @Size(max = 40)
        String magnitudeUnit,

        @Size(max = 20)
        String priority,

        @Size(max = 1000)
        String notes
) {
}
