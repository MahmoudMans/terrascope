package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerHistoryResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerVerificationResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceipt;
import com.terrascope.platform.repository.GovernanceAssuranceReceiptLedgerRepository;
import com.terrascope.platform.repository.GovernanceAssuranceVerificationReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GovernanceAssuranceReceiptLedgerService {

    private final GovernanceAssuranceReceiptLedgerRepository ledgerRepository;

    private final GovernanceAssuranceVerificationReceiptRepository receiptRepository;

    private final GovernanceAssuranceReceiptLedgerHasher hasher;

    public GovernanceAssuranceReceiptLedgerService(
            GovernanceAssuranceReceiptLedgerRepository ledgerRepository,
            GovernanceAssuranceVerificationReceiptRepository receiptRepository,
            GovernanceAssuranceReceiptLedgerHasher hasher
    ) {
        this.ledgerRepository = ledgerRepository;
        this.receiptRepository = receiptRepository;
        this.hasher = hasher;
    }

    @Transactional
    public GovernanceAssuranceReceiptLedgerEntry append(
            GovernanceAssuranceVerificationReceipt receipt
    ) {
        int ledgerNumber =
                ledgerRepository.nextLedgerNumber();

        String previousLedgerHash =
                ledgerRepository
                        .findLatest()
                        .map(
                                GovernanceAssuranceReceiptLedgerEntry::ledgerHash
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
                        receiptPayloadHash,
                        previousLedgerHash
                );

        return ledgerRepository.save(
                UUID.randomUUID(),

                ledgerNumber,

                receipt,

                receiptPayloadHash,
                previousLedgerHash,

                GovernanceAssuranceReceiptLedgerHasher.LEDGER_VERSION,
                ledgerHash
        );
    }

    public GovernanceAssuranceReceiptLedgerHistoryResponse history() {
        return new GovernanceAssuranceReceiptLedgerHistoryResponse(
                ledgerRepository.findHistory()
        );
    }

    public GovernanceAssuranceReceiptLedgerVerificationResponse verify() {
        List<GovernanceAssuranceReceiptLedgerEntry> entries =
                ledgerRepository.findChain();

        String expectedPreviousHash =
                null;

        int expectedLedgerNumber =
                1;

        int verifiedEntryCount =
                0;

        for (
                GovernanceAssuranceReceiptLedgerEntry entry
                : entries
        ) {
            if (
                    entry.ledgerNumber()
                    != expectedLedgerNumber
            ) {
                return invalid(
                        entries.size(),
                        verifiedEntryCount,
                        entry,

                        "Receipt transparency ledger sequence is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            entry.previousLedgerHash(),
                            expectedPreviousHash
                    )
            ) {
                return invalid(
                        entries.size(),
                        verifiedEntryCount,
                        entry,

                        "Receipt transparency ledger previous hash does not match the chain."
                );
            }

            if (
                    !GovernanceAssuranceReceiptLedgerHasher.LEDGER_VERSION
                            .equals(
                                    entry.ledgerVersion()
                            )
            ) {
                return invalid(
                        entries.size(),
                        verifiedEntryCount,
                        entry,

                        "Unsupported receipt transparency ledger version."
                );
            }

            GovernanceAssuranceVerificationReceipt receipt =
                    receiptRepository
                            .findReceipt(
                                    entry.receiptId()
                            )
                            .orElse(
                                    null
                            );

            if (
                    receipt == null
            ) {
                return invalid(
                        entries.size(),
                        verifiedEntryCount,
                        entry,

                        "Verification receipt referenced by the transparency ledger is missing."
                );
            }

            if (
                    entry.receiptNumber()
                    != receipt.receiptNumber()

                    || !entry.verificationResult()
                            .equals(
                                    receipt.verificationResult()
                            )

                    || !entry.envelopeHash()
                            .equals(
                                    receipt.envelopeHash()
                            )

                    || !Objects.equals(
                            entry.bundleHash(),
                            receipt.bundleHash()
                    )
            ) {
                return invalid(
                        entries.size(),
                        verifiedEntryCount,
                        entry,

                        "Receipt transparency ledger metadata does not match its verification receipt."
                );
            }

            String calculatedReceiptPayloadHash =
                    hasher.receiptPayloadHash(
                            receipt
                    );

            if (
                    !calculatedReceiptPayloadHash
                            .equals(
                                    entry.receiptPayloadHash()
                            )
            ) {
                return invalid(
                        entries.size(),
                        verifiedEntryCount,
                        entry,

                        "Verification receipt payload has changed since transparency anchoring."
                );
            }

            String calculatedLedgerHash =
                    hasher.ledgerHash(
                            entry.ledgerNumber(),
                            entry.receiptId(),
                            entry.receiptPayloadHash(),
                            entry.previousLedgerHash()
                    );

            if (
                    !calculatedLedgerHash
                            .equals(
                                    entry.ledgerHash()
                            )
            ) {
                return invalid(
                        entries.size(),
                        verifiedEntryCount,
                        entry,

                        "Receipt transparency ledger hash does not match its canonical payload."
                );
            }

            expectedPreviousHash =
                    entry.ledgerHash();

            expectedLedgerNumber++;

            verifiedEntryCount++;
        }

        return new GovernanceAssuranceReceiptLedgerVerificationResponse(
                true,

                entries.size(),
                verifiedEntryCount,

                null,
                null,

                null,
                null,

                entries.isEmpty()
                        ? "No assurance verification receipts have been anchored."
                        : "Verification receipt transparency ledger verified.",

                Instant.now()
        );
    }

    private GovernanceAssuranceReceiptLedgerVerificationResponse invalid(
            int entryCount,
            int verifiedEntryCount,

            GovernanceAssuranceReceiptLedgerEntry entry,

            String message
    ) {
        return new GovernanceAssuranceReceiptLedgerVerificationResponse(
                false,

                entryCount,
                verifiedEntryCount,

                entry.id(),
                entry.ledgerNumber(),

                entry.receiptId(),
                entry.receiptNumber(),

                message,

                Instant.now()
        );
    }
}
