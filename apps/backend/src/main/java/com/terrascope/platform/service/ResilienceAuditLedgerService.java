package com.terrascope.platform.service;

import com.terrascope.platform.api.model.IncidentCase;
import com.terrascope.platform.api.model.ResilienceAuditLedgerHistoryResponse;
import com.terrascope.platform.api.model.ResilienceAuditLedgerPreview;
import com.terrascope.platform.api.model.ResilienceAuditLedgerVerificationResponse;
import com.terrascope.platform.api.model.ResilienceAuditReceipt;
import com.terrascope.platform.domain.IncidentStatus;
import com.terrascope.platform.domain.ResilienceAuditLedgerMaterial;
import com.terrascope.platform.domain.ResilienceEvidenceAnchor;
import com.terrascope.platform.repository.IncidentResponseRepository;
import com.terrascope.platform.repository.ResilienceAuditLedgerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResilienceAuditLedgerService {

    private final ResilienceAuditLedgerRepository repository;
    private final IncidentResponseRepository incidentRepository;
    private final ResilienceAuditLedgerHasher hasher;

    public ResilienceAuditLedgerService(
            ResilienceAuditLedgerRepository repository,
            IncidentResponseRepository incidentRepository,
            ResilienceAuditLedgerHasher hasher
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.hasher = hasher;
    }

    public ResilienceAuditLedgerPreview preview(
            UUID incidentId
    ) {
        requireClosedIncident(
                incidentId
        );

        ResilienceEvidenceAnchor evidencePackage =
                requireLatestPackage(
                        incidentId
                );

        Optional<ResilienceAuditReceipt> latestReceipt =
                repository.findLatestReceipt(
                        incidentId
                );

        int nextReceiptNumber =
                latestReceipt
                        .map(receipt ->
                                receipt.receiptNumber()
                                        + 1
                        )
                        .orElse(1);

        String previousReceiptHash =
                latestReceipt
                        .map(
                                ResilienceAuditReceipt::ledgerHash
                        )
                        .orElse(null);

        ResilienceAuditLedgerMaterial material =
                material(
                        incidentId,
                        nextReceiptNumber,
                        evidencePackage,
                        previousReceiptHash
                );

        return new ResilienceAuditLedgerPreview(
                incidentId,

                nextReceiptNumber,

                evidencePackage.id(),
                evidencePackage.packageNumber(),

                evidencePackage.manifestVersion(),
                evidencePackage.manifestHash(),

                repository.receiptExistsForPackage(
                        incidentId,
                        evidencePackage.id()
                ),

                previousReceiptHash,

                ResilienceAuditLedgerHasher.LEDGER_VERSION,
                hasher.hash(
                        material
                ),

                Instant.now()
        );
    }

    public ResilienceAuditLedgerHistoryResponse history(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        return new ResilienceAuditLedgerHistoryResponse(
                incidentId,
                repository.findHistory(
                        incidentId
                )
        );
    }

    @Transactional
    public ResilienceAuditReceipt createReceipt(
            UUID incidentId
    ) {
        requireClosedIncident(
                incidentId
        );

        repository.lockIncident(
                incidentId
        );

        ResilienceEvidenceAnchor evidencePackage =
                requireLatestPackage(
                        incidentId
                );

        if (
                repository.receiptExistsForPackage(
                        incidentId,
                        evidencePackage.id()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The latest evidence package already has an audit receipt."
            );
        }

        Optional<ResilienceAuditReceipt> latestReceipt =
                repository.findLatestReceipt(
                        incidentId
                );

        int receiptNumber =
                repository.nextReceiptNumber(
                        incidentId
                );

        String previousReceiptHash =
                latestReceipt
                        .map(
                                ResilienceAuditReceipt::ledgerHash
                        )
                        .orElse(null);

        ResilienceAuditLedgerMaterial material =
                material(
                        incidentId,
                        receiptNumber,
                        evidencePackage,
                        previousReceiptHash
                );

        String ledgerHash =
                hasher.hash(
                        material
                );

        ResilienceAuditReceipt receipt =
                repository.saveReceipt(
                        UUID.randomUUID(),
                        material,
                        ResilienceAuditLedgerHasher.LEDGER_VERSION,
                        ledgerHash
                );

        incidentRepository.appendTimeline(
                UUID.randomUUID(),
                incidentId,
                "RESILIENCE_AUDIT_RECEIPT_CREATED",
                "Tamper-evident audit receipt #"
                        + receipt.receiptNumber()
                        + " chained evidence package #"
                        + receipt.evidencePackageNumber()
                        + " with ledger hash "
                        + receipt.ledgerHash()
                        + "."
        );

        return receipt;
    }

    public ResilienceAuditLedgerVerificationResponse verify(
            UUID incidentId
    ) {
        requireIncident(
                incidentId
        );

        List<ResilienceAuditReceipt> receipts =
                repository.findChain(
                        incidentId
                );

        String expectedPreviousHash =
                null;

        int verifiedCount =
                0;

        for (
                ResilienceAuditReceipt receipt
                : receipts
        ) {
            if (
                    !Objects.equals(
                            receipt.previousReceiptHash(),
                            expectedPreviousHash
                    )
            ) {
                return invalid(
                        incidentId,
                        receipts.size(),
                        verifiedCount,
                        receipt,
                        "Previous receipt hash does not match the ledger chain."
                );
            }

            Optional<ResilienceEvidenceAnchor> sourcePackage =
                    repository.findEvidencePackageById(
                            incidentId,
                            receipt.evidencePackageId()
                    );

            if (
                    sourcePackage.isEmpty()
            ) {
                return invalid(
                        incidentId,
                        receipts.size(),
                        verifiedCount,
                        receipt,
                        "The source evidence package is missing."
                );
            }

            ResilienceEvidenceAnchor anchor =
                    sourcePackage.get();

            if (
                    anchor.packageNumber()
                    != receipt.evidencePackageNumber()
                    || !anchor.manifestVersion()
                            .equals(
                                    receipt.packageManifestVersion()
                            )
                    || !anchor.manifestHash()
                            .equals(
                                    receipt.packageManifestHash()
                            )
            ) {
                return invalid(
                        incidentId,
                        receipts.size(),
                        verifiedCount,
                        receipt,
                        "The source evidence package no longer matches the audit receipt."
                );
            }

            if (
                    !receipt.ledgerVersion()
                            .equals(
                                    ResilienceAuditLedgerHasher.LEDGER_VERSION
                            )
            ) {
                return invalid(
                        incidentId,
                        receipts.size(),
                        verifiedCount,
                        receipt,
                        "Unsupported audit ledger version."
                );
            }

            ResilienceAuditLedgerMaterial material =
                    new ResilienceAuditLedgerMaterial(
                            receipt.incidentId(),

                            receipt.receiptNumber(),

                            receipt.evidencePackageId(),
                            receipt.evidencePackageNumber(),

                            receipt.packageManifestVersion(),
                            receipt.packageManifestHash(),

                            receipt.previousReceiptHash()
                    );

            String recomputedHash =
                    hasher.hash(
                            material
                    );

            if (
                    !recomputedHash.equals(
                            receipt.ledgerHash()
                    )
            ) {
                return invalid(
                        incidentId,
                        receipts.size(),
                        verifiedCount,
                        receipt,
                        "The receipt ledger hash does not match its canonical payload."
                );
            }

            expectedPreviousHash =
                    receipt.ledgerHash();

            verifiedCount++;
        }

        return new ResilienceAuditLedgerVerificationResponse(
                incidentId,

                true,

                receipts.size(),
                verifiedCount,

                null,
                null,

                receipts.isEmpty()
                        ? "Audit ledger is empty."
                        : "Audit ledger integrity verified.",

                Instant.now()
        );
    }

    private ResilienceAuditLedgerVerificationResponse invalid(
            UUID incidentId,
            int receiptCount,
            int verifiedCount,
            ResilienceAuditReceipt brokenReceipt,
            String message
    ) {
        return new ResilienceAuditLedgerVerificationResponse(
                incidentId,

                false,

                receiptCount,
                verifiedCount,

                brokenReceipt.id(),
                brokenReceipt.receiptNumber(),

                message,

                Instant.now()
        );
    }

    private ResilienceAuditLedgerMaterial material(
            UUID incidentId,
            int receiptNumber,
            ResilienceEvidenceAnchor evidencePackage,
            String previousReceiptHash
    ) {
        return new ResilienceAuditLedgerMaterial(
                incidentId,

                receiptNumber,

                evidencePackage.id(),
                evidencePackage.packageNumber(),

                evidencePackage.manifestVersion(),
                evidencePackage.manifestHash(),

                previousReceiptHash
        );
    }

    private ResilienceEvidenceAnchor requireLatestPackage(
            UUID incidentId
    ) {
        return repository
                .findLatestEvidencePackage(
                        incidentId
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "A resilience evidence package is required before creating an audit receipt."
                        )
                );
    }

    private IncidentCase requireClosedIncident(
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
                    "Audit ledger operations require a CLOSED incident."
            );
        }

        return incident;
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
