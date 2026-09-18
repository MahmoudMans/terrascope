package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustLineagePinReceipt;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationRequest;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import com.terrascope.platform.repository.GovernanceTrustLineagePinReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceTrustLineagePinReceiptServiceTest {

    private GovernanceTrustLineagePinnedVerifier verifier;

    private GovernanceTrustLineagePinReceiptRepository repository;

    private GovernanceTrustLineagePinReceiptService service;

    @BeforeEach
    void setUp() {
        verifier =
                mock(
                        GovernanceTrustLineagePinnedVerifier.class
                );

        repository =
                mock(
                        GovernanceTrustLineagePinReceiptRepository.class
                );

        service =
                new GovernanceTrustLineagePinReceiptService(
                        verifier,
                        repository
                );
    }

    @Test
    void persistsValidPinnedVerificationReceipt() {
        String pin =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        GovernanceTrustLineagePinnedVerificationRequest request =
                new GovernanceTrustLineagePinnedVerificationRequest(
                        "{\"envelope\":true}",
                        pin
                );

        GovernanceTrustLineagePinnedVerificationResponse verification =
                response(
                        true,
                        pin,
                        pin
                );

        GovernanceTrustLineagePinReceipt receipt =
                receipt(
                        verification,
                        "VALID"
                );

        when(
                verifier.verify(
                        any(
                                byte[].class
                        ),
                        eq(
                                pin
                        )
                )
        ).thenReturn(
                verification
        );

        when(
                repository.nextReceiptNumber()
        ).thenReturn(
                1
        );

        when(
                repository.save(
                        any(),
                        eq(1),
                        eq(
                                verification
                        )
                )
        ).thenReturn(
                receipt
        );

        GovernanceTrustLineagePinReceipt result =
                service.createReceipt(
                        request
                );

        assertThat(
                result.receiptNumber()
        ).isEqualTo(
                1
        );

        assertThat(
                result.verificationResult()
        ).isEqualTo(
                "VALID"
        );

        assertThat(
                result.rootPinValid()
        ).isTrue();
    }

    @Test
    void persistsCryptographicallyValidButWrongRootPinAsInvalid() {
        String trustedPin =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        String presentedPin =
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        GovernanceTrustLineagePinnedVerificationRequest request =
                new GovernanceTrustLineagePinnedVerificationRequest(
                        "{\"envelope\":true}",
                        trustedPin
                );

        GovernanceTrustLineagePinnedVerificationResponse verification =
                response(
                        false,
                        trustedPin,
                        presentedPin
                );

        GovernanceTrustLineagePinReceipt receipt =
                receipt(
                        verification,
                        "INVALID"
                );

        when(
                verifier.verify(
                        any(
                                byte[].class
                        ),
                        eq(
                                trustedPin
                        )
                )
        ).thenReturn(
                verification
        );

        when(
                repository.nextReceiptNumber()
        ).thenReturn(
                2
        );

        when(
                repository.save(
                        any(),
                        eq(2),
                        eq(
                                verification
                        )
                )
        ).thenReturn(
                receipt
        );

        GovernanceTrustLineagePinReceipt result =
                service.createReceipt(
                        request
                );

        assertThat(
                result.verificationResult()
        ).isEqualTo(
                "INVALID"
        );

        assertThat(
                result.lineageValid()
        ).isTrue();

        assertThat(
                result.rootPinValid()
        ).isFalse();
    }

    private GovernanceTrustLineagePinnedVerificationResponse response(
            boolean valid,
            String trustedPin,
            String presentedPin
    ) {
        return new GovernanceTrustLineagePinnedVerificationResponse(
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                ),

                1,

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                valid,

                true,
                true,
                true,

                valid,

                2,
                2,
                3,

                trustedPin,
                presentedPin,

                "key-1",
                "key-3",

                valid
                        ? "Valid."
                        : "Root pin mismatch.",

                Instant.parse(
                        "2030-01-01T00:00:00Z"
                )
        );
    }

    private GovernanceTrustLineagePinReceipt receipt(
            GovernanceTrustLineagePinnedVerificationResponse verification,
            String result
    ) {
        return new GovernanceTrustLineagePinReceipt(
                UUID.randomUUID(),

                "VALID".equals(
                        result
                )
                        ? 1
                        : 2,

                verification.sourcePackageId(),
                verification.sourcePackageNumber(),

                verification.envelopeHash(),
                verification.packageHash(),

                verification.trustedRootFingerprint(),
                verification.presentedRootFingerprint(),

                result,

                verification.packageHashValid(),
                verification.canonicalPayloadValid(),
                verification.lineageValid(),
                verification.rootPinValid(),

                verification.transitionCount(),
                verification.verifiedTransitionCount(),
                verification.trustAnchorCount(),

                verification.message(),

                verification.verifiedAt()
        );
    }
}
