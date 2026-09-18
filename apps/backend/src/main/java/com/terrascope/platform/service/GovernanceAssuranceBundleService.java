package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceBundle;
import com.terrascope.platform.api.model.GovernanceAssuranceBundleHistoryResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceBundlePreview;
import com.terrascope.platform.api.model.GovernanceAssuranceBundleVerificationResponse;
import com.terrascope.platform.api.model.GovernanceClosureCertificate;
import com.terrascope.platform.api.model.GovernanceClosureSignature;
import com.terrascope.platform.api.model.GovernanceClosureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSignatureVerificationResponse;
import com.terrascope.platform.api.model.GovernanceTrustAnchorEvent;
import com.terrascope.platform.api.model.GovernanceTrustAnchorView;
import com.terrascope.platform.domain.GovernanceAssuranceBundlePayload;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.repository.GovernanceAssuranceBundleRepository;
import com.terrascope.platform.repository.GovernanceClosureCertificateRepository;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GovernanceAssuranceBundleService {

    private final GovernanceAssuranceBundleRepository bundleRepository;

    private final GovernanceClosureCertificateRepository certificateRepository;

    private final GovernanceClosureSignatureRepository signatureRepository;

    private final GovernanceClosureCertificateService certificateService;

    private final GovernanceClosureSignatureService signatureService;

    private final GovernanceAssuranceBundleCodec codec;

    private final GovernanceAssurancePortableVerifier portableVerifier;

    public GovernanceAssuranceBundleService(
            GovernanceAssuranceBundleRepository bundleRepository,
            GovernanceClosureCertificateRepository certificateRepository,
            GovernanceClosureSignatureRepository signatureRepository,
            GovernanceClosureCertificateService certificateService,
            GovernanceClosureSignatureService signatureService,
            GovernanceAssuranceBundleCodec codec,
            GovernanceAssurancePortableVerifier portableVerifier
    ) {
        this.bundleRepository = bundleRepository;
        this.certificateRepository = certificateRepository;
        this.signatureRepository = signatureRepository;
        this.certificateService = certificateService;
        this.signatureService = signatureService;
        this.codec = codec;
        this.portableVerifier = portableVerifier;
    }

    public GovernanceAssuranceBundlePreview preview(
            UUID certificateId
    ) {
        BundleMaterial material =
                buildMaterial(
                        certificateId
                );

        return new GovernanceAssuranceBundlePreview(
                material.payload()
                        .certificate()
                        .id(),

                material.payload()
                        .certificate()
                        .certificateNumber(),

                GovernanceAssuranceBundleCodec.BUNDLE_VERSION,

                material.bundleHash(),

                material.signatureCount(),
                material.trustAnchorCount(),
                material.retiredAnchorCount(),
                material.lifecycleEventCount(),

                material.verification()
                        .valid(),

                Instant.now()
        );
    }

    public GovernanceAssuranceBundleHistoryResponse history() {
        return new GovernanceAssuranceBundleHistoryResponse(
                bundleRepository.findHistory()
        );
    }

    @Transactional
    public GovernanceAssuranceBundle create(
            UUID certificateId
    ) {
        BundleMaterial material =
                buildMaterial(
                        certificateId
                );

        if (
                !material.verification()
                        .valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    material.verification()
                            .message()
            );
        }

        return bundleRepository.save(
                UUID.randomUUID(),

                bundleRepository.nextBundleNumber(),

                material.payload()
                        .certificate()
                        .id(),

                material.payload()
                        .certificate()
                        .certificateNumber(),

                GovernanceAssuranceBundleCodec.BUNDLE_VERSION,

                material.bundleHash(),

                material.signatureCount(),
                material.trustAnchorCount(),
                material.retiredAnchorCount(),
                material.lifecycleEventCount(),

                true,

                material.payloadJson()
        );
    }

    public GovernanceAssuranceBundleVerificationResponse verify(
            UUID bundleId
    ) {
        GovernanceAssuranceBundle bundle =
                bundleRepository
                        .findById(
                                bundleId
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Governance assurance bundle was not found."
                                )
                        );

        String calculatedHash =
                codec.hash(
                        bundle.payloadJson()
                );

        boolean hashValid =
                calculatedHash.equals(
                        bundle.bundleHash()
                );

        if (
                !hashValid
        ) {
            return new GovernanceAssuranceBundleVerificationResponse(
                    false,

                    bundle.id(),
                    bundle.bundleNumber(),

                    false,
                    false,
                    false,

                    bundle.signatureCount(),
                    0,

                    "Portable assurance bundle SHA-256 fingerprint does not match its stored payload.",

                    Instant.now()
            );
        }

        GovernanceAssuranceBundlePayload payload;

        try {
            payload =
                    codec.decode(
                            bundle.payloadJson()
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            return new GovernanceAssuranceBundleVerificationResponse(
                    false,

                    bundle.id(),
                    bundle.bundleNumber(),

                    true,
                    false,
                    false,

                    bundle.signatureCount(),
                    0,

                    "Portable assurance bundle payload cannot be decoded.",

                    Instant.now()
            );
        }

        boolean canonical =
                codec.encode(
                        payload
                ).equals(
                        bundle.payloadJson()
                );

        GovernanceAssurancePortableVerifier.Result verification =
                portableVerifier.verify(
                        payload
                );

        boolean metadataValid =
                payload.certificate()
                        .id()
                        .equals(
                                bundle.certificateId()
                        )

                && payload.certificate()
                        .certificateNumber()
                   == bundle.certificateNumber()

                && GovernanceAssuranceBundleCodec.BUNDLE_VERSION
                        .equals(
                                bundle.bundleVersion()
                        )

                && payload.signatures()
                        .size()
                   == bundle.signatureCount()

                && payload.trustAnchors()
                        .size()
                   == bundle.trustAnchorCount()

                && payload.lifecycleEvents()
                        .size()
                   == bundle.lifecycleEventCount();

        boolean valid =
                hashValid
                && canonical
                && metadataValid
                && verification.valid();

        return new GovernanceAssuranceBundleVerificationResponse(
                valid,

                bundle.id(),
                bundle.bundleNumber(),

                hashValid,
                canonical,
                verification.valid(),

                verification.signatureCount(),
                verification.verifiedSignatureCount(),

                valid
                        ? "Portable governance assurance bundle verified independently from live signing configuration."
                        : (
                            metadataValid
                                    ? verification.message()
                                    : "Portable assurance bundle metadata does not match its stored envelope."
                        ),

                Instant.now()
        );
    }

    private BundleMaterial buildMaterial(
            UUID certificateId
    ) {
        GovernanceClosureVerificationResponse certificateVerification =
                certificateService.verify();

        if (
                !certificateVerification.valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance closure certificate chain must verify before assurance bundle export."
            );
        }

        GovernanceSignatureVerificationResponse signatureVerification =
                signatureService.verify();

        if (
                !signatureVerification.valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance signatures must verify before assurance bundle export."
            );
        }

        GovernanceClosureCertificate certificate;

        try {
            certificate =
                    certificateRepository.findCertificate(
                            certificateId
                    );

        } catch (
                RuntimeException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Governance closure certificate was not found."
            );
        }

        List<GovernanceClosureSignature> signatures =
                signatureRepository
                        .findHistory()
                        .stream()
                        .filter(signature ->
                                signature
                                        .certificateId()
                                        .equals(
                                                certificateId
                                        )
                        )
                        .sorted(
                                Comparator
                                        .comparing(
                                                GovernanceClosureSignature::createdAt
                                        )
                                        .thenComparing(signature ->
                                                signature
                                                        .id()
                                                        .toString()
                                        )
                        )
                        .toList();

        if (
                signatures.isEmpty()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Governance closure certificate must have at least one Ed25519 signature before bundle export."
            );
        }

        Set<String> relevantKeyIds =
                new LinkedHashSet<>();

        for (
                GovernanceClosureSignature signature
                : signatures
        ) {
            relevantKeyIds.add(
                    signature.keyId()
            );
        }

        List<GovernanceTrustAnchorView> allViews =
                signatureRepository.findTrustAnchorViews();

        boolean changed;

        do {
            changed =
                    false;

            for (
                    GovernanceTrustAnchorView view
                    : allViews
            ) {
                if (
                        relevantKeyIds.contains(
                                view.keyId()
                        )

                        && view.supersededByKeyId()
                           != null

                        && relevantKeyIds.add(
                                view.supersededByKeyId()
                        )
                ) {
                    changed =
                            true;
                }
            }

        } while (
                changed
        );

        List<String> sortedKeyIds =
                relevantKeyIds
                        .stream()
                        .sorted()
                        .toList();

        List<GovernanceAssuranceBundlePayload.TrustAnchorEvidence> anchors =
                new ArrayList<>();

        int retiredAnchorCount =
                0;

        for (
                String keyId
                : sortedKeyIds
        ) {
            GovernanceTrustAnchor anchor =
                    signatureRepository
                            .findTrustAnchor(
                                    keyId
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "Governance trust anchor "
                                                    + keyId
                                                    + " is missing."
                                    )
                            );

            GovernanceTrustAnchorView view =
                    allViews
                            .stream()
                            .filter(candidate ->
                                    candidate
                                            .keyId()
                                            .equals(
                                                    keyId
                                            )
                            )
                            .findFirst()
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "Governance trust-anchor lifecycle view is missing."
                                    )
                            );

            if (
                    "RETIRED"
                            .equals(
                                    view.status()
                            )
            ) {
                retiredAnchorCount++;
            }

            anchors.add(
                    new GovernanceAssuranceBundlePayload.TrustAnchorEvidence(
                            anchor.keyId(),

                            anchor.algorithm(),

                            anchor.publicKeyX509Base64(),
                            anchor.publicKeyFingerprint(),

                            view.status(),

                            view.supersededByKeyId(),
                            view.retirementReason(),

                            anchor.createdAt(),
                            anchor.lastUsedAt(),
                            anchor.retiredAt()
                    )
            );
        }

        List<GovernanceTrustAnchorEvent> relevantEvents =
                signatureRepository
                        .findTrustAnchorEvents()
                        .stream()
                        .filter(event ->
                                relevantKeyIds.contains(
                                        event.keyId()
                                )

                                || (
                                    event.replacementKeyId()
                                    != null

                                    && relevantKeyIds.contains(
                                            event.replacementKeyId()
                                    )
                                )
                        )
                        .sorted(
                                Comparator
                                        .comparing(
                                                GovernanceTrustAnchorEvent::createdAt
                                        )
                                        .thenComparing(event ->
                                                event
                                                        .id()
                                                        .toString()
                                        )
                        )
                        .toList();

        GovernanceAssuranceBundlePayload payload =
                new GovernanceAssuranceBundlePayload(
                        GovernanceAssuranceBundleCodec.BUNDLE_VERSION,

                        certificateEvidence(
                                certificate
                        ),

                        signatures
                                .stream()
                                .map(
                                        this::signatureEvidence
                                )
                                .toList(),

                        anchors,

                        relevantEvents
                                .stream()
                                .map(
                                        this::eventEvidence
                                )
                                .toList()
                );

        String payloadJson =
                codec.encode(
                        payload
                );

        GovernanceAssurancePortableVerifier.Result verification =
                portableVerifier.verify(
                        payload
                );

        return new BundleMaterial(
                payload,

                payloadJson,

                codec.hash(
                        payloadJson
                ),

                signatures.size(),
                anchors.size(),
                retiredAnchorCount,
                relevantEvents.size(),

                verification
        );
    }

    private GovernanceAssuranceBundlePayload.CertificateEvidence certificateEvidence(
            GovernanceClosureCertificate certificate
    ) {
        return new GovernanceAssuranceBundlePayload.CertificateEvidence(
                certificate.id(),

                certificate.certificateNumber(),

                certificate.benefitSnapshotId(),
                certificate.benefitSnapshotNumber(),

                certificate.band().name(),
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
                certificate.certificateHash(),

                certificate.executiveSummary(),

                certificate.createdAt()
        );
    }

    private GovernanceAssuranceBundlePayload.SignatureEvidence signatureEvidence(
            GovernanceClosureSignature signature
    ) {
        return new GovernanceAssuranceBundlePayload.SignatureEvidence(
                signature.id(),

                signature.certificateId(),
                signature.certificateNumber(),

                signature.keyId(),
                signature.algorithm(),

                signature.publicKeyFingerprint(),

                signature.signatureVersion(),

                signature.certificateHash(),
                signature.dossierHash(),

                signature.signatureBase64(),

                signature.createdAt()
        );
    }

    private GovernanceAssuranceBundlePayload.TrustAnchorEventEvidence eventEvidence(
            GovernanceTrustAnchorEvent event
    ) {
        return new GovernanceAssuranceBundlePayload.TrustAnchorEventEvidence(
                event.id(),

                event.keyId(),

                event.eventType(),

                event.replacementKeyId(),

                event.detail(),

                event.createdAt()
        );
    }

    private record BundleMaterial(
            GovernanceAssuranceBundlePayload payload,

            String payloadJson,
            String bundleHash,

            int signatureCount,
            int trustAnchorCount,
            int retiredAnchorCount,
            int lifecycleEventCount,

            GovernanceAssurancePortableVerifier.Result verification
    ) {
    }
}
