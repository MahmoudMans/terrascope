package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.api.model.GovernanceTrustLineageVerificationResponse;
import com.terrascope.platform.domain.GovernanceTrustAnchorTransitionMaterial;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableEnvelope;
import com.terrascope.platform.domain.GovernanceTrustLineagePortablePayload;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableTrustAnchor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class GovernanceTrustLineagePortableVerifier {

    private final GovernanceTrustLineagePackageCodec codec;

    private final GovernanceEd25519Crypto crypto;

    private final GovernanceTrustAnchorTransitionHasher transitionHasher;

    public GovernanceTrustLineagePortableVerifier(
            GovernanceTrustLineagePackageCodec codec,
            GovernanceEd25519Crypto crypto,
            GovernanceTrustAnchorTransitionHasher transitionHasher
    ) {
        this.codec = codec;
        this.crypto = crypto;
        this.transitionHasher = transitionHasher;
    }

    public GovernanceTrustLineageVerificationResponse verify(
            byte[] envelopeBytes
    ) {
        GovernanceTrustLineagePortableEnvelope envelope;

        try {
            envelope =
                    codec.decodeEnvelope(
                            envelopeBytes
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            return invalid(
                    false,
                    false,
                    false,
                    0,
                    0,
                    0,
                    null,
                    null,
                    "Portable trust-lineage envelope could not be decoded."
            );
        }

        if (
                !GovernanceTrustLineagePackageCodec.ENVELOPE_VERSION
                        .equals(
                                envelope.envelopeVersion()
                        )
        ) {
            return invalid(
                    false,
                    false,
                    false,
                    0,
                    0,
                    0,
                    null,
                    null,
                    "Unsupported trust-lineage envelope version."
            );
        }

        boolean hashValid =
                codec.sha256(
                        envelope.payloadJson()
                ).equals(
                        envelope.packageHash()
                );

        if (
                !hashValid
        ) {
            return invalid(
                    false,
                    false,
                    false,
                    0,
                    0,
                    0,
                    null,
                    null,
                    "Portable trust-lineage package hash does not match its payload."
            );
        }

        GovernanceTrustLineagePortablePayload payload;

        try {
            payload =
                    codec.decodePayload(
                            envelope.payloadJson()
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            return invalid(
                    true,
                    false,
                    false,
                    0,
                    0,
                    0,
                    null,
                    null,
                    "Portable trust-lineage payload could not be decoded."
            );
        }

        boolean canonical =
                codec.isCanonicalPayload(
                        envelope.payloadJson()
                );

        if (
                !canonical
        ) {
            return invalid(
                    true,
                    false,
                    false,
                    payload.transitions() == null
                            ? 0
                            : payload.transitions().size(),
                    0,
                    payload.trustAnchors() == null
                            ? 0
                            : payload.trustAnchors().size(),
                    payload.rootKeyId(),
                    payload.terminalKeyId(),
                    "Portable trust-lineage payload is not canonical."
            );
        }

        if (
                !GovernanceTrustLineagePackageCodec.PACKAGE_VERSION
                        .equals(
                                payload.packageVersion()
                        )

                || !payload.packageVersion()
                        .equals(
                                envelope.packageVersion()
                        )
        ) {
            return invalid(
                    true,
                    true,
                    false,
                    count(
                            payload.transitions()
                    ),
                    0,
                    count(
                            payload.trustAnchors()
                    ),
                    payload.rootKeyId(),
                    payload.terminalKeyId(),
                    "Unsupported portable trust-lineage package version."
            );
        }

        List<GovernanceTrustAnchorTransition> transitions =
                payload.transitions();

        List<GovernanceTrustLineagePortableTrustAnchor> anchors =
                payload.trustAnchors();

        if (
                transitions == null
                || transitions.isEmpty()
        ) {
            return invalid(
                    true,
                    true,
                    false,
                    0,
                    0,
                    count(
                            anchors
                    ),
                    payload.rootKeyId(),
                    payload.terminalKeyId(),
                    "Portable trust-lineage package has no signed transitions."
            );
        }

        if (
                anchors == null
                || anchors.size() < 2
        ) {
            return invalid(
                    true,
                    true,
                    false,
                    transitions.size(),
                    0,
                    count(
                            anchors
                    ),
                    payload.rootKeyId(),
                    payload.terminalKeyId(),
                    "Portable trust-lineage package does not contain enough public trust anchors."
            );
        }

        Map<String, GovernanceTrustLineagePortableTrustAnchor> anchorsByKey =
                new HashMap<>();

        Set<String> fingerprints =
                new HashSet<>();

        for (
                GovernanceTrustLineagePortableTrustAnchor anchor
                : anchors
        ) {
            if (
                    anchor.keyId() == null
                    || anchor.keyId().isBlank()

                    || anchor.publicKeyX509Base64() == null
                    || anchor.publicKeyX509Base64().isBlank()

                    || anchor.publicKeyFingerprint() == null
                    || anchor.publicKeyFingerprint().isBlank()

                    || !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    anchor.algorithm()
                            )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        0,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage package contains invalid trust-anchor metadata."
                );
            }

            if (
                    anchorsByKey.put(
                            anchor.keyId(),
                            anchor
                    ) != null
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        0,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage package contains duplicate trust-anchor key IDs."
                );
            }

            if (
                    !fingerprints.add(
                            anchor.publicKeyFingerprint()
                    )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        0,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage package contains duplicate trust-anchor fingerprints."
                );
            }

            String calculatedFingerprint;

            try {
                calculatedFingerprint =
                        crypto.publicKeyFingerprint(
                                anchor.publicKeyX509Base64()
                        );

            } catch (
                    IllegalArgumentException exception
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        0,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage package contains an invalid Ed25519 public key."
                );
            }

            if (
                    !calculatedFingerprint
                            .equals(
                                    anchor.publicKeyFingerprint()
                            )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        0,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage trust-anchor fingerprint verification failed."
                );
            }
        }

        GovernanceTrustAnchorTransition first =
                transitions.getFirst();

        GovernanceTrustAnchorTransition last =
                transitions.getLast();

        if (
                !Objects.equals(
                        payload.rootKeyId(),
                        first.outgoingKeyId()
                )

                || !Objects.equals(
                        payload.rootPublicKeyFingerprint(),
                        first.outgoingPublicKeyFingerprint()
                )
        ) {
            return invalid(
                    true,
                    true,
                    false,
                    transitions.size(),
                    0,
                    anchors.size(),
                    payload.rootKeyId(),
                    payload.terminalKeyId(),
                    "Portable trust-lineage root does not match the first signed transition."
            );
        }

        if (
                !Objects.equals(
                        payload.terminalKeyId(),
                        last.replacementKeyId()
                )

                || !Objects.equals(
                        payload.terminalPublicKeyFingerprint(),
                        last.replacementPublicKeyFingerprint()
                )
        ) {
            return invalid(
                    true,
                    true,
                    false,
                    transitions.size(),
                    0,
                    anchors.size(),
                    payload.rootKeyId(),
                    payload.terminalKeyId(),
                    "Portable trust-lineage terminal key does not match the final signed transition."
            );
        }

        String expectedPreviousTransitionHash =
                null;

        GovernanceTrustAnchorTransition previous =
                null;

        int expectedTransitionNumber =
                1;

        int verifiedTransitionCount =
                0;

        for (
                GovernanceTrustAnchorTransition transition
                : transitions
        ) {
            if (
                    transition.transitionNumber()
                    != expectedTransitionNumber
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage transition sequence is not contiguous from one."
                );
            }

            if (
                    !Objects.equals(
                            transition.previousTransitionHash(),
                            expectedPreviousTransitionHash
                    )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage previous transition hash does not match."
                );
            }

            if (
                    previous != null

                    && (
                        !Objects.equals(
                                previous.replacementKeyId(),
                                transition.outgoingKeyId()
                        )

                        || !Objects.equals(
                                previous.replacementPublicKeyFingerprint(),
                                transition.outgoingPublicKeyFingerprint()
                        )
                    )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage successor continuity is broken."
                );
            }

            GovernanceTrustLineagePortableTrustAnchor outgoing =
                    anchorsByKey.get(
                            transition.outgoingKeyId()
                    );

            GovernanceTrustLineagePortableTrustAnchor replacement =
                    anchorsByKey.get(
                            transition.replacementKeyId()
                    );

            if (
                    outgoing == null
                    || replacement == null
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage transition references a missing public trust anchor."
                );
            }

            if (
                    !transition.outgoingPublicKeyFingerprint()
                            .equals(
                                    outgoing.publicKeyFingerprint()
                            )

                    || !transition.replacementPublicKeyFingerprint()
                            .equals(
                                    replacement.publicKeyFingerprint()
                            )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage transition fingerprints do not match embedded trust anchors."
                );
            }

            if (
                    !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    transition.algorithm()
                            )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage transition uses an unsupported algorithm."
                );
            }

            if (
                    !GovernanceTrustAnchorTransitionHasher.TRANSITION_VERSION
                            .equals(
                                    transition.transitionVersion()
                            )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage transition version is unsupported."
                );
            }

            GovernanceTrustAnchorTransitionMaterial material =
                    new GovernanceTrustAnchorTransitionMaterial(
                            transition.transitionNumber(),

                            transition.outgoingKeyId(),
                            transition.outgoingPublicKeyFingerprint(),

                            transition.replacementKeyId(),
                            transition.replacementPublicKeyFingerprint(),

                            transition.algorithm(),

                            transition.reason(),

                            transition.previousTransitionHash()
                    );

            boolean signatureValid;

            try {
                signatureValid =
                        crypto.verify(
                                outgoing.publicKeyX509Base64(),

                                transitionHasher.signingPayload(
                                        material
                                ),

                                transition.signatureBase64()
                        );

            } catch (
                    IllegalArgumentException exception
            ) {
                signatureValid =
                        false;
            }

            if (
                    !signatureValid
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage Ed25519 transition signature verification failed."
                );
            }

            String calculatedTransitionHash =
                    transitionHasher.transitionHash(
                            material,
                            transition.signatureBase64()
                    );

            if (
                    !calculatedTransitionHash
                            .equals(
                                    transition.transitionHash()
                            )
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        payload.rootKeyId(),
                        payload.terminalKeyId(),
                        "Portable trust-lineage transition hash does not match its signed payload."
                );
            }

            expectedPreviousTransitionHash =
                    transition.transitionHash();

            previous =
                    transition;

            expectedTransitionNumber++;

            verifiedTransitionCount++;
        }

        return new GovernanceTrustLineageVerificationResponse(
                true,

                true,
                true,
                true,

                transitions.size(),
                verifiedTransitionCount,

                anchors.size(),

                payload.rootKeyId(),
                payload.terminalKeyId(),

                "Portable trust-lineage package verified without source-state or private-key access.",

                Instant.now()
        );
    }

    private GovernanceTrustLineageVerificationResponse invalid(
            boolean hashValid,
            boolean canonicalPayloadValid,
            boolean lineageValid,

            int transitionCount,
            int verifiedTransitionCount,

            int trustAnchorCount,

            String rootKeyId,
            String terminalKeyId,

            String message
    ) {
        return new GovernanceTrustLineageVerificationResponse(
                false,

                hashValid,
                canonicalPayloadValid,
                lineageValid,

                transitionCount,
                verifiedTransitionCount,

                trustAnchorCount,

                rootKeyId,
                terminalKeyId,

                message,

                Instant.now()
        );
    }

    private int count(
            List<?> values
    ) {
        return values == null
                ? 0
                : values.size();
    }
}
