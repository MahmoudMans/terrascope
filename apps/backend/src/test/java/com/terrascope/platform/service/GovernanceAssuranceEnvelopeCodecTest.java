package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceAssurancePortableEnvelope;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceAssuranceEnvelopeCodecTest {

    private final GovernanceAssuranceEnvelopeCodec codec =
            new GovernanceAssuranceEnvelopeCodec();

    @Test
    void canonicalEnvelopeEncodingIsStable() {
        GovernanceAssurancePortableEnvelope envelope =
                envelope();

        byte[] first =
                codec.encode(
                        envelope
                );

        byte[] second =
                codec.encode(
                        envelope
                );

        assertThat(
                first
        ).containsExactly(
                second
        );

        assertThat(
                codec.decode(
                        first
                )
        ).isEqualTo(
                envelope
        );

        assertThat(
                codec.hash(
                        first
                )
        ).matches(
                "[0-9a-f]{64}"
        );
    }

    @Test
    void exactEnvelopeByteMutationChangesFingerprint() {
        byte[] original =
                codec.encode(
                        envelope()
                );

        byte[] modified =
                (
                    new String(
                            original,
                            StandardCharsets.UTF_8
                    )
                    + " "
                ).getBytes(
                        StandardCharsets.UTF_8
                );

        assertThat(
                codec.hash(
                        modified
                )
        ).isNotEqualTo(
                codec.hash(
                        original
                )
        );
    }

    private GovernanceAssurancePortableEnvelope envelope() {
        return new GovernanceAssurancePortableEnvelope(
                GovernanceAssuranceEnvelopeCodec.ENVELOPE_VERSION,

                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                1,

                GovernanceAssuranceBundleCodec.BUNDLE_VERSION,

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                "{}"
        );
    }
}
