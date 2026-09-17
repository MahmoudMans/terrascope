package com.terrascope.platform.service;

import com.terrascope.platform.domain.CommandPressureBand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandPressureBandTest {

    @Test
    void mapsLowerPressureBands() {
        assertThat(
                CommandPressureBand.fromScore(
                        0
                )
        ).isEqualTo(
                CommandPressureBand.STABLE
        );

        assertThat(
                CommandPressureBand.fromScore(
                        20
                )
        ).isEqualTo(
                CommandPressureBand.GUARDED
        );

        assertThat(
                CommandPressureBand.fromScore(
                        40
                )
        ).isEqualTo(
                CommandPressureBand.ELEVATED
        );
    }

    @Test
    void mapsHigherPressureBands() {
        assertThat(
                CommandPressureBand.fromScore(
                        60
                )
        ).isEqualTo(
                CommandPressureBand.SEVERE
        );

        assertThat(
                CommandPressureBand.fromScore(
                        80
                )
        ).isEqualTo(
                CommandPressureBand.CRITICAL
        );

        assertThat(
                CommandPressureBand.fromScore(
                        100
                )
        ).isEqualTo(
                CommandPressureBand.CRITICAL
        );
    }
}
