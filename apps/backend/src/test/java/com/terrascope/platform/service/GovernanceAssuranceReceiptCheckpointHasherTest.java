package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceAssuranceReceiptCheckpointMaterial;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceAssuranceReceiptCheckpointHasherTest {

    private final GovernanceAssuranceReceiptCheckpointHasher hasher =
            new GovernanceAssuranceReceiptCheckpointHasher();

    @Test
    void checkpointSigningPayloadIsDeterministic() {
        GovernanceAssuranceReceiptCheckpointMaterial material =
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
                "checkpointVersion=TS-ASSURANCE-RECEIPT-CHECKPOINT-V1"
        );
    }

    @Test
    void signatureAndPreviousCheckpointChangeCheckpointHash() {
        GovernanceAssuranceReceiptCheckpointMaterial genesis =
                material(
                        null
                );

        GovernanceAssuranceReceiptCheckpointMaterial chained =
                material(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        String first =
                hasher.checkpointHash(
                        genesis,
                        "signature-a"
                );

        String changedSignature =
                hasher.checkpointHash(
                        genesis,
                        "signature-b"
                );

        String changedPreviousHash =
                hasher.checkpointHash(
                        chained,
                        "signature-a"
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
                changedPreviousHash
        ).isNotEqualTo(
                first
        );
    }

    private GovernanceAssuranceReceiptCheckpointMaterial material(
            String previousCheckpointHash
    ) {
        return new GovernanceAssuranceReceiptCheckpointMaterial(
                1,

                2,

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                2,
                1,
                1,

                "checkpoint-key",
                "ED25519",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                previousCheckpointHash
        );
    }
}
