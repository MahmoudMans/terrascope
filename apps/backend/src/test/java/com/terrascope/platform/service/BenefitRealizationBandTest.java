package com.terrascope.platform.service;

import com.terrascope.platform.domain.BenefitRealizationBand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenefitRealizationBandTest {

    @Test
    void mapsUnprovenAndEmergingStates() {
        assertThat(
                BenefitRealizationBand.derive(
                        0,
                        0,
                        0,
                        0.0,
                        0.0,
                        false
                )
        ).isEqualTo(
                BenefitRealizationBand.UNPROVEN
        );

        assertThat(
                BenefitRealizationBand.derive(
                        4,
                        1,
                        0,
                        25.0,
                        100.0,
                        false
                )
        ).isEqualTo(
                BenefitRealizationBand.EMERGING
        );

        assertThat(
                BenefitRealizationBand.derive(
                        2,
                        2,
                        1,
                        100.0,
                        50.0,
                        false
                )
        ).isEqualTo(
                BenefitRealizationBand.EMERGING
        );
    }

    @Test
    void mapsRealizingAndRealizedStates() {
        assertThat(
                BenefitRealizationBand.derive(
                        2,
                        2,
                        0,
                        100.0,
                        50.0,
                        false
                )
        ).isEqualTo(
                BenefitRealizationBand.REALIZING
        );

        assertThat(
                BenefitRealizationBand.derive(
                        2,
                        2,
                        0,
                        100.0,
                        100.0,
                        true
                )
        ).isEqualTo(
                BenefitRealizationBand.REALIZED
        );
    }
}
