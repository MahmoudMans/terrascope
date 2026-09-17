package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateLifelineAssetRequest(

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

        @NotBlank
        @Size(max = 30)
        String status,

        @NotNull
        @Min(0)
        @Max(100000000)
        Integer affectedPopulation,

        @Size(max = 120)
        String owner,

        Instant restorationEta
) {
}
