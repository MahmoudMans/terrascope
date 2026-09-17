package com.terrascope.platform.service;

import com.terrascope.platform.domain.IncidentResourceCategory;
import com.terrascope.platform.domain.IncidentResourceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentResourceDomainTest {

    @Test
    void parsesResourceCategoryCaseInsensitively() {
        assertThat(
                IncidentResourceCategory.fromValue(
                        "medical"
                )
        ).isEqualTo(
                IncidentResourceCategory.MEDICAL
        );
    }

    @Test
    void exposesExpectedResourceStatuses() {
        assertThat(
                IncidentResourceStatus.values()
        ).containsExactly(
                IncidentResourceStatus.REQUESTED,
                IncidentResourceStatus.PARTIALLY_ALLOCATED,
                IncidentResourceStatus.FULFILLED,
                IncidentResourceStatus.CANCELLED
        );
    }
}
