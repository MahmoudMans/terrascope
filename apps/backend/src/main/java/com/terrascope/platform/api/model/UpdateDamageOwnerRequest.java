package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDamageOwnerRequest(

        @NotBlank
        @Size(max = 120)
        String owner
) {
}
