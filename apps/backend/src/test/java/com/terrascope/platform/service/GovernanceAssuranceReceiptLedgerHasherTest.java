package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceipt;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceAssuranceReceiptLedgerHasherTest {

    private final GovernanceAssuranceReceiptLedgerHasher hasher =
            new GovernanceAssuranceReceiptLedgerHasher();

    @Test
    void receiptPayloadFingerprintIsStable() {
        GovernanceAssuranceVerificationReceipt receipt =
                receipt(
                        "Verification succeeded."
                );

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
    void receiptMutationAndPreviousHashChangeLedgerFingerprint() {
        GovernanceAssuranceVerificationReceipt firstReceipt =
                receipt(
                        "Verification succeeded."
                );

        GovernanceAssuranceVerificationReceipt secondReceipt =
                receipt(
                        "Verification changed."
                );

        String firstPayload =
                hasher.receiptPayloadHash(
                        firstReceipt
                );

        String secondPayload =
                hasher.receiptPayloadHash(
                        secondReceipt
                );

        assertThat(
                firstPayload
        ).isNotEqualTo(
                secondPayload
        );

        String genesis =
                hasher.ledgerHash(
                        1,
                        firstReceipt.id(),
                        firstPayload,
                        null
                );

        String chained =
                hasher.ledgerHash(
                        2,
                        firstReceipt.id(),
                        firstPayload,
                        genesis
                );

        assertThat(
                genesis
        ).isNotEqualTo(
                chained
        );
    }

    private GovernanceAssuranceVerificationReceipt receipt(
            String message
    ) {
        return new GovernanceAssuranceVerificationReceipt(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                1,

                UUID.fromString(
                        "22222222-2222-2222-2222-222222222222"
                ),

                1,

                "TS-GOVERNANCE-AIRGAP-ENVELOPE-V1",
                "TS-GOVERNANCE-ASSURANCE-BUNDLE-V1",

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                "VALID",

                true,
                true,
                true,

                1,
                1,

                1,
                0,
                2,

                message,

                Instant.parse(
                        "2030-01-01T00:00:00Z"
                )
        );
    }
}
