package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateShelterRequest(

        @NotBlank
        @Size(max = 300)
        String name,

        @Size(max = 500)
        String address,

        @NotNull
        @Min(1)
        @Max(10000000)
        Integer capacity,

        @NotNull
        @Min(0)
        @Max(10000000)
        Integer occupancy,

        @Size(max = 120)
        String owner
) {
}
