package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceClosureSignature;
import com.terrascope.platform.api.model.GovernanceClosureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSignatureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSigningReadinessResponse;
import com.terrascope.platform.domain.GovernanceSignatureMaterial;
import com.terrascope.platform.domain.GovernanceSignatureTarget;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceClosureSignatureServiceTest {

    private GovernanceClosureSignatureRepository repository;

    private GovernanceClosureCertificateService closureService;

    private GovernanceSigningConfiguration configuration;

    private GovernanceEd25519Crypto crypto;

    private GovernanceSignaturePayloadBuilder payloadBuilder;

    private GovernanceClosureSignatureService service;

    private String privateKey;
    private String publicKey;
    private String fingerprint;

    private GovernanceSignatureTarget target;

    @BeforeEach
    void setUp() throws Exception {
        repository =
                mock(
                        GovernanceClosureSignatureRepository.class
                );

        closureService =
                mock(
                        GovernanceClosureCertificateService.class
                );

        configuration =
                mock(
                        GovernanceSigningConfiguration.class
                );

        crypto =
                new GovernanceEd25519Crypto();

        payloadBuilder =
                new GovernanceSignaturePayloadBuilder();

        service =
                new GovernanceClosureSignatureService(
                        repository,
                        closureService,
                        configuration,
                        crypto,
                        payloadBuilder
                );

        KeyPair pair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        privateKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPrivate()
                                        .getEncoded()
                        );

        publicKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPublic()
                                        .getEncoded()
                        );

        fingerprint =
                crypto.publicKeyFingerprint(
                        publicKey
                );

        target =
                new GovernanceSignatureTarget(
                        UUID.randomUUID(),
                        1,

                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                );
    }

    @Test
    void missingLocalKeyIsNotReady() {
        when(
                configuration.configured()
        ).thenReturn(
                false
        );

        GovernanceSigningReadinessResponse readiness =
                service.readiness();

        assertThat(
                readiness.ready()
        ).isFalse();

        assertThat(
                readiness.trustAnchorRegistered()
        ).isFalse();
    }

    @Test
    void signingRegistersTrustAnchorAndPersistsSignature() {
        configureSigningKey();

        whenValidClosureChain();

        when(
                repository.findCertificateTarget(
                        target.certificateId()
                )
        ).thenReturn(
                Optional.of(
                        target
                )
        );

        when(
                repository.signatureExistsForCertificate(
                        target.certificateId()
                )
        ).thenReturn(
                false
        );

        when(
                repository.findTrustAnchor(
                        "test-key"
                )
        ).thenReturn(
                Optional.empty()
        );

        GovernanceTrustAnchor anchor =
                anchor();

        when(
                repository.registerTrustAnchor(
                        "test-key",
                        publicKey,
                        fingerprint
                )
        ).thenReturn(
                anchor
        );

        GovernanceSignatureMaterial material =
                new GovernanceSignatureMaterial(
                        target.certificateId(),
                        target.certificateNumber(),

                        target.certificateHash(),
                        target.dossierHash(),

                        anchor.keyId(),
                        anchor.algorithm(),
                        anchor.publicKeyFingerprint()
                );

        String signature =
                crypto.sign(
                        privateKey,
                        payloadBuilder.payload(
                                material
                        )
                );

        GovernanceClosureSignature stored =
                storedSignature(
                        signature
                );

        when(
                repository.saveSignature(
                        any(),
                        eq(target),
                        eq(anchor),
                        eq(
                                GovernanceSignaturePayloadBuilder.SIGNATURE_VERSION
                        ),
                        any()
                )
        ).thenReturn(
                stored
        );

        GovernanceClosureSignature result =
                service.sign(
                        target.certificateId()
                );

        assertThat(
                result.certificateId()
        ).isEqualTo(
                target.certificateId()
        );

        verify(
                repository
        ).registerTrustAnchor(
                "test-key",
                publicKey,
                fingerprint
        );

        verify(
                repository
        ).touchTrustAnchor(
                "test-key"
        );
    }

    @Test
    void duplicateCertificateSignatureIsRejected() {
        configureSigningKey();

        whenValidClosureChain();

        when(
                repository.findCertificateTarget(
                        target.certificateId()
                )
        ).thenReturn(
                Optional.of(
                        target
                )
        );

        when(
                repository.signatureExistsForCertificate(
                        target.certificateId()
                )
        ).thenReturn(
                true
        );

        assertThatThrownBy(() ->
                service.sign(
                        target.certificateId()
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void verificationDetectsValidAndTamperedSignatures() {
        configureSigningKey();

        whenValidClosureChain();

        GovernanceTrustAnchor anchor =
                anchor();

        GovernanceSignatureMaterial material =
                new GovernanceSignatureMaterial(
                        target.certificateId(),
                        target.certificateNumber(),

                        target.certificateHash(),
                        target.dossierHash(),

                        anchor.keyId(),
                        anchor.algorithm(),
                        anchor.publicKeyFingerprint()
                );

        String validSignature =
                crypto.sign(
                        privateKey,
                        payloadBuilder.payload(
                                material
                        )
                );

        GovernanceClosureSignature valid =
                storedSignature(
                        validSignature
                );

        when(
                repository.findAllAscending()
        ).thenReturn(
                List.of(
                        valid
                )
        );

        when(
                repository.findCertificateTarget(
                        target.certificateId()
                )
        ).thenReturn(
                Optional.of(
                        target
                )
        );

        when(
                repository.findTrustAnchor(
                        "test-key"
                )
        ).thenReturn(
                Optional.of(
                        anchor
                )
        );

        GovernanceSignatureVerificationResponse verified =
                service.verify();

        assertThat(
                verified.valid()
        ).isTrue();

        GovernanceClosureSignature tampered =
                storedSignature(
                        Base64
                                .getEncoder()
                                .encodeToString(
                                        new byte[64]
                                )
                );

        when(
                repository.findAllAscending()
        ).thenReturn(
                List.of(
                        tampered
                )
        );

        GovernanceSignatureVerificationResponse broken =
                service.verify();

        assertThat(
                broken.valid()
        ).isFalse();

        assertThat(
                broken.brokenCertificateNumber()
        ).isEqualTo(1);

        assertThat(
                broken.verifiedSignatureCount()
        ).isZero();
    }

    private void configureSigningKey() {
        when(
                configuration.configured()
        ).thenReturn(
                true
        );

        when(
                configuration.keyId()
        ).thenReturn(
                "test-key"
        );

        when(
                configuration.privateKeyPkcs8Base64()
        ).thenReturn(
                privateKey
        );

        when(
                configuration.publicKeyX509Base64()
        ).thenReturn(
                publicKey
        );
    }

    private void whenValidClosureChain() {
        when(
                closureService.verify()
        ).thenReturn(
                new GovernanceClosureVerificationResponse(
                        true,

                        1,
                        1,

                        null,
                        null,

                        "Valid.",

                        Instant.now()
                )
        );
    }

    private GovernanceTrustAnchor anchor() {
        Instant now =
                Instant.now();

        return new GovernanceTrustAnchor(
                "test-key",
                GovernanceEd25519Crypto.ALGORITHM,

                publicKey,
                fingerprint,

                "ACTIVE",

                now,
                now,
                null
        );
    }

    private GovernanceClosureSignature storedSignature(
            String signature
    ) {
        return new GovernanceClosureSignature(
                UUID.randomUUID(),

                target.certificateId(),
                target.certificateNumber(),

                "test-key",
                GovernanceEd25519Crypto.ALGORITHM,

                fingerprint,

                GovernanceSignaturePayloadBuilder.SIGNATURE_VERSION,

                target.certificateHash(),
                target.dossierHash(),

                signature,

                Instant.now()
        );
    }
}
