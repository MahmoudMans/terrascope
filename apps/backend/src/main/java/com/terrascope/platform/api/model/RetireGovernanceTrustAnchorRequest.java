package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RetireGovernanceTrustAnchorRequest(

        @NotBlank
        @Size(max = 120)
        String replacementKeyId,

        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
