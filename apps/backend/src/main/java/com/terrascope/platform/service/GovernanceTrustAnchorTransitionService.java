package com.terrascope.platform.service;

import com.terrascope.platform.api.model.CreateGovernanceTrustAnchorTransitionRequest;
import com.terrascope.platform.api.model.GovernanceSigningReadinessResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransitionHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransitionVerificationResponse;
import com.terrascope.platform.api.model.RegisterGovernanceTrustAnchorRequest;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.domain.GovernanceTrustAnchorTransitionMaterial;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
import com.terrascope.platform.repository.GovernanceTrustAnchorTransitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GovernanceTrustAnchorTransitionService {

    private final GovernanceTrustAnchorTransitionRepository transitionRepository;

    private final GovernanceClosureSignatureRepository trustAnchorRepository;

    private final GovernanceClosureSignatureService signingService;

    private final GovernanceSigningConfiguration configuration;

    private final GovernanceEd25519Crypto crypto;

    private final GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy;

    private final GovernanceTrustAnchorTransitionHasher hasher;

    public GovernanceTrustAnchorTransitionService(
            GovernanceTrustAnchorTransitionRepository transitionRepository,
            GovernanceClosureSignatureRepository trustAnchorRepository,
            GovernanceClosureSignatureService signingService,
            GovernanceSigningConfiguration configuration,
            GovernanceEd25519Crypto crypto,
            GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy,
            GovernanceTrustAnchorTransitionHasher hasher
    ) {
        this.transitionRepository = transitionRepository;
        this.trustAnchorRepository = trustAnchorRepository;
        this.signingService = signingService;
        this.configuration = configuration;
        this.crypto = crypto;
        this.lifecyclePolicy = lifecyclePolicy;
        this.hasher = hasher;
    }

    public GovernanceTrustAnchorTransitionHistoryResponse history() {
        return new GovernanceTrustAnchorTransitionHistoryResponse(
                transitionRepository.findHistory()
        );
    }

    @Transactional
    public GovernanceTrustAnchorTransition rotate(
            CreateGovernanceTrustAnchorTransitionRequest request
    ) {
        if (
                request == null

                || request.replacementKeyId() == null

                || request.replacementKeyId()
                        .isBlank()

                || request.replacementPublicKeyX509Base64() == null

                || request.replacementPublicKeyX509Base64()
                        .isBlank()

                || request.reason() == null

                || request.reason()
                        .isBlank()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Replacement key, public key and rotation reason are required."
            );
        }

        String replacementKeyId =
                request.replacementKeyId()
                        .trim();

        String reason =
                request.reason()
                        .trim();

        GovernanceSigningReadinessResponse readiness =
                signingService.readiness();

        if (
                !readiness.ready()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    readiness.message()
            );
        }

        String outgoingKeyId =
                readiness.keyId();

        if (
                outgoingKeyId.equals(
                        replacementKeyId
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Outgoing and replacement trust-anchor keys must differ."
            );
        }

        if (
                transitionRepository.existsForOutgoingKey(
                        outgoingKeyId
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Outgoing trust anchor already has a signed transition."
            );
        }

        GovernanceTrustAnchor outgoing =
                trustAnchorRepository
                        .findTrustAnchor(
                                outgoingKeyId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Configured outgoing trust anchor is missing."
                                )
                        );

        if (
                !lifecyclePolicy.canSign(
                        outgoing
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Outgoing trust anchor must be ACTIVE before rotation."
            );
        }

        String replacementFingerprint;

        try {
            replacementFingerprint =
                    crypto.publicKeyFingerprint(
                            request.replacementPublicKeyX509Base64()
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Replacement Ed25519 public key is invalid."
            );
        }

        GovernanceTrustAnchor replacement =
                trustAnchorRepository
                        .findTrustAnchor(
                                replacementKeyId
                        )
                        .orElseGet(() -> {
                            signingService.registerTrustAnchor(
                                    new RegisterGovernanceTrustAnchorRequest(
                                            replacementKeyId,
                                            request.replacementPublicKeyX509Base64()
                                    )
                            );

                            return trustAnchorRepository
                                    .findTrustAnchor(
                                            replacementKeyId
                                    )
                                    .orElseThrow(() ->
                                            new IllegalStateException(
                                                    "Replacement trust anchor disappeared after registration."
                                            )
                                    );
                        });

        if (
                !"ACTIVE".equals(
                        replacement.status()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Replacement trust anchor must be ACTIVE."
            );
        }

        if (
                !GovernanceEd25519Crypto.ALGORITHM
                        .equals(
                                replacement.algorithm()
                        )

                || !replacementFingerprint
                        .equals(
                                replacement.publicKeyFingerprint()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Replacement trust-anchor identity does not match the supplied public key."
            );
        }

        int transitionNumber =
                transitionRepository.nextTransitionNumber();

        String previousTransitionHash =
                transitionRepository
                        .findLatest()
                        .map(
                                GovernanceTrustAnchorTransition::transitionHash
                        )
                        .orElse(
                                null
                        );

        GovernanceTrustAnchorTransitionMaterial material =
                new GovernanceTrustAnchorTransitionMaterial(
                        transitionNumber,

                        outgoing.keyId(),
                        outgoing.publicKeyFingerprint(),

                        replacement.keyId(),
                        replacement.publicKeyFingerprint(),

                        GovernanceEd25519Crypto.ALGORITHM,

                        reason,

                        previousTransitionHash
                );

        String signature =
                crypto.sign(
                        configuration.privateKeyPkcs8Base64(),

                        hasher.signingPayload(
                                material
                        )
                );

        if (
                !crypto.verify(
                        outgoing.publicKeyX509Base64(),

                        hasher.signingPayload(
                                material
                        ),

                        signature
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Outgoing Ed25519 key failed trust-transition self-verification."
            );
        }

        String transitionHash =
                hasher.transitionHash(
                        material,
                        signature
                );

        Instant transitionTime =
                Instant.now();

        GovernanceTrustAnchorTransition transition =
                transitionRepository.save(
                        UUID.randomUUID(),

                        material,

                        GovernanceTrustAnchorTransitionHasher.TRANSITION_VERSION,

                        signature,
                        transitionHash,

                        transitionTime
                );

        transitionRepository.retireOutgoingAnchor(
                outgoing.keyId(),
                replacement.keyId(),
                reason,
                transitionTime
        );

        return transition;
    }

    public GovernanceTrustAnchorTransitionVerificationResponse verify() {
        List<GovernanceTrustAnchorTransition> transitions =
                transitionRepository.findChain();

        String expectedPreviousHash =
                null;

        int expectedTransitionNumber =
                1;

        int verifiedCount =
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
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Trust-anchor transition sequence is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            transition.previousTransitionHash(),
                            expectedPreviousHash
                    )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Trust-anchor transition previous hash does not match."
                );
            }

            if (
                    !GovernanceTrustAnchorTransitionHasher.TRANSITION_VERSION
                            .equals(
                                    transition.transitionVersion()
                            )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Unsupported signed trust-anchor transition version."
                );
            }

            if (
                    !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    transition.algorithm()
                            )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Unsupported trust-anchor transition algorithm."
                );
            }

            GovernanceTrustAnchor outgoing =
                    trustAnchorRepository
                            .findTrustAnchor(
                                    transition.outgoingKeyId()
                            )
                            .orElse(
                                    null
                            );

            GovernanceTrustAnchor replacement =
                    trustAnchorRepository
                            .findTrustAnchor(
                                    transition.replacementKeyId()
                            )
                            .orElse(
                                    null
                            );

            if (
                    outgoing == null
                    || replacement == null
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Signed trust-anchor transition references a missing trust anchor."
                );
            }

            if (
                    !transition
                            .outgoingPublicKeyFingerprint()
                            .equals(
                                    outgoing.publicKeyFingerprint()
                            )

                    || !transition
                            .replacementPublicKeyFingerprint()
                            .equals(
                                    replacement.publicKeyFingerprint()
                            )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Signed trust-anchor transition fingerprint metadata does not match."
                );
            }

            String outgoingCalculatedFingerprint;
            String replacementCalculatedFingerprint;

            try {
                outgoingCalculatedFingerprint =
                        crypto.publicKeyFingerprint(
                                outgoing.publicKeyX509Base64()
                        );

                replacementCalculatedFingerprint =
                        crypto.publicKeyFingerprint(
                                replacement.publicKeyX509Base64()
                        );

            } catch (
                    IllegalArgumentException exception
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Trust-anchor transition contains invalid Ed25519 public-key evidence."
                );
            }

            if (
                    !outgoingCalculatedFingerprint
                            .equals(
                                    transition.outgoingPublicKeyFingerprint()
                            )

                    || !replacementCalculatedFingerprint
                            .equals(
                                    transition.replacementPublicKeyFingerprint()
                            )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Trust-anchor transition fingerprint recomputation failed."
                );
            }

            if (
                    outgoing.retiredAt() == null

                    || transition.createdAt()
                            .isAfter(
                                    outgoing.retiredAt()
                            )

                    || !"RETIRED".equals(
                            outgoing.status()
                    )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Outgoing trust anchor lifecycle does not match its signed transition."
                );
            }

            if (
                    replacement.createdAt()
                            .isAfter(
                                    transition.createdAt()
                            )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Replacement trust anchor was registered after its signed transition."
                );
            }

            if (
                    !transitionRepository.lifecycleMatches(
                            transition.outgoingKeyId(),
                            transition.replacementKeyId(),
                            transition.reason(),
                            transition.createdAt()
                    )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Persisted trust-anchor retirement state does not match its signed transition."
                );
            }

            if (
                    !lifecyclePolicy.canVerifyHistoricalSignature(
                            outgoing,
                            transition.createdAt()
                    )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Signed trust-anchor transition falls outside outgoing-key validity."
                );
            }

            GovernanceTrustAnchorTransitionMaterial material =
                    material(
                            transition
                    );

            if (
                    !crypto.verify(
                            outgoing.publicKeyX509Base64(),

                            hasher.signingPayload(
                                    material
                            ),

                            transition.signatureBase64()
                    )
            ) {
                return invalid(
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Ed25519 trust-anchor transition signature verification failed."
                );
            }

            String calculatedTransitionHash =
                    hasher.transitionHash(
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
                        transitions.size(),
                        verifiedCount,
                        transition,
                        "Trust-anchor transition hash does not match its signed payload."
                );
            }

            expectedPreviousHash =
                    transition.transitionHash();

            expectedTransitionNumber++;

            verifiedCount++;
        }

        return new GovernanceTrustAnchorTransitionVerificationResponse(
                true,

                transitions.size(),
                verifiedCount,

                null,
                null,

                null,
                null,

                transitions.isEmpty()
                        ? "No signed trust-anchor transitions have been issued."
                        : "Signed trust-anchor transition lineage verified.",

                Instant.now()
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

    private GovernanceTrustAnchorTransitionVerificationResponse invalid(
            int transitionCount,
            int verifiedCount,

            GovernanceTrustAnchorTransition transition,

            String message
    ) {
        return new GovernanceTrustAnchorTransitionVerificationResponse(
                false,

                transitionCount,
                verifiedCount,

                transition.id(),
                transition.transitionNumber(),

                transition.outgoingKeyId(),
                transition.replacementKeyId(),

                message,

                Instant.now()
        );
    }
}
