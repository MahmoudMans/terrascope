package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceOfflineTrustReceiptLedgerHasherTest {

    private final GovernanceOfflineTrustReceiptLedgerHasher hasher =
            new GovernanceOfflineTrustReceiptLedgerHasher();

    @Test
    void receiptPayloadHashIsDeterministic() {
        GovernanceOfflineTrustVerificationReceipt receipt =
                receipt();

        String first =
                hasher.receiptPayloadHash(
                        receipt
                );

        String second =
                hasher.receiptPayloadHash(
                        receipt
                );

        assertThat(
                first
        ).isEqualTo(
                second
        );

        assertThat(
                first
        ).matches(
                "[0-9a-f]{64}"
        );
    }

    @Test
    void ledgerHashBindsPreviousHash() {
        GovernanceOfflineTrustVerificationReceipt receipt =
                receipt();

        String payloadHash =
                hasher.receiptPayloadHash(
                        receipt
                );

        String genesis =
                hasher.ledgerHash(
                        1,

                        receipt.id(),

                        receipt.importNumber(),

                        receipt.verificationResult(),

                        receipt.receiptHash(),

                        payloadHash,

                        null
                );

        String chained =
                hasher.ledgerHash(
                        1,

                        receipt.id(),

                        receipt.importNumber(),

                        receipt.verificationResult(),

                        receipt.receiptHash(),

                        payloadHash,

                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        assertThat(
                genesis
        ).isNotEqualTo(
                chained
        );
    }

    private GovernanceOfflineTrustVerificationReceipt receipt() {
        return new GovernanceOfflineTrustVerificationReceipt(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                1,

                "TS-STANDALONE-TRUST-VERIFIER-V1",

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                UUID.fromString(
                        "22222222-2222-2222-2222-222222222222"
                ),

                1,

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                "VALID",

                true,
                true,
                true,
                true,

                2,
                2,
                3,

                "key-1",
                "key-3",

                Instant.parse(
                        "2030-01-01T00:00:00Z"
                ),

                Instant.parse(
                        "2030-01-01T00:00:01Z"
                )
        );
    }
}
