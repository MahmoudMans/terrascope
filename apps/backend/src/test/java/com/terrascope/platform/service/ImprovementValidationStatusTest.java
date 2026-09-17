package com.terrascope.platform.service;

import com.terrascope.platform.domain.ImprovementValidationStatus;
import com.terrascope.platform.domain.PreparednessValidationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImprovementValidationStatusTest {

    @Test
    void derivesUntestedAndValidatedStates() {
        assertThat(
                ImprovementValidationStatus.derive(
                        null,
                        0
                )
        ).isEqualTo(
                ImprovementValidationStatus.UNTESTED
        );

        assertThat(
                ImprovementValidationStatus.derive(
                        PreparednessValidationResult.PASSED,
                        0
                )
        ).isEqualTo(
                ImprovementValidationStatus.VALIDATED
        );

        assertThat(
                ImprovementValidationStatus.derive(
                        PreparednessValidationResult.PASSED,
                        1
                )
        ).isEqualTo(
                ImprovementValidationStatus.NEEDS_REVALIDATION
        );
    }

    @Test
    void derivesPartialAndFailedStates() {
        assertThat(
                ImprovementValidationStatus.derive(
                        PreparednessValidationResult.PARTIAL,
                        0
                )
        ).isEqualTo(
                ImprovementValidationStatus.NEEDS_REVALIDATION
        );

        assertThat(
                ImprovementValidationStatus.derive(
                        PreparednessValidationResult.FAILED,
                        0
                )
        ).isEqualTo(
                ImprovementValidationStatus.FAILED
        );
    }
}
