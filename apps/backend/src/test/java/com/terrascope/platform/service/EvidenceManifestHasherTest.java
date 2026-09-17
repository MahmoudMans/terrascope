package com.terrascope.platform.service;

import com.terrascope.platform.domain.ResilienceEvidenceManifest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceManifestHasherTest {

    private final EvidenceManifestHasher hasher =
            new EvidenceManifestHasher();

    @Test
    void producesStableSha256Manifest() {
        ResilienceEvidenceManifest manifest =
                manifest(
                        10
                );

        String first =
                hasher.hash(
                        manifest
                );

        String second =
                hasher.hash(
                        manifest
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
    void evidenceMutationChangesFingerprint() {
        String first =
                hasher.hash(
                        manifest(
                                10
                        )
                );

        String second =
                hasher.hash(
                        manifest(
                                11
                        )
                );

        assertThat(
                first
        ).isNotEqualTo(
                second
        );
    }

    private ResilienceEvidenceManifest manifest(
            int timelineCount
    ) {
        return new ResilienceEvidenceManifest(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                UUID.fromString(
                        "22222222-2222-2222-2222-222222222222"
                ),

                1,
                95.0,
                1,

                1,
                1,
                1,
                2,
                1,
                timelineCount
        );
    }
}
