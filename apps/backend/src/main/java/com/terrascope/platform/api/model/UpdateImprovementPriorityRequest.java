package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateImprovementPriorityRequest(

        @NotBlank
        @Size(max = 30)
        String priority
) {
}
