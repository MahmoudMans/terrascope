package com.terrascope.platform.service;

import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.ResilienceAuditLedgerVerificationResponse;
import com.terrascope.platform.api.model.ResilienceAuditReceipt;
import com.terrascope.platform.domain.IncidentSeverity;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.ResilienceAuditLedgerMaterial;
import com.terrascope.platform.domain.ResilienceEvidenceAnchor;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.ResilienceAuditLedgerRepository;
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

class ResilienceAuditLedgerServiceTest {

    private ResilienceAuditLedgerRepository repository;
    private IncidentResponseRepository incidentRepository;
    private ResilienceAuditLedgerHasher hasher;
    private ResilienceAuditLedgerService service;

    private UUID incidentId;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        ResilienceAuditLedgerRepository.class
                );

        incidentRepository =
                mock(
                        IncidentResponseRepository.class
                );

        hasher =
                new ResilienceAuditLedgerHasher();

        service =
                new ResilienceAuditLedgerService(
                        repository,
                        incidentRepository,
                        hasher
                );

        incidentId =
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
    void previewRequiresEvidencePackage() {
        when(
                repository.findLatestEvidencePackage(
                        incidentId
                )
        ).thenReturn(
                Optional.empty()
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
    void createsGenesisReceiptAndTimelineEvidence() {
        ResilienceEvidenceAnchor anchor =
                anchor(
                        1,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        when(
                repository.findLatestEvidencePackage(
                        incidentId
                )
        ).thenReturn(
                Optional.of(
                        anchor
                )
        );

        when(
                repository.receiptExistsForPackage(
                        incidentId,
                        anchor.id()
                )
        ).thenReturn(
                false
        );

        when(
                repository.findLatestReceipt(
                        incidentId
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                repository.nextReceiptNumber(
                        incidentId
                )
        ).thenReturn(1);

        ResilienceAuditLedgerMaterial material =
                new ResilienceAuditLedgerMaterial(
                        incidentId,
                        1,
                        anchor.id(),
                        1,
                        anchor.manifestVersion(),
                        anchor.manifestHash(),
                        null
                );

        ResilienceAuditReceipt receipt =
                receipt(
                        1,
                        anchor,
                        null,
                        hasher.hash(
                                material
                        )
                );

        when(
                repository.saveReceipt(
                        any(),
                        eq(material),
                        eq(
                                ResilienceAuditLedgerHasher.LEDGER_VERSION
                        ),
                        eq(
                                receipt.ledgerHash()
                        )
                )
        ).thenReturn(
                receipt
        );

        ResilienceAuditReceipt result =
                service.createReceipt(
                        incidentId
                );

        assertThat(
                result.receiptNumber()
        ).isEqualTo(1);

        assertThat(
                result.previousReceiptHash()
        ).isNull();

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
                        "RESILIENCE_AUDIT_RECEIPT_CREATED"
                ),
                any()
        );
    }

    @Test
    void verifiesTwoReceiptChain() {
        ResilienceEvidenceAnchor firstAnchor =
                anchor(
                        1,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        ResilienceAuditLedgerMaterial firstMaterial =
                new ResilienceAuditLedgerMaterial(
                        incidentId,
                        1,
                        firstAnchor.id(),
                        1,
                        firstAnchor.manifestVersion(),
                        firstAnchor.manifestHash(),
                        null
                );

        String firstHash =
                hasher.hash(
                        firstMaterial
                );

        ResilienceAuditReceipt first =
                receipt(
                        1,
                        firstAnchor,
                        null,
                        firstHash
                );

        ResilienceEvidenceAnchor secondAnchor =
                anchor(
                        2,
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                );

        ResilienceAuditLedgerMaterial secondMaterial =
                new ResilienceAuditLedgerMaterial(
                        incidentId,
                        2,
                        secondAnchor.id(),
                        2,
                        secondAnchor.manifestVersion(),
                        secondAnchor.manifestHash(),
                        firstHash
                );

        ResilienceAuditReceipt second =
                receipt(
                        2,
                        secondAnchor,
                        firstHash,
                        hasher.hash(
                                secondMaterial
                        )
                );

        when(
                repository.findChain(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                repository.findEvidencePackageById(
                        incidentId,
                        firstAnchor.id()
                )
        ).thenReturn(
                Optional.of(
                        firstAnchor
                )
        );

        when(
                repository.findEvidencePackageById(
                        incidentId,
                        secondAnchor.id()
                )
        ).thenReturn(
                Optional.of(
                        secondAnchor
                )
        );

        ResilienceAuditLedgerVerificationResponse verification =
                service.verify(
                        incidentId
                );

        assertThat(
                verification.valid()
        ).isTrue();

        assertThat(
                verification.receiptCount()
        ).isEqualTo(2);

        assertThat(
                verification.verifiedReceiptCount()
        ).isEqualTo(2);
    }

    @Test
    void detectsBrokenPreviousReceiptLink() {
        ResilienceEvidenceAnchor firstAnchor =
                anchor(
                        1,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        ResilienceAuditLedgerMaterial firstMaterial =
                new ResilienceAuditLedgerMaterial(
                        incidentId,
                        1,
                        firstAnchor.id(),
                        1,
                        firstAnchor.manifestVersion(),
                        firstAnchor.manifestHash(),
                        null
                );

        String firstHash =
                hasher.hash(
                        firstMaterial
                );

        ResilienceAuditReceipt first =
                receipt(
                        1,
                        firstAnchor,
                        null,
                        firstHash
                );

        ResilienceEvidenceAnchor secondAnchor =
                anchor(
                        2,
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                );

        String brokenPrevious =
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

        ResilienceAuditLedgerMaterial brokenMaterial =
                new ResilienceAuditLedgerMaterial(
                        incidentId,
                        2,
                        secondAnchor.id(),
                        2,
                        secondAnchor.manifestVersion(),
                        secondAnchor.manifestHash(),
                        brokenPrevious
                );

        ResilienceAuditReceipt second =
                receipt(
                        2,
                        secondAnchor,
                        brokenPrevious,
                        hasher.hash(
                                brokenMaterial
                        )
                );

        when(
                repository.findChain(
                        incidentId
                )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                repository.findEvidencePackageById(
                        incidentId,
                        firstAnchor.id()
                )
        ).thenReturn(
                Optional.of(
                        firstAnchor
                )
        );

        ResilienceAuditLedgerVerificationResponse verification =
                service.verify(
                        incidentId
                );

        assertThat(
                verification.valid()
        ).isFalse();

        assertThat(
                verification.verifiedReceiptCount()
        ).isEqualTo(1);

        assertThat(
                verification.brokenReceiptNumber()
        ).isEqualTo(2);
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

    private ResilienceEvidenceAnchor anchor(
            int packageNumber,
            String manifestHash
    ) {
        return new ResilienceEvidenceAnchor(
                UUID.randomUUID(),
                packageNumber,
                "TS-RESILIENCE-EVIDENCE-V1",
                manifestHash
        );
    }

    private ResilienceAuditReceipt receipt(
            int receiptNumber,
            ResilienceEvidenceAnchor anchor,
            String previousHash,
            String ledgerHash
    ) {
        return new ResilienceAuditReceipt(
                UUID.randomUUID(),
                incidentId,

                receiptNumber,

                anchor.id(),
                anchor.packageNumber(),

                anchor.manifestVersion(),
                anchor.manifestHash(),

                previousHash,

                ResilienceAuditLedgerHasher.LEDGER_VERSION,
                ledgerHash,

                Instant.now()
        );
    }
}
