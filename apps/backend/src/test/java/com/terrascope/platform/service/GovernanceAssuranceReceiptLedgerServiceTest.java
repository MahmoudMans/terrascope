package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerVerificationResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceipt;
import com.terrascope.platform.repository.GovernanceAssuranceReceiptLedgerRepository;
import com.terrascope.platform.repository.GovernanceAssuranceVerificationReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceAssuranceReceiptLedgerServiceTest {

    private GovernanceAssuranceReceiptLedgerRepository ledgerRepository;

    private GovernanceAssuranceVerificationReceiptRepository receiptRepository;

    private GovernanceAssuranceReceiptLedgerHasher hasher;

    private GovernanceAssuranceReceiptLedgerService service;

    @BeforeEach
    void setUp() {
        ledgerRepository =
                mock(
                        GovernanceAssuranceReceiptLedgerRepository.class
                );

        receiptRepository =
                mock(
                        GovernanceAssuranceVerificationReceiptRepository.class
                );

        hasher =
                new GovernanceAssuranceReceiptLedgerHasher();

        service =
                new GovernanceAssuranceReceiptLedgerService(
                        ledgerRepository,
                        receiptRepository,
                        hasher
                );
    }

    @Test
    void firstReceiptCreatesGenesisLedgerEntry() {
        GovernanceAssuranceVerificationReceipt receipt =
                receipt(
                        1,
                        "VALID",
                        "Verification succeeded."
                );

        when(
                ledgerRepository.nextLedgerNumber()
        ).thenReturn(
                1
        );

        when(
                ledgerRepository.findLatest()
        ).thenReturn(
                Optional.empty()
        );

        String payloadHash =
                hasher.receiptPayloadHash(
                        receipt
                );

        String ledgerHash =
                hasher.ledgerHash(
                        1,
                        receipt.id(),
                        payloadHash,
                        null
                );

        GovernanceAssuranceReceiptLedgerEntry expected =
                entry(
                        1,
                        receipt,
                        payloadHash,
                        null,
                        ledgerHash
                );

        when(
                ledgerRepository.save(
                        any(),
                        eq(1),
                        eq(receipt),
                        eq(payloadHash),
                        nullable(String.class),
                        eq(
                                GovernanceAssuranceReceiptLedgerHasher.LEDGER_VERSION
                        ),
                        eq(ledgerHash)
                )
        ).thenReturn(
                expected
        );

        GovernanceAssuranceReceiptLedgerEntry created =
                service.append(
                        receipt
                );

        assertThat(
                created.ledgerNumber()
        ).isEqualTo(1);

        assertThat(
                created.previousLedgerHash()
        ).isNull();
    }

    @Test
    void secondReceiptChainsToPreviousLedgerHash() {
        GovernanceAssuranceVerificationReceipt firstReceipt =
                receipt(
                        1,
                        "VALID",
                        "First."
                );

        String firstPayload =
                hasher.receiptPayloadHash(
                        firstReceipt
                );

        String firstHash =
                hasher.ledgerHash(
                        1,
                        firstReceipt.id(),
                        firstPayload,
                        null
                );

        GovernanceAssuranceReceiptLedgerEntry first =
                entry(
                        1,
                        firstReceipt,
                        firstPayload,
                        null,
                        firstHash
                );

        GovernanceAssuranceVerificationReceipt secondReceipt =
                receipt(
                        2,
                        "INVALID",
                        "Second."
                );

        String secondPayload =
                hasher.receiptPayloadHash(
                        secondReceipt
                );

        String secondHash =
                hasher.ledgerHash(
                        2,
                        secondReceipt.id(),
                        secondPayload,
                        firstHash
                );

        GovernanceAssuranceReceiptLedgerEntry second =
                entry(
                        2,
                        secondReceipt,
                        secondPayload,
                        firstHash,
                        secondHash
                );

        when(
                ledgerRepository.nextLedgerNumber()
        ).thenReturn(
                2
        );

        when(
                ledgerRepository.findLatest()
        ).thenReturn(
                Optional.of(
                        first
                )
        );

        when(
                ledgerRepository.save(
                        any(),
                        eq(2),
                        eq(secondReceipt),
                        eq(secondPayload),
                        eq(firstHash),
                        eq(
                                GovernanceAssuranceReceiptLedgerHasher.LEDGER_VERSION
                        ),
                        eq(secondHash)
                )
        ).thenReturn(
                second
        );

        GovernanceAssuranceReceiptLedgerEntry created =
                service.append(
                        secondReceipt
                );

        assertThat(
                created.previousLedgerHash()
        ).isEqualTo(
                firstHash
        );
    }

    @Test
    void validReceiptChainVerifies() {
        GovernanceAssuranceVerificationReceipt firstReceipt =
                receipt(
                        1,
                        "VALID",
                        "First."
                );

        GovernanceAssuranceVerificationReceipt secondReceipt =
                receipt(
                        2,
                        "INVALID",
                        "Second."
                );

        String firstPayload =
                hasher.receiptPayloadHash(
                        firstReceipt
                );

        String firstHash =
                hasher.ledgerHash(
                        1,
                        firstReceipt.id(),
                        firstPayload,
                        null
                );

        GovernanceAssuranceReceiptLedgerEntry first =
                entry(
                        1,
                        firstReceipt,
                        firstPayload,
                        null,
                        firstHash
                );

        String secondPayload =
                hasher.receiptPayloadHash(
                        secondReceipt
                );

        String secondHash =
                hasher.ledgerHash(
                        2,
                        secondReceipt.id(),
                        secondPayload,
                        firstHash
                );

        GovernanceAssuranceReceiptLedgerEntry second =
                entry(
                        2,
                        secondReceipt,
                        secondPayload,
                        firstHash,
                        secondHash
                );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                receiptRepository.findReceipt(
                        firstReceipt.id()
                )
        ).thenReturn(
                Optional.of(
                        firstReceipt
                )
        );

        when(
                receiptRepository.findReceipt(
                        secondReceipt.id()
                )
        ).thenReturn(
                Optional.of(
                        secondReceipt
                )
        );

        GovernanceAssuranceReceiptLedgerVerificationResponse result =
                service.verify();

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.entryCount()
        ).isEqualTo(2);

        assertThat(
                result.verifiedEntryCount()
        ).isEqualTo(2);
    }

    @Test
    void changedReceiptPayloadIsDetected() {
        GovernanceAssuranceVerificationReceipt original =
                receipt(
                        1,
                        "VALID",
                        "Original."
                );

        String payloadHash =
                hasher.receiptPayloadHash(
                        original
                );

        String ledgerHash =
                hasher.ledgerHash(
                        1,
                        original.id(),
                        payloadHash,
                        null
                );

        GovernanceAssuranceReceiptLedgerEntry entry =
                entry(
                        1,
                        original,
                        payloadHash,
                        null,
                        ledgerHash
                );

        GovernanceAssuranceVerificationReceipt changed =
                new GovernanceAssuranceVerificationReceipt(
                        original.id(),
                        original.receiptNumber(),

                        original.sourceBundleId(),
                        original.sourceBundleNumber(),

                        original.envelopeVersion(),
                        original.bundleVersion(),

                        original.envelopeHash(),
                        original.bundleHash(),

                        original.verificationResult(),

                        original.hashValid(),
                        original.canonicalPayloadValid(),
                        original.portableVerificationValid(),

                        original.signatureCount(),
                        original.verifiedSignatureCount(),

                        original.trustAnchorCount(),
                        original.retiredAnchorCount(),
                        original.lifecycleEventCount(),

                        "Changed after anchoring.",

                        original.verifiedAt()
                );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        entry
                )
        );

        when(
                receiptRepository.findReceipt(
                        original.id()
                )
        ).thenReturn(
                Optional.of(
                        changed
                )
        );

        GovernanceAssuranceReceiptLedgerVerificationResponse result =
                service.verify();

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.brokenReceiptId()
        ).isEqualTo(
                original.id()
        );

        assertThat(
                result.message()
        ).contains(
                "payload has changed"
        );
    }

    private GovernanceAssuranceVerificationReceipt receipt(
            int number,
            String result,
            String message
    ) {
        return new GovernanceAssuranceVerificationReceipt(
                UUID.randomUUID(),

                number,

                UUID.randomUUID(),

                number,

                "TS-GOVERNANCE-AIRGAP-ENVELOPE-V1",
                "TS-GOVERNANCE-ASSURANCE-BUNDLE-V1",

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                result,

                "VALID".equals(
                        result
                ),

                true,

                "VALID".equals(
                        result
                ),

                1,

                "VALID".equals(
                        result
                )
                        ? 1
                        : 0,

                1,
                0,
                2,

                message,

                Instant.parse(
                        "2030-01-01T00:00:00Z"
                ).plusSeconds(
                        number
                )
        );
    }

    private GovernanceAssuranceReceiptLedgerEntry entry(
            int ledgerNumber,

            GovernanceAssuranceVerificationReceipt receipt,

            String payloadHash,
            String previousHash,
            String ledgerHash
    ) {
        return new GovernanceAssuranceReceiptLedgerEntry(
                UUID.randomUUID(),

                ledgerNumber,

                receipt.id(),
                receipt.receiptNumber(),

                receipt.verificationResult(),

                receipt.envelopeHash(),
                receipt.bundleHash(),

                payloadHash,

                previousHash,

                GovernanceAssuranceReceiptLedgerHasher.LEDGER_VERSION,

                ledgerHash,

                receipt.verifiedAt()
        );
    }
}
