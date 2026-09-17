package com.terrascope.platform.service;

import com.terrascope.platform.domain.PortfolioAssuranceBand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioAssuranceBandTest {

    @Test
    void mapsLowerAssuranceBands() {
        assertThat(
                PortfolioAssuranceBand.fromScore(
                        0
                )
        ).isEqualTo(
                PortfolioAssuranceBand.FOUNDATIONAL
        );

        assertThat(
                PortfolioAssuranceBand.fromScore(
                        40
                )
        ).isEqualTo(
                PortfolioAssuranceBand.DEVELOPING
        );
    }

    @Test
    void mapsUpperAssuranceBands() {
        assertThat(
                PortfolioAssuranceBand.fromScore(
                        60
                )
        ).isEqualTo(
                PortfolioAssuranceBand.ASSURED
        );

        assertThat(
                PortfolioAssuranceBand.fromScore(
                        80
                )
        ).isEqualTo(
                PortfolioAssuranceBand.HIGH_ASSURANCE
        );
    }
}
