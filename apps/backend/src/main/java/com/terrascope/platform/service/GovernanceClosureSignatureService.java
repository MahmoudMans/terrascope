package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceClosureSignature;
import com.terrascope.platform.api.model.GovernanceClosureSignatureHistoryResponse;
import com.terrascope.platform.api.model.GovernanceClosureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSignatureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSigningReadinessResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorView;
import com.terrascope.platform.api.model.RegisterGovernanceTrustAnchorRequest;
import com.terrascope.platform.api.model.RetireGovernanceTrustAnchorRequest;
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

    private final GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy;

    public GovernanceClosureSignatureService(
            GovernanceClosureSignatureRepository repository,
            GovernanceClosureCertificateService closureService,
            GovernanceSigningConfiguration configuration,
            GovernanceEd25519Crypto crypto,
            GovernanceSignaturePayloadBuilder payloadBuilder,
            GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy
    ) {
        this.repository = repository;
        this.closureService = closureService;
        this.configuration = configuration;
        this.crypto = crypto;
        this.payloadBuilder = payloadBuilder;
        this.lifecyclePolicy = lifecyclePolicy;
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
        ) {
            if (
                    !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    existing.algorithm()
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

            if (
                    !lifecyclePolicy.canSign(
                            existing
                    )
            ) {
                return new GovernanceSigningReadinessResponse(
                        false,

                        GovernanceEd25519Crypto.ALGORITHM,

                        keyId,
                        fingerprint,

                        true,

                        "Configured governance signing key is retired and cannot issue new signatures.",

                        Instant.now()
                );
            }
        }

        return new GovernanceSigningReadinessResponse(
                true,

                GovernanceEd25519Crypto.ALGORITHM,

                keyId,
                fingerprint,

                existing != null,

                existing == null
                        ? "Ed25519 signing key is ready. Trust anchor will be registered on first signature."
                        : "Ed25519 signing key matches the active governance trust anchor.",

                Instant.now()
        );
    }

    public GovernanceClosureSignatureHistoryResponse history() {
        return new GovernanceClosureSignatureHistoryResponse(
                repository.findHistory()
        );
    }

    public GovernanceTrustAnchorHistoryResponse trustAnchorHistory() {
        return new GovernanceTrustAnchorHistoryResponse(
                repository.findTrustAnchorViews(),
                repository.findTrustAnchorEvents()
        );
    }

    @Transactional
    public GovernanceTrustAnchorView registerTrustAnchor(
            RegisterGovernanceTrustAnchorRequest request
    ) {
        String keyId =
                request.keyId()
                        .trim();

        if (
                repository
                        .findTrustAnchor(
                                keyId
                        )
                        .isPresent()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance trust anchor key ID already exists."
            );
        }

        String publicKey =
                request.publicKeyX509Base64()
                        .trim();

        String fingerprint;

        try {
            fingerprint =
                    crypto.publicKeyFingerprint(
                            publicKey
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Public key is not a valid Ed25519 X.509 key."
            );
        }

        GovernanceTrustAnchor anchor =
                repository.registerTrustAnchor(
                        keyId,
                        publicKey,
                        fingerprint
                );

        repository.appendTrustAnchorEvent(
                UUID.randomUUID(),

                anchor.keyId(),

                "REGISTERED",

                null,

                "Ed25519 governance trust anchor registered with fingerprint "
                        + anchor.publicKeyFingerprint()
                        + "."
        );

        return repository.requireTrustAnchorView(
                anchor.keyId()
        );
    }

    @Transactional
    public GovernanceTrustAnchorView retireTrustAnchor(
            String keyId,
            RetireGovernanceTrustAnchorRequest request
    ) {
        GovernanceTrustAnchor current =
                repository
                        .findTrustAnchor(
                                keyId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Governance trust anchor was not found."
                                )
                        );

        if (
                !lifecyclePolicy.canSign(
                        current
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance trust anchor is already retired."
            );
        }

        String replacementKeyId =
                request.replacementKeyId()
                        .trim();

        if (
                current.keyId()
                        .equals(
                                replacementKeyId
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Replacement governance key must be different from the retired key."
            );
        }

        GovernanceTrustAnchor replacement =
                repository
                        .findTrustAnchor(
                                replacementKeyId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Replacement governance trust anchor does not exist."
                                )
                        );

        if (
                !lifecyclePolicy.canSign(
                        replacement
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Replacement governance trust anchor must be ACTIVE."
            );
        }

        String reason =
                request.reason()
                        .trim();

        GovernanceTrustAnchorView retired =
                repository.retireTrustAnchor(
                        current.keyId(),
                        replacement.keyId(),
                        reason
                );

        repository.appendTrustAnchorEvent(
                UUID.randomUUID(),

                current.keyId(),

                "RETIRED",

                replacement.keyId(),

                "Governance trust anchor retired. Replacement key: "
                        + replacement.keyId()
                        + ". Reason: "
                        + reason
        );

        return retired;
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
                repository
                        .signatureExistsForCertificateAndKey(
                                certificateId,
                                readiness.keyId()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance closure certificate already has a signature from this trust anchor."
            );
        }

        GovernanceTrustAnchor anchor =
                repository
                        .findTrustAnchor(
                                readiness.keyId()
                        )
                        .orElseGet(() -> {
                            GovernanceTrustAnchor registered =
                                    repository.registerTrustAnchor(
                                            readiness.keyId(),
                                            configuration.publicKeyX509Base64(),
                                            readiness.publicKeyFingerprint()
                                    );

                            repository.appendTrustAnchorEvent(
                                    UUID.randomUUID(),

                                    registered.keyId(),

                                    "REGISTERED",

                                    null,

                                    "Ed25519 governance trust anchor automatically registered during first signature issuance."
                            );

                            return registered;
                        });

        requireCompatibleActiveAnchor(
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

        repository.appendTrustAnchorEvent(
                UUID.randomUUID(),

                anchor.keyId(),

                "SIGNATURE_ISSUED",

                null,

                "Ed25519 governance signature issued for closure certificate #"
                        + saved.certificateNumber()
                        + "."
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
                    !GovernanceEd25519Crypto.ALGORITHM
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

            if (
                    !lifecyclePolicy
                            .canVerifyHistoricalSignature(
                                    anchor,
                                    signature.createdAt()
                            )
            ) {
                return invalid(
                        signatures.size(),
                        verifiedCount,
                        signature,
                        "Governance signature falls outside the trust anchor validity period."
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
                        : "All Ed25519 governance signatures verified against their trust-anchor validity periods.",

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

    private void requireCompatibleActiveAnchor(
            GovernanceTrustAnchor anchor,
            GovernanceSigningReadinessResponse readiness
    ) {
        if (
                !GovernanceEd25519Crypto.ALGORITHM
                        .equals(
                                anchor.algorithm()
                        )

                || !lifecyclePolicy.canSign(
                        anchor
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
                    "Configured signing key does not match an ACTIVE registered governance trust anchor."
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
