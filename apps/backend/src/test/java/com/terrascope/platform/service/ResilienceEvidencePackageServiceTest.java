package com.terrascope.platform.service;

import com.terrascope.platform.api.model.EvidencePackageVerificationResponse;
import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.ResilienceEvidencePackage;
import com.terrascope.platform.api.model.ResilienceEvidencePackagePreview;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.ResilienceEvidenceManifest;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.ResilienceEvidencePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilienceEvidencePackageServiceTest {

    private ResilienceEvidencePackageRepository repository;
    private IncidentResponseRepository incidentRepository;
    private EvidenceManifestHasher hasher;
    private ResilienceEvidencePackageService service;

    private UUID incidentId;
    private UUID certificationId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        ResilienceEvidencePackageRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        hasher =
                new EvidenceManifestHasher();

        service =
                new ResilienceEvidencePackageService(
                        repository,
                        incidentRepository,
                        hasher
                );

        incidentId =
                UUID.randomUUID();

        certificationId =
                UUID.randomUUID();

        when(
                incidentRepository.findById(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        incident()
                )
        );
    }

    @Test
    void previewRequiresCertification() {
        when(
                repository.loadManifest(
                        incidentId
                )
        ).thenReturn(
                new ResilienceEvidenceManifest(
                        incidentId,
                        null,
                        0,
                        0,
                        0,
                        1,
                        1,
                        1,
                        1,
                        0,
                        10
                )
        );

        assertThatThrownBy(() ->
                service.preview(
                        incidentId
                )
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void previewProducesDeterministicHash() {
        ResilienceEvidenceManifest manifest =
                manifest();

        when(
                repository.loadManifest(
                        incidentId
                )
        ).thenReturn(
                manifest
        );

        ResilienceEvidencePackagePreview preview =
                service.preview(
                        incidentId
                );

        assertThat(
                preview.manifestHash()
        ).isEqualTo(
                hasher.hash(
                        manifest
                )
        );

        assertThat(
                preview.manifestVersion()
        ).isEqualTo(
                EvidenceManifestHasher.MANIFEST_VERSION
        );
    }

    @Test
    void createPersistsPackageAndTimelineEvidence() {
        ResilienceEvidenceManifest manifest =
                manifest();

        when(
                repository.loadManifest(
                        incidentId
                )
        ).thenReturn(
                manifest
        );

        when(
                repository.nextPackageNumber(
                        incidentId
                )
        ).thenReturn(1);

        when(
                repository.savePackage(
                        any(),
                        eq(1),
                        eq(manifest),
                        eq(
                                EvidenceManifestHasher.MANIFEST_VERSION
                        ),
                        eq(
                                hasher.hash(
                                        manifest
                                )
                        ),
                        any()
                )
        ).thenReturn(
                evidencePackage(
                        manifest
                )
        );

        ResilienceEvidencePackage result =
                service.create(
                        incidentId
                );

        assertThat(
                result.packageNumber()
        ).isEqualTo(1);

        verify(
                repository
        ).lockIncident(
                incidentId
        );

        verify(
                incidentRepository
        ).appendTimeline(
                any(),
                eq(incidentId),
                eq(
                        "RESILIENCE_EVIDENCE_PACKAGE_CREATED"
                ),
                any()
        );
    }

    @Test
    void verifiesStoredPackageIntegrity() {
        ResilienceEvidenceManifest manifest =
                manifest();

        ResilienceEvidencePackage evidencePackage =
                evidencePackage(
                        manifest
                );

        when(
                repository.findById(
                        incidentId,
                        evidencePackage.id()
                )
        ).thenReturn(
                Optional.of(
                        evidencePackage
                )
        );

        EvidencePackageVerificationResponse verification =
                service.verify(
                        incidentId,
                        evidencePackage.id()
                );

        assertThat(
                verification.valid()
        ).isTrue();

        assertThat(
                verification.storedHash()
        ).isEqualTo(
                verification.recomputedHash()
        );
    }

    private IncidentCase incident() {
        Instant opened =
                Instant.parse(
                        "2026-09-17T10:00:00Z"
                );

        Instant closed =
                Instant.parse(
                        "2026-09-17T12:00:00Z"
                );

        return new IncidentCase(
                incidentId,
                null,
                "source-1",
                "earthquakes",
                "Verification Incident",
                "",
                36.8,
                10.2,
                IncidentSeverity.SEVERE,
                IncidentStatus.CLOSED,
                "Command",
                0,
                opened,
                closed,
                closed
        );
    }

    private ResilienceEvidenceManifest manifest() {
        return new ResilienceEvidenceManifest(
                incidentId,

                certificationId,
                1,
                95.0,
                1,

                1,
                1,
                1,
                2,
                1,
                20
        );
    }

    private ResilienceEvidencePackage evidencePackage(
            ResilienceEvidenceManifest manifest
    ) {
        return new ResilienceEvidencePackage(
                UUID.randomUUID(),
                incidentId,
                1,

                certificationId,
                1,
                95.0,
                1,

                1,
                1,
                1,
                2,
                1,
                20,

                EvidenceManifestHasher.MANIFEST_VERSION,
                hasher.hash(
                        manifest
                ),

                "Evidence summary.",

                Instant.now()
        );
    }
}
