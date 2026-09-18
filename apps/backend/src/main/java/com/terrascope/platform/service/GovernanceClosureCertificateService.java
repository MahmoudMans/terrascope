package com.terrascope.platform.service;

import com.terrascope.platform.api.model.BenefitRealizationResponse;
import com.terrascope.platform.api.model.BenefitRealizationSnapshot;
import com.terrascope.platform.api.model.GovernanceClosureCertificate;
import com.terrascope.platform.api.model.GovernanceClosureCertificateHistoryResponse;
import com.terrascope.platform.api.model.GovernanceClosureReadinessResponse;
import com.terrascope.platform.api.model.GovernanceClosureVerificationResponse;
import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.GovernanceClosureDossierMaterial;
import com.terrascope.platform.repository.GovernanceClosureCertificateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GovernanceClosureCertificateService {

    private final GovernanceClosureCertificateRepository repository;
    private final BenefitRealizationService benefitRealizationService;
    private final GovernanceClosureHasher hasher;

    public GovernanceClosureCertificateService(
            GovernanceClosureCertificateRepository repository,
            BenefitRealizationService benefitRealizationService,
            GovernanceClosureHasher hasher
    ) {
        this.repository = repository;
        this.benefitRealizationService = benefitRealizationService;
        this.hasher = hasher;
    }

    public GovernanceClosureReadinessResponse readiness() {
        BenefitRealizationResponse realization =
                benefitRealizationService.realization();

        List<String> blockers =
                new ArrayList<>();

        if (
                realization.totalObjectiveCount()
                == 0
        ) {
            blockers.add(
                    "NO_STRATEGIC_OBJECTIVES"
            );
        }

        if (
                realization.plannedObjectiveCount()
                + realization.inProgressObjectiveCount()
                + realization.blockedObjectiveCount()
                > 0
        ) {
            blockers.add(
                    "ACTIVE_STRATEGIC_OBJECTIVES"
            );
        }

        if (
                realization.completedObjectiveCount()
                == 0
        ) {
            blockers.add(
                    "NO_COMPLETED_OBJECTIVES"
            );
        }

        if (
                realization.unvalidatedCompletedObjectiveCount()
                > 0
        ) {
            blockers.add(
                    "UNVALIDATED_COMPLETED_OBJECTIVES"
            );
        }

        if (
                realization.partialCount()
                > 0
        ) {
            blockers.add(
                    "PARTIAL_OUTCOMES"
            );
        }

        if (
                realization.notAchievedCount()
                > 0
        ) {
            blockers.add(
                    "NOT_ACHIEVED_OUTCOMES"
            );
        }

        if (
                realization.validationCoveragePct()
                < 100.0
        ) {
            blockers.add(
                    "VALIDATION_COVERAGE_BELOW_100"
            );
        }

        if (
                realization.realizedBenefitRatePct()
                < 100.0
        ) {
            blockers.add(
                    "REALIZED_BENEFIT_RATE_BELOW_100"
            );
        }

        if (
                realization.band()
                != BenefitRealizationBand.REALIZED
        ) {
            blockers.add(
                    "REALIZATION_BAND_NOT_REALIZED"
            );
        }

        if (
                !realization.closureReady()
        ) {
            blockers.add(
                    "GOVERNANCE_CLOSURE_NOT_READY"
            );
        }

        return new GovernanceClosureReadinessResponse(
                blockers.isEmpty(),

                realization.band(),

                realization.totalObjectiveCount(),
                realization.completedObjectiveCount(),
                realization.validatedObjectiveCount(),
                realization.achievedCount(),

                realization.validationCoveragePct(),
                realization.realizedBenefitRatePct(),

                List.copyOf(
                        blockers
                ),

                Instant.now()
        );
    }

    public GovernanceClosureCertificateHistoryResponse history() {
        return new GovernanceClosureCertificateHistoryResponse(
                repository.findHistory()
        );
    }

    @Transactional
    public GovernanceClosureCertificate certify() {
        GovernanceClosureReadinessResponse readiness =
                readiness();

        if (
                !readiness.ready()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Enterprise resilience governance is not ready for closure certification."
            );
        }

        BenefitRealizationSnapshot snapshot =
                benefitRealizationService.createSnapshot();

        if (
                snapshot.band()
                != BenefitRealizationBand.REALIZED
                || !snapshot.closureReady()
                || snapshot.validationCoveragePct() != 100.0
                || snapshot.realizedBenefitRatePct() != 100.0
                || snapshot.completedObjectiveCount() <= 0
                || snapshot.validatedObjectiveCount()
                   != snapshot.completedObjectiveCount()
                || snapshot.achievedCount()
                   != snapshot.completedObjectiveCount()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Captured benefit-realization snapshot no longer satisfies governance closure requirements."
            );
        }

        int certificateNumber =
                repository.nextCertificateNumber();

        String previousHash =
                repository
                        .findLatest()
                        .map(
                                GovernanceClosureCertificate::certificateHash
                        )
                        .orElse(
                                null
                        );

        GovernanceClosureDossierMaterial material =
                materialFromSnapshot(
                        snapshot
                );

        String dossierHash =
                hasher.dossierHash(
                        material
                );

        String certificateHash =
                hasher.certificateHash(
                        certificateNumber,
                        dossierHash,
                        previousHash
                );

        String summary =
                "Enterprise resilience governance closure certificate #"
                        + certificateNumber
                        + " confirms REALIZED benefit assurance across "
                        + snapshot.completedObjectiveCount()
                        + " completed strategic objective(s), with "
                        + String.format(
                                java.util.Locale.ROOT,
                                "%.1f%% validation coverage and %.1f%% realized-benefit rate.",
                                snapshot.validationCoveragePct(),
                                snapshot.realizedBenefitRatePct()
                        );

        return repository.save(
                UUID.randomUUID(),

                certificateNumber,

                material,

                previousHash,

                GovernanceClosureHasher.DOSSIER_VERSION,
                dossierHash,

                GovernanceClosureHasher.CERTIFICATE_VERSION,
                certificateHash,

                summary
        );
    }

    public GovernanceClosureVerificationResponse verify() {
        List<GovernanceClosureCertificate> certificates =
                repository.findChain();

        String expectedPreviousHash =
                null;

        int expectedNumber =
                1;

        int verifiedCount =
                0;

        for (
                GovernanceClosureCertificate certificate
                : certificates
        ) {
            if (
                    certificate.certificateNumber()
                    != expectedNumber
            ) {
                return invalid(
                        certificates.size(),
                        verifiedCount,
                        certificate,
                        "Certificate sequence is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            certificate.previousCertificateHash(),
                            expectedPreviousHash
                    )
            ) {
                return invalid(
                        certificates.size(),
                        verifiedCount,
                        certificate,
                        "Previous certificate hash does not match the enterprise closure chain."
                );
            }

            if (
                    !GovernanceClosureHasher.DOSSIER_VERSION
                            .equals(
                                    certificate.dossierVersion()
                            )
            ) {
                return invalid(
                        certificates.size(),
                        verifiedCount,
                        certificate,
                        "Unsupported governance closure dossier version."
                );
            }

            if (
                    !GovernanceClosureHasher.CERTIFICATE_VERSION
                            .equals(
                                    certificate.certificateVersion()
                            )
            ) {
                return invalid(
                        certificates.size(),
                        verifiedCount,
                        certificate,
                        "Unsupported governance closure certificate version."
                );
            }

            BenefitRealizationSnapshot sourceSnapshot =
                    repository
                            .findBenefitSnapshot(
                                    certificate.benefitSnapshotId()
                            )
                            .orElse(
                                    null
                            );

            if (
                    sourceSnapshot == null
                    || !matchesSourceSnapshot(
                            certificate,
                            sourceSnapshot
                    )
            ) {
                return invalid(
                        certificates.size(),
                        verifiedCount,
                        certificate,
                        "Benefit-realization source snapshot does not match the certificate dossier."
                );
            }

            GovernanceClosureDossierMaterial material =
                    materialFromCertificate(
                            certificate
                    );

            String dossierHash =
                    hasher.dossierHash(
                            material
                    );

            if (
                    !dossierHash.equals(
                            certificate.dossierHash()
                    )
            ) {
                return invalid(
                        certificates.size(),
                        verifiedCount,
                        certificate,
                        "Governance closure dossier hash does not match its canonical payload."
                );
            }

            String certificateHash =
                    hasher.certificateHash(
                            certificate.certificateNumber(),
                            certificate.dossierHash(),
                            certificate.previousCertificateHash()
                    );

            if (
                    !certificateHash.equals(
                            certificate.certificateHash()
                    )
            ) {
                return invalid(
                        certificates.size(),
                        verifiedCount,
                        certificate,
                        "Governance closure certificate hash does not match its canonical payload."
                );
            }

            expectedPreviousHash =
                    certificate.certificateHash();

            expectedNumber++;

            verifiedCount++;
        }

        return new GovernanceClosureVerificationResponse(
                true,

                certificates.size(),
                verifiedCount,

                null,
                null,

                certificates.isEmpty()
                        ? "No enterprise governance closure certificates have been issued."
                        : "Enterprise governance closure certificate chain verified.",

                Instant.now()
        );
    }

    private boolean matchesSourceSnapshot(
            GovernanceClosureCertificate certificate,
            BenefitRealizationSnapshot snapshot
    ) {
        return certificate.benefitSnapshotNumber()
                == snapshot.snapshotNumber()

                && certificate.band()
                   == snapshot.band()

                && certificate.closureReady()
                   == snapshot.closureReady()

                && certificate.totalObjectiveCount()
                   == snapshot.totalObjectiveCount()

                && certificate.completedObjectiveCount()
                   == snapshot.completedObjectiveCount()

                && certificate.validatedObjectiveCount()
                   == snapshot.validatedObjectiveCount()

                && certificate.achievedCount()
                   == snapshot.achievedCount()

                && Double.compare(
                        certificate.validationCoveragePct(),
                        snapshot.validationCoveragePct()
                ) == 0

                && Double.compare(
                        certificate.realizedBenefitRatePct(),
                        snapshot.realizedBenefitRatePct()
                ) == 0

                && Double.compare(
                        certificate.averageScoreDelta(),
                        snapshot.averageScoreDelta()
                ) == 0

                && Double.compare(
                        certificate.averageAssuranceDebtDelta(),
                        snapshot.averageAssuranceDebtDelta()
                ) == 0;
    }

    private GovernanceClosureDossierMaterial materialFromSnapshot(
            BenefitRealizationSnapshot snapshot
    ) {
        return new GovernanceClosureDossierMaterial(
                snapshot.id(),
                snapshot.snapshotNumber(),

                snapshot.band(),
                snapshot.closureReady(),

                snapshot.totalObjectiveCount(),
                snapshot.completedObjectiveCount(),
                snapshot.validatedObjectiveCount(),
                snapshot.achievedCount(),

                snapshot.validationCoveragePct(),
                snapshot.realizedBenefitRatePct(),

                snapshot.averageScoreDelta(),
                snapshot.averageAssuranceDebtDelta()
        );
    }

    private GovernanceClosureDossierMaterial materialFromCertificate(
            GovernanceClosureCertificate certificate
    ) {
        return new GovernanceClosureDossierMaterial(
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
                certificate.averageAssuranceDebtDelta()
        );
    }

    private GovernanceClosureVerificationResponse invalid(
            int certificateCount,
            int verifiedCount,
            GovernanceClosureCertificate certificate,
            String message
    ) {
        return new GovernanceClosureVerificationResponse(
                false,

                certificateCount,
                verifiedCount,

                certificate.id(),
                certificate.certificateNumber(),

                message,

                Instant.now()
        );
    }
}
