package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustAnchorTransition;
import com.terrascope.platform.api.model.GovernanceTrustAnchorTransitionVerificationResponse;
import com.terrascope.platform.api.model.GovernanceTrustLineagePackage;
import com.terrascope.platform.api.model.GovernanceTrustLineagePackageHistoryResponse;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableEnvelope;
import com.terrascope.platform.domain.GovernanceTrustLineagePortablePayload;
import com.terrascope.platform.domain.GovernanceTrustLineagePortableTrustAnchor;
import com.terrascope.platform.repository.GovernanceTrustAnchorTransitionRepository;
import com.terrascope.platform.repository.GovernanceTrustLineagePackageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GovernanceTrustLineagePackageService {

    private final GovernanceTrustAnchorTransitionRepository transitionRepository;

    private final GovernanceTrustAnchorTransitionService transitionService;

    private final GovernanceTrustLineagePackageRepository packageRepository;

    private final GovernanceTrustLineagePackageCodec codec;

    private final GovernanceTrustLineagePortableVerifier verifier;

    public GovernanceTrustLineagePackageService(
            GovernanceTrustAnchorTransitionRepository transitionRepository,
            GovernanceTrustAnchorTransitionService transitionService,
            GovernanceTrustLineagePackageRepository packageRepository,
            GovernanceTrustLineagePackageCodec codec,
            GovernanceTrustLineagePortableVerifier verifier
    ) {
        this.transitionRepository = transitionRepository;
        this.transitionService = transitionService;
        this.packageRepository = packageRepository;
        this.codec = codec;
        this.verifier = verifier;
    }

    @Transactional
    public GovernanceTrustLineagePackage create() {
        GovernanceTrustAnchorTransitionVerificationResponse transitionVerification =
                transitionService.verify();

        if (
                !transitionVerification.valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Signed trust-anchor transition lineage must verify before portable packaging."
            );
        }

        List<GovernanceTrustAnchorTransition> transitions =
                transitionRepository.findChain();

        if (
                transitions.isEmpty()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No signed trust-anchor transitions exist to package."
            );
        }

        List<GovernanceTrustLineagePortableTrustAnchor> anchors =
                packageRepository.findPortableAnchors();

        Set<String> requiredKeyIds =
                new HashSet<>();

        for (
                GovernanceTrustAnchorTransition transition
                : transitions
        ) {
            requiredKeyIds.add(
                    transition.outgoingKeyId()
            );

            requiredKeyIds.add(
                    transition.replacementKeyId()
            );
        }

        Set<String> packagedKeyIds =
                new HashSet<>();

        for (
                GovernanceTrustLineagePortableTrustAnchor anchor
                : anchors
        ) {
            packagedKeyIds.add(
                    anchor.keyId()
            );
        }

        if (
                !packagedKeyIds.equals(
                        requiredKeyIds
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Portable trust-lineage package does not have the exact public trust-anchor set required by its signed transitions."
            );
        }

        GovernanceTrustAnchorTransition first =
                transitions.getFirst();

        GovernanceTrustAnchorTransition last =
                transitions.getLast();

        GovernanceTrustLineagePortablePayload payload =
                new GovernanceTrustLineagePortablePayload(
                        GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                        first.outgoingKeyId(),
                        first.outgoingPublicKeyFingerprint(),

                        last.replacementKeyId(),
                        last.replacementPublicKeyFingerprint(),

                        List.copyOf(
                                anchors
                        ),

                        List.copyOf(
                                transitions
                        )
                );

        String payloadJson =
                codec.encodePayload(
                        payload
                );

        String packageHash =
                codec.sha256(
                        payloadJson
                );

        var existing =
                packageRepository.findByHash(
                        packageHash
                );

        if (
                existing.isPresent()
        ) {
            return existing.get();
        }

        GovernanceTrustLineagePackage created =
                packageRepository.save(
                        UUID.randomUUID(),

                        packageRepository.nextPackageNumber(),

                        GovernanceTrustLineagePackageCodec.PACKAGE_VERSION,

                        transitions.size(),
                        anchors.size(),

                        first.transitionNumber(),
                        last.transitionNumber(),

                        last.transitionHash(),

                        payloadJson,
                        packageHash
                );

        byte[] envelopeBytes =
                export(
                        created.id()
                );

        if (
                !verifier
                        .verify(
                                envelopeBytes
                        )
                        .valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "New portable trust-lineage package failed stateless self-verification."
            );
        }

        return created;
    }

    public byte[] export(
            UUID packageId
    ) {
        GovernanceTrustLineagePackage portablePackage =
                packageRepository
                        .findById(
                                packageId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Portable trust-lineage package was not found."
                                )
                        );

        if (
                !codec.sha256(
                        portablePackage.payloadJson()
                ).equals(
                        portablePackage.packageHash()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stored portable trust-lineage package hash is invalid."
            );
        }

        GovernanceTrustLineagePortableEnvelope envelope =
                new GovernanceTrustLineagePortableEnvelope(
                        GovernanceTrustLineagePackageCodec.ENVELOPE_VERSION,

                        portablePackage.id(),
                        portablePackage.packageNumber(),

                        portablePackage.packageVersion(),
                        portablePackage.packageHash(),

                        portablePackage.payloadJson()
                );

        byte[] bytes =
                codec.encodeEnvelope(
                        envelope
                );

        if (
                !verifier
                        .verify(
                                bytes
                        )
                        .valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stored portable trust-lineage package failed stateless verification."
            );
        }

        return bytes;
    }

    public GovernanceTrustLineagePackageHistoryResponse history() {
        return new GovernanceTrustLineagePackageHistoryResponse(
                packageRepository.findHistory()
        );
    }
}
