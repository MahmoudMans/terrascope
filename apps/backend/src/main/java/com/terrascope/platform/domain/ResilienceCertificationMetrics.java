package com.terrascope.platform.domain;

public record ResilienceCertificationMetrics(
        int afterActionReviewCount,
        int sitrepCount,
        int totalExerciseCount,
        int passedExerciseCount
) {
}
