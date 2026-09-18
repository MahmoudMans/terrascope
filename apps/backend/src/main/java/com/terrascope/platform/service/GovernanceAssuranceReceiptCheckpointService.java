package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpointHistoryResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpointVerificationResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerVerificationResponse;
import com.terrascope.platform.api.model.GovernanceSigningReadinessResponse;
import com.terrascope.platform.api.model.RegisterGovernanceTrustAnchorRequest;
import com.terrascope.platform.domain.GovernanceAssuranceReceiptCheckpointMaterial;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.repository.GovernanceAssuranceReceiptCheckpointRepository;
import com.terrascope.platform.repository.GovernanceAssuranceReceiptLedgerRepository;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GovernanceAssuranceReceiptCheckpointService {

    private final GovernanceAssuranceReceiptCheckpointRepository checkpointRepository;

    private final GovernanceAssuranceReceiptLedgerRepository ledgerRepository;

    private final GovernanceAssuranceReceiptLedgerService ledgerService;

    private final GovernanceClosureSignatureRepository trustAnchorRepository;

    private final GovernanceClosureSignatureService signingService;

    private final GovernanceSigningConfiguration configuration;

    private final GovernanceEd25519Crypto crypto;

    private final GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy;

    private final GovernanceAssuranceReceiptCheckpointHasher hasher;

    public GovernanceAssuranceReceiptCheckpointService(
            GovernanceAssuranceReceiptCheckpointRepository checkpointRepository,
            GovernanceAssuranceReceiptLedgerRepository ledgerRepository,
            GovernanceAssuranceReceiptLedgerService ledgerService,
            GovernanceClosureSignatureRepository trustAnchorRepository,
            GovernanceClosureSignatureService signingService,
            GovernanceSigningConfiguration configuration,
            GovernanceEd25519Crypto crypto,
            GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy,
            GovernanceAssuranceReceiptCheckpointHasher hasher
    ) {
        this.checkpointRepository = checkpointRepository;
        this.ledgerRepository = ledgerRepository;
        this.ledgerService = ledgerService;
        this.trustAnchorRepository = trustAnchorRepository;
        this.signingService = signingService;
        this.configuration = configuration;
        this.crypto = crypto;
        this.lifecyclePolicy = lifecyclePolicy;
        this.hasher = hasher;
    }

    public GovernanceAssuranceReceiptCheckpointHistoryResponse history() {
        return new GovernanceAssuranceReceiptCheckpointHistoryResponse(
                checkpointRepository.findHistory()
        );
    }

    @Transactional
    public GovernanceAssuranceReceiptCheckpoint create() {
        GovernanceAssuranceReceiptLedgerVerificationResponse ledgerVerification =
                ledgerService.verify();

        if (
                !ledgerVerification.valid()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Receipt transparency ledger must verify before checkpoint signing."
            );
        }

        List<GovernanceAssuranceReceiptLedgerEntry> ledger =
                ledgerRepository.findChain();

        if (
                ledger.isEmpty()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Receipt transparency ledger has no entries to checkpoint."
            );
        }

        GovernanceSigningReadinessResponse readiness =
                signingService.readiness();

        if (
                !readiness.ready()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    readiness.message()
            );
        }

        GovernanceTrustAnchor anchor =
                trustAnchorRepository
                        .findTrustAnchor(
                                readiness.keyId()
                        )
                        .orElseGet(() -> {
                            signingService.registerTrustAnchor(
                                    new RegisterGovernanceTrustAnchorRequest(
                                            readiness.keyId(),
                                            configuration.publicKeyX509Base64()
                                    )
                            );

                            return trustAnchorRepository
                                    .findTrustAnchor(
                                            readiness.keyId()
                                    )
                                    .orElseThrow(() ->
                                            new IllegalStateException(
                                                    "Registered checkpoint trust anchor disappeared."
                                            )
                                    );
                        });

        if (
                !lifecyclePolicy.canSign(
                        anchor
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Receipt-ledger checkpoint signing key must be ACTIVE."
            );
        }

        GovernanceAssuranceReceiptLedgerEntry head =
                ledger.getLast();

        if (
                checkpointRepository.existsForLedgerAndKey(
                        head.ledgerNumber(),
                        anchor.keyId()
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This receipt-ledger head already has a checkpoint from the configured key."
            );
        }

        int checkpointNumber =
                checkpointRepository.nextCheckpointNumber();

        String previousCheckpointHash =
                checkpointRepository
                        .findLatest()
                        .map(
                                GovernanceAssuranceReceiptCheckpoint::checkpointHash
                        )
                        .orElse(
                                null
                        );

        int validCount =
                (int) ledger
                        .stream()
                        .filter(entry ->
                                "VALID"
                                        .equals(
                                                entry.verificationResult()
                                        )
                        )
                        .count();

        int invalidCount =
                ledger.size()
                - validCount;

        GovernanceAssuranceReceiptCheckpointMaterial material =
                new GovernanceAssuranceReceiptCheckpointMaterial(
                        checkpointNumber,

                        head.ledgerNumber(),
                        head.ledgerHash(),

                        ledger.size(),
                        validCount,
                        invalidCount,

                        anchor.keyId(),
                        anchor.algorithm(),
                        anchor.publicKeyFingerprint(),

                        previousCheckpointHash
                );

        String signature =
                crypto.sign(
                        configuration.privateKeyPkcs8Base64(),

                        hasher.signingPayload(
                                material
                        )
                );

        if (
                !crypto.verify(
                        anchor.publicKeyX509Base64(),

                        hasher.signingPayload(
                                material
                        ),

                        signature
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configured Ed25519 key pair failed checkpoint self-verification."
            );
        }

        String checkpointHash =
                hasher.checkpointHash(
                        material,
                        signature
                );

        GovernanceAssuranceReceiptCheckpoint checkpoint =
                checkpointRepository.save(
                        UUID.randomUUID(),

                        material,

                        GovernanceAssuranceReceiptCheckpointHasher.CHECKPOINT_VERSION,

                        signature,

                        checkpointHash
                );

        trustAnchorRepository.touchTrustAnchor(
                anchor.keyId()
        );

        return checkpoint;
    }

    public GovernanceAssuranceReceiptCheckpointVerificationResponse verify() {
        List<GovernanceAssuranceReceiptCheckpoint> checkpoints =
                checkpointRepository.findChain();

        if (
                checkpoints.isEmpty()
        ) {
            return new GovernanceAssuranceReceiptCheckpointVerificationResponse(
                    true,

                    0,
                    0,

                    null,
                    null,

                    null,

                    "No signed receipt-ledger checkpoints have been issued.",

                    Instant.now()
            );
        }

        GovernanceAssuranceReceiptLedgerVerificationResponse ledgerVerification =
                ledgerService.verify();

        if (
                !ledgerVerification.valid()
        ) {
            return new GovernanceAssuranceReceiptCheckpointVerificationResponse(
                    false,

                    checkpoints.size(),
                    0,

                    null,
                    null,

                    ledgerVerification.brokenLedgerNumber(),

                    "Underlying verification receipt ledger is invalid: "
                            + ledgerVerification.message(),

                    Instant.now()
            );
        }

        List<GovernanceAssuranceReceiptLedgerEntry> ledger =
                ledgerRepository.findChain();

        String expectedPreviousCheckpointHash =
                null;

        int expectedCheckpointNumber =
                1;

        int previousCoveredLedgerNumber =
                0;

        int verifiedCount =
                0;

        for (
                GovernanceAssuranceReceiptCheckpoint checkpoint
                : checkpoints
        ) {
            if (
                    checkpoint.checkpointNumber()
                    != expectedCheckpointNumber
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed receipt-ledger checkpoint sequence is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            checkpoint.previousCheckpointHash(),
                            expectedPreviousCheckpointHash
                    )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed receipt-ledger checkpoint previous hash does not match."
                );
            }

            if (
                    checkpoint.coveredLedgerNumber()
                    < previousCoveredLedgerNumber
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed receipt-ledger checkpoint coverage moved backwards."
                );
            }

            GovernanceAssuranceReceiptLedgerEntry covered =
                    ledgerRepository
                            .findByLedgerNumber(
                                    checkpoint.coveredLedgerNumber()
                            )
                            .orElse(
                                    null
                            );

            if (
                    covered == null

                    || !covered
                            .ledgerHash()
                            .equals(
                                    checkpoint.coveredLedgerHash()
                            )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed checkpoint does not match its covered receipt-ledger head."
                );
            }

            List<GovernanceAssuranceReceiptLedgerEntry> coveredEntries =
                    ledger
                            .stream()
                            .filter(entry ->
                                    entry.ledgerNumber()
                                    <= checkpoint.coveredLedgerNumber()
                            )
                            .toList();

            int validCount =
                    (int) coveredEntries
                            .stream()
                            .filter(entry ->
                                    "VALID"
                                            .equals(
                                                    entry.verificationResult()
                                            )
                            )
                            .count();

            int invalidCount =
                    coveredEntries.size()
                    - validCount;

            if (
                    checkpoint.entryCount()
                    != coveredEntries.size()

                    || checkpoint.validReceiptCount()
                       != validCount

                    || checkpoint.invalidReceiptCount()
                       != invalidCount
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed checkpoint receipt counts do not match its covered ledger prefix."
                );
            }

            GovernanceTrustAnchor anchor =
                    trustAnchorRepository
                            .findTrustAnchor(
                                    checkpoint.keyId()
                            )
                            .orElse(
                                    null
                            );

            if (
                    anchor == null
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed checkpoint trust anchor is missing."
                );
            }

            if (
                    !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    checkpoint.algorithm()
                            )

                    || !GovernanceEd25519Crypto.ALGORITHM
                            .equals(
                                    anchor.algorithm()
                            )

                    || !checkpoint
                            .publicKeyFingerprint()
                            .equals(
                                    anchor.publicKeyFingerprint()
                            )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed checkpoint trust-anchor metadata does not match."
                );
            }

            String calculatedFingerprint;

            try {
                calculatedFingerprint =
                        crypto.publicKeyFingerprint(
                                anchor.publicKeyX509Base64()
                        );

            } catch (
                    IllegalArgumentException exception
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed checkpoint trust-anchor public key is invalid."
                );
            }

            if (
                    !calculatedFingerprint.equals(
                            checkpoint.publicKeyFingerprint()
                    )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed checkpoint public-key fingerprint does not match."
                );
            }

            if (
                    !lifecyclePolicy
                            .canVerifyHistoricalSignature(
                                    anchor,
                                    checkpoint.createdAt()
                            )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Signed checkpoint falls outside its trust-anchor validity period."
                );
            }

            if (
                    !GovernanceAssuranceReceiptCheckpointHasher.CHECKPOINT_VERSION
                            .equals(
                                    checkpoint.checkpointVersion()
                            )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Unsupported signed receipt-ledger checkpoint version."
                );
            }

            GovernanceAssuranceReceiptCheckpointMaterial material =
                    material(
                            checkpoint
                    );

            if (
                    !crypto.verify(
                            anchor.publicKeyX509Base64(),

                            hasher.signingPayload(
                                    material
                            ),

                            checkpoint.signatureBase64()
                    )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Ed25519 receipt-ledger checkpoint signature verification failed."
                );
            }

            String calculatedCheckpointHash =
                    hasher.checkpointHash(
                            material,
                            checkpoint.signatureBase64()
                    );

            if (
                    !calculatedCheckpointHash
                            .equals(
                                    checkpoint.checkpointHash()
                            )
            ) {
                return invalid(
                        checkpoints.size(),
                        verifiedCount,
                        checkpoint,

                        "Receipt-ledger checkpoint hash does not match its signed payload."
                );
            }

            expectedPreviousCheckpointHash =
                    checkpoint.checkpointHash();

            previousCoveredLedgerNumber =
                    checkpoint.coveredLedgerNumber();

            expectedCheckpointNumber++;

            verifiedCount++;
        }

        return new GovernanceAssuranceReceiptCheckpointVerificationResponse(
                true,

                checkpoints.size(),
                verifiedCount,

                null,
                null,

                null,

                "All Ed25519 receipt-ledger checkpoints verified.",

                Instant.now()
        );
    }

    private GovernanceAssuranceReceiptCheckpointMaterial material(
            GovernanceAssuranceReceiptCheckpoint checkpoint
    ) {
        return new GovernanceAssuranceReceiptCheckpointMaterial(
                checkpoint.checkpointNumber(),

                checkpoint.coveredLedgerNumber(),
                checkpoint.coveredLedgerHash(),

                checkpoint.entryCount(),
                checkpoint.validReceiptCount(),
                checkpoint.invalidReceiptCount(),

                checkpoint.keyId(),
                checkpoint.algorithm(),
                checkpoint.publicKeyFingerprint(),

                checkpoint.previousCheckpointHash()
        );
    }

    private GovernanceAssuranceReceiptCheckpointVerificationResponse invalid(
            int checkpointCount,
            int verifiedCount,

            GovernanceAssuranceReceiptCheckpoint checkpoint,

            String message
    ) {
        return new GovernanceAssuranceReceiptCheckpointVerificationResponse(
                false,

                checkpointCount,
                verifiedCount,

                checkpoint.id(),
                checkpoint.checkpointNumber(),

                checkpoint.coveredLedgerNumber(),

                message,

                Instant.now()
        );
    }
}
