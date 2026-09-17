package com.terrascope.platform.api.model;

import java.time.Instant;

public record UpdatePreparednessExerciseScheduleRequest(
        Instant scheduledAt
) {
}
