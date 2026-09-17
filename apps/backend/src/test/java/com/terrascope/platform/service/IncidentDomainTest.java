package com.terrascope.platform.service;

import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentDomainTest {

    @Test
    void parsesIncidentSeverityCaseInsensitively() {
        assertThat(
                IncidentSeverity.fromValue(
                        "critical"
                )
        ).isEqualTo(
                IncidentSeverity.CRITICAL
        );
    }

    @Test
    void parsesIncidentStatusCaseInsensitively() {
        assertThat(
                IncidentStatus.fromValue(
                        "responding"
                )
        ).isEqualTo(
                IncidentStatus.RESPONDING
        );
    }
}
