package com.terrascope.platform.service;

import com.terrascope.platform.domain.PortfolioObjectivePriority;
import com.terrascope.platform.domain.PortfolioObjectiveStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioObjectiveDomainTest {

    @Test
    void parsesPriorityAndStatus() {
        assertThat(
                PortfolioObjectivePriority.fromValue(
                        "critical"
                )
        ).isEqualTo(
                PortfolioObjectivePriority.CRITICAL
        );

        assertThat(
                PortfolioObjectiveStatus.fromValue(
                        "in_progress"
                )
        ).isEqualTo(
                PortfolioObjectiveStatus.IN_PROGRESS
        );
    }

    @Test
    void distinguishesMutableAndTerminalStates() {
        assertThat(
                PortfolioObjectiveStatus.PLANNED.mutable()
        ).isTrue();

        assertThat(
                PortfolioObjectiveStatus.IN_PROGRESS.mutable()
        ).isTrue();

        assertThat(
                PortfolioObjectiveStatus.BLOCKED.mutable()
        ).isTrue();

        assertThat(
                PortfolioObjectiveStatus.COMPLETED.terminal()
        ).isTrue();

        assertThat(
                PortfolioObjectiveStatus.CANCELLED.terminal()
        ).isTrue();
    }
}
