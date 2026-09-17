package com.terrascope.platform.service;

import com.terrascope.platform.domain.LifelineAssetCategory;
import com.terrascope.platform.domain.LifelineAssetStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifelineOperationsDomainTest {

    @Test
    void parsesCategoryCaseInsensitively() {
        assertThat(
                LifelineAssetCategory.fromValue(
                        "healthcare"
                )
        ).isEqualTo(
                LifelineAssetCategory.HEALTHCARE
        );
    }

    @Test
    void identifiesDisruptedStatuses() {
        assertThat(
                LifelineAssetStatus.OUTAGE.disrupted()
        ).isTrue();

        assertThat(
                LifelineAssetStatus.DEGRADED.disrupted()
        ).isTrue();

        assertThat(
                LifelineAssetStatus.OPERATIONAL.disrupted()
        ).isFalse();

        assertThat(
                LifelineAssetStatus.RESTORED.disrupted()
        ).isFalse();
    }
}
