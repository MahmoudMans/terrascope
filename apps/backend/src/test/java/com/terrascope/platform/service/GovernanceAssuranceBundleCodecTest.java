package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceAssuranceBundlePayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceAssuranceBundleCodecTest {

    private final GovernanceAssuranceBundleCodec codec =
            new GovernanceAssuranceBundleCodec();

    @Test
    void canonicalEncodingAndHashAreStable() {
        GovernanceAssuranceBundlePayload payload =
                payload();

        String first =
                codec.encode(
                        payload
                );

        String second =
                codec.encode(
                        payload
                );

        assertThat(
                first
        ).isEqualTo(
                second
        );

        assertThat(
                codec.hash(
                        first
                )
        ).isEqualTo(
                codec.hash(
                        second
                )
        );

        assertThat(
                codec.hash(
                        first
                )
        ).matches(
                "[0-9a-f]{64}"
        );

        assertThat(
                codec.canonical(
                        first
                )
        ).isTrue();

        assertThat(
                codec.decode(
                        first
                )
        ).isEqualTo(
                payload
        );
    }

    @Test
    void payloadMutationChangesFingerprint() {
        String payload =
                codec.encode(
                        payload()
                );

        String tampered =
                payload + " ";

        assertThat(
                codec.hash(
                        tampered
                )
        ).isNotEqualTo(
                codec.hash(
                        payload
                )
        );

        assertThat(
                codec.canonical(
                        tampered
                )
        ).isFalse();
    }

    private GovernanceAssuranceBundlePayload payload() {
        UUID certificateId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        Instant now =
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                );

        return new GovernanceAssuranceBundlePayload(
                GovernanceAssuranceBundleCodec.BUNDLE_VERSION,

                new GovernanceAssuranceBundlePayload.CertificateEvidence(
                        certificateId,
                        1,

                        UUID.fromString(
                                "22222222-2222-2222-2222-222222222222"
                        ),

                        1,

                        "REALIZED",
                        true,

                        1,
                        1,
                        1,
                        1,

                        100.0,
                        100.0,

                        50.0,
                        0.0,

                        null,

                        GovernanceClosureHasher.DOSSIER_VERSION,

                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        GovernanceClosureHasher.CERTIFICATE_VERSION,

                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                        "Assurance closure.",

                        now
                ),

                List.of(),

                List.of(),

                List.of()
        );
    }
}
