package com.terrascope.platform.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreatePreparednessCorrectiveActionRequest(

        @NotBlank
        @Size(max = 300)
        String title,

        @Size(max = 2000)
        String description,

        @NotBlank
        @Size(max = 30)
        String priority,

        @Size(max = 120)
        String owner,

        Instant dueAt
) {
}
