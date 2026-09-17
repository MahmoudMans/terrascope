package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEvacuationOwnerRequest(

        @NotBlank
        @Size(max = 120)
        String owner
) {
}
