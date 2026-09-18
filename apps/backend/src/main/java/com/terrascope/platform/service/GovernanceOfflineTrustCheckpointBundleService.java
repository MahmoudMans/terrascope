package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustCheckpointBundle;
import com.terrascope.platform.api.model.GovernanceOfflineTrustCheckpointBundleHistoryResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustCheckpointBundleVerificationResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpointVerificationResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;
import com.terrascope.platform.api.model.VerifyGovernanceOfflineTrustCheckpointBundleRequest;

import com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointBundleMaterial;
import com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointPortableEnvelope;

import com.terrascope.platform.repository.GovernanceOfflineTrustCheckpointBundleRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptCheckpointRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptCheckpointRepository.TrustAnchorEvidence;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptLedgerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.X509EncodedKeySpec;

import java.time.Instant;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GovernanceOfflineTrustCheckpointBundleService {

    private static final Pattern SHA256 =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private final GovernanceOfflineTrustCheckpointBundleRepository repository;

    private final GovernanceOfflineTrustReceiptCheckpointRepository checkpointRepository;

    private final GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository;

    private final GovernanceOfflineTrustReceiptCheckpointService checkpointService;

    private final GovernanceOfflineTrustCheckpointBundleCodec codec;

    private final GovernanceOfflineTrustReceiptCheckpointHasher checkpointHasher;

    private final GovernanceOfflineTrustReceiptLedgerHasher ledgerHasher;

    public GovernanceOfflineTrustCheckpointBundleService(
            GovernanceOfflineTrustCheckpointBundleRepository repository,
            GovernanceOfflineTrustReceiptCheckpointRepository checkpointRepository,
            GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository,
            GovernanceOfflineTrustReceiptCheckpointService checkpointService,
            GovernanceOfflineTrustCheckpointBundleCodec codec,
            GovernanceOfflineTrustReceiptCheckpointHasher checkpointHasher,
            GovernanceOfflineTrustReceiptLedgerHasher ledgerHasher
    ) {
        this.repository = repository;
        this.checkpointRepository = checkpointRepository;
        this.ledgerRepository = ledgerRepository;
        this.checkpointService = checkpointService;
        this.codec = codec;
        this.checkpointHasher = checkpointHasher;
        this.ledgerHasher = ledgerHasher;
    }

    @Transactional
    public GovernanceOfflineTrustCheckpointBundle create() {
        GovernanceOfflineTrustReceiptCheckpointVerificationResponse verification =
                checkpointService.verify();

        if (
                !verification.valid()
        ) {
            throw new IllegalStateException(
                    "Signed offline receipt checkpoint chain must verify before portable bundle creation."
            );
        }

        GovernanceOfflineTrustReceiptCheckpoint checkpoint =
                checkpointRepository
                        .findLatest()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No signed offline receipt checkpoint exists."
                                )
                        );

        Optional<GovernanceOfflineTrustCheckpointBundle> existing =
                repository.findBySourceCheckpointId(
                        checkpoint.id()
                );

        if (
                existing.isPresent()
        ) {
            return existing.get();
        }

        List<GovernanceOfflineTrustReceiptLedgerEntry> ledgerEntries =
                ledgerRepository
                        .findChain()
                        .stream()
                        .filter(entry ->
                                entry.ledgerNumber()
                                <= checkpoint.coveredLedgerNumber()
                        )
                        .toList();

        if (
                ledgerEntries.size()
                != checkpoint.entryCount()
        ) {
            throw new IllegalStateException(
                    "Checkpoint ledger prefix does not match its signed entry count."
            );
        }

        if (
                ledgerEntries.isEmpty()

                || ledgerEntries
                        .getLast()
                        .ledgerNumber()
                        != checkpoint.coveredLedgerNumber()

                || !ledgerEntries
                        .getLast()
                        .ledgerHash()
                        .equals(
                                checkpoint.coveredLedgerHash()
                        )
        ) {
            throw new IllegalStateException(
                    "Checkpoint ledger prefix does not terminate at the signed ledger head."
            );
        }

        TrustAnchorEvidence anchor =
                checkpointRepository
                        .findTrustAnchor(
                                checkpoint.keyId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Checkpoint signing trust anchor is unavailable."
                                )
                        );

        if (
                !checkpoint.publicKeyFingerprint()
                        .equals(
                                anchor.publicKeyFingerprint()
                        )
        ) {
            throw new IllegalStateException(
                    "Checkpoint signing trust-anchor fingerprint does not match."
            );
        }

        GovernanceOfflineTrustCheckpointBundleMaterial material =
                new GovernanceOfflineTrustCheckpointBundleMaterial(
                        GovernanceOfflineTrustCheckpointBundleCodec.BUNDLE_VERSION,

                        checkpoint,

                        List.copyOf(
                                ledgerEntries
                        ),

                        anchor.publicKeyX509Base64(),

                        anchor.publicKeyFingerprint()
                );

        GovernanceOfflineTrustCheckpointPortableEnvelope envelope =
                codec.encode(
                        material
                );

        GovernanceOfflineTrustCheckpointBundle bundle =
                new GovernanceOfflineTrustCheckpointBundle(
                        UUID.randomUUID(),

                        repository.nextBundleNumber(),

                        checkpoint.id(),

                        checkpoint.checkpointNumber(),

                        checkpoint.coveredLedgerNumber(),

                        checkpoint.checkpointHash(),

                        checkpoint.keyId(),

                        checkpoint.publicKeyFingerprint(),

                        GovernanceOfflineTrustCheckpointBundleCodec.BUNDLE_VERSION,

                        envelope.payloadJson(),

                        envelope.bundleHash(),

                        Instant.now()
                );

        return repository.save(
                bundle
        );
    }

    public GovernanceOfflineTrustCheckpointBundleHistoryResponse history() {
        return new GovernanceOfflineTrustCheckpointBundleHistoryResponse(
                repository.findHistory()
        );
    }

    public GovernanceOfflineTrustCheckpointPortableEnvelope export(
            UUID id
    ) {
        GovernanceOfflineTrustCheckpointBundle bundle =
                repository
                        .findById(
                                id
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Portable offline trust checkpoint bundle was not found."
                                )
                        );

        return new GovernanceOfflineTrustCheckpointPortableEnvelope(
                GovernanceOfflineTrustCheckpointBundleCodec.ENVELOPE_VERSION,

                bundle.bundleHash(),

                bundle.payloadJson()
        );
    }

    public GovernanceOfflineTrustCheckpointBundleVerificationResponse verify(
            VerifyGovernanceOfflineTrustCheckpointBundleRequest request
    ) {
        try {
            if (
                    request == null

                    || request.envelopeJson() == null

                    || request.envelopeJson().isBlank()
            ) {
                return invalid(
                        false,
                        false,
                        false,
                        false,
                        false,

                        0,
                        0,

                        null,
                        null,

                        null,
                        null,

                        "Portable checkpoint envelope JSON is required."
                );
            }

            String trustedFingerprint =
                    normalizeFingerprint(
                            request.trustedSigningKeyFingerprint()
                    );

            GovernanceOfflineTrustCheckpointPortableEnvelope envelope =
                    codec.decodeEnvelope(
                            request.envelopeJson()
                    );

            if (
                    !GovernanceOfflineTrustCheckpointBundleCodec.ENVELOPE_VERSION
                            .equals(
                                    envelope.envelopeVersion()
                            )
            ) {
                return invalid(
                        false,
                        false,
                        false,
                        false,
                        false,

                        0,
                        0,

                        null,
                        null,

                        envelope.bundleHash(),
                        null,

                        "Portable checkpoint envelope version is unsupported."
                );
            }

            boolean bundleHashValid =
                    envelope.bundleHash() != null

                    && envelope.bundleHash()
                            .equals(
                                    codec.sha256(
                                            envelope.payloadJson()
                                    )
                            );

            if (
                    !bundleHashValid
            ) {
                return invalid(
                        false,
                        false,
                        false,
                        false,
                        false,

                        0,
                        0,

                        null,
                        null,

                        envelope.bundleHash(),
                        null,

                        "Portable checkpoint bundle hash does not match its payload."
                );
            }

            GovernanceOfflineTrustCheckpointBundleMaterial material =
                    codec.decodeMaterial(
                            envelope.payloadJson()
                    );

            String canonicalPayload =
                    codec.materialJson(
                            material
                    );

            boolean canonicalPayloadValid =
                    envelope.payloadJson()
                            .equals(
                                    canonicalPayload
                            )

                    && GovernanceOfflineTrustCheckpointBundleCodec.BUNDLE_VERSION
                            .equals(
                                    material.bundleVersion()
                            );

            GovernanceOfflineTrustReceiptCheckpoint checkpoint =
                    material.checkpoint();

            List<GovernanceOfflineTrustReceiptLedgerEntry> ledgerEntries =
                    material.ledgerEntries();

            if (
                    !canonicalPayloadValid

                    || checkpoint == null

                    || ledgerEntries == null
            ) {
                return invalid(
                        true,
                        false,
                        false,
                        false,
                        false,

                        ledgerEntries == null
                                ? 0
                                : ledgerEntries.size(),

                        0,

                        checkpoint == null
                                ? null
                                : checkpoint.checkpointNumber(),

                        checkpoint == null
                                ? null
                                : checkpoint.coveredLedgerNumber(),

                        envelope.bundleHash(),

                        checkpoint == null
                                ? null
                                : checkpoint.checkpointHash(),

                        "Portable checkpoint payload is not canonical."
                );
            }

            byte[] publicKeyBytes;

            try {
                publicKeyBytes =
                        Base64
                                .getDecoder()
                                .decode(
                                        material.signingPublicKeyX509Base64()
                                );

            } catch (
                    Exception exception
            ) {
                return invalid(
                        true,
                        true,
                        false,
                        false,
                        false,

                        ledgerEntries.size(),
                        0,

                        checkpoint.checkpointNumber(),
                        checkpoint.coveredLedgerNumber(),

                        envelope.bundleHash(),
                        checkpoint.checkpointHash(),

                        "Portable checkpoint signing public key is invalid."
                );
            }

            String calculatedFingerprint =
                    checkpointHasher.fingerprint(
                            publicKeyBytes
                    );

            boolean signingKeyPinValid =
                    trustedFingerprint != null

                    && trustedFingerprint.equals(
                            calculatedFingerprint
                    )

                    && calculatedFingerprint.equals(
                            material.signingPublicKeyFingerprint()
                    )

                    && calculatedFingerprint.equals(
                            checkpoint.publicKeyFingerprint()
                    );

            LedgerVerification ledgerVerification =
                    verifyLedger(
                            ledgerEntries
                    );

            boolean ledgerValid =
                    ledgerVerification.valid();

            boolean checkpointValid =
                    ledgerValid

                    && verifyCheckpoint(
                            checkpoint,

                            ledgerEntries,

                            publicKeyBytes
                    );

            boolean valid =
                    bundleHashValid

                    && canonicalPayloadValid

                    && ledgerValid

                    && checkpointValid

                    && signingKeyPinValid;

            String message;

            if (
                    !signingKeyPinValid
            ) {
                message =
                        "Portable checkpoint signing key does not match the independently trusted fingerprint.";

            } else if (
                    !ledgerValid
            ) {
                message =
                        ledgerVerification.message();

            } else if (
                    !checkpointValid
            ) {
                message =
                        "Portable checkpoint cryptographic verification failed.";

            } else {
                message =
                        "Portable offline receipt checkpoint bundle verified against the independently supplied signing-key fingerprint.";
            }

            return new GovernanceOfflineTrustCheckpointBundleVerificationResponse(
                    valid,

                    bundleHashValid,

                    canonicalPayloadValid,

                    ledgerValid,

                    checkpointValid,

                    signingKeyPinValid,

                    ledgerEntries.size(),

                    ledgerVerification.verifiedEntries(),

                    checkpoint.checkpointNumber(),

                    checkpoint.coveredLedgerNumber(),

                    envelope.bundleHash(),

                    checkpoint.checkpointHash(),

                    message,

                    Instant.now()
            );

        } catch (
                Exception exception
        ) {
            return invalid(
                    false,
                    false,
                    false,
                    false,
                    false,

                    0,
                    0,

                    null,
                    null,

                    null,
                    null,

                    "Portable checkpoint bundle could not be verified: "
                            + exception.getMessage()
            );
        }
    }

    private LedgerVerification verifyLedger(
            List<GovernanceOfflineTrustReceiptLedgerEntry> entries
    ) {
        if (
                entries.isEmpty()
        ) {
            return new LedgerVerification(
                    false,
                    0,
                    "Portable checkpoint ledger evidence is empty."
            );
        }

        String expectedPreviousHash =
                null;

        int expectedNumber =
                1;

        int verified =
                0;

        for (
                GovernanceOfflineTrustReceiptLedgerEntry entry
                : entries
        ) {
            if (
                    entry.ledgerNumber()
                    != expectedNumber
            ) {
                return new LedgerVerification(
                        false,
                        verified,
                        "Portable checkpoint ledger numbering is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            entry.previousLedgerHash(),
                            expectedPreviousHash
                    )
            ) {
                return new LedgerVerification(
                        false,
                        verified,
                        "Portable checkpoint ledger previous-hash linkage is invalid."
                );
            }

            if (
                    !GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION
                            .equals(
                                    entry.ledgerVersion()
                            )
            ) {
                return new LedgerVerification(
                        false,
                        verified,
                        "Portable checkpoint ledger version is unsupported."
                );
            }

            String calculated =
                    ledgerHasher.ledgerHash(
                            entry.ledgerNumber(),

                            entry.receiptId(),

                            entry.receiptImportNumber(),

                            entry.verificationResult(),

                            entry.receiptHash(),

                            entry.receiptPayloadHash(),

                            entry.previousLedgerHash()
                    );

            if (
                    !calculated.equals(
                            entry.ledgerHash()
                    )
            ) {
                return new LedgerVerification(
                        false,
                        verified,
                        "Portable checkpoint ledger hash verification failed."
                );
            }

            expectedPreviousHash =
                    entry.ledgerHash();

            expectedNumber++;

            verified++;
        }

        return new LedgerVerification(
                true,
                verified,
                "Portable checkpoint ledger evidence is valid."
        );
    }

    private boolean verifyCheckpoint(
            GovernanceOfflineTrustReceiptCheckpoint checkpoint,

            List<GovernanceOfflineTrustReceiptLedgerEntry> ledgerEntries,

            byte[] publicKeyBytes
    ) {
        try {
            if (
                    !GovernanceOfflineTrustReceiptCheckpointHasher.CHECKPOINT_VERSION
                            .equals(
                                    checkpoint.checkpointVersion()
                            )

                    || !GovernanceOfflineTrustReceiptCheckpointHasher.ALGORITHM
                            .equals(
                                    checkpoint.algorithm()
                            )
            ) {
                return false;
            }

            if (
                    checkpoint.entryCount()
                    != ledgerEntries.size()

                    || checkpoint.coveredLedgerNumber()
                    != ledgerEntries
                            .getLast()
                            .ledgerNumber()

                    || !checkpoint.coveredLedgerHash()
                            .equals(
                                    ledgerEntries
                                            .getLast()
                                            .ledgerHash()
                            )
            ) {
                return false;
            }

            int validCount =
                    (int) ledgerEntries
                            .stream()
                            .filter(entry ->
                                    "VALID".equals(
                                            entry.verificationResult()
                                    )
                            )
                            .count();

            int invalidCount =
                    ledgerEntries.size()
                    - validCount;

            if (
                    checkpoint.validReceiptCount()
                    != validCount

                    || checkpoint.invalidReceiptCount()
                    != invalidCount
            ) {
                return false;
            }

            String signingPayload =
                    checkpointHasher.signingPayload(
                            checkpoint.checkpointNumber(),

                            checkpoint.coveredLedgerNumber(),

                            checkpoint.coveredLedgerHash(),

                            checkpoint.entryCount(),

                            checkpoint.validReceiptCount(),

                            checkpoint.invalidReceiptCount(),

                            checkpoint.keyId(),

                            checkpoint.publicKeyFingerprint(),

                            checkpoint.previousCheckpointHash()
                    );

            PublicKey publicKey =
                    KeyFactory
                            .getInstance(
                                    "Ed25519"
                            )
                            .generatePublic(
                                    new X509EncodedKeySpec(
                                            publicKeyBytes
                                    )
                            );

            Signature verifier =
                    Signature.getInstance(
                            "Ed25519"
                    );

            verifier.initVerify(
                    publicKey
            );

            verifier.update(
                    signingPayload.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            boolean signatureValid =
                    verifier.verify(
                            Base64
                                    .getDecoder()
                                    .decode(
                                            checkpoint.signatureBase64()
                                    )
                    );

            if (
                    !signatureValid
            ) {
                return false;
            }

            String calculatedCheckpointHash =
                    checkpointHasher.checkpointHash(
                            signingPayload,
                            checkpoint.signatureBase64()
                    );

            return calculatedCheckpointHash.equals(
                    checkpoint.checkpointHash()
            );

        } catch (
                Exception exception
        ) {
            return false;
        }
    }

    private String normalizeFingerprint(
            String value
    ) {
        if (
                value == null
        ) {
            return null;
        }

        String normalized =
                value
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return SHA256
                .matcher(
                        normalized
                )
                .matches()
                ? normalized
                : null;
    }

    private GovernanceOfflineTrustCheckpointBundleVerificationResponse invalid(
            boolean bundleHashValid,

            boolean canonicalPayloadValid,

            boolean ledgerValid,

            boolean checkpointValid,

            boolean signingKeyPinValid,

            int ledgerEntryCount,

            int verifiedLedgerEntryCount,

            Integer checkpointNumber,

            Integer coveredLedgerNumber,

            String bundleHash,

            String checkpointHash,

            String message
    ) {
        return new GovernanceOfflineTrustCheckpointBundleVerificationResponse(
                false,

                bundleHashValid,

                canonicalPayloadValid,

                ledgerValid,

                checkpointValid,

                signingKeyPinValid,

                ledgerEntryCount,

                verifiedLedgerEntryCount,

                checkpointNumber,

                coveredLedgerNumber,

                bundleHash,

                checkpointHash,

                message,

                Instant.now()
        );
    }

    private record LedgerVerification(
            boolean valid,

            int verifiedEntries,

            String message
    ) {
    }
}
