package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceEnvelopeVerificationResponse;
import com.terrascope.platform.domain.GovernanceAssuranceBundlePayload;
import com.terrascope.platform.domain.GovernanceAssurancePortableEnvelope;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class GovernanceAssuranceStatelessVerifier {

    private final GovernanceAssuranceEnvelopeCodec envelopeCodec;

    private final GovernanceAssuranceBundleCodec bundleCodec;

    private final GovernanceAssurancePortableVerifier portableVerifier;

    public GovernanceAssuranceStatelessVerifier(
            GovernanceAssuranceEnvelopeCodec envelopeCodec,
            GovernanceAssuranceBundleCodec bundleCodec,
            GovernanceAssurancePortableVerifier portableVerifier
    ) {
        this.envelopeCodec = envelopeCodec;
        this.bundleCodec = bundleCodec;
        this.portableVerifier = portableVerifier;
    }

    public GovernanceAssuranceEnvelopeVerificationResponse verify(
            byte[] envelopeBytes
    ) {
        String envelopeHash =
                envelopeCodec.hash(
                        envelopeBytes
                );

        GovernanceAssurancePortableEnvelope envelope;

        try {
            envelope =
                    envelopeCodec.decode(
                            envelopeBytes
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            return invalid(
                    null,
                    null,
                    null,
                    null,

                    envelopeHash,
                    null,

                    false,
                    false,
                    false,

                    0,
                    0,
                    0,
                    0,
                    0,

                    "Portable governance assurance envelope cannot be decoded."
            );
        }

        if (
                !GovernanceAssuranceEnvelopeCodec.ENVELOPE_VERSION
                        .equals(
                                envelope.envelopeVersion()
                        )
        ) {
            return invalid(
                    envelope.sourceBundleId(),
                    envelope.sourceBundleNumber(),
                    envelope.envelopeVersion(),
                    envelope.bundleVersion(),

                    envelopeHash,
                    envelope.bundleHash(),

                    false,
                    false,
                    false,

                    0,
                    0,
                    0,
                    0,
                    0,

                    "Unsupported portable governance assurance envelope version."
            );
        }

        String calculatedBundleHash =
                bundleCodec.hash(
                        envelope.payloadJson()
                );

        boolean hashValid =
                calculatedBundleHash.equals(
                        envelope.bundleHash()
                );

        GovernanceAssuranceBundlePayload payload;

        try {
            payload =
                    bundleCodec.decode(
                            envelope.payloadJson()
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            return invalid(
                    envelope.sourceBundleId(),
                    envelope.sourceBundleNumber(),
                    envelope.envelopeVersion(),
                    envelope.bundleVersion(),

                    envelopeHash,
                    envelope.bundleHash(),

                    hashValid,
                    false,
                    false,

                    0,
                    0,
                    0,
                    0,
                    0,

                    "Portable bundle payload cannot be decoded."
            );
        }

        boolean canonicalPayload =
                bundleCodec.encode(
                        payload
                ).equals(
                        envelope.payloadJson()
                );

        int signatureCount =
                payload.signatures()
                        .size();

        int trustAnchorCount =
                payload.trustAnchors()
                        .size();

        int retiredAnchorCount =
                (int) payload
                        .trustAnchors()
                        .stream()
                        .filter(anchor ->
                                "RETIRED"
                                        .equals(
                                                anchor.status()
                                        )
                        )
                        .count();

        int lifecycleEventCount =
                payload.lifecycleEvents()
                        .size();

        boolean bundleVersionValid =
                GovernanceAssuranceBundleCodec.BUNDLE_VERSION
                        .equals(
                                envelope.bundleVersion()
                        )

                && envelope
                        .bundleVersion()
                        .equals(
                                payload.bundleVersion()
                        );

        GovernanceAssurancePortableVerifier.Result portable =
                portableVerifier.verify(
                        payload
                );

        boolean valid =
                hashValid
                && canonicalPayload
                && bundleVersionValid
                && portable.valid();

        String message;

        if (
                !hashValid
        ) {
            message =
                    "Portable bundle SHA-256 fingerprint does not match the enclosed canonical payload.";

        } else if (
                !canonicalPayload
        ) {
            message =
                    "Portable bundle payload is not in canonical TerraScope representation.";

        } else if (
                !bundleVersionValid
        ) {
            message =
                    "Portable bundle version metadata does not match its enclosed payload.";

        } else {
            message =
                    portable.message();
        }

        return new GovernanceAssuranceEnvelopeVerificationResponse(
                valid,

                envelope.sourceBundleId(),
                envelope.sourceBundleNumber(),

                envelope.envelopeVersion(),
                envelope.bundleVersion(),

                envelopeHash,
                envelope.bundleHash(),

                hashValid,
                canonicalPayload,
                portable.valid(),

                signatureCount,
                portable.verifiedSignatureCount(),

                trustAnchorCount,
                retiredAnchorCount,
                lifecycleEventCount,

                message,

                Instant.now()
        );
    }

    private GovernanceAssuranceEnvelopeVerificationResponse invalid(
            java.util.UUID sourceBundleId,
            Integer sourceBundleNumber,

            String envelopeVersion,
            String bundleVersion,

            String envelopeHash,
            String bundleHash,

            boolean hashValid,
            boolean canonicalPayloadValid,
            boolean portableVerificationValid,

            int signatureCount,
            int verifiedSignatureCount,

            int trustAnchorCount,
            int retiredAnchorCount,
            int lifecycleEventCount,

            String message
    ) {
        return new GovernanceAssuranceEnvelopeVerificationResponse(
                false,

                sourceBundleId,
                sourceBundleNumber,

                envelopeVersion,
                bundleVersion,

                envelopeHash,
                bundleHash,

                hashValid,
                canonicalPayloadValid,
                portableVerificationValid,

                signatureCount,
                verifiedSignatureCount,

                trustAnchorCount,
                retiredAnchorCount,
                lifecycleEventCount,

                message,

                Instant.now()
        );
    }
}
