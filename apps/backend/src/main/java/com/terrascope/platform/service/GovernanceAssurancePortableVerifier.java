package com.terrascope.platform.service;

import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.GovernanceAssuranceBundlePayload;
import com.terrascope.platform.domain.GovernanceClosureDossierMaterial;
import com.terrascope.platform.domain.GovernanceSignatureMaterial;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class GovernanceAssurancePortableVerifier {

    private final GovernanceClosureHasher closureHasher;

    private final GovernanceEd25519Crypto crypto;

    private final GovernanceSignaturePayloadBuilder signaturePayloadBuilder;

    private final GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy;

    public GovernanceAssurancePortableVerifier(
            GovernanceClosureHasher closureHasher,
            GovernanceEd25519Crypto crypto,
            GovernanceSignaturePayloadBuilder signaturePayloadBuilder,
            GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy
    ) {
        this.closureHasher = closureHasher;
        this.crypto = crypto;
        this.signaturePayloadBuilder = signaturePayloadBuilder;
        this.lifecyclePolicy = lifecyclePolicy;
    }

    public Result verify(
            GovernanceAssuranceBundlePayload payload
    ) {
        if (
                payload == null
        ) {
            return invalid(
                    0,
                    0,
                    "Portable governance assurance bundle is missing."
            );
        }

        if (
                !GovernanceAssuranceBundleCodec.BUNDLE_VERSION
                        .equals(
                                payload.bundleVersion()
                        )
        ) {
            return invalid(
                    payload.signatures().size(),
                    0,
                    "Unsupported governance assurance bundle version."
            );
        }

        GovernanceAssuranceBundlePayload.CertificateEvidence certificate =
                payload.certificate();

        if (
                certificate == null
        ) {
            return invalid(
                    payload.signatures().size(),
                    0,
                    "Portable bundle does not contain closure certificate evidence."
            );
        }

        if (
                !GovernanceClosureHasher.DOSSIER_VERSION
                        .equals(
                                certificate.dossierVersion()
                        )

                || !GovernanceClosureHasher.CERTIFICATE_VERSION
                        .equals(
                                certificate.certificateVersion()
                        )
        ) {
            return invalid(
                    payload.signatures().size(),
                    0,
                    "Unsupported closure certificate evidence version."
            );
        }

        BenefitRealizationBand band;

        try {
            band =
                    BenefitRealizationBand.valueOf(
                            certificate.band()
                    );

        } catch (
                RuntimeException exception
        ) {
            return invalid(
                    payload.signatures().size(),
                    0,
                    "Portable certificate contains an invalid realization band."
            );
        }

        GovernanceClosureDossierMaterial dossierMaterial =
                new GovernanceClosureDossierMaterial(
                        certificate.benefitSnapshotId(),
                        certificate.benefitSnapshotNumber(),

                        band,
                        certificate.closureReady(),

                        certificate.totalObjectiveCount(),
                        certificate.completedObjectiveCount(),
                        certificate.validatedObjectiveCount(),
                        certificate.achievedCount(),

                        certificate.validationCoveragePct(),
                        certificate.realizedBenefitRatePct(),

                        certificate.averageScoreDelta(),
                        certificate.averageAssuranceDebtDelta()
                );

        String expectedDossierHash =
                closureHasher.dossierHash(
                        dossierMaterial
                );

        if (
                !expectedDossierHash.equals(
                        certificate.dossierHash()
                )
        ) {
            return invalid(
                    payload.signatures().size(),
                    0,
                    "Portable closure dossier hash is invalid."
            );
        }

        String expectedCertificateHash =
                closureHasher.certificateHash(
                        certificate.certificateNumber(),
                        certificate.dossierHash(),
                        certificate.previousCertificateHash()
                );

        if (
                !expectedCertificateHash.equals(
                        certificate.certificateHash()
                )
        ) {
            return invalid(
                    payload.signatures().size(),
                    0,
                    "Portable closure certificate hash is invalid."
            );
        }

        if (
                payload.signatures().isEmpty()
        ) {
            return invalid(
                    0,
                    0,
                    "Portable bundle contains no detached Ed25519 signatures."
            );
        }

        Map<String, GovernanceAssuranceBundlePayload.TrustAnchorEvidence> anchors =
                new HashMap<>();

        for (
                GovernanceAssuranceBundlePayload.TrustAnchorEvidence anchor
                : payload.trustAnchors()
        ) {
            if (
                    anchors.put(
                            anchor.keyId(),
                            anchor
                    )
                    != null
            ) {
                return invalid(
                        payload.signatures().size(),
                        0,
                        "Portable bundle contains duplicate trust-anchor key IDs."
                );
            }
        }

        int verifiedCount =
                0;

        for (
                GovernanceAssuranceBundlePayload.SignatureEvidence signature
                : payload.signatures()
        ) {
            if (
                    !Objects.equals(
                            signature.certificateId(),
                            certificate.id()
                    )

                    || signature.certificateNumber()
                       != certificate.certificateNumber()

                    || !Objects.equals(
                            signature.certificateHash(),
                            certificate.certificateHash()
                    )

                    || !Objects.equals(
                            signature.dossierHash(),
                            certificate.dossierHash()
                    )
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Detached signature is not anchored to the bundled closure certificate."
                );
            }

            GovernanceAssuranceBundlePayload.TrustAnchorEvidence anchorEvidence =
                    anchors.get(
                            signature.keyId()
                    );

            if (
                    anchorEvidence == null
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Detached signature references a missing portable trust anchor."
                );
            }

            if (
                    !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    signature.algorithm()
                            )

                    || !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    anchorEvidence.algorithm()
                            )

                    || !Objects.equals(
                            signature.publicKeyFingerprint(),
                            anchorEvidence.publicKeyFingerprint()
                    )
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Detached signature trust-anchor metadata does not match."
                );
            }

            String calculatedFingerprint;

            try {
                calculatedFingerprint =
                        crypto.publicKeyFingerprint(
                                anchorEvidence.publicKeyX509Base64()
                        );

            } catch (
                    IllegalArgumentException exception
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Portable trust anchor contains an invalid Ed25519 public key."
                );
            }

            if (
                    !calculatedFingerprint.equals(
                            anchorEvidence.publicKeyFingerprint()
                    )
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Portable trust-anchor fingerprint does not match its public key."
                );
            }

            GovernanceTrustAnchor lifecycleAnchor =
                    new GovernanceTrustAnchor(
                            anchorEvidence.keyId(),

                            anchorEvidence.algorithm(),

                            anchorEvidence.publicKeyX509Base64(),
                            anchorEvidence.publicKeyFingerprint(),

                            anchorEvidence.status(),

                            anchorEvidence.createdAt(),
                            anchorEvidence.lastUsedAt(),
                            anchorEvidence.retiredAt()
                    );

            if (
                    !lifecyclePolicy
                            .canVerifyHistoricalSignature(
                                    lifecycleAnchor,
                                    signature.createdAt()
                            )
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Portable signature falls outside its trust-anchor validity period."
                );
            }

            if (
                    !GovernanceSignaturePayloadBuilder.SIGNATURE_VERSION
                            .equals(
                                    signature.signatureVersion()
                            )
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Unsupported detached Ed25519 signature version."
                );
            }

            GovernanceSignatureMaterial signatureMaterial =
                    new GovernanceSignatureMaterial(
                            signature.certificateId(),
                            signature.certificateNumber(),

                            signature.certificateHash(),
                            signature.dossierHash(),

                            signature.keyId(),
                            signature.algorithm(),
                            signature.publicKeyFingerprint()
                    );

            boolean verified =
                    crypto.verify(
                            anchorEvidence.publicKeyX509Base64(),

                            signaturePayloadBuilder.payload(
                                    signatureMaterial
                            ),

                            signature.signatureBase64()
                    );

            if (
                    !verified
            ) {
                return invalid(
                        payload.signatures().size(),
                        verifiedCount,
                        "Portable Ed25519 governance signature verification failed."
                );
            }

            verifiedCount++;
        }

        return new Result(
                true,

                payload.signatures().size(),
                verifiedCount,

                "Portable governance assurance bundle verified independently."
        );
    }

    private Result invalid(
            int signatureCount,
            int verifiedSignatureCount,
            String message
    ) {
        return new Result(
                false,

                signatureCount,
                verifiedSignatureCount,

                message
        );
    }

    public record Result(
            boolean valid,

            int signatureCount,
            int verifiedSignatureCount,

            String message
    ) {
    }
}
