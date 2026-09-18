package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableEnvelope;
import com.terrascope.platform.domain.GovernanceTrustLineagePortablePayload;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableTrustAnchor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceTrustLineagePackageCodecTest {

    private final GovernanceTrustLineagePackageCodec codec =
            new GovernanceTrustLineagePackageCodec();

    @Test
    void canonicalPayloadIsDeterministic() {
        GovernanceTrustLineagePortablePayload payload =
                payload();

        String first =
                codec.encodePayload(
                        payload
                );

        String second =
                codec.encodePayload(
                        payload
                );

        assertThat(
                first
        ).isEqualTo(
                second
        );

        assertThat(
                codec.isCanonicalPayload(
                        first
                )
        ).isTrue();

        assertThat(
                codec.sha256(
                        first
                )
        ).matches(
                "[0-9a-f]{64}"
        );
    }

    @Test
    void portableEnvelopeRoundTripsDeterministically() {
        String payloadJson =
                codec.encodePayload(
                        payload()
                );

        GovernanceTrustLineagePortableEnvelope envelope =
                new GovernanceTrustLineagePortableEnvelope(
                        GovernanceTrustLineagePackageCodec.ENVELOPE_VERSION,

                        UUID.fromString(
                                "11111111-1111-1111-1111-111111111111"
                        ),

                        1,

                        GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                        codec.sha256(
                                payloadJson
                        ),

                        payloadJson
                );

        byte[] first =
                codec.encodeEnvelope(
                        envelope
                );

        byte[] second =
                codec.encodeEnvelope(
                        codec.decodeEnvelope(
                                first
                        )
                );

        assertThat(
                first
        ).containsExactly(
                second
        );
    }

    private GovernanceTrustLineagePortablePayload payload() {
        GovernanceTrustLineagePortableTrustAnchor firstAnchor =
                new GovernanceTrustLineagePortableTrustAnchor(
                        "key-1",
                        "ED25519",
                        "public-key-one",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "RETIRED",
                        "key-2",
                        "Rotation.",
                        Instant.parse(
                                "2030-01-01T00:00:00Z"
                        ),
                        Instant.parse(
                                "2030-01-02T00:00:00Z"
                        ),
                        Instant.parse(
                                "2030-02-01T00:00:00Z"
                        )
                );

        GovernanceTrustLineagePortableTrustAnchor secondAnchor =
                new GovernanceTrustLineagePortableTrustAnchor(
                        "key-2",
                        "ED25519",
                        "public-key-two",
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "ACTIVE",
                        null,
                        null,
                        Instant.parse(
                                "2030-01-15T00:00:00Z"
                        ),
                        null,
                        null
                );

        GovernanceTrustAnchorTransition transition =
                new GovernanceTrustAnchorTransition(
                        UUID.fromString(
                                "22222222-2222-2222-2222-222222222222"
                        ),

                        1,

                        firstAnchor.keyId(),
                        firstAnchor.publicKeyFingerprint(),

                        secondAnchor.keyId(),
                        secondAnchor.publicKeyFingerprint(),

                        "ED25519",

                        "Rotation.",

                        GovernanceTrustAnchorTransitionHasher.TRANSITION_VERSION,

                        "signature",

                        null,

                        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                        Instant.parse(
                                "2030-02-01T00:00:00Z"
                        )
                );

        return new GovernanceTrustLineagePortablePayload(
                GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                firstAnchor.keyId(),
                firstAnchor.publicKeyFingerprint(),

                secondAnchor.keyId(),
                secondAnchor.publicKeyFingerprint(),

                List.of(
                        firstAnchor,
                        secondAnchor
                ),

                List.of(
                        transition
                )
        );
    }
}
