package com.terrascope.platform.service;

import com.terrascope.platform.domain.ResilienceCertificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceCertificationStatusTest {

    @Test
    void mapsBlockedState() {
        assertThat(
                ResilienceCertificationStatus.fromReady(
                        false
                )
        ).isEqualTo(
                ResilienceCertificationStatus.BLOCKED
        );
    }

    @Test
    void mapsReadyState() {
        assertThat(
                ResilienceCertificationStatus.fromReady(
                        true
                )
        ).isEqualTo(
                ResilienceCertificationStatus.READY
        );
    }
}
