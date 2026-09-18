package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceTrustLineagePinReceipt;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinReceiptHistoryResponse;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationRequest;
import com.terrascope.platform.api.model.GovernanceTrustLineagePinnedVerificationResponse;
import com.terrascope.platform.repository.GovernanceTrustLineagePinReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GovernanceTrustLineagePinReceiptService {

    private static final Pattern SHA256_PATTERN =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private final GovernanceTrustLineagePinnedVerifier verifier;

    private final GovernanceTrustLineagePinReceiptRepository repository;

    public GovernanceTrustLineagePinReceiptService(
            GovernanceTrustLineagePinnedVerifier verifier,
            GovernanceTrustLineagePinReceiptRepository repository
    ) {
        this.verifier = verifier;
        this.repository = repository;
    }

    public GovernanceTrustLineagePinnedVerificationResponse verify(
            GovernanceTrustLineagePinnedVerificationRequest request
    ) {
        requireEnvelope(
                request
        );

        return verifier.verify(
                request
                        .envelopeJson()
                        .getBytes(
                                StandardCharsets.UTF_8
                        ),

                request.trustedRootFingerprint()
        );
    }

    @Transactional
    public GovernanceTrustLineagePinReceipt createReceipt(
            GovernanceTrustLineagePinnedVerificationRequest request
    ) {
        requireEnvelope(
                request
        );

        String normalizedPin =
                request
                        .trustedRootFingerprint()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                !SHA256_PATTERN
                        .matcher(
                                normalizedPin
                        )
                        .matches()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid 64-character SHA-256 trusted root fingerprint is required to persist a verification receipt."
            );
        }

        GovernanceTrustLineagePinnedVerificationResponse verification =
                verifier.verify(
                        request
                                .envelopeJson()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                ),

                        normalizedPin
                );

        return repository.save(
                UUID.randomUUID(),

                repository.nextReceiptNumber(),

                verification
        );
    }

    public GovernanceTrustLineagePinReceiptHistoryResponse history() {
        return new GovernanceTrustLineagePinReceiptHistoryResponse(
                repository.findHistory()
        );
    }

    private void requireEnvelope(
            GovernanceTrustLineagePinnedVerificationRequest request
    ) {
        if (
                request == null

                || request.envelopeJson() == null

                || request.envelopeJson()
                        .isBlank()

                || request.trustedRootFingerprint() == null

                || request.trustedRootFingerprint()
                        .isBlank()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Portable trust-lineage envelope and trusted root fingerprint are required."
            );
        }
    }
}
