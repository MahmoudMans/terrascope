package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWatchPriorityRequest(

        @NotBlank
        @Size(max = 20)
        String priority
) {
}
