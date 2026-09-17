package com.terrascope.platform.service;

import com.terrascope.platform.api.model.EvidencePackageVerificationResponse;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.ResilienceEvidencePackage;
import com.terrascope.platform.api.model.ResilienceEvidencePackageHistoryResponse;
import com.terrascope.platform.api.model.ResilienceEvidencePackagePreview;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.ResilienceEvidenceManifest;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.ResilienceEvidencePackageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResilienceEvidencePackageService {

    private final ResilienceEvidencePackageRepository repository;
    private final IncidentResponseRepository incidentRepository;
    private final EvidenceManifestHasher hasher;

    public ResilienceEvidencePackageService(
            ResilienceEvidencePackageRepository repository,
            IncidentResponseRepository incidentRepository,
            EvidenceManifestHasher hasher
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.hasher = hasher;
    }

    public ResilienceEvidencePackagePreview preview(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireCertifiedIncident(
                        incidentId
                );

        ResilienceEvidenceManifest manifest =
                repository.loadManifest(
                        incidentId
                );

        requireCertification(
                manifest
        );

        String hash =
                hasher.hash(
                        manifest
                );

        return new ResilienceEvidencePackagePreview(
                incidentId,

                manifest.certificationId(),
                manifest.certificationNumber(),
                manifest.certificationReadinessScore(),
                manifest.certificationCount(),

                manifest.afterActionReviewCount(),
                manifest.sitrepCount(),
                manifest.improvementCount(),
                manifest.exerciseCount(),
                manifest.correctiveActionCount(),
                manifest.timelineEventCount(),

                EvidenceManifestHasher.MANIFEST_VERSION,
                hash,

                buildSummary(
                        incident,
                        manifest
                ),

                Instant.now()
        );
    }

    public ResilienceEvidencePackageHistoryResponse history(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        return new ResilienceEvidencePackageHistoryResponse(
                incidentId,
                repository.findHistory(
                        incidentId
                )
        );
    }

    @Transactional
    public ResilienceEvidencePackage create(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireCertifiedIncident(
                        incidentId
                );

        repository.lockIncident(
                incidentId
        );

        ResilienceEvidenceManifest manifest =
                repository.loadManifest(
                        incidentId
                );

        requireCertification(
                manifest
        );

        int packageNumber =
                repository.nextPackageNumber(
                        incidentId
                );

        String manifestHash =
                hasher.hash(
                        manifest
                );

        String executiveSummary =
                buildSummary(
                        incident,
                        manifest
                );

        ResilienceEvidencePackage evidencePackage =
                repository.savePackage(
                        UUID.randomUUID(),
                        packageNumber,
                        manifest,
                        EvidenceManifestHasher.MANIFEST_VERSION,
                        manifestHash,
                        executiveSummary
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESILIENCE_EVIDENCE_PACKAGE_CREATED",
                "Resilience evidence package #"
                        + evidencePackage.packageNumber()
                        + " captured from certification #"
                        + evidencePackage.certificationNumber()
                        + " with SHA-256 manifest "
                        + evidencePackage.manifestHash()
                        + "."
        );

        return evidencePackage;
    }

    public EvidencePackageVerificationResponse verify(
            UUID incidentId,
            UUID packageId
    ) {
        requireIncident(
                incidentId
        );

        ResilienceEvidencePackage evidencePackage =
                repository.findById(
                        incidentId,
                        packageId
                ).orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Evidence package "
                                        + packageId
                                        + " was not found."
                        )
                );

        ResilienceEvidenceManifest storedManifest =
                manifestFromPackage(
                        evidencePackage
                );

        String recomputedHash =
                hasher.hash(
                        storedManifest
                );

        boolean valid =
                evidencePackage.manifestVersion()
                        .equals(
                                EvidenceManifestHasher.MANIFEST_VERSION
                        )
                && evidencePackage.manifestHash()
                        .equals(
                                recomputedHash
                        );

        return new EvidencePackageVerificationResponse(
                incidentId,
                packageId,

                valid,

                evidencePackage.manifestVersion(),
                evidencePackage.manifestHash(),
                recomputedHash,

                Instant.now()
        );
    }

    private ResilienceEvidenceManifest manifestFromPackage(
            ResilienceEvidencePackage evidencePackage
    ) {
        return new ResilienceEvidenceManifest(
                evidencePackage.incidentId(),

                evidencePackage.certificationId(),
                evidencePackage.certificationNumber(),
                evidencePackage.certificationReadinessScore(),
                evidencePackage.certificationCount(),

                evidencePackage.afterActionReviewCount(),
                evidencePackage.sitrepCount(),
                evidencePackage.improvementCount(),
                evidencePackage.exerciseCount(),
                evidencePackage.correctiveActionCount(),
                evidencePackage.timelineEventCount()
        );
    }

    private String buildSummary(
            IncidentCase incident,
            ResilienceEvidenceManifest manifest
    ) {
        return String.format(
                Locale.ROOT,
                "Evidence package for incident '%s' is anchored to resilience "
                        + "certification #%d with readiness %.1f/100. "
                        + "The immutable manifest records %d certifications, "
                        + "%d after-action reviews, %d situation reports, "
                        + "%d improvement actions, %d preparedness exercises, "
                        + "%d corrective actions, and %d incident timeline events. "
                        + "The package fingerprint is generated locally with SHA-256 "
                        + "using canonical TerraScope evidence manifest version %s.",
                incident.title(),
                manifest.certificationNumber(),
                manifest.certificationReadinessScore(),
                manifest.certificationCount(),
                manifest.afterActionReviewCount(),
                manifest.sitrepCount(),
                manifest.improvementCount(),
                manifest.exerciseCount(),
                manifest.correctiveActionCount(),
                manifest.timelineEventCount(),
                EvidenceManifestHasher.MANIFEST_VERSION
        );
    }

    private IncidentCase requireCertifiedIncident(
            UUID incidentId
    ) {
        IncidentCase incident =
                requireIncident(
                        incidentId
                );

        if (
                incident.status()
                != IncidentStatus.CLOSED
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Resilience evidence packaging requires a CLOSED incident."
            );
        }

        return incident;
    }

    private void requireCertification(
            ResilienceEvidenceManifest manifest
    ) {
        if (
                !manifest.hasCertification()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Resilience certification is required before evidence packaging."
            );
        }
    }

    private IncidentCase requireIncident(
            UUID incidentId
    ) {
        return incidentRepository
                .findById(
                        incidentId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Incident "
                                        + incidentId
                                        + " was not found."
                        )
                );
    }
}
