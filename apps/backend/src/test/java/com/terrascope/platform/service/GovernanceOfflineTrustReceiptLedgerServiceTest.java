package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptLedgerRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustVerificationReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceOfflineTrustReceiptLedgerServiceTest {

    private GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository;

    private GovernanceOfflineTrustVerificationReceiptRepository receiptRepository;

    private GovernanceOfflineTrustReceiptLedgerHasher hasher;

    private GovernanceMonotonicSequenceService allocatorService;

    private GovernanceOfflineTrustReceiptLedgerService service;

    @BeforeEach
    void setUp() {
        ledgerRepository =
                mock(
                        GovernanceOfflineTrustReceiptLedgerRepository.class
                );

        receiptRepository =
                mock(
                        GovernanceOfflineTrustVerificationReceiptRepository.class
                );

        hasher =
                new GovernanceOfflineTrustReceiptLedgerHasher();

        allocatorService =
                mock(
                        GovernanceMonotonicSequenceService.class
                );

        service =
                new GovernanceOfflineTrustReceiptLedgerService(
                        ledgerRepository,
                        receiptRepository,
                        hasher,
                        allocatorService
                );
    }

    @Test
    void firstReceiptCreatesGenesisLedgerEntry() {
        GovernanceOfflineTrustVerificationReceipt receipt =
                receipt(
                        1,
                        "VALID",
                        true
                );

        when(
                ledgerRepository.findByReceiptId(
                        receipt.id()
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                allocatorService.nextLedgerNumber()
        ).thenReturn(
                1
        );

        when(
                ledgerRepository.findLatest()
        ).thenReturn(
                Optional.empty()
        );

        when(
                ledgerRepository.save(
                        any(),

                        eq(1),

                        eq(
                                receipt.id()
                        ),

                        eq(1),

                        eq(
                                "VALID"
                        ),

                        eq(
                                receipt.receiptHash()
                        ),

                        any(),

                        eq(
                                null
                        ),

                        eq(
                                GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION
                        ),

                        any()
                )
        ).thenAnswer(
                invocation ->
                        new GovernanceOfflineTrustReceiptLedgerEntry(
                                UUID.randomUUID(),

                                1,

                                receipt.id(),
                                1,

                                "VALID",

                                receipt.receiptHash(),

                                invocation.getArgument(
                                        6
                                ),

                                null,

                                GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION,

                                invocation.getArgument(
                                        9
                                ),

                                Instant.parse(
                                        "2030-01-01T00:00:02Z"
                                )
                        )
        );

        GovernanceOfflineTrustReceiptLedgerEntry result =
                service.anchor(
                        receipt
                );

        assertThat(
                result.ledgerNumber()
        ).isEqualTo(
                1
        );

        assertThat(
                result.previousLedgerHash()
        ).isNull();

        assertThat(
                result.ledgerHash()
        ).matches(
                "[0-9a-f]{64}"
        );
    }

    @Test
    void anchoringSameReceiptIsIdempotent() {
        GovernanceOfflineTrustVerificationReceipt receipt =
                receipt(
                        1,
                        "VALID",
                        true
                );

        GovernanceOfflineTrustReceiptLedgerEntry existing =
                entry(
                        1,
                        receipt,
                        null
                );

        when(
                ledgerRepository.findByReceiptId(
                        receipt.id()
                )
        ).thenReturn(
                Optional.of(
                        existing
                )
        );

        assertThat(
                service.anchor(
                        receipt
                )
        ).isSameAs(
                existing
        );
    }

    @Test
    void validTwoEntryChainVerifies() {
        GovernanceOfflineTrustVerificationReceipt first =
                receipt(
                        1,
                        "VALID",
                        true
                );

        GovernanceOfflineTrustVerificationReceipt second =
                receipt(
                        2,
                        "INVALID",
                        false
                );

        GovernanceOfflineTrustReceiptLedgerEntry firstEntry =
                entry(
                        1,
                        first,
                        null
                );

        GovernanceOfflineTrustReceiptLedgerEntry secondEntry =
                entry(
                        2,
                        second,
                        firstEntry.ledgerHash()
                );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        firstEntry,
                        secondEntry
                )
        );

        when(
                receiptRepository.findById(
                        first.id()
                )
        ).thenReturn(
                Optional.of(
                        first
                )
        );

        when(
                receiptRepository.findById(
                        second.id()
                )
        ).thenReturn(
                Optional.of(
                        second
                )
        );

        var result =
                service.verify();

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.ledgerEntryCount()
        ).isEqualTo(
                2
        );

        assertThat(
                result.verifiedEntryCount()
        ).isEqualTo(
                2
        );

        assertThat(
                result.headLedgerHash()
        ).isEqualTo(
                secondEntry.ledgerHash()
        );
    }

    @Test
    void modifiedReceiptRowBreaksLedgerVerification() {
        GovernanceOfflineTrustVerificationReceipt anchored =
                receipt(
                        1,
                        "VALID",
                        true
                );

        GovernanceOfflineTrustReceiptLedgerEntry entry =
                entry(
                        1,
                        anchored,
                        null
                );

        GovernanceOfflineTrustVerificationReceipt modified =
                new GovernanceOfflineTrustVerificationReceipt(
                        anchored.id(),

                        anchored.importNumber(),

                        anchored.verifierVersion(),

                        anchored.receiptHash(),

                        anchored.sourcePackageId(),
                        anchored.sourcePackageNumber(),

                        anchored.envelopeHash(),
                        anchored.packageHash(),

                        anchored.trustedRootFingerprint(),
                        anchored.presentedRootFingerprint(),

                        anchored.verificationResult(),

                        anchored.packageHashValid(),
                        anchored.canonicalPayloadValid(),
                        anchored.lineageValid(),

                        false,

                        anchored.transitionCount(),
                        anchored.verifiedTransitionCount(),
                        anchored.trustAnchorCount(),

                        anchored.rootKeyId(),
                        anchored.terminalKeyId(),

                        anchored.offlineVerifiedAt(),
                        anchored.importedAt()
                );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        entry
                )
        );

        when(
                receiptRepository.findById(
                        anchored.id()
                )
        ).thenReturn(
                Optional.of(
                        modified
                )
        );

        var result =
                service.verify();

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.brokenLedgerNumber()
        ).isEqualTo(
                1
        );

        assertThat(
                result.message()
        ).contains(
                "modified after ledger anchoring"
        );
    }

    private GovernanceOfflineTrustReceiptLedgerEntry entry(
            int number,
            GovernanceOfflineTrustVerificationReceipt receipt,
            String previousHash
    ) {
        String payloadHash =
                hasher.receiptPayloadHash(
                        receipt
                );

        String hash =
                hasher.ledgerHash(
                        number,

                        receipt.id(),

                        receipt.importNumber(),

                        receipt.verificationResult(),

                        receipt.receiptHash(),

                        payloadHash,

                        previousHash
                );

        return new GovernanceOfflineTrustReceiptLedgerEntry(
                UUID.randomUUID(),

                number,

                receipt.id(),

                receipt.importNumber(),

                receipt.verificationResult(),

                receipt.receiptHash(),

                payloadHash,

                previousHash,

                GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION,

                hash,

                Instant.parse(
                        "2030-01-01T00:00:10Z"
                ).plusSeconds(
                        number
                )
        );
    }

    private GovernanceOfflineTrustVerificationReceipt receipt(
            int importNumber,
            String result,
            boolean rootPinValid
    ) {
        String digit =
                Integer.toString(
                        importNumber
                );

        UUID id =
                UUID.fromString(
                        importNumber == 1
                                ? "11111111-1111-1111-1111-111111111111"
                                : "22222222-2222-2222-2222-222222222222"
                );

        return new GovernanceOfflineTrustVerificationReceipt(
                id,

                importNumber,

                "TS-STANDALONE-TRUST-VERIFIER-V1",

                (
                    digit.repeat(
                            64
                    )
                ).substring(
                        0,
                        64
                ),

                UUID.fromString(
                        "33333333-3333-3333-3333-333333333333"
                ),

                1,

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                result,

                true,
                true,
                true,
                rootPinValid,

                2,
                2,
                3,

                "key-1",
                "key-3",

                Instant.parse(
                        "2030-01-01T00:00:00Z"
                ).plusSeconds(
                        importNumber
                ),

                Instant.parse(
                        "2030-01-01T00:01:00Z"
                ).plusSeconds(
                        importNumber
                )
        );
    }
}
