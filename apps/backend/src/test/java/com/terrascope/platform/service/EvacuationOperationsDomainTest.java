package com.terrascope.platform.service;

import com.terrascope.platform.domain.EvacuationZoneStatus;
import com.terrascope.platform.domain.ShelterStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvacuationOperationsDomainTest {

    @Test
    void parsesEvacuationStatusCaseInsensitively() {
        assertThat(
                EvacuationZoneStatus.fromValue(
                        "evacuating"
                )
        ).isEqualTo(
                EvacuationZoneStatus.EVACUATING
        );
    }

    @Test
    void parsesShelterStatusCaseInsensitively() {
        assertThat(
                ShelterStatus.fromValue(
                        "closed"
                )
        ).isEqualTo(
                ShelterStatus.CLOSED
        );
    }
}
