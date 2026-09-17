package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateIncidentSeverityRequest(

        @NotBlank
        @Size(max = 30)
        String severity
) {
}
