package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateDamageProgressRequest(

        @NotNull
        @Min(0)
        @Max(100)
        Integer repairProgressPct
) {
}
