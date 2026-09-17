package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDamageSeverityRequest(

        @NotBlank
        @Size(max = 30)
        String severity
) {
}
