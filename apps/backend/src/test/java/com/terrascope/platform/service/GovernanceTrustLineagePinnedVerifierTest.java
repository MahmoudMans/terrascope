package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.domain.GovernanceTrustAnchorTransitionMaterial;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableEnvelope;
import com.terrascope.platform.domain.GovernanceTrustLineagePortablePayload;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableTrustAnchor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceTrustLineagePinnedVerifierTest {

    private GovernanceTrustLineagePackageCodec codec;

    private GovernanceEd25519Crypto crypto;

    private GovernanceTrustAnchorTransitionHasher transitionHasher;

    private GovernanceTrustLineagePinnedVerifier verifier;

    @BeforeEach
    void setUp() {
        codec =
                new GovernanceTrustLineagePackageCodec();

        crypto =
                new GovernanceEd25519Crypto();

        transitionHasher =
                new GovernanceTrustAnchorTransitionHasher();

        verifier =
                new GovernanceTrustLineagePinnedVerifier(
                        new GovernanceTrustLineagePortableVerifier(
                                codec,
                                crypto,
                                transitionHasher
                        ),

                        codec
                );
    }

    @Test
    void externallyPinnedRootVerifiesValidPortableLineage() {
        Fixture fixture =
                fixture();

        var result =
                verifier.verify(
                        fixture.envelopeBytes(),
                        fixture.rootFingerprint()
                );

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.packageHashValid()
        ).isTrue();

        assertThat(
                result.canonicalPayloadValid()
        ).isTrue();

        assertThat(
                result.lineageValid()
        ).isTrue();

        assertThat(
                result.rootPinValid()
        ).isTrue();

        assertThat(
                result.trustedRootFingerprint()
        ).isEqualTo(
                fixture.rootFingerprint()
        );

        assertThat(
                result.presentedRootFingerprint()
        ).isEqualTo(
                fixture.rootFingerprint()
        );
    }

    @Test
    void wrongExternalRootPinRejectsOtherwiseValidLineage() {
        Fixture fixture =
                fixture();

        String wrongPin =
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

        assertThat(
                wrongPin
        ).isNotEqualTo(
                fixture.rootFingerprint()
        );

        var result =
                verifier.verify(
                        fixture.envelopeBytes(),
                        wrongPin
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.packageHashValid()
        ).isTrue();

        assertThat(
                result.canonicalPayloadValid()
        ).isTrue();

        assertThat(
                result.lineageValid()
        ).isTrue();

        assertThat(
                result.rootPinValid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "does not match"
        );
    }

    @Test
    void malformedExternalRootPinIsRejected() {
        Fixture fixture =
                fixture();

        var result =
                verifier.verify(
                        fixture.envelopeBytes(),
                        "not-a-sha256-pin"
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.rootPinValid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "64-character"
        );
    }

    @Test
    void correctRootPinDoesNotOverrideSignatureFailure() {
        Fixture fixture =
                fixture();

        GovernanceTrustLineagePortableEnvelope envelope =
                codec.decodeEnvelope(
                        fixture.envelopeBytes()
                );

        GovernanceTrustLineagePortablePayload original =
                codec.decodePayload(
                        envelope.payloadJson()
                );

        List<GovernanceTrustAnchorTransition> transitions =
                new ArrayList<>(
                        original.transitions()
                );

        GovernanceTrustAnchorTransition target =
                transitions.getFirst();

        String zeroSignature =
                Base64
                        .getEncoder()
                        .encodeToString(
                                new byte[64]
                        );

        GovernanceTrustAnchorTransitionMaterial material =
                new GovernanceTrustAnchorTransitionMaterial(
                        target.transitionNumber(),

                        target.outgoingKeyId(),
                        target.outgoingPublicKeyFingerprint(),

                        target.replacementKeyId(),
                        target.replacementPublicKeyFingerprint(),

                        target.algorithm(),

                        target.reason(),

                        target.previousTransitionHash()
                );

        transitions.set(
                0,

                new GovernanceTrustAnchorTransition(
                        target.id(),

                        target.transitionNumber(),

                        target.outgoingKeyId(),
                        target.outgoingPublicKeyFingerprint(),

                        target.replacementKeyId(),
                        target.replacementPublicKeyFingerprint(),

                        target.algorithm(),

                        target.reason(),

                        target.transitionVersion(),

                        zeroSignature,

                        target.previousTransitionHash(),

                        transitionHasher.transitionHash(
                                material,
                                zeroSignature
                        ),

                        target.createdAt()
                )
        );

        GovernanceTrustLineagePortablePayload tampered =
                new GovernanceTrustLineagePortablePayload(
                        original.packageVersion(),

                        original.rootKeyId(),
                        original.rootPublicKeyFingerprint(),

                        original.terminalKeyId(),
                        original.terminalPublicKeyFingerprint(),

                        original.trustAnchors(),

                        transitions
                );

        byte[] envelopeBytes =
                envelope(
                        tampered
                );

        var result =
                verifier.verify(
                        envelopeBytes,
                        fixture.rootFingerprint()
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.rootPinValid()
        ).isTrue();

        assertThat(
                result.lineageValid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "signature verification failed"
        );
    }

    private Fixture fixture() {
        KeyMaterial first =
                key();

        KeyMaterial second =
                key();

        KeyMaterial third =
                key();

        GovernanceTrustAnchorTransition firstTransition =
                transition(
                        1,
                        first,
                        second,
                        null,
                        "First transition."
                );

        GovernanceTrustAnchorTransition secondTransition =
                transition(
                        2,
                        second,
                        third,
                        firstTransition.transitionHash(),
                        "Second transition."
                );

        GovernanceTrustLineagePortablePayload payload =
                new GovernanceTrustLineagePortablePayload(
                        GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                        first.keyId(),
                        first.fingerprint(),

                        third.keyId(),
                        third.fingerprint(),

                        List.of(
                                anchor(
                                        first,
                                        "RETIRED"
                                ),

                                anchor(
                                        second,
                                        "RETIRED"
                                ),

                                anchor(
                                        third,
                                        "ACTIVE"
                                )
                        ),

                        List.of(
                                firstTransition,
                                secondTransition
                        )
                );

        return new Fixture(
                envelope(
                        payload
                ),

                first.fingerprint()
        );
    }

    private byte[] envelope(
            GovernanceTrustLineagePortablePayload payload
    ) {
        String payloadJson =
                codec.encodePayload(
                        payload
                );

        return codec.encodeEnvelope(
                new GovernanceTrustLineagePortableEnvelope(
                        GovernanceTrustLineagePackageCodec.ENVELOPE_VERSION,

                        UUID.fromString(
                                "11111111-1111-1111-1111-111111111111"
                        ),

                        1,

                        GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                        codec.sha256(
                                payloadJson
                        ),

                        payloadJson
                )
        );
    }

    private GovernanceTrustAnchorTransition transition(
            int number,

            KeyMaterial outgoing,
            KeyMaterial replacement,

            String previousHash,
            String reason
    ) {
        GovernanceTrustAnchorTransitionMaterial material =
                new GovernanceTrustAnchorTransitionMaterial(
                        number,

                        outgoing.keyId(),
                        outgoing.fingerprint(),

                        replacement.keyId(),
                        replacement.fingerprint(),

                        GovernanceEd25519Crypto.ALGORITHM,

                        reason,

                        previousHash
                );

        String signature =
                crypto.sign(
                        outgoing.privateKey(),

                        transitionHasher.signingPayload(
                                material
                        )
                );

        return new GovernanceTrustAnchorTransition(
                UUID.randomUUID(),

                number,

                outgoing.keyId(),
                outgoing.fingerprint(),

                replacement.keyId(),
                replacement.fingerprint(),

                GovernanceEd25519Crypto.ALGORITHM,

                reason,

                GovernanceTrustAnchorTransitionHasher.TRANSITION_VERSION,

                signature,

                previousHash,

                transitionHasher.transitionHash(
                        material,
                        signature
                ),

                Instant.parse(
                        "2030-01-01T00:00:00Z"
                ).plusSeconds(
                        number
                )
        );
    }

    private GovernanceTrustLineagePortableTrustAnchor anchor(
            KeyMaterial key,
            String status
    ) {
        return new GovernanceTrustLineagePortableTrustAnchor(
                key.keyId(),

                GovernanceEd25519Crypto.ALGORITHM,

                key.publicKey(),
                key.fingerprint(),

                status,

                null,
                null,

                Instant.parse(
                        "2029-01-01T00:00:00Z"
                ),

                null,

                "RETIRED".equals(
                        status
                )
                        ? Instant.parse(
                                "2031-01-01T00:00:00Z"
                        )
                        : null
        );
    }

    private KeyMaterial key() {
        try {
            KeyPair pair =
                    KeyPairGenerator
                            .getInstance(
                                    "Ed25519"
                            )
                            .generateKeyPair();

            String privateKey =
                    Base64
                            .getEncoder()
                            .encodeToString(
                                    pair
                                            .getPrivate()
                                            .getEncoded()
                            );

            String publicKey =
                    Base64
                            .getEncoder()
                            .encodeToString(
                                    pair
                                            .getPublic()
                                            .getEncoded()
                            );

            return new KeyMaterial(
                    "key-"
                            + UUID.randomUUID(),

                    privateKey,
                    publicKey,

                    crypto.publicKeyFingerprint(
                            publicKey
                    )
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    exception
            );
        }
    }

    private record Fixture(
            byte[] envelopeBytes,
            String rootFingerprint
    ) {
    }

    private record KeyMaterial(
            String keyId,
            String privateKey,
            String publicKey,
            String fingerprint
    ) {
    }
}
