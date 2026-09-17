package com.terrascope.platform.service;

import com.terrascope.platform.domain.IncidentActionPriority;
import com.terrascope.platform.domain.IncidentActionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentActionDomainTest {

    @Test
    void parsesPriorityCaseInsensitively() {
        assertThat(
                IncidentActionPriority.fromValue(
                        "critical"
                )
        ).isEqualTo(
                IncidentActionPriority.CRITICAL
        );
    }

    @Test
    void parsesActionStatusCaseInsensitively() {
        assertThat(
                IncidentActionStatus.fromValue(
                        "in_progress"
                )
        ).isEqualTo(
                IncidentActionStatus.IN_PROGRESS
        );
    }
}
