package com.terrascope.platform.service;

import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.GovernanceClosureDossierMaterial;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceClosureHasherTest {

    private final GovernanceClosureHasher hasher =
            new GovernanceClosureHasher();

    @Test
    void dossierFingerprintIsStable() {
        GovernanceClosureDossierMaterial material =
                material();

        String first =
                hasher.dossierHash(
                        material
                );

        String second =
                hasher.dossierHash(
                        material
                );

        assertThat(
                first
        ).hasSize(64);

        assertThat(
                first
        ).matches(
                "[0-9a-f]{64}"
        );

        assertThat(
                first
        ).isEqualTo(
                second
        );
    }

    @Test
    void previousCertificateChangesCertificateFingerprint() {
        String dossierHash =
                hasher.dossierHash(
                        material()
                );

        String genesis =
                hasher.certificateHash(
                        1,
                        dossierHash,
                        null
                );

        String chained =
                hasher.certificateHash(
                        2,
                        dossierHash,
                        genesis
                );

        assertThat(
                genesis
        ).isNotEqualTo(
                chained
        );

        assertThat(
                chained
        ).matches(
                "[0-9a-f]{64}"
        );
    }

    private GovernanceClosureDossierMaterial material() {
        return new GovernanceClosureDossierMaterial(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                1,

                BenefitRealizationBand.REALIZED,
                true,

                2,
                2,
                2,
                2,

                100.0,
                100.0,

                25.0,
                -3.0
        );
    }
}
