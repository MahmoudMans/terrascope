package com.terrascope.platform.service;

import com.terrascope.platform.domain.ImprovementActionStatus;
import com.terrascope.platform.domain.ImprovementCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImprovementActionDomainTest {

    @Test
    void parsesCategoryCaseInsensitively() {
        assertThat(
                ImprovementCategory.fromValue(
                        "communications"
                )
        ).isEqualTo(
                ImprovementCategory.COMMUNICATIONS
        );
    }

    @Test
    void identifiesActiveImprovementStatuses() {
        assertThat(
                ImprovementActionStatus.OPEN.active()
        ).isTrue();

        assertThat(
                ImprovementActionStatus.IN_PROGRESS.active()
        ).isTrue();

        assertThat(
                ImprovementActionStatus.COMPLETED.active()
        ).isFalse();

        assertThat(
                ImprovementActionStatus.DISMISSED.active()
        ).isFalse();
    }
}
