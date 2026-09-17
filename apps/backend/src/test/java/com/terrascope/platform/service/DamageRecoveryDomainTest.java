package com.terrascope.platform.service;

import com.terrascope.platform.domain.DamageAssetCategory;
import com.terrascope.platform.domain.DamageSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DamageRecoveryDomainTest {

    @Test
    void parsesDamageCategoryCaseInsensitively() {
        assertThat(
                DamageAssetCategory.fromValue(
                        "public_building"
                )
        ).isEqualTo(
                DamageAssetCategory.PUBLIC_BUILDING
        );
    }

    @Test
    void identifiesCriticalDamageSeverities() {
        assertThat(
                DamageSeverity.MAJOR.critical()
        ).isTrue();

        assertThat(
                DamageSeverity.DESTROYED.critical()
        ).isTrue();

        assertThat(
                DamageSeverity.MODERATE.critical()
        ).isFalse();

        assertThat(
                DamageSeverity.MINOR.critical()
        ).isFalse();
    }
}
