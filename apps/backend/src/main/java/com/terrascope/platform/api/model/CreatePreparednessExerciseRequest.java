package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreatePreparednessExerciseRequest(

        @NotNull
        UUID improvementActionId,

        @NotBlank
        @Size(max = 40)
        String exerciseType,

        @NotBlank
        @Size(max = 300)
        String title,

        @Size(max = 2000)
        String description,

        @Size(max = 120)
        String owner,

        Instant scheduledAt
) {
}
