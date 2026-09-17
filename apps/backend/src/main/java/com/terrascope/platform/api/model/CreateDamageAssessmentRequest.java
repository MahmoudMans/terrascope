package com.terrascope.platform.api.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateDamageAssessmentRequest(

        @NotBlank
        @Size(max = 40)
        String category,

        @NotBlank
        @Size(max = 300)
        String assetName,

        @Size(max = 2000)
        String description,

        @NotBlank
        @Size(max = 30)
        String severity,

        @NotNull
        @Min(0)
        @Max(100000000)
        Integer affectedUnits,

        @NotNull
        @DecimalMin("0.0")
        Double estimatedLossMusd,

        @Size(max = 120)
        String owner,

        Instant recoveryEta
) {
}
