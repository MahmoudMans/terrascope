package com.terrascope.platform.service;

import com.terrascope.platform.domain.PreparednessExerciseStatus;
import com.terrascope.platform.domain.PreparednessExerciseType;
import com.terrascope.platform.domain.PreparednessValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreparednessExerciseDomainTest {

    @Test
    void parsesExerciseTypeCaseInsensitively() {
        assertThat(
                PreparednessExerciseType.fromValue(
                        "full-scale"
                )
        ).isEqualTo(
                PreparednessExerciseType.FULL_SCALE
        );
    }

    @Test
    void distinguishesMutableAndAssessedStates() {
        assertThat(
                PreparednessExerciseStatus.PLANNED.mutable()
        ).isTrue();

        assertThat(
                PreparednessExerciseStatus.IN_PROGRESS.mutable()
        ).isTrue();

        assertThat(
                PreparednessExerciseStatus.COMPLETED.mutable()
        ).isFalse();

        assertThat(
                PreparednessValidationResult.PASSED.assessed()
        ).isTrue();

        assertThat(
                PreparednessValidationResult.NOT_ASSESSED.assessed()
        ).isFalse();
    }
}
