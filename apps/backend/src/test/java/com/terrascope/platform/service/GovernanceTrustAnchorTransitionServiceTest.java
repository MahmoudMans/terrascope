package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateGovernanceTrustAnchorTransitionRequest;
import com.terrascope.platform.api.model.GovernanceSigningReadinessResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransitionVerificationResponse;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.domain.GovernanceTrustAnchorTransitionMaterial;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
import com.terrascope.platform.repository.GovernanceTrustAnchorTransitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceTrustAnchorTransitionServiceTest {

    private GovernanceTrustAnchorTransitionRepository transitionRepository;

    private GovernanceClosureSignatureRepository trustAnchorRepository;

    private GovernanceClosureSignatureService signingService;

    private GovernanceSigningConfiguration configuration;

    private GovernanceEd25519Crypto crypto;

    private GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy;

    private GovernanceTrustAnchorTransitionHasher hasher;

    private GovernanceTrustAnchorTransitionService service;

    private String outgoingPrivate;
    private String outgoingPublic;
    private String outgoingFingerprint;

    private String replacementPublic;
    private String replacementFingerprint;

    @BeforeEach
    void setUp() throws Exception {
        transitionRepository =
                mock(
                        GovernanceTrustAnchorTransitionRepository.class
                );

        trustAnchorRepository =
                mock(
                        GovernanceClosureSignatureRepository.class
                );

        signingService =
                mock(
                        GovernanceClosureSignatureService.class
                );

        configuration =
                mock(
                        GovernanceSigningConfiguration.class
                );

        crypto =
                new GovernanceEd25519Crypto();

        lifecyclePolicy =
                new GovernanceTrustAnchorLifecyclePolicy();

        hasher =
                new GovernanceTrustAnchorTransitionHasher();

        service =
                new GovernanceTrustAnchorTransitionService(
                        transitionRepository,
                        trustAnchorRepository,
                        signingService,
                        configuration,
                        crypto,
                        lifecyclePolicy,
                        hasher
                );

        KeyPair outgoingPair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        KeyPair replacementPair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        outgoingPrivate =
                Base64
                        .getEncoder()
                        .encodeToString(
                                outgoingPair
                                        .getPrivate()
                                        .getEncoded()
                        );

        outgoingPublic =
                Base64
                        .getEncoder()
                        .encodeToString(
                                outgoingPair
                                        .getPublic()
                                        .getEncoded()
                        );

        outgoingFingerprint =
                crypto.publicKeyFingerprint(
                        outgoingPublic
                );

        replacementPublic =
                Base64
                        .getEncoder()
                        .encodeToString(
                                replacementPair
                                        .getPublic()
                                        .getEncoded()
                        );

        replacementFingerprint =
                crypto.publicKeyFingerprint(
                        replacementPublic
                );
    }

    @Test
    void signingReadinessIsRequiredForRotation() {
        when(
                signingService.readiness()
        ).thenReturn(
                new GovernanceSigningReadinessResponse(
                        false,
                        "ED25519",
                        null,
                        null,
                        false,
                        "Signing material is not configured.",
                        Instant.now()
                )
        );

        assertThatThrownBy(() ->
                service.rotate(
                        request()
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void activeOutgoingKeySignsReplacementTransition() {
        Instant now =
                Instant.now();

        GovernanceTrustAnchor outgoing =
                activeAnchor(
                        "outgoing-key",
                        outgoingPublic,
                        outgoingFingerprint,
                        now.minusSeconds(100)
                );

        GovernanceTrustAnchor replacement =
                activeAnchor(
                        "replacement-key",
                        replacementPublic,
                        replacementFingerprint,
                        now.minusSeconds(50)
                );

        when(
                signingService.readiness()
        ).thenReturn(
                new GovernanceSigningReadinessResponse(
                        true,
                        "ED25519",
                        outgoing.keyId(),
                        outgoing.publicKeyFingerprint(),
                        true,
                        "Ready.",
                        now
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        outgoing.keyId()
                )
        ).thenReturn(
                Optional.of(
                        outgoing
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        replacement.keyId()
                )
        ).thenReturn(
                Optional.of(
                        replacement
                )
        );

        when(
                transitionRepository.existsForOutgoingKey(
                        outgoing.keyId()
                )
        ).thenReturn(
                false
        );

        when(
                transitionRepository.nextTransitionNumber()
        ).thenReturn(
                1
        );

        when(
                transitionRepository.findLatest()
        ).thenReturn(
                Optional.empty()
        );

        when(
                configuration.privateKeyPkcs8Base64()
        ).thenReturn(
                outgoingPrivate
        );

        when(
                transitionRepository.save(
                        any(),
                        any(),
                        eq(
                                GovernanceTrustAnchorTransitionHasher.TRANSITION_VERSION
                        ),
                        any(),
                        any(),
                        any()
                )
        ).thenAnswer(invocation -> {
            GovernanceTrustAnchorTransitionMaterial material =
                    invocation.getArgument(
                            1
                    );

            String signature =
                    invocation.getArgument(
                            3
                    );

            String transitionHash =
                    invocation.getArgument(
                            4
                    );

            Instant createdAt =
                    invocation.getArgument(
                            5
                    );

            return transition(
                    material,
                    signature,
                    transitionHash,
                    createdAt
            );
        });

        GovernanceTrustAnchorTransition created =
                service.rotate(
                        request()
                );

        assertThat(
                created.transitionNumber()
        ).isEqualTo(1);

        assertThat(
                created.outgoingKeyId()
        ).isEqualTo(
                outgoing.keyId()
        );

        assertThat(
                created.replacementKeyId()
        ).isEqualTo(
                replacement.keyId()
        );
    }

    @Test
    void validTwoGenerationLineageVerifies() {
        Instant firstTime =
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                );

        Instant secondTime =
                Instant.parse(
                        "2030-02-01T00:00:00Z"
                );

        KeyPair replacementPair;
        KeyPair thirdPair;

        try {
            KeyPairGenerator generator =
                    KeyPairGenerator
                            .getInstance(
                                    "Ed25519"
                            );

            replacementPair =
                    generator.generateKeyPair();

            thirdPair =
                    generator.generateKeyPair();

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    exception
            );
        }

        String middlePrivate =
                Base64
                        .getEncoder()
                        .encodeToString(
                                replacementPair
                                        .getPrivate()
                                        .getEncoded()
                        );

        String middlePublic =
                Base64
                        .getEncoder()
                        .encodeToString(
                                replacementPair
                                        .getPublic()
                                        .getEncoded()
                        );

        String middleFingerprint =
                crypto.publicKeyFingerprint(
                        middlePublic
                );

        String thirdPublic =
                Base64
                        .getEncoder()
                        .encodeToString(
                                thirdPair
                                        .getPublic()
                                        .getEncoded()
                        );

        String thirdFingerprint =
                crypto.publicKeyFingerprint(
                        thirdPublic
                );

        GovernanceTrustAnchor outgoing =
                retiredAnchor(
                        "outgoing-key",
                        outgoingPublic,
                        outgoingFingerprint,
                        firstTime,
                        "replacement-key"
                );

        GovernanceTrustAnchor replacement =
                new GovernanceTrustAnchor(
                        "replacement-key",
                        "ED25519",
                        middlePublic,
                        middleFingerprint,
                        "RETIRED",

                        firstTime.minusSeconds(
                                100
                        ),

                        secondTime.minusSeconds(
                                1
                        ),

                        secondTime
                );

        GovernanceTrustAnchor third =
                activeAnchor(
                        "third-key",
                        thirdPublic,
                        thirdFingerprint,

                        secondTime.minusSeconds(
                                100
                        )
                );

        GovernanceTrustAnchorTransitionMaterial firstMaterial =
                new GovernanceTrustAnchorTransitionMaterial(
                        1,

                        outgoing.keyId(),
                        outgoing.publicKeyFingerprint(),

                        replacement.keyId(),
                        replacement.publicKeyFingerprint(),

                        "ED25519",

                        "First rotation.",

                        null
                );

        String firstSignature =
                crypto.sign(
                        outgoingPrivate,

                        hasher.signingPayload(
                                firstMaterial
                        )
                );

        String firstHash =
                hasher.transitionHash(
                        firstMaterial,
                        firstSignature
                );

        GovernanceTrustAnchorTransition first =
                transition(
                        firstMaterial,
                        firstSignature,
                        firstHash,
                        firstTime
                );

        GovernanceTrustAnchorTransitionMaterial secondMaterial =
                new GovernanceTrustAnchorTransitionMaterial(
                        2,

                        replacement.keyId(),
                        replacement.publicKeyFingerprint(),

                        third.keyId(),
                        third.publicKeyFingerprint(),

                        "ED25519",

                        "Second rotation.",

                        firstHash
                );

        String secondSignature =
                crypto.sign(
                        middlePrivate,

                        hasher.signingPayload(
                                secondMaterial
                        )
                );

        String secondHash =
                hasher.transitionHash(
                        secondMaterial,
                        secondSignature
                );

        GovernanceTrustAnchorTransition second =
                transition(
                        secondMaterial,
                        secondSignature,
                        secondHash,
                        secondTime
                );

        when(
                transitionRepository.findChain()
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        outgoing.keyId()
                )
        ).thenReturn(
                Optional.of(
                        outgoing
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        replacement.keyId()
                )
        ).thenReturn(
                Optional.of(
                        replacement
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        third.keyId()
                )
        ).thenReturn(
                Optional.of(
                        third
                )
        );

        when(
                transitionRepository.lifecycleMatches(
                        outgoing.keyId(),
                        replacement.keyId(),
                        first.reason(),
                        first.createdAt()
                )
        ).thenReturn(
                true
        );

        when(
                transitionRepository.lifecycleMatches(
                        replacement.keyId(),
                        third.keyId(),
                        second.reason(),
                        second.createdAt()
                )
        ).thenReturn(
                true
        );

        GovernanceTrustAnchorTransitionVerificationResponse result =
                service.verify();

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.transitionCount()
        ).isEqualTo(
                2
        );

        assertThat(
                result.verifiedTransitionCount()
        ).isEqualTo(
                2
        );
    }

    @Test
    void tamperedTransitionSignatureIsDetected() {
        Instant createdAt =
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                );

        GovernanceTrustAnchor outgoing =
                retiredAnchor(
                        "outgoing-key",
                        outgoingPublic,
                        outgoingFingerprint,
                        createdAt,
                        "replacement-key"
                );

        GovernanceTrustAnchor replacement =
                activeAnchor(
                        "replacement-key",
                        replacementPublic,
                        replacementFingerprint,
                        createdAt.minusSeconds(10)
                );

        GovernanceTrustAnchorTransitionMaterial material =
                new GovernanceTrustAnchorTransitionMaterial(
                        1,

                        outgoing.keyId(),
                        outgoing.publicKeyFingerprint(),

                        replacement.keyId(),
                        replacement.publicKeyFingerprint(),

                        "ED25519",

                        "Rotation.",

                        null
                );

        GovernanceTrustAnchorTransition transition =
                transition(
                        material,

                        Base64
                                .getEncoder()
                                .encodeToString(
                                        new byte[64]
                                ),

                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                        createdAt
                );

        when(
                transitionRepository.findChain()
        ).thenReturn(
                List.of(
                        transition
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        outgoing.keyId()
                )
        ).thenReturn(
                Optional.of(
                        outgoing
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        replacement.keyId()
                )
        ).thenReturn(
                Optional.of(
                        replacement
                )
        );

        when(
                transitionRepository.lifecycleMatches(
                        outgoing.keyId(),
                        replacement.keyId(),
                        transition.reason(),
                        transition.createdAt()
                )
        ).thenReturn(
                true
        );

        GovernanceTrustAnchorTransitionVerificationResponse result =
                service.verify();

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "signature verification failed"
        );
    }

    private CreateGovernanceTrustAnchorTransitionRequest request() {
        return new CreateGovernanceTrustAnchorTransitionRequest(
                "replacement-key",
                replacementPublic,
                "Scheduled cryptographic rotation."
        );
    }

    private GovernanceTrustAnchor activeAnchor(
            String keyId,
            String publicKey,
            String fingerprint,
            Instant createdAt
    ) {
        return new GovernanceTrustAnchor(
                keyId,
                "ED25519",
                publicKey,
                fingerprint,
                "ACTIVE",
                createdAt,
                createdAt,
                null
        );
    }

    private GovernanceTrustAnchor retiredAnchor(
            String keyId,
            String publicKey,
            String fingerprint,
            Instant retiredAt,
            String replacementKey
    ) {
        return new GovernanceTrustAnchor(
                keyId,
                "ED25519",
                publicKey,
                fingerprint,
                "RETIRED",
                retiredAt.minusSeconds(100),
                retiredAt.minusSeconds(1),
                retiredAt
        );
    }

    private GovernanceTrustAnchorTransition transition(
            GovernanceTrustAnchorTransitionMaterial material,
            String signature,
            String transitionHash,
            Instant createdAt
    ) {
        return new GovernanceTrustAnchorTransition(
                UUID.randomUUID(),

                material.transitionNumber(),

                material.outgoingKeyId(),
                material.outgoingPublicKeyFingerprint(),

                material.replacementKeyId(),
                material.replacementPublicKeyFingerprint(),

                material.algorithm(),

                material.reason(),

                GovernanceTrustAnchorTransitionHasher.TRANSITION_VERSION,

                signature,

                material.previousTransitionHash(),
                transitionHash,

                createdAt
        );
    }
}
