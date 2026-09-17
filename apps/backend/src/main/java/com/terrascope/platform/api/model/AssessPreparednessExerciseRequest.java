package com.terrascope.platform.api.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AssessPreparednessExerciseRequest(

        @NotBlank
        @Size(max = 30)
        String result,

        @NotNull
        @Min(0)
        @Max(100)
        Integer readinessScore,

        Instant performedAt,

        @Size(max = 2000)
        String evidenceSummary,

        @Size(max = 3000)
        String findings,

        @Size(max = 2000)
        String correctiveAction
) {
}
