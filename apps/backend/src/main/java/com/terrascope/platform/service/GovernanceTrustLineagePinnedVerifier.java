package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import com.terrascope.platform.api.model.GovernanceTrustLineageVerificationResponse;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableEnvelope;
import com.terrascope.platform.domain.GovernanceTrustLineagePortablePayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class GovernanceTrustLineagePinnedVerifier {

    private static final Pattern SHA256_PATTERN =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private final GovernanceTrustLineagePortableVerifier portableVerifier;

    private final GovernanceTrustLineagePackageCodec codec;

    public GovernanceTrustLineagePinnedVerifier(
            GovernanceTrustLineagePortableVerifier portableVerifier,
            GovernanceTrustLineagePackageCodec codec
    ) {
        this.portableVerifier = portableVerifier;
        this.codec = codec;
    }

    public GovernanceTrustLineagePinnedVerificationResponse verify(
            byte[] envelopeBytes,
            String trustedRootFingerprint
    ) {
        String normalizedPin =
                normalizeFingerprint(
                        trustedRootFingerprint
                );

        String envelopeHash =
                codec.sha256(
                        envelopeBytes
                );

        GovernanceTrustLineageVerificationResponse portable =
                portableVerifier.verify(
                        envelopeBytes
                );

        UUID sourcePackageId =
                null;

        Integer sourcePackageNumber =
                null;

        String packageHash =
                null;

        String presentedRootFingerprint =
                null;

        try {
            GovernanceTrustLineagePortableEnvelope envelope =
                    codec.decodeEnvelope(
                            envelopeBytes
                    );

            sourcePackageId =
                    envelope.sourcePackageId();

            sourcePackageNumber =
                    envelope.sourcePackageNumber();

            packageHash =
                    envelope.packageHash();

            GovernanceTrustLineagePortablePayload payload =
                    codec.decodePayload(
                            envelope.payloadJson()
                    );

            presentedRootFingerprint =
                    normalizeFingerprint(
                            payload.rootPublicKeyFingerprint()
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            // Portable verifier already captures the envelope or
            // payload decoding failure in its own result.
        }

        boolean pinFormatValid =
                normalizedPin != null

                && SHA256_PATTERN
                        .matcher(
                                normalizedPin
                        )
                        .matches();

        boolean rootPinValid =
                pinFormatValid

                && presentedRootFingerprint != null

                && normalizedPin.equals(
                        presentedRootFingerprint
                );

        boolean valid =
                portable.valid()

                && rootPinValid;

        String message;

        if (
                !pinFormatValid
        ) {
            message =
                    "Trusted root fingerprint must be a 64-character lowercase SHA-256 value.";

        } else if (
                !portable.valid()
        ) {
            message =
                    "Portable trust lineage failed cryptographic verification: "
                            + portable.message();

        } else if (
                !rootPinValid
        ) {
            message =
                    "Portable trust-lineage root does not match the externally trusted root fingerprint.";

        } else {
            message =
                    "Portable trust lineage verified against the externally supplied root fingerprint.";
        }

        return new GovernanceTrustLineagePinnedVerificationResponse(
                sourcePackageId,
                sourcePackageNumber,

                envelopeHash,
                packageHash,

                valid,

                portable.hashValid(),
                portable.canonicalPayloadValid(),
                portable.lineageValid(),
                rootPinValid,

                portable.transitionCount(),
                portable.verifiedTransitionCount(),
                portable.trustAnchorCount(),

                normalizedPin,
                presentedRootFingerprint,

                portable.rootKeyId(),
                portable.terminalKeyId(),

                message,

                Instant.now()
        );
    }

    private String normalizeFingerprint(
            String value
    ) {
        if (
                value == null
        ) {
            return null;
        }

        String normalized =
                value
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}
