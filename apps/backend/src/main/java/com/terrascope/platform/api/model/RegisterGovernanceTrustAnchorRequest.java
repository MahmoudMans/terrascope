package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterGovernanceTrustAnchorRequest(

        @NotBlank
        @Size(max = 120)
        String keyId,

        @NotBlank
        String publicKeyX509Base64
) {
}
