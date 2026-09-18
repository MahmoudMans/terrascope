package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceTrustAnchorTransitionMaterial;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceTrustAnchorTransitionHasherTest {

    private final GovernanceTrustAnchorTransitionHasher hasher =
            new GovernanceTrustAnchorTransitionHasher();

    @Test
    void transitionSigningPayloadIsDeterministic() {
        GovernanceTrustAnchorTransitionMaterial material =
                material(
                        null
                );

        assertThat(
                hasher.signingPayload(
                        material
                )
        ).containsExactly(
                hasher.signingPayload(
                        material
                )
        );

        assertThat(
                new String(
                        hasher.signingPayload(
                                material
                        ),
                        StandardCharsets.UTF_8
                )
        ).contains(
                "transitionVersion=TS-GOVERNANCE-TRUST-TRANSITION-V1"
        );
    }

    @Test
    void signatureAndPreviousTransitionAffectTransitionHash() {
        GovernanceTrustAnchorTransitionMaterial genesis =
                material(
                        null
                );

        GovernanceTrustAnchorTransitionMaterial chained =
                material(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        String first =
                hasher.transitionHash(
                        genesis,
                        "signature-one"
                );

        String changedSignature =
                hasher.transitionHash(
                        genesis,
                        "signature-two"
                );

        String changedPrevious =
                hasher.transitionHash(
                        chained,
                        "signature-one"
                );

        assertThat(
                first
        ).matches(
                "[0-9a-f]{64}"
        );

        assertThat(
                changedSignature
        ).isNotEqualTo(
                first
        );

        assertThat(
                changedPrevious
        ).isNotEqualTo(
                first
        );
    }

    private GovernanceTrustAnchorTransitionMaterial material(
            String previousHash
    ) {
        return new GovernanceTrustAnchorTransitionMaterial(
                1,

                "outgoing-key",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                "replacement-key",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "ED25519",

                "Scheduled cryptographic rotation.",

                previousHash
        );
    }
}
