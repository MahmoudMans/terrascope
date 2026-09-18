package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;

import com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointBundleMaterial;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceOfflineTrustCheckpointBundleCodecTest {

    private final GovernanceOfflineTrustCheckpointBundleCodec codec =
            new GovernanceOfflineTrustCheckpointBundleCodec(
                    new ObjectMapper()
            );

    @Test
    void portableEnvelopeRoundTripsCanonicalPayload() {
        GovernanceOfflineTrustCheckpointBundleMaterial material =
                material(
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        var envelope =
                codec.encode(
                        material
                );

        var decodedEnvelope =
                codec.decodeEnvelope(
                        codec.envelopeJson(
                                envelope
                        )
                );

        var decodedMaterial =
                codec.decodeMaterial(
                        decodedEnvelope.payloadJson()
                );

        assertThat(
                decodedEnvelope.envelopeVersion()
        ).isEqualTo(
                GovernanceOfflineTrustCheckpointBundleCodec.ENVELOPE_VERSION
        );

        assertThat(
                decodedEnvelope.bundleHash()
        ).isEqualTo(
                codec.sha256(
                        decodedEnvelope.payloadJson()
                )
        );

        assertThat(
                codec.materialJson(
                        decodedMaterial
                )
        ).isEqualTo(
                decodedEnvelope.payloadJson()
        );
    }

    @Test
    void ledgerMutationChangesBundleHash() {
        String first =
                codec
                        .encode(
                                material(
                                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                )
                        )
                        .bundleHash();

        String second =
                codec
                        .encode(
                                material(
                                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                )
                        )
                        .bundleHash();

        assertThat(
                first
        ).isNotEqualTo(
                second
        );
    }

    private GovernanceOfflineTrustCheckpointBundleMaterial material(
            String ledgerHash
    ) {
        GovernanceOfflineTrustReceiptLedgerEntry ledgerEntry =
                new GovernanceOfflineTrustReceiptLedgerEntry(
                        UUID.fromString(
                                "11111111-1111-1111-1111-111111111111"
                        ),

                        1,

                        UUID.fromString(
                                "22222222-2222-2222-2222-222222222222"
                        ),

                        1,

                        "VALID",

                        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                        null,

                        GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION,

                        ledgerHash,

                        Instant.parse(
                                "2030-01-01T00:00:00Z"
                        )
                );

        GovernanceOfflineTrustReceiptCheckpoint checkpoint =
                new GovernanceOfflineTrustReceiptCheckpoint(
                        UUID.fromString(
                                "33333333-3333-3333-3333-333333333333"
                        ),

                        1,

                        1,

                        ledgerHash,

                        1,

                        1,

                        0,

                        "key-1",

                        "ED25519",

                        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",

                        GovernanceOfflineTrustReceiptCheckpointHasher.CHECKPOINT_VERSION,

                        "signature",

                        null,

                        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",

                        Instant.parse(
                                "2030-01-01T00:01:00Z"
                        )
                );

        return new GovernanceOfflineTrustCheckpointBundleMaterial(
                GovernanceOfflineTrustCheckpointBundleCodec.BUNDLE_VERSION,

                checkpoint,

                List.of(
                        ledgerEntry
                ),

                "MCowBQYDK2VwAyEA//////////////////////////////////////////8=",

                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        );
    }
}
