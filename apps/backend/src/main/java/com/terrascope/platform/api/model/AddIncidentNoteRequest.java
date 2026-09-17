package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddIncidentNoteRequest(

        @NotBlank
        @Size(max = 2000)
        String message
) {
}
