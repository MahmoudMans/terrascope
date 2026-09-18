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

class GovernanceTrustLineagePortableVerifierTest {

    private GovernanceTrustLineagePackageCodec codec;

    private GovernanceEd25519Crypto crypto;

    private GovernanceTrustAnchorTransitionHasher transitionHasher;

    private GovernanceTrustLineagePortableVerifier verifier;

    @BeforeEach
    void setUp() {
        codec =
                new GovernanceTrustLineagePackageCodec();

        crypto =
                new GovernanceEd25519Crypto();

        transitionHasher =
                new GovernanceTrustAnchorTransitionHasher();

        verifier =
                new GovernanceTrustLineagePortableVerifier(
                        codec,
                        crypto,
                        transitionHasher
                );
    }

    @Test
    void verifiesPortableMultiGenerationLineage() {
        Fixture fixture =
                fixture(
                        false
                );

        var result =
                verifier.verify(
                        fixture.envelopeBytes()
                );

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.hashValid()
        ).isTrue();

        assertThat(
                result.canonicalPayloadValid()
        ).isTrue();

        assertThat(
                result.lineageValid()
        ).isTrue();

        assertThat(
                result.transitionCount()
        ).isEqualTo(2);

        assertThat(
                result.verifiedTransitionCount()
        ).isEqualTo(2);

        assertThat(
                result.trustAnchorCount()
        ).isEqualTo(3);
    }

    @Test
    void rejectsCryptographicallyTamperedTransitionSignature() {
        Fixture fixture =
                fixture(
                        false
                );

        GovernanceTrustLineagePortablePayload original =
                fixture.payload();

        List<GovernanceTrustAnchorTransition> transitions =
                new ArrayList<>(
                        original.transitions()
                );

        GovernanceTrustAnchorTransition target =
                transitions.get(1);

        GovernanceTrustAnchorTransitionMaterial material =
                material(
                        target
                );

        String zeroSignature =
                Base64
                        .getEncoder()
                        .encodeToString(
                                new byte[64]
                        );

        GovernanceTrustAnchorTransition tampered =
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
                );

        transitions.set(
                1,
                tampered
        );

        GovernanceTrustLineagePortablePayload payload =
                copyWithTransitions(
                        original,
                        transitions
                );

        var result =
                verifier.verify(
                        envelope(
                                payload
                        )
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.hashValid()
        ).isTrue();

        assertThat(
                result.canonicalPayloadValid()
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

    @Test
    void rejectsIndividuallySignedButDisconnectedSuccessorChain() {
        KeyMaterial first =
                key();

        KeyMaterial second =
                key();

        KeyMaterial detached =
                key();

        KeyMaterial fourth =
                key();

        GovernanceTrustAnchorTransition firstTransition =
                transition(
                        1,
                        first,
                        second,
                        null,
                        "First transition."
                );

        GovernanceTrustAnchorTransition detachedTransition =
                transition(
                        2,
                        detached,
                        fourth,
                        firstTransition.transitionHash(),
                        "Disconnected transition."
                );

        GovernanceTrustLineagePortablePayload payload =
                new GovernanceTrustLineagePortablePayload(
                        GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                        first.keyId(),
                        first.fingerprint(),

                        fourth.keyId(),
                        fourth.fingerprint(),

                        List.of(
                                anchor(
                                        first,
                                        "RETIRED"
                                ),

                                anchor(
                                        second,
                                        "ACTIVE"
                                ),

                                anchor(
                                        detached,
                                        "RETIRED"
                                ),

                                anchor(
                                        fourth,
                                        "ACTIVE"
                                )
                        ),

                        List.of(
                                firstTransition,
                                detachedTransition
                        )
                );

        var result =
                verifier.verify(
                        envelope(
                                payload
                        )
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "successor continuity is broken"
        );
    }

    @Test
    void rejectsEmbeddedPublicKeyFingerprintMismatch() {
        Fixture fixture =
                fixture(
                        false
                );

        GovernanceTrustLineagePortablePayload original =
                fixture.payload();

        List<GovernanceTrustLineagePortableTrustAnchor> anchors =
                new ArrayList<>(
                        original.trustAnchors()
                );

        GovernanceTrustLineagePortableTrustAnchor target =
                anchors.get(1);

        KeyMaterial unrelated =
                key();

        anchors.set(
                1,

                new GovernanceTrustLineagePortableTrustAnchor(
                        target.keyId(),
                        target.algorithm(),

                        unrelated.publicKey(),

                        target.publicKeyFingerprint(),

                        target.status(),

                        target.supersededByKeyId(),
                        target.retirementReason(),

                        target.createdAt(),
                        target.lastUsedAt(),
                        target.retiredAt()
                )
        );

        GovernanceTrustLineagePortablePayload payload =
                new GovernanceTrustLineagePortablePayload(
                        original.packageVersion(),

                        original.rootKeyId(),
                        original.rootPublicKeyFingerprint(),

                        original.terminalKeyId(),
                        original.terminalPublicKeyFingerprint(),

                        anchors,

                        original.transitions()
                );

        var result =
                verifier.verify(
                        envelope(
                                payload
                        )
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "fingerprint verification failed"
        );
    }

    private Fixture fixture(
            boolean ignored
    ) {
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
                payload,
                envelope(
                        payload
                )
        );
    }

    private byte[] envelope(
            GovernanceTrustLineagePortablePayload payload
    ) {
        String payloadJson =
                codec.encodePayload(
                        payload
                );

        GovernanceTrustLineagePortableEnvelope envelope =
                new GovernanceTrustLineagePortableEnvelope(
                        GovernanceTrustLineagePackageCodec.ENVELOPE_VERSION,

                        UUID.fromString(
                                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                        ),

                        1,

                        GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                        codec.sha256(
                                payloadJson
                        ),

                        payloadJson
                );

        return codec.encodeEnvelope(
                envelope
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

    private GovernanceTrustAnchorTransitionMaterial material(
            GovernanceTrustAnchorTransition transition
    ) {
        return new GovernanceTrustAnchorTransitionMaterial(
                transition.transitionNumber(),

                transition.outgoingKeyId(),
                transition.outgoingPublicKeyFingerprint(),

                transition.replacementKeyId(),
                transition.replacementPublicKeyFingerprint(),

                transition.algorithm(),

                transition.reason(),

                transition.previousTransitionHash()
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

    private GovernanceTrustLineagePortablePayload copyWithTransitions(
            GovernanceTrustLineagePortablePayload original,
            List<GovernanceTrustAnchorTransition> transitions
    ) {
        return new GovernanceTrustLineagePortablePayload(
                original.packageVersion(),

                original.rootKeyId(),
                original.rootPublicKeyFingerprint(),

                original.terminalKeyId(),
                original.terminalPublicKeyFingerprint(),

                original.trustAnchors(),

                transitions
        );
    }

    private record Fixture(
            GovernanceTrustLineagePortablePayload payload,
            byte[] envelopeBytes
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
