package com.terrascope.platform.service;

import com.terrascope.platform.api.model.BenefitRealizationResponse;
import com.terrascope.platform.api.model.BenefitRealizationSnapshot;
import com.terrascope.platform.api.model.GovernanceClosureCertificate;
import com.terrascope.platform.api.model.GovernanceClosureReadinessResponse;
import com.terrascope.platform.api.model.GovernanceClosureVerificationResponse;
import com.terrascope.platform.domain.BenefitRealizationBand;
import com.terrascope.platform.domain.GovernanceClosureDossierMaterial;
import com.terrascope.platform.repository.GovernanceClosureCertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceClosureCertificateServiceTest {

    private GovernanceClosureCertificateRepository repository;
    private BenefitRealizationService benefitRealizationService;
    private GovernanceClosureHasher hasher;

    private GovernanceClosureCertificateService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        GovernanceClosureCertificateRepository.class
                );

        benefitRealizationService =
                mock(
                        BenefitRealizationService.class
                );

        hasher =
                new GovernanceClosureHasher();

        service =
                new GovernanceClosureCertificateService(
                        repository,
                        benefitRealizationService,
                        hasher
                );
    }

    @Test
    void blockedPortfolioCannotBeCertified() {
        when(
                benefitRealizationService.realization()
        ).thenReturn(
                new BenefitRealizationResponse(
                        BenefitRealizationBand.REALIZING,
                        false,

                        1,

                        0,
                        0,
                        0,
                        1,
                        0,

                        1,
                        0,

                        0,
                        1,
                        0,

                        100.0,
                        0.0,

                        0.0,
                        0.0,

                        Instant.now()
                )
        );

        GovernanceClosureReadinessResponse readiness =
                service.readiness();

        assertThat(
                readiness.ready()
        ).isFalse();

        assertThat(
                readiness.blockers()
        ).contains(
                "PARTIAL_OUTCOMES",
                "REALIZED_BENEFIT_RATE_BELOW_100",
                "REALIZATION_BAND_NOT_REALIZED",
                "GOVERNANCE_CLOSURE_NOT_READY"
        );

        assertThatThrownBy(() ->
                service.certify()
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void fullyRealizedPortfolioIsReady() {
        when(
                benefitRealizationService.realization()
        ).thenReturn(
                realized()
        );

        GovernanceClosureReadinessResponse readiness =
                service.readiness();

        assertThat(
                readiness.ready()
        ).isTrue();

        assertThat(
                readiness.blockers()
        ).isEmpty();

        assertThat(
                readiness.validationCoveragePct()
        ).isEqualTo(100.0);

        assertThat(
                readiness.realizedBenefitRatePct()
        ).isEqualTo(100.0);
    }

    @Test
    void certificationPersistsChainedHashEvidence() {
        when(
                benefitRealizationService.realization()
        ).thenReturn(
                realized()
        );

        BenefitRealizationSnapshot snapshot =
                realizedSnapshot(
                        2
                );

        when(
                benefitRealizationService.createSnapshot()
        ).thenReturn(
                snapshot
        );

        when(
                repository.nextCertificateNumber()
        ).thenReturn(2);

        GovernanceClosureCertificate previous =
                certificate(
                        1,
                        snapshot,
                        null,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        when(
                repository.findLatest()
        ).thenReturn(
                Optional.of(
                        previous
                )
        );

        GovernanceClosureDossierMaterial material =
                new GovernanceClosureDossierMaterial(
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

        String dossierHash =
                hasher.dossierHash(
                        material
                );

        String certificateHash =
                hasher.certificateHash(
                        2,
                        dossierHash,
                        previous.certificateHash()
                );

        GovernanceClosureCertificate created =
                certificate(
                        2,
                        snapshot,
                        previous.certificateHash(),
                        certificateHash
                );

        when(
                repository.save(
                        any(),
                        eq(2),
                        eq(material),
                        eq(previous.certificateHash()),
                        eq(GovernanceClosureHasher.DOSSIER_VERSION),
                        eq(dossierHash),
                        eq(GovernanceClosureHasher.CERTIFICATE_VERSION),
                        eq(certificateHash),
                        any()
                )
        ).thenReturn(
                created
        );

        GovernanceClosureCertificate result =
                service.certify();

        assertThat(
                result.certificateNumber()
        ).isEqualTo(2);

        assertThat(
                result.previousCertificateHash()
        ).isEqualTo(
                previous.certificateHash()
        );

        verify(
                benefitRealizationService
        ).createSnapshot();
    }

    @Test
    void verificationAcceptsValidCertificateChain() {
        BenefitRealizationSnapshot firstSnapshot =
                realizedSnapshot(
                        1
                );

        GovernanceClosureDossierMaterial firstMaterial =
                material(
                        firstSnapshot
                );

        String firstDossier =
                hasher.dossierHash(
                        firstMaterial
                );

        String firstHash =
                hasher.certificateHash(
                        1,
                        firstDossier,
                        null
                );

        GovernanceClosureCertificate first =
                certificate(
                        1,
                        firstSnapshot,
                        null,
                        firstHash
                );

        BenefitRealizationSnapshot secondSnapshot =
                realizedSnapshot(
                        2
                );

        GovernanceClosureDossierMaterial secondMaterial =
                material(
                        secondSnapshot
                );

        String secondDossier =
                hasher.dossierHash(
                        secondMaterial
                );

        String secondHash =
                hasher.certificateHash(
                        2,
                        secondDossier,
                        firstHash
                );

        GovernanceClosureCertificate second =
                certificate(
                        2,
                        secondSnapshot,
                        firstHash,
                        secondHash
                );

        when(
                repository.findChain()
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                repository.findBenefitSnapshot(
                        firstSnapshot.id()
                )
        ).thenReturn(
                Optional.of(
                        firstSnapshot
                )
        );

        when(
                repository.findBenefitSnapshot(
                        secondSnapshot.id()
                )
        ).thenReturn(
                Optional.of(
                        secondSnapshot
                )
        );

        GovernanceClosureVerificationResponse verification =
                service.verify();

        assertThat(
                verification.valid()
        ).isTrue();

        assertThat(
                verification.certificateCount()
        ).isEqualTo(2);

        assertThat(
                verification.verifiedCertificateCount()
        ).isEqualTo(2);
    }

    private BenefitRealizationResponse realized() {
        return new BenefitRealizationResponse(
                BenefitRealizationBand.REALIZED,
                true,

                1,

                0,
                0,
                0,
                1,
                0,

                1,
                0,

                1,
                0,
                0,

                100.0,
                100.0,

                100.0,
                0.0,

                Instant.now()
        );
    }

    private BenefitRealizationSnapshot realizedSnapshot(
            int snapshotNumber
    ) {
        return new BenefitRealizationSnapshot(
                UUID.randomUUID(),
                snapshotNumber,

                BenefitRealizationBand.REALIZED,
                true,

                1,

                0,
                0,
                0,
                1,
                0,

                1,
                0,

                1,
                0,
                0,

                100.0,
                100.0,

                100.0,
                0.0,

                Instant.now()
        );
    }

    private GovernanceClosureDossierMaterial material(
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

    private GovernanceClosureCertificate certificate(
            int number,
            BenefitRealizationSnapshot snapshot,
            String previousHash,
            String certificateHash
    ) {
        GovernanceClosureDossierMaterial material =
                material(
                        snapshot
                );

        String dossierHash =
                hasher.dossierHash(
                        material
                );

        return new GovernanceClosureCertificate(
                UUID.randomUUID(),

                number,

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
                snapshot.averageAssuranceDebtDelta(),

                previousHash,

                GovernanceClosureHasher.DOSSIER_VERSION,
                dossierHash,

                GovernanceClosureHasher.CERTIFICATE_VERSION,
                certificateHash,

                "Enterprise governance closure.",

                Instant.now()
        );
    }
}
