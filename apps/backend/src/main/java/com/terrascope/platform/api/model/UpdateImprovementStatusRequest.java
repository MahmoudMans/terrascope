package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateImprovementStatusRequest(

        @NotBlank
        @Size(max = 30)
        String status
) {
}
