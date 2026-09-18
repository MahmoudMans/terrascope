package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceiptHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import com.terrascope.platform.api.model.ImportGovernanceOfflineTrustVerificationReceiptRequest;
import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptDocument;
import com.terrascope.platform.repository.GovernanceOfflineTrustVerificationReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GovernanceOfflineTrustVerificationReceiptService {

    private static final Pattern SHA256_PATTERN =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private final GovernanceOfflineTrustVerificationReceiptCodec codec;

    private final GovernanceTrustLineagePinnedVerifier verifier;

    private final GovernanceOfflineTrustVerificationReceiptRepository repository;

    private final GovernanceOfflineTrustReceiptLedgerService ledgerService;

    public GovernanceOfflineTrustVerificationReceiptService(
            GovernanceOfflineTrustVerificationReceiptCodec codec,
            GovernanceTrustLineagePinnedVerifier verifier,
            GovernanceOfflineTrustVerificationReceiptRepository repository,
            GovernanceOfflineTrustReceiptLedgerService ledgerService
    ) {
        this.codec = codec;
        this.verifier = verifier;
        this.repository = repository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public GovernanceOfflineTrustVerificationReceipt importReceipt(
            ImportGovernanceOfflineTrustVerificationReceiptRequest request
    ) {
        if (
                request == null

                || request.receiptJson() == null

                || request.receiptJson()
                        .isBlank()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offline trust verification receipt JSON is required."
            );
        }

        GovernanceOfflineTrustVerificationReceiptDocument document;

        try {
            document =
                    codec.decode(
                            request.receiptJson()
                    );

        } catch (
                IllegalArgumentException exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offline trust verification receipt is invalid JSON."
            );
        }

        if (
                !GovernanceOfflineTrustVerificationReceiptCodec.VERIFIER_VERSION
                        .equals(
                                document.verifierVersion()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported standalone trust verifier version."
            );
        }

        if (
                document.receiptHash() == null

                || !SHA256_PATTERN
                        .matcher(
                                document.receiptHash()
                        )
                        .matches()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offline trust verification receipt hash is invalid."
            );
        }

        if (
                !codec.isCanonicalDocument(
                        request.receiptJson()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Offline trust verification receipt is not canonical."
            );
        }

        String calculatedReceiptHash =
                codec.receiptHash(
                        codec.material(
                                document
                        )
                );

        if (
                !calculatedReceiptHash
                        .equals(
                                document.receiptHash()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Offline trust verification receipt hash does not match its canonical material."
            );
        }

        try {
            Instant.parse(
                    document.verifiedAt()
            );

        } catch (
                Exception exception
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Offline verification timestamp is invalid."
            );
        }

        GovernanceTrustLineagePinnedVerificationResponse reverified =
                verifier.verify(
                        document
                                .envelopeJson()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),

                        document.trustedRootFingerprint()
                );

        if (
                !matches(
                        document,
                        reverified
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Offline verification receipt does not match fresh cryptographic re-verification."
            );
        }

        var existing =
                repository.findByReceiptHash(
                        document.receiptHash()
                );

        if (
                existing.isPresent()
        ) {
            GovernanceOfflineTrustVerificationReceipt imported =
                    existing.get();

            ledgerService.anchor(
                    imported
            );

            return imported;
        }

        GovernanceOfflineTrustVerificationReceipt imported =
                repository.save(
                        UUID.randomUUID(),

                        repository.nextImportNumber(),

                        document,

                        reverified
                );

        ledgerService.anchor(
                imported
        );

        return imported;
    }

    public GovernanceOfflineTrustVerificationReceiptHistoryResponse history() {
        return new GovernanceOfflineTrustVerificationReceiptHistoryResponse(
                repository.findHistory()
        );
    }

    private boolean matches(
            GovernanceOfflineTrustVerificationReceiptDocument document,
            GovernanceTrustLineagePinnedVerificationResponse verification
    ) {
        String sourcePackageId =
                verification.sourcePackageId() == null
                        ? null
                        : verification
                                .sourcePackageId()
                                .toString();

        return Objects.equals(
                        document.sourcePackageId(),
                        sourcePackageId
                )

                && Objects.equals(
                        document.sourcePackageNumber(),
                        verification.sourcePackageNumber()
                )

                && Objects.equals(
                        document.envelopeHash(),
                        verification.envelopeHash()
                )

                && Objects.equals(
                        document.packageHash(),
                        verification.packageHash()
                )

                && Objects.equals(
                        document.trustedRootFingerprint(),
                        verification.trustedRootFingerprint()
                )

                && Objects.equals(
                        document.presentedRootFingerprint(),
                        verification.presentedRootFingerprint()
                )

                && document.valid()
                   == verification.valid()

                && document.packageHashValid()
                   == verification.packageHashValid()

                && document.canonicalPayloadValid()
                   == verification.canonicalPayloadValid()

                && document.lineageValid()
                   == verification.lineageValid()

                && document.rootPinValid()
                   == verification.rootPinValid()

                && document.transitionCount()
                   == verification.transitionCount()

                && document.verifiedTransitionCount()
                   == verification.verifiedTransitionCount()

                && document.trustAnchorCount()
                   == verification.trustAnchorCount()

                && Objects.equals(
                        document.rootKeyId(),
                        verification.rootKeyId()
                )

                && Objects.equals(
                        document.terminalKeyId(),
                        verification.terminalKeyId()
                )

                && Objects.equals(
                        document.message(),
                        verification.message()
                );
    }
}
