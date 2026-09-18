package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreatePortfolioObjectiveRequest(

        @NotNull
        UUID portfolioReviewId,

        @NotBlank
        @Size(max = 300)
        String title,

        @Size(max = 2500)
        String description,

        @NotBlank
        @Size(max = 30)
        String priority,

        @Size(max = 120)
        String owner,

        Instant targetAt,

        @NotBlank
        @Size(max = 1200)
        String successMetric
) {
}
