package com.terrascope.platform.service;

import com.terrascope.platform.domain.PortfolioTrendDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTrendDirectionTest {

    @Test
    void derivesBaselineAndStableStates() {
        assertThat(
                PortfolioTrendDirection.derive(
                        false,
                        0,
                        0
                )
        ).isEqualTo(
                PortfolioTrendDirection.BASELINE
        );

        assertThat(
                PortfolioTrendDirection.derive(
                        true,
                        1,
                        0
                )
        ).isEqualTo(
                PortfolioTrendDirection.STABLE
        );
    }

    @Test
    void derivesImprovingAndDecliningStates() {
        assertThat(
                PortfolioTrendDirection.derive(
                        true,
                        5,
                        2
                )
        ).isEqualTo(
                PortfolioTrendDirection.IMPROVING
        );

        assertThat(
                PortfolioTrendDirection.derive(
                        true,
                        -5,
                        -2
                )
        ).isEqualTo(
                PortfolioTrendDirection.DECLINING
        );

        assertThat(
                PortfolioTrendDirection.derive(
                        true,
                        0,
                        -3
                )
        ).isEqualTo(
                PortfolioTrendDirection.IMPROVING
        );

        assertThat(
                PortfolioTrendDirection.derive(
                        true,
                        0,
                        3
                )
        ).isEqualTo(
                PortfolioTrendDirection.DECLINING
        );
    }
}
