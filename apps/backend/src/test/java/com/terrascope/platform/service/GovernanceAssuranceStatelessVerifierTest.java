package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceEnvelopeVerificationResponse;
import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.GovernanceAssuranceBundlePayload;
import com.terrascope.platform.domain.GovernanceAssurancePortableEnvelope;
import com.terrascope.platform.domain.GovernanceClosureDossierMaterial;
import com.terrascope.platform.domain.GovernanceSignatureMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceAssuranceStatelessVerifierTest {

    private GovernanceClosureHasher closureHasher;

    private GovernanceEd25519Crypto crypto;

    private GovernanceSignaturePayloadBuilder signaturePayloadBuilder;

    private GovernanceAssuranceBundleCodec bundleCodec;

    private GovernanceAssuranceEnvelopeCodec envelopeCodec;

    private GovernanceAssuranceStatelessVerifier verifier;

    private String privateKey;
    private String publicKey;
    private String fingerprint;

    @BeforeEach
    void setUp() throws Exception {
        closureHasher =
                new GovernanceClosureHasher();

        crypto =
                new GovernanceEd25519Crypto();

        signaturePayloadBuilder =
                new GovernanceSignaturePayloadBuilder();

        GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy =
                new GovernanceTrustAnchorLifecyclePolicy();

        GovernanceAssurancePortableVerifier portableVerifier =
                new GovernanceAssurancePortableVerifier(
                        closureHasher,
                        crypto,
                        signaturePayloadBuilder,
                        lifecyclePolicy
                );

        bundleCodec =
                new GovernanceAssuranceBundleCodec();

        envelopeCodec =
                new GovernanceAssuranceEnvelopeCodec();

        verifier =
                new GovernanceAssuranceStatelessVerifier(
                        envelopeCodec,
                        bundleCodec,
                        portableVerifier
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
    }

    @Test
    void verifiesEnvelopeWithoutRepositoryState() {
        GovernanceAssurancePortableEnvelope envelope =
                envelope(
                        validPayload()
                );

        GovernanceAssuranceEnvelopeVerificationResponse result =
                verifier.verify(
                        envelopeCodec.encode(
                                envelope
                        )
                );

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.signatureCount()
        ).isEqualTo(1);

        assertThat(
                result.verifiedSignatureCount()
        ).isEqualTo(1);
    }

    @Test
    void verifiesHistoricalRetiredAnchorWithoutRepositoryState() {
        GovernanceAssuranceBundlePayload payload =
                validPayload(
                        "RETIRED",

                        Instant.parse(
                                "2030-01-02T00:00:00Z"
                        )
                );

        GovernanceAssuranceEnvelopeVerificationResponse result =
                verifier.verify(
                        envelopeCodec.encode(
                                envelope(
                                        payload
                                )
                        )
                );

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.retiredAnchorCount()
        ).isEqualTo(1);
    }

    @Test
    void detectsPayloadHashMismatch() {
        GovernanceAssurancePortableEnvelope valid =
                envelope(
                        validPayload()
                );

        GovernanceAssurancePortableEnvelope tampered =
                new GovernanceAssurancePortableEnvelope(
                        valid.envelopeVersion(),

                        valid.sourceBundleId(),
                        valid.sourceBundleNumber(),

                        valid.bundleVersion(),

                        valid.bundleHash(),

                        valid.payloadJson()
                                + " "
                );

        GovernanceAssuranceEnvelopeVerificationResponse result =
                verifier.verify(
                        envelopeCodec.encode(
                                tampered
                        )
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.hashValid()
        ).isFalse();
    }

    @Test
    void detectsSignatureTamperAfterBundleHashRecalculation() {
        GovernanceAssuranceBundlePayload valid =
                validPayload();

        GovernanceAssuranceBundlePayload.SignatureEvidence signature =
                valid.signatures()
                        .getFirst();

        GovernanceAssuranceBundlePayload tamperedPayload =
                new GovernanceAssuranceBundlePayload(
                        valid.bundleVersion(),

                        valid.certificate(),

                        List.of(
                                new GovernanceAssuranceBundlePayload.SignatureEvidence(
                                        signature.id(),

                                        signature.certificateId(),
                                        signature.certificateNumber(),

                                        signature.keyId(),
                                        signature.algorithm(),

                                        signature.publicKeyFingerprint(),

                                        signature.signatureVersion(),

                                        signature.certificateHash(),
                                        signature.dossierHash(),

                                        Base64
                                                .getEncoder()
                                                .encodeToString(
                                                        new byte[64]
                                                ),

                                        signature.createdAt()
                                )
                        ),

                        valid.trustAnchors(),
                        valid.lifecycleEvents()
                );

        GovernanceAssuranceEnvelopeVerificationResponse result =
                verifier.verify(
                        envelopeCodec.encode(
                                envelope(
                                        tamperedPayload
                                )
                        )
                );

        assertThat(
                result.hashValid()
        ).isTrue();

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.portableVerificationValid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "verification failed"
        );
    }

    private GovernanceAssurancePortableEnvelope envelope(
            GovernanceAssuranceBundlePayload payload
    ) {
        String payloadJson =
                bundleCodec.encode(
                        payload
                );

        return new GovernanceAssurancePortableEnvelope(
                GovernanceAssuranceEnvelopeCodec.ENVELOPE_VERSION,

                UUID.fromString(
                        "55555555-5555-5555-5555-555555555555"
                ),

                1,

                GovernanceAssuranceBundleCodec.BUNDLE_VERSION,

                bundleCodec.hash(
                        payloadJson
                ),

                payloadJson
        );
    }

    private GovernanceAssuranceBundlePayload validPayload() {
        return validPayload(
                "ACTIVE",
                null
        );
    }

    private GovernanceAssuranceBundlePayload validPayload(
            String anchorStatus,
            Instant retiredAt
    ) {
        UUID certificateId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        UUID benefitSnapshotId =
                UUID.fromString(
                        "22222222-2222-2222-2222-222222222222"
                );

        Instant certificateTime =
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                );

        Instant signatureTime =
                Instant.parse(
                        "2030-01-01T12:00:00Z"
                );

        GovernanceClosureDossierMaterial dossierMaterial =
                new GovernanceClosureDossierMaterial(
                        benefitSnapshotId,
                        1,

                        BenefitRealizationBand.REALIZED,
                        true,

                        1,
                        1,
                        1,
                        1,

                        100.0,
                        100.0,

                        50.0,
                        0.0
                );

        String dossierHash =
                closureHasher.dossierHash(
                        dossierMaterial
                );

        String certificateHash =
                closureHasher.certificateHash(
                        1,
                        dossierHash,
                        null
                );

        GovernanceSignatureMaterial signatureMaterial =
                new GovernanceSignatureMaterial(
                        certificateId,
                        1,

                        certificateHash,
                        dossierHash,

                        "portable-key",
                        "ED25519",
                        fingerprint
                );

        String signature =
                crypto.sign(
                        privateKey,

                        signaturePayloadBuilder.payload(
                                signatureMaterial
                        )
                );

        return new GovernanceAssuranceBundlePayload(
                GovernanceAssuranceBundleCodec.BUNDLE_VERSION,

                new GovernanceAssuranceBundlePayload.CertificateEvidence(
                        certificateId,
                        1,

                        benefitSnapshotId,
                        1,

                        "REALIZED",
                        true,

                        1,
                        1,
                        1,
                        1,

                        100.0,
                        100.0,

                        50.0,
                        0.0,

                        null,

                        GovernanceClosureHasher.DOSSIER_VERSION,
                        dossierHash,

                        GovernanceClosureHasher.CERTIFICATE_VERSION,
                        certificateHash,

                        "Portable enterprise closure.",

                        certificateTime
                ),

                List.of(
                        new GovernanceAssuranceBundlePayload.SignatureEvidence(
                                UUID.fromString(
                                        "33333333-3333-3333-3333-333333333333"
                                ),

                                certificateId,
                                1,

                                "portable-key",
                                "ED25519",

                                fingerprint,

                                GovernanceSignaturePayloadBuilder.SIGNATURE_VERSION,

                                certificateHash,
                                dossierHash,

                                signature,

                                signatureTime
                        )
                ),

                List.of(
                        new GovernanceAssuranceBundlePayload.TrustAnchorEvidence(
                                "portable-key",

                                "ED25519",

                                publicKey,
                                fingerprint,

                                anchorStatus,

                                "RETIRED"
                                        .equals(
                                                anchorStatus
                                        )
                                        ? "replacement-key"
                                        : null,

                                "RETIRED"
                                        .equals(
                                                anchorStatus
                                        )
                                        ? "Scheduled rotation."
                                        : "",

                                Instant.parse(
                                        "2029-12-01T00:00:00Z"
                                ),

                                signatureTime,

                                retiredAt
                        )
                ),

                List.of()
        );
    }
}
