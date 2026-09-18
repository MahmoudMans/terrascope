package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePortfolioObjectiveRequest(

        @NotBlank
        @Size(max = 3000)
        String completionEvidence
) {
}
