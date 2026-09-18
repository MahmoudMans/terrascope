package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import com.terrascope.platform.api.model.ImportGovernanceOfflineTrustVerificationReceiptRequest;
import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptDocument;
import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptMaterial;
import com.terrascope.platform.repository.GovernanceOfflineTrustVerificationReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceOfflineTrustVerificationReceiptServiceTest {

    private GovernanceOfflineTrustVerificationReceiptCodec codec;

    private GovernanceTrustLineagePinnedVerifier verifier;

    private GovernanceOfflineTrustVerificationReceiptRepository repository;

    private GovernanceOfflineTrustVerificationReceiptService service;

    @BeforeEach
    void setUp() {
        codec =
                mock(
                        GovernanceOfflineTrustVerificationReceiptCodec.class
                );

        verifier =
                mock(
                        GovernanceTrustLineagePinnedVerifier.class
                );

        repository =
                mock(
                        GovernanceOfflineTrustVerificationReceiptRepository.class
                );

        service =
                new GovernanceOfflineTrustVerificationReceiptService(
                        codec,
                        verifier,
                        repository
                );
    }

    @Test
    void importsCryptographicallyReverifiedOfflineReceipt() {
        GovernanceOfflineTrustVerificationReceiptDocument document =
                document(
                        true
                );

        GovernanceOfflineTrustVerificationReceiptMaterial material =
                material(
                        document
                );

        GovernanceTrustLineagePinnedVerificationResponse verification =
                verification(
                        true
                );

        GovernanceOfflineTrustVerificationReceipt imported =
                imported(
                        verification
                );

        when(
                codec.decode(
                        "receipt-json"
                )
        ).thenReturn(
                document
        );

        when(
                codec.isCanonicalDocument(
                        "receipt-json"
                )
        ).thenReturn(
                true
        );

        when(
                codec.material(
                        document
                )
        ).thenReturn(
                material
        );

        when(
                codec.receiptHash(
                        material
                )
        ).thenReturn(
                document.receiptHash()
        );

        when(
                verifier.verify(
                        any(
                                byte[].class
                        ),

                        eq(
                                document.trustedRootFingerprint()
                        )
                )
        ).thenReturn(
                verification
        );

        when(
                repository.findByReceiptHash(
                        document.receiptHash()
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                repository.nextImportNumber()
        ).thenReturn(
                1
        );

        when(
                repository.save(
                        any(),
                        eq(1),
                        eq(
                                document
                        ),
                        eq(
                                verification
                        )
                )
        ).thenReturn(
                imported
        );

        GovernanceOfflineTrustVerificationReceipt result =
                service.importReceipt(
                        new ImportGovernanceOfflineTrustVerificationReceiptRequest(
                                "receipt-json"
                        )
                );

        assertThat(
                result.importNumber()
        ).isEqualTo(
                1
        );

        assertThat(
                result.verificationResult()
        ).isEqualTo(
                "VALID"
        );
    }

    @Test
    void rejectsReceiptThatDisagreesWithFreshCryptographicVerification() {
        GovernanceOfflineTrustVerificationReceiptDocument document =
                document(
                        true
                );

        GovernanceOfflineTrustVerificationReceiptMaterial material =
                material(
                        document
                );

        GovernanceTrustLineagePinnedVerificationResponse verification =
                verification(
                        false
                );

        when(
                codec.decode(
                        "receipt-json"
                )
        ).thenReturn(
                document
        );

        when(
                codec.isCanonicalDocument(
                        "receipt-json"
                )
        ).thenReturn(
                true
        );

        when(
                codec.material(
                        document
                )
        ).thenReturn(
                material
        );

        when(
                codec.receiptHash(
                        material
                )
        ).thenReturn(
                document.receiptHash()
        );

        when(
                verifier.verify(
                        any(
                                byte[].class
                        ),

                        eq(
                                document.trustedRootFingerprint()
                        )
                )
        ).thenReturn(
                verification
        );

        assertThatThrownBy(() ->
                service.importReceipt(
                        new ImportGovernanceOfflineTrustVerificationReceiptRequest(
                                "receipt-json"
                        )
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    private GovernanceOfflineTrustVerificationReceiptDocument document(
            boolean valid
    ) {
        return new GovernanceOfflineTrustVerificationReceiptDocument(
                GovernanceOfflineTrustVerificationReceiptCodec.VERIFIER_VERSION,

                "11111111-1111-1111-1111-111111111111",
                1,

                "{\"envelope\":true}",

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                valid,

                true,
                true,
                true,
                true,

                2,
                2,
                3,

                "key-1",
                "key-3",

                "Portable trust lineage verified against the externally supplied root fingerprint.",

                "2030-01-01T00:00:00Z",

                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        );
    }

    private GovernanceOfflineTrustVerificationReceiptMaterial material(
            GovernanceOfflineTrustVerificationReceiptDocument document
    ) {
        return new GovernanceOfflineTrustVerificationReceiptMaterial(
                document.verifierVersion(),

                document.sourcePackageId(),
                document.sourcePackageNumber(),

                document.envelopeJson(),
                document.envelopeHash(),
                document.packageHash(),

                document.trustedRootFingerprint(),
                document.presentedRootFingerprint(),

                document.valid(),

                document.packageHashValid(),
                document.canonicalPayloadValid(),
                document.lineageValid(),
                document.rootPinValid(),

                document.transitionCount(),
                document.verifiedTransitionCount(),
                document.trustAnchorCount(),

                document.rootKeyId(),
                document.terminalKeyId(),

                document.message(),
                document.verifiedAt()
        );
    }

    private GovernanceTrustLineagePinnedVerificationResponse verification(
            boolean valid
    ) {
        return new GovernanceTrustLineagePinnedVerificationResponse(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                1,

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                valid,

                true,
                true,
                true,
                true,

                2,
                2,
                3,

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "key-1",
                "key-3",

                valid
                        ? "Portable trust lineage verified against the externally supplied root fingerprint."
                        : "Different result.",

                Instant.parse(
                        "2030-01-01T00:00:01Z"
                )
        );
    }

    private GovernanceOfflineTrustVerificationReceipt imported(
            GovernanceTrustLineagePinnedVerificationResponse verification
    ) {
        return new GovernanceOfflineTrustVerificationReceipt(
                UUID.randomUUID(),

                1,

                GovernanceOfflineTrustVerificationReceiptCodec.VERIFIER_VERSION,

                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                verification.sourcePackageId(),
                verification.sourcePackageNumber(),

                verification.envelopeHash(),
                verification.packageHash(),

                verification.trustedRootFingerprint(),
                verification.presentedRootFingerprint(),

                verification.valid()
                        ? "VALID"
                        : "INVALID",

                verification.packageHashValid(),
                verification.canonicalPayloadValid(),
                verification.lineageValid(),
                verification.rootPinValid(),

                verification.transitionCount(),
                verification.verifiedTransitionCount(),
                verification.trustAnchorCount(),

                verification.rootKeyId(),
                verification.terminalKeyId(),

                Instant.parse(
                        "2030-01-01T00:00:00Z"
                ),

                Instant.parse(
                        "2030-01-01T00:00:02Z"
                )
        );
    }
}
