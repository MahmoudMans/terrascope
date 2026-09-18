package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceClosureSignature;
import com.terrascope.platform.api.model.GovernanceClosureSignatureHistoryResponse;
import com.terrascope.platform.api.model.GovernanceClosureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSignatureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSigningReadinessResponse;
import com.terrascope.platform.domain.GovernanceSignatureMaterial;
import com.terrascope.platform.domain.GovernanceSignatureTarget;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GovernanceClosureSignatureService {

    private final GovernanceClosureSignatureRepository repository;

    private final GovernanceClosureCertificateService closureService;

    private final GovernanceSigningConfiguration configuration;

    private final GovernanceEd25519Crypto crypto;

    private final GovernanceSignaturePayloadBuilder payloadBuilder;

    public GovernanceClosureSignatureService(
            GovernanceClosureSignatureRepository repository,
            GovernanceClosureCertificateService closureService,
            GovernanceSigningConfiguration configuration,
            GovernanceEd25519Crypto crypto,
            GovernanceSignaturePayloadBuilder payloadBuilder
    ) {
        this.repository = repository;
        this.closureService = closureService;
        this.configuration = configuration;
        this.crypto = crypto;
        this.payloadBuilder = payloadBuilder;
    }

    public GovernanceSigningReadinessResponse readiness() {
        if (
                !configuration.configured()
        ) {
            return new GovernanceSigningReadinessResponse(
                    false,

                    GovernanceEd25519Crypto.ALGORITHM,

                    null,
                    null,

                    false,

                    "Local Ed25519 signing key is not configured.",

                    Instant.now()
            );
        }

        String keyId =
                configuration.keyId();

        String fingerprint;

        try {
            fingerprint =
                    crypto.publicKeyFingerprint(
                            configuration.publicKeyX509Base64()
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            return new GovernanceSigningReadinessResponse(
                    false,

                    GovernanceEd25519Crypto.ALGORITHM,

                    keyId,
                    null,

                    false,

                    "Configured Ed25519 public key is invalid.",

                    Instant.now()
            );
        }

        GovernanceTrustAnchor existing =
                repository
                        .findTrustAnchor(
                                keyId
                        )
                        .orElse(
                                null
                        );

        if (
                existing != null
                && (
                    !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    existing.algorithm()
                            )
                    || !"ACTIVE"
                            .equals(
                                    existing.status()
                            )
                    || !fingerprint
                            .equals(
                                    existing.publicKeyFingerprint()
                            )
                    || !configuration
                            .publicKeyX509Base64()
                            .equals(
                                    existing.publicKeyX509Base64()
                            )
                )
        ) {
            return new GovernanceSigningReadinessResponse(
                    false,

                    GovernanceEd25519Crypto.ALGORITHM,

                    keyId,
                    fingerprint,

                    true,

                    "Configured key conflicts with the registered governance trust anchor.",

                    Instant.now()
            );
        }

        return new GovernanceSigningReadinessResponse(
                true,

                GovernanceEd25519Crypto.ALGORITHM,

                keyId,
                fingerprint,

                existing != null,

                existing == null
                        ? "Ed25519 signing key is ready. Trust anchor will be registered on first signature."
                        : "Ed25519 signing key matches the registered governance trust anchor.",

                Instant.now()
        );
    }

    public GovernanceClosureSignatureHistoryResponse history() {
        return new GovernanceClosureSignatureHistoryResponse(
                repository.findHistory()
        );
    }

    @Transactional
    public GovernanceClosureSignature sign(
            UUID certificateId
    ) {
        GovernanceSigningReadinessResponse readiness =
                readiness();

        if (
                !readiness.ready()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    readiness.message()
            );
        }

        GovernanceClosureVerificationResponse closureVerification =
                closureService.verify();

        if (
                !closureVerification.valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance closure certificate chain must verify before signing."
            );
        }

        GovernanceSignatureTarget target =
                repository
                        .findCertificateTarget(
                                certificateId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Governance closure certificate was not found."
                                )
                        );

        if (
                repository.signatureExistsForCertificate(
                        certificateId
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance closure certificate already has an Ed25519 signature."
            );
        }

        GovernanceTrustAnchor anchor =
                repository
                        .findTrustAnchor(
                                readiness.keyId()
                        )
                        .orElseGet(() ->
                                repository.registerTrustAnchor(
                                        readiness.keyId(),
                                        configuration.publicKeyX509Base64(),
                                        readiness.publicKeyFingerprint()
                                )
                        );

        requireCompatibleAnchor(
                anchor,
                readiness
        );

        GovernanceSignatureMaterial material =
                material(
                        target,
                        anchor
                );

        byte[] payload =
                payloadBuilder.payload(
                        material
                );

        String signature =
                crypto.sign(
                        configuration.privateKeyPkcs8Base64(),
                        payload
                );

        if (
                !crypto.verify(
                        anchor.publicKeyX509Base64(),
                        payload,
                        signature
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configured Ed25519 private and public keys do not form a valid signing pair."
            );
        }

        GovernanceClosureSignature saved =
                repository.saveSignature(
                        UUID.randomUUID(),

                        target,
                        anchor,

                        GovernanceSignaturePayloadBuilder.SIGNATURE_VERSION,

                        signature
                );

        repository.touchTrustAnchor(
                anchor.keyId()
        );

        return saved;
    }

    public GovernanceSignatureVerificationResponse verify() {
        GovernanceClosureVerificationResponse closureVerification =
                closureService.verify();

        List<GovernanceClosureSignature> signatures =
                repository.findAllAscending();

        if (
                !closureVerification.valid()
        ) {
            return new GovernanceSignatureVerificationResponse(
                    false,

                    signatures.size(),
                    0,

                    null,
                    closureVerification.brokenCertificateId(),
                    closureVerification.brokenCertificateNumber(),

                    "Underlying governance closure certificate chain is invalid: "
                            + closureVerification.message(),

                    Instant.now()
            );
        }

        int verifiedCount =
                0;

        for (
                GovernanceClosureSignature signature
                : signatures
        ) {
            GovernanceSignatureTarget target =
                    repository
                            .findCertificateTarget(
                                    signature.certificateId()
                            )
                            .orElse(
                                    null
                            );

            if (
                    target == null
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Signed governance closure certificate no longer exists."
                );
            }

            if (
                    signature.certificateNumber()
                    != target.certificateNumber()

                    || !signature.certificateHash()
                            .equals(
                                    target.certificateHash()
                            )

                    || !signature.dossierHash()
                            .equals(
                                    target.dossierHash()
                            )
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Stored Ed25519 signature anchor does not match its governance closure certificate."
                );
            }

            GovernanceTrustAnchor anchor =
                    repository
                            .findTrustAnchor(
                                    signature.keyId()
                            )
                            .orElse(
                                    null
                            );

            if (
                    anchor == null
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Governance trust anchor is missing."
                );
            }

            if (
                    !"ACTIVE"
                            .equals(
                                    anchor.status()
                            )

                    || !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    anchor.algorithm()
                            )

                    || !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    signature.algorithm()
                            )

                    || !Objects.equals(
                            anchor.publicKeyFingerprint(),
                            signature.publicKeyFingerprint()
                    )
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Governance trust anchor metadata does not match the stored signature."
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
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Governance trust anchor public key is invalid."
                );
            }

            if (
                    !calculatedFingerprint.equals(
                            signature.publicKeyFingerprint()
                    )
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Governance trust anchor fingerprint does not match its public key."
                );
            }

            if (
                    !GovernanceSignaturePayloadBuilder.SIGNATURE_VERSION
                            .equals(
                                    signature.signatureVersion()
                            )
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Unsupported governance Ed25519 signature version."
                );
            }

            GovernanceSignatureMaterial material =
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
                            anchor.publicKeyX509Base64(),

                            payloadBuilder.payload(
                                    material
                            ),

                            signature.signatureBase64()
                    );

            if (
                    !verified
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Ed25519 governance signature verification failed."
                );
            }

            verifiedCount++;
        }

        return new GovernanceSignatureVerificationResponse(
                true,

                signatures.size(),
                verifiedCount,

                null,
                null,
                null,

                signatures.isEmpty()
                        ? "No Ed25519 governance signatures have been issued."
                        : "All Ed25519 governance signatures verified against registered trust anchors.",

                Instant.now()
        );
    }

    private GovernanceSignatureMaterial material(
            GovernanceSignatureTarget target,
            GovernanceTrustAnchor anchor
    ) {
        return new GovernanceSignatureMaterial(
                target.certificateId(),
                target.certificateNumber(),

                target.certificateHash(),
                target.dossierHash(),

                anchor.keyId(),
                anchor.algorithm(),
                anchor.publicKeyFingerprint()
        );
    }

    private void requireCompatibleAnchor(
            GovernanceTrustAnchor anchor,
            GovernanceSigningReadinessResponse readiness
    ) {
        if (
                !GovernanceEd25519Crypto.ALGORITHM
                        .equals(
                                anchor.algorithm()
                        )

                || !"ACTIVE"
                        .equals(
                                anchor.status()
                        )

                || !readiness
                        .publicKeyFingerprint()
                        .equals(
                                anchor.publicKeyFingerprint()
                        )

                || !configuration
                        .publicKeyX509Base64()
                        .equals(
                                anchor.publicKeyX509Base64()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configured signing key does not match the registered governance trust anchor."
            );
        }
    }

    private GovernanceSignatureVerificationResponse invalid(
            int signatureCount,
            int verifiedCount,
            GovernanceClosureSignature signature,
            String message
    ) {
        return new GovernanceSignatureVerificationResponse(
                false,

                signatureCount,
                verifiedCount,

                signature.id(),
                signature.certificateId(),
                signature.certificateNumber(),

                message,

                Instant.now()
        );
    }
}
