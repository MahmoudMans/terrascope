package com.terrascope.platform.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceOfflineTrustReceiptCheckpointHasherTest {

    private final GovernanceOfflineTrustReceiptCheckpointHasher hasher =
            new GovernanceOfflineTrustReceiptCheckpointHasher();

    @Test
    void signingPayloadIsDeterministic() {
        String first =
                hasher.signingPayload(
                        1,

                        7,

                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        7,

                        5,

                        2,

                        "checkpoint-key",

                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                        null
                );

        String second =
                hasher.signingPayload(
                        1,

                        7,

                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        7,

                        5,

                        2,

                        "checkpoint-key",

                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                        null
                );

        assertThat(
                first
        ).isEqualTo(
                second
        );

        assertThat(
                first
        ).contains(
                "previousCheckpointHash=GENESIS"
        );
    }

    @Test
    void checkpointHashBindsSignature() {
        String payload =
                hasher.signingPayload(
                        1,

                        1,

                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        1,

                        1,

                        0,

                        "checkpoint-key",

                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                        null
                );

        String first =
                hasher.checkpointHash(
                        payload,
                        "signature-one"
                );

        String second =
                hasher.checkpointHash(
                        payload,
                        "signature-two"
                );

        assertThat(
                first
        ).matches(
                "[0-9a-f]{64}"
        );

        assertThat(
                first
        ).isNotEqualTo(
                second
        );
    }
}
