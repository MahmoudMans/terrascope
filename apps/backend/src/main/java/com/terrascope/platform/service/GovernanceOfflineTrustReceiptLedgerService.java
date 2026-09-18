package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerHistoryResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerVerificationResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptLedgerRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustVerificationReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GovernanceOfflineTrustReceiptLedgerService {

    private final GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository;

    private final GovernanceOfflineTrustVerificationReceiptRepository receiptRepository;

    private final GovernanceOfflineTrustReceiptLedgerHasher hasher;

    private final GovernanceMonotonicSequenceService allocatorService;

    public GovernanceOfflineTrustReceiptLedgerService(
            GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository,
            GovernanceOfflineTrustVerificationReceiptRepository receiptRepository,
            GovernanceOfflineTrustReceiptLedgerHasher hasher,
            GovernanceMonotonicSequenceService allocatorService
    ) {
        this.ledgerRepository = ledgerRepository;
        this.receiptRepository = receiptRepository;
        this.hasher = hasher;
        this.allocatorService = allocatorService;
    }

    @Transactional
    public GovernanceOfflineTrustReceiptLedgerEntry anchor(
            GovernanceOfflineTrustVerificationReceipt receipt
    ) {
        Optional<GovernanceOfflineTrustReceiptLedgerEntry> existing =
                ledgerRepository.findByReceiptId(
                        receipt.id()
                );

        if (
                existing.isPresent()
        ) {
            return existing.get();
        }

        int ledgerNumber =
                allocatorService.nextLedgerNumber();

        String previousLedgerHash =
                ledgerRepository
                        .findLatest()
                        .map(
                                GovernanceOfflineTrustReceiptLedgerEntry::ledgerHash
                        )
                        .orElse(
                                null
                        );

        String receiptPayloadHash =
                hasher.receiptPayloadHash(
                        receipt
                );

        String ledgerHash =
                hasher.ledgerHash(
                        ledgerNumber,

                        receipt.id(),

                        receipt.importNumber(),

                        receipt.verificationResult(),

                        receipt.receiptHash(),

                        receiptPayloadHash,

                        previousLedgerHash
                );

        return ledgerRepository.save(
                UUID.randomUUID(),

                ledgerNumber,

                receipt.id(),

                receipt.importNumber(),

                receipt.verificationResult(),

                receipt.receiptHash(),

                receiptPayloadHash,

                previousLedgerHash,

                GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION,

                ledgerHash
        );
    }

    public GovernanceOfflineTrustReceiptLedgerHistoryResponse history() {
        return new GovernanceOfflineTrustReceiptLedgerHistoryResponse(
                ledgerRepository.findHistory()
        );
    }

    public GovernanceOfflineTrustReceiptLedgerVerificationResponse verify() {
        List<GovernanceOfflineTrustReceiptLedgerEntry> chain =
                ledgerRepository.findChain();

        String expectedPreviousHash =
                null;

        int expectedLedgerNumber =
                1;

        int verified =
                0;

        for (
                GovernanceOfflineTrustReceiptLedgerEntry entry
                : chain
        ) {
            if (
                    entry.ledgerNumber()
                    != expectedLedgerNumber
            ) {
                return invalid(
                        chain.size(),
                        verified,
                        entry.ledgerNumber(),
                        expectedPreviousHash,
                        "Offline receipt ledger numbering is not contiguous."
                );
            }

            if (
                    !java.util.Objects.equals(
                            entry.previousLedgerHash(),
                            expectedPreviousHash
                    )
            ) {
                return invalid(
                        chain.size(),
                        verified,
                        entry.ledgerNumber(),
                        expectedPreviousHash,
                        "Offline receipt ledger previous hash does not match."
                );
            }

            Optional<GovernanceOfflineTrustVerificationReceipt> receiptOptional =
                    receiptRepository.findById(
                            entry.receiptId()
                    );

            if (
                    receiptOptional.isEmpty()
            ) {
                return invalid(
                        chain.size(),
                        verified,
                        entry.ledgerNumber(),
                        expectedPreviousHash,
                        "Offline receipt ledger references a missing receipt."
                );
            }

            GovernanceOfflineTrustVerificationReceipt receipt =
                    receiptOptional.get();

            if (
                    receipt.importNumber()
                    != entry.receiptImportNumber()

                    || !receipt.verificationResult()
                            .equals(
                                    entry.verificationResult()
                            )

                    || !receipt.receiptHash()
                            .equals(
                                    entry.receiptHash()
                            )
            ) {
                return invalid(
                        chain.size(),
                        verified,
                        entry.ledgerNumber(),
                        expectedPreviousHash,
                        "Offline receipt ledger metadata no longer matches the receipt row."
                );
            }

            String receiptPayloadHash =
                    hasher.receiptPayloadHash(
                            receipt
                    );

            if (
                    !receiptPayloadHash.equals(
                            entry.receiptPayloadHash()
                    )
            ) {
                return invalid(
                        chain.size(),
                        verified,
                        entry.ledgerNumber(),
                        expectedPreviousHash,
                        "Offline verification receipt row has been modified after ledger anchoring."
                );
            }

            if (
                    !GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION
                            .equals(
                                    entry.ledgerVersion()
                            )
            ) {
                return invalid(
                        chain.size(),
                        verified,
                        entry.ledgerNumber(),
                        expectedPreviousHash,
                        "Offline receipt ledger version is unsupported."
                );
            }

            String calculatedLedgerHash =
                    hasher.ledgerHash(
                            entry.ledgerNumber(),

                            entry.receiptId(),

                            entry.receiptImportNumber(),

                            entry.verificationResult(),

                            entry.receiptHash(),

                            entry.receiptPayloadHash(),

                            entry.previousLedgerHash()
                    );

            if (
                    !calculatedLedgerHash.equals(
                            entry.ledgerHash()
                    )
            ) {
                return invalid(
                        chain.size(),
                        verified,
                        entry.ledgerNumber(),
                        expectedPreviousHash,
                        "Offline receipt ledger hash verification failed."
                );
            }

            expectedPreviousHash =
                    entry.ledgerHash();

            expectedLedgerNumber++;

            verified++;
        }

        return new GovernanceOfflineTrustReceiptLedgerVerificationResponse(
                true,

                chain.size(),
                verified,

                null,

                expectedPreviousHash,

                chain.isEmpty()
                        ? "Offline verification receipt ledger is empty and structurally valid."
                        : "Offline verification receipt transparency ledger is valid.",

                Instant.now()
        );
    }

    private GovernanceOfflineTrustReceiptLedgerVerificationResponse invalid(
            int count,
            int verified,

            Integer brokenLedgerNumber,

            String headLedgerHash,

            String message
    ) {
        return new GovernanceOfflineTrustReceiptLedgerVerificationResponse(
                false,

                count,
                verified,

                brokenLedgerNumber,

                headLedgerHash,

                message,

                Instant.now()
        );
    }
}
