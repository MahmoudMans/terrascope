package com.terrascope.platform.service;

import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.GovernanceAssuranceBundlePayload;
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

class GovernanceAssurancePortableVerifierTest {

    private GovernanceClosureHasher closureHasher;
    private GovernanceEd25519Crypto crypto;
    private GovernanceSignaturePayloadBuilder signaturePayloadBuilder;
    private GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy;

    private GovernanceAssurancePortableVerifier verifier;

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

        lifecyclePolicy =
                new GovernanceTrustAnchorLifecyclePolicy();

        verifier =
                new GovernanceAssurancePortableVerifier(
                        closureHasher,
                        crypto,
                        signaturePayloadBuilder,
                        lifecyclePolicy
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
    void verifiesPortableBundleWithActiveAnchor() {
        GovernanceAssurancePortableVerifier.Result result =
                verifier.verify(
                        payload(
                                "ACTIVE",
                                null,
                                false
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
    void verifiesHistoricalSignatureCreatedBeforeAnchorRetirement() {
        GovernanceAssurancePortableVerifier.Result result =
                verifier.verify(
                        payload(
                                "RETIRED",
                                Instant.parse(
                                        "2030-01-02T00:00:00Z"
                                ),
                                false
                        )
                );

        assertThat(
                result.valid()
        ).isTrue();
    }

    @Test
    void detectsTamperedDetachedSignature() {
        GovernanceAssurancePortableVerifier.Result result =
                verifier.verify(
                        payload(
                                "ACTIVE",
                                null,
                                true
                        )
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "verification failed"
        );
    }

    @Test
    void detectsTamperedClosureCertificateHash() {
        GovernanceAssuranceBundlePayload valid =
                payload(
                        "ACTIVE",
                        null,
                        false
                );

        GovernanceAssuranceBundlePayload.CertificateEvidence certificate =
                valid.certificate();

        GovernanceAssuranceBundlePayload tampered =
                new GovernanceAssuranceBundlePayload(
                        valid.bundleVersion(),

                        new GovernanceAssuranceBundlePayload.CertificateEvidence(
                                certificate.id(),
                                certificate.certificateNumber(),

                                certificate.benefitSnapshotId(),
                                certificate.benefitSnapshotNumber(),

                                certificate.band(),
                                certificate.closureReady(),

                                certificate.totalObjectiveCount(),
                                certificate.completedObjectiveCount(),
                                certificate.validatedObjectiveCount(),
                                certificate.achievedCount(),

                                certificate.validationCoveragePct(),
                                certificate.realizedBenefitRatePct(),

                                certificate.averageScoreDelta(),
                                certificate.averageAssuranceDebtDelta(),

                                certificate.previousCertificateHash(),

                                certificate.dossierVersion(),
                                certificate.dossierHash(),

                                certificate.certificateVersion(),

                                "0000000000000000000000000000000000000000000000000000000000000000",

                                certificate.executiveSummary(),
                                certificate.createdAt()
                        ),

                        valid.signatures(),
                        valid.trustAnchors(),
                        valid.lifecycleEvents()
                );

        GovernanceAssurancePortableVerifier.Result result =
                verifier.verify(
                        tampered
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "certificate hash"
        );
    }

    private GovernanceAssuranceBundlePayload payload(
            String status,
            Instant retiredAt,
            boolean tamperSignature
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

                        "test-key",
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

        if (
                tamperSignature
        ) {
            signature =
                    Base64
                            .getEncoder()
                            .encodeToString(
                                    new byte[64]
                            );
        }

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

                        "Enterprise closure.",

                        certificateTime
                ),

                List.of(
                        new GovernanceAssuranceBundlePayload.SignatureEvidence(
                                UUID.fromString(
                                        "33333333-3333-3333-3333-333333333333"
                                ),

                                certificateId,
                                1,

                                "test-key",
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
                                "test-key",

                                "ED25519",

                                publicKey,
                                fingerprint,

                                status,

                                "RETIRED"
                                        .equals(
                                                status
                                        )
                                        ? "replacement-key"
                                        : null,

                                "RETIRED"
                                        .equals(
                                                status
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
