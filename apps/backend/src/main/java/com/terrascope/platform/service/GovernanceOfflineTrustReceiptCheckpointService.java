package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpointHistoryResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpointVerificationResponse;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerVerificationResponse;

import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptCheckpointRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptCheckpointRepository.TrustAnchorEvidence;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptLedgerRepository;

import org.springframework.core.env.Environment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.time.Instant;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class GovernanceOfflineTrustReceiptCheckpointService {

    private static final String ENV_KEY_ID =
            "TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID";

    private static final String ENV_PRIVATE_KEY =
            "TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64";

    private static final String ENV_PUBLIC_KEY =
            "TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64";

    private final GovernanceOfflineTrustReceiptCheckpointRepository repository;

    private final GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository;

    private final GovernanceOfflineTrustReceiptLedgerService ledgerService;

    private final GovernanceOfflineTrustReceiptCheckpointHasher hasher;

    private final Environment environment;

    public GovernanceOfflineTrustReceiptCheckpointService(
            GovernanceOfflineTrustReceiptCheckpointRepository repository,
            GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository,
            GovernanceOfflineTrustReceiptLedgerService ledgerService,
            GovernanceOfflineTrustReceiptCheckpointHasher hasher,
            Environment environment
    ) {
        this.repository = repository;
        this.ledgerRepository = ledgerRepository;
        this.ledgerService = ledgerService;
        this.hasher = hasher;
        this.environment = environment;
    }

    @Transactional
    public GovernanceOfflineTrustReceiptCheckpoint create() {
        GovernanceOfflineTrustReceiptLedgerVerificationResponse ledgerVerification =
                ledgerService.verify();

        if (
                !ledgerVerification.valid()
        ) {
            throw new IllegalStateException(
                    "Offline trust receipt ledger must verify before checkpoint creation."
            );
        }

        List<GovernanceOfflineTrustReceiptLedgerEntry> ledger =
                ledgerRepository.findChain();

        if (
                ledger.isEmpty()
        ) {
            throw new IllegalStateException(
                    "Cannot checkpoint an empty offline trust receipt ledger."
            );
        }

        GovernanceOfflineTrustReceiptLedgerEntry head =
                ledger.getLast();

        SigningMaterial signing =
                signingMaterial();

        Optional<GovernanceOfflineTrustReceiptCheckpoint> existing =
                repository.findByCoveredLedgerAndKey(
                        head.ledgerNumber(),
                        signing.keyId()
                );

        if (
                existing.isPresent()
        ) {
            return existing.get();
        }

        TrustAnchorEvidence anchor =
                repository
                        .findTrustAnchor(
                                signing.keyId()
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Checkpoint signing key is not registered as a TerraScope trust anchor."
                                )
                        );

        requireActiveSigningAnchor(
                signing,
                anchor
        );

        int checkpointNumber =
                repository.nextCheckpointNumber();

        String previousCheckpointHash =
                repository
                        .findLatest()
                        .map(
                                GovernanceOfflineTrustReceiptCheckpoint::checkpointHash
                        )
                        .orElse(
                                null
                        );

        int validReceiptCount =
                (int) ledger
                        .stream()
                        .filter(entry ->
                                "VALID".equals(
                                        entry.verificationResult()
                                )
                        )
                        .count();

        int invalidReceiptCount =
                ledger.size()
                - validReceiptCount;

        String signingPayload =
                hasher.signingPayload(
                        checkpointNumber,

                        head.ledgerNumber(),

                        head.ledgerHash(),

                        ledger.size(),

                        validReceiptCount,

                        invalidReceiptCount,

                        signing.keyId(),

                        signing.publicKeyFingerprint(),

                        previousCheckpointHash
                );

        String signatureBase64 =
                sign(
                        signing.privateKey(),

                        signingPayload
                );

        String checkpointHash =
                hasher.checkpointHash(
                        signingPayload,
                        signatureBase64
                );

        GovernanceOfflineTrustReceiptCheckpoint checkpoint =
                new GovernanceOfflineTrustReceiptCheckpoint(
                        UUID.randomUUID(),

                        checkpointNumber,

                        head.ledgerNumber(),

                        head.ledgerHash(),

                        ledger.size(),

                        validReceiptCount,

                        invalidReceiptCount,

                        signing.keyId(),

                        GovernanceOfflineTrustReceiptCheckpointHasher.ALGORITHM,

                        signing.publicKeyFingerprint(),

                        GovernanceOfflineTrustReceiptCheckpointHasher.CHECKPOINT_VERSION,

                        signatureBase64,

                        previousCheckpointHash,

                        checkpointHash,

                        Instant.now()
                );

        return repository.save(
                checkpoint
        );
    }

    public GovernanceOfflineTrustReceiptCheckpointHistoryResponse history() {
        return new GovernanceOfflineTrustReceiptCheckpointHistoryResponse(
                repository.findHistory()
        );
    }

    public GovernanceOfflineTrustReceiptCheckpointVerificationResponse verify() {
        GovernanceOfflineTrustReceiptLedgerVerificationResponse ledgerVerification =
                ledgerService.verify();

        List<GovernanceOfflineTrustReceiptCheckpoint> checkpoints =
                repository.findChain();

        String checkpointHeadHash =
                checkpoints.isEmpty()
                        ? null
                        : checkpoints
                                .getLast()
                                .checkpointHash();

        if (
                !ledgerVerification.valid()
        ) {
            return invalid(
                    checkpoints.size(),

                    0,

                    checkpoints.isEmpty()
                            ? null
                            : checkpoints
                                    .getFirst()
                                    .checkpointNumber(),

                    checkpointHeadHash,

                    ledgerVerification.headLedgerHash(),

                    "Offline receipt ledger is invalid; signed checkpoint verification cannot continue."
            );
        }

        List<GovernanceOfflineTrustReceiptLedgerEntry> ledger =
                ledgerRepository.findChain();

        Map<Integer, GovernanceOfflineTrustReceiptLedgerEntry> ledgerByNumber =
                new HashMap<>();

        for (
                GovernanceOfflineTrustReceiptLedgerEntry entry
                : ledger
        ) {
            ledgerByNumber.put(
                    entry.ledgerNumber(),
                    entry
            );
        }

        String expectedPreviousHash =
                null;

        int expectedCheckpointNumber =
                1;

        int verified =
                0;

        for (
                GovernanceOfflineTrustReceiptCheckpoint checkpoint
                : checkpoints
        ) {
            if (
                    checkpoint.checkpointNumber()
                    != expectedCheckpointNumber
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint sequence is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            checkpoint.previousCheckpointHash(),
                            expectedPreviousHash
                    )
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint previous hash does not match."
                );
            }

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
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint version or algorithm is unsupported."
                );
            }

            GovernanceOfflineTrustReceiptLedgerEntry coveredLedger =
                    ledgerByNumber.get(
                            checkpoint.coveredLedgerNumber()
                    );

            if (
                    coveredLedger == null

                    || !coveredLedger
                            .ledgerHash()
                            .equals(
                                    checkpoint.coveredLedgerHash()
                            )
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint no longer matches its covered ledger head."
                );
            }

            List<GovernanceOfflineTrustReceiptLedgerEntry> prefix =
                    ledger
                            .stream()
                            .filter(entry ->
                                    entry.ledgerNumber()
                                    <= checkpoint.coveredLedgerNumber()
                            )
                            .toList();

            int validReceiptCount =
                    (int) prefix
                            .stream()
                            .filter(entry ->
                                    "VALID".equals(
                                            entry.verificationResult()
                                    )
                            )
                            .count();

            int invalidReceiptCount =
                    prefix.size()
                    - validReceiptCount;

            if (
                    checkpoint.entryCount()
                    != prefix.size()

                    || checkpoint.validReceiptCount()
                    != validReceiptCount

                    || checkpoint.invalidReceiptCount()
                    != invalidReceiptCount
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint counts no longer match the covered ledger prefix."
                );
            }

            TrustAnchorEvidence anchor =
                    repository
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

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint signing trust anchor is missing."
                );
            }

            byte[] publicKeyBytes;

            try {
                publicKeyBytes =
                        Base64
                                .getDecoder()
                                .decode(
                                        anchor.publicKeyX509Base64()
                                );

            } catch (
                    IllegalArgumentException exception
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint trust anchor contains an invalid public key."
                );
            }

            String calculatedFingerprint =
                    hasher.fingerprint(
                            publicKeyBytes
                    );

            if (
                    !calculatedFingerprint.equals(
                            checkpoint.publicKeyFingerprint()
                    )

                    || !calculatedFingerprint.equals(
                            anchor.publicKeyFingerprint()
                    )
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint trust-anchor fingerprint verification failed."
                );
            }

            if (
                    !historicallyValid(
                            checkpoint,
                            anchor
                    )
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint signing key was not historically valid at checkpoint creation."
                );
            }

            String signingPayload =
                    hasher.signingPayload(
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

            if (
                    !verifySignature(
                            publicKeyBytes,

                            signingPayload,

                            checkpoint.signatureBase64()
                    )
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint Ed25519 signature verification failed."
                );
            }

            String calculatedCheckpointHash =
                    hasher.checkpointHash(
                            signingPayload,
                            checkpoint.signatureBase64()
                    );

            if (
                    !calculatedCheckpointHash.equals(
                            checkpoint.checkpointHash()
                    )
            ) {
                return invalid(
                        checkpoints.size(),

                        verified,

                        checkpoint.checkpointNumber(),

                        checkpointHeadHash,

                        ledgerVerification.headLedgerHash(),

                        "Offline receipt checkpoint hash verification failed."
                );
            }

            expectedPreviousHash =
                    checkpoint.checkpointHash();

            expectedCheckpointNumber++;

            verified++;
        }

        return new GovernanceOfflineTrustReceiptCheckpointVerificationResponse(
                true,

                checkpoints.size(),

                verified,

                null,

                checkpointHeadHash,

                ledgerVerification.headLedgerHash(),

                checkpoints.isEmpty()
                        ? "No signed offline receipt checkpoints exist."
                        : "Signed offline receipt checkpoints are valid.",

                Instant.now()
        );
    }

    private SigningMaterial signingMaterial() {
        String keyId =
                requiredEnvironment(
                        ENV_KEY_ID
                );

        String privateKeyBase64 =
                requiredEnvironment(
                        ENV_PRIVATE_KEY
                );

        String publicKeyBase64 =
                requiredEnvironment(
                        ENV_PUBLIC_KEY
                );

        try {
            byte[] privateKeyBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    privateKeyBase64
                            );

            byte[] publicKeyBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    publicKeyBase64
                            );

            KeyFactory keyFactory =
                    KeyFactory.getInstance(
                            "Ed25519"
                    );

            PrivateKey privateKey =
                    keyFactory.generatePrivate(
                            new PKCS8EncodedKeySpec(
                                    privateKeyBytes
                            )
                    );

            PublicKey publicKey =
                    keyFactory.generatePublic(
                            new X509EncodedKeySpec(
                                    publicKeyBytes
                            )
                    );

            return new SigningMaterial(
                    keyId,

                    privateKey,

                    publicKey,

                    publicKeyBase64,

                    hasher.fingerprint(
                            publicKeyBytes
                    )
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "TerraScope checkpoint signing key material is invalid.",
                    exception
            );
        }
    }

    private String requiredEnvironment(
            String key
    ) {
        String value =
                environment.getProperty(
                        key
                );

        if (
                value == null

                || value.isBlank()
        ) {
            throw new IllegalStateException(
                    "Required TerraScope checkpoint signing environment value is missing: "
                            + key
            );
        }

        return value.trim();
    }

    private void requireActiveSigningAnchor(
            SigningMaterial signing,
            TrustAnchorEvidence anchor
    ) {
        if (
                !"ACTIVE".equals(
                        anchor.status()
                )
        ) {
            throw new IllegalStateException(
                    "Only an ACTIVE TerraScope trust anchor may sign an offline receipt checkpoint."
            );
        }

        if (
                !GovernanceOfflineTrustReceiptCheckpointHasher.ALGORITHM
                        .equals(
                                anchor.algorithm()
                        )
        ) {
            throw new IllegalStateException(
                    "Checkpoint signing trust anchor must use ED25519."
            );
        }

        if (
                !signing.publicKeyFingerprint()
                        .equals(
                                anchor.publicKeyFingerprint()
                        )
        ) {
            throw new IllegalStateException(
                    "Checkpoint signing public key does not match the active trust anchor."
            );
        }

        if (
                !signing.publicKeyBase64()
                        .equals(
                                anchor.publicKeyX509Base64()
                        )
        ) {
            throw new IllegalStateException(
                    "Checkpoint signing public-key bytes do not match the active trust anchor."
            );
        }
    }

    private boolean historicallyValid(
            GovernanceOfflineTrustReceiptCheckpoint checkpoint,
            TrustAnchorEvidence anchor
    ) {
        if (
                checkpoint.createdAt()
                        .isBefore(
                                anchor.createdAt()
                        )
        ) {
            return false;
        }

        if (
                "ACTIVE".equals(
                        anchor.status()
                )
        ) {
            return true;
        }

        return "RETIRED".equals(
                anchor.status()
        )

               && anchor.retiredAt() != null

               && !checkpoint
                        .createdAt()
                        .isAfter(
                                anchor.retiredAt()
                        );
    }

    private String sign(
            PrivateKey privateKey,
            String payload
    ) {
        try {
            Signature signature =
                    Signature.getInstance(
                            "Ed25519"
                    );

            signature.initSign(
                    privateKey
            );

            signature.update(
                    payload.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return Base64
                    .getEncoder()
                    .encodeToString(
                            signature.sign()
                    );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Unable to create Ed25519 offline receipt checkpoint signature.",
                    exception
            );
        }
    }

    private boolean verifySignature(
            byte[] publicKeyBytes,
            String payload,
            String signatureBase64
    ) {
        try {
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

            Signature signature =
                    Signature.getInstance(
                            "Ed25519"
                    );

            signature.initVerify(
                    publicKey
            );

            signature.update(
                    payload.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return signature.verify(
                    Base64
                            .getDecoder()
                            .decode(
                                    signatureBase64
                            )
            );

        } catch (
                Exception exception
        ) {
            return false;
        }
    }

    private GovernanceOfflineTrustReceiptCheckpointVerificationResponse invalid(
            int checkpointCount,

            int verifiedCheckpointCount,

            Integer brokenCheckpointNumber,

            String checkpointHeadHash,

            String coveredLedgerHeadHash,

            String message
    ) {
        return new GovernanceOfflineTrustReceiptCheckpointVerificationResponse(
                false,

                checkpointCount,

                verifiedCheckpointCount,

                brokenCheckpointNumber,

                checkpointHeadHash,

                coveredLedgerHeadHash,

                message,

                Instant.now()
        );
    }

    private record SigningMaterial(
            String keyId,

            PrivateKey privateKey,

            PublicKey publicKey,

            String publicKeyBase64,

            String publicKeyFingerprint
    ) {
    }
}
