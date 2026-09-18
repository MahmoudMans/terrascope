package com.terrascope.platform.service;

import com.terrascope.platform.domain.PortfolioObjectiveOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioObjectiveOutcomeTest {

    @Test
    void derivesAchievedAndPartialOutcomes() {
        assertThat(
                PortfolioObjectiveOutcome.derive(
                        8,
                        1
                )
        ).isEqualTo(
                PortfolioObjectiveOutcome.ACHIEVED
        );

        assertThat(
                PortfolioObjectiveOutcome.derive(
                        0,
                        -3
                )
        ).isEqualTo(
                PortfolioObjectiveOutcome.ACHIEVED
        );

        assertThat(
                PortfolioObjectiveOutcome.derive(
                        2,
                        0
                )
        ).isEqualTo(
                PortfolioObjectiveOutcome.PARTIAL
        );
    }

    @Test
    void severeRegressionProducesNotAchieved() {
        assertThat(
                PortfolioObjectiveOutcome.derive(
                        -5,
                        0
                )
        ).isEqualTo(
                PortfolioObjectiveOutcome.NOT_ACHIEVED
        );

        assertThat(
                PortfolioObjectiveOutcome.derive(
                        8,
                        3
                )
        ).isEqualTo(
                PortfolioObjectiveOutcome.NOT_ACHIEVED
        );
    }
}
