package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptCheckpointVerificationResponse;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceAssuranceReceiptLedgerVerificationResponse;
import com.terrascope.platform.domain.GovernanceAssuranceReceiptCheckpointMaterial;
import com.terrascope.platform.domain.GovernanceTrustAnchor;
import com.terrascope.platform.repository.GovernanceAssuranceReceiptCheckpointRepository;
import com.terrascope.platform.repository.GovernanceAssuranceReceiptLedgerRepository;
import com.terrascope.platform.repository.GovernanceClosureSignatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceAssuranceReceiptCheckpointServiceTest {

    private GovernanceAssuranceReceiptCheckpointRepository checkpointRepository;

    private GovernanceAssuranceReceiptLedgerRepository ledgerRepository;

    private GovernanceAssuranceReceiptLedgerService ledgerService;

    private GovernanceClosureSignatureRepository trustAnchorRepository;

    private GovernanceClosureSignatureService signingService;

    private GovernanceSigningConfiguration configuration;

    private GovernanceEd25519Crypto crypto;

    private GovernanceTrustAnchorLifecyclePolicy lifecyclePolicy;

    private GovernanceAssuranceReceiptCheckpointHasher hasher;

    private GovernanceAssuranceReceiptCheckpointService service;

    private String privateKey;
    private String publicKey;
    private String fingerprint;

    @BeforeEach
    void setUp() throws Exception {
        checkpointRepository =
                mock(
                        GovernanceAssuranceReceiptCheckpointRepository.class
                );

        ledgerRepository =
                mock(
                        GovernanceAssuranceReceiptLedgerRepository.class
                );

        ledgerService =
                mock(
                        GovernanceAssuranceReceiptLedgerService.class
                );

        trustAnchorRepository =
                mock(
                        GovernanceClosureSignatureRepository.class
                );

        signingService =
                mock(
                        GovernanceClosureSignatureService.class
                );

        configuration =
                mock(
                        GovernanceSigningConfiguration.class
                );

        crypto =
                new GovernanceEd25519Crypto();

        lifecyclePolicy =
                new GovernanceTrustAnchorLifecyclePolicy();

        hasher =
                new GovernanceAssuranceReceiptCheckpointHasher();

        service =
                new GovernanceAssuranceReceiptCheckpointService(
                        checkpointRepository,
                        ledgerRepository,
                        ledgerService,
                        trustAnchorRepository,
                        signingService,
                        configuration,
                        crypto,
                        lifecyclePolicy,
                        hasher
                );

        KeyPair pair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        privateKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPrivate()
                                        .getEncoded()
                        );

        publicKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPublic()
                                        .getEncoded()
                        );

        fingerprint =
                crypto.publicKeyFingerprint(
                        publicKey
                );
    }

    @Test
    void emptyReceiptLedgerCannotBeCheckpointed() {
        when(
                ledgerService.verify()
        ).thenReturn(
                ledgerVerification(
                        true,
                        0
                )
        );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of()
        );

        assertThatThrownBy(() ->
                service.create()
        ).isInstanceOf(
                ResponseStatusException.class
        );
    }

    @Test
    void createsSignedCheckpointForVerifiedLedgerHead() {
        GovernanceAssuranceReceiptLedgerEntry ledgerEntry =
                ledgerEntry(
                        1,
                        "VALID"
                );

        when(
                ledgerService.verify()
        ).thenReturn(
                ledgerVerification(
                        true,
                        1
                )
        );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        ledgerEntry
                )
        );

        var readiness =
                new com.terrascope.platform.api.model.GovernanceSigningReadinessResponse(
                        true,
                        "ED25519",
                        "checkpoint-key",
                        fingerprint,
                        true,
                        "Ready.",
                        Instant.now()
                );

        when(
                signingService.readiness()
        ).thenReturn(
                readiness
        );

        when(
                configuration.privateKeyPkcs8Base64()
        ).thenReturn(
                privateKey
        );

        when(
                configuration.publicKeyX509Base64()
        ).thenReturn(
                publicKey
        );

        GovernanceTrustAnchor anchor =
                activeAnchor();

        when(
                trustAnchorRepository.findTrustAnchor(
                        "checkpoint-key"
                )
        ).thenReturn(
                Optional.of(
                        anchor
                )
        );

        when(
                checkpointRepository.existsForLedgerAndKey(
                        1,
                        "checkpoint-key"
                )
        ).thenReturn(
                false
        );

        when(
                checkpointRepository.nextCheckpointNumber()
        ).thenReturn(
                1
        );

        when(
                checkpointRepository.findLatest()
        ).thenReturn(
                Optional.empty()
        );

        when(
                checkpointRepository.save(
                        any(),
                        any(),
                        eq(
                                GovernanceAssuranceReceiptCheckpointHasher.CHECKPOINT_VERSION
                        ),
                        any(),
                        any()
                )
        ).thenAnswer(invocation -> {
            GovernanceAssuranceReceiptCheckpointMaterial material =
                    invocation.getArgument(
                            1
                    );

            String signature =
                    invocation.getArgument(
                            3
                    );

            String checkpointHash =
                    invocation.getArgument(
                            4
                    );

            return checkpoint(
                    1,
                    material,
                    signature,
                    checkpointHash,
                    Instant.now()
            );
        });

        GovernanceAssuranceReceiptCheckpoint checkpoint =
                service.create();

        assertThat(
                checkpoint.checkpointNumber()
        ).isEqualTo(1);

        assertThat(
                checkpoint.coveredLedgerNumber()
        ).isEqualTo(1);

        assertThat(
                checkpoint.validReceiptCount()
        ).isEqualTo(1);

        assertThat(
                checkpoint.invalidReceiptCount()
        ).isZero();
    }

    @Test
    void historicalCheckpointUnderRetiredAnchorStillVerifies() {
        Instant createdAt =
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                );

        GovernanceAssuranceReceiptLedgerEntry ledgerEntry =
                ledgerEntry(
                        1,
                        "VALID"
                );

        GovernanceTrustAnchor retired =
                new GovernanceTrustAnchor(
                        "checkpoint-key",
                        "ED25519",
                        publicKey,
                        fingerprint,
                        "RETIRED",
                        createdAt.minusSeconds(100),
                        createdAt,
                        createdAt.plusSeconds(100)
                );

        GovernanceAssuranceReceiptCheckpointMaterial material =
                new GovernanceAssuranceReceiptCheckpointMaterial(
                        1,
                        1,
                        ledgerEntry.ledgerHash(),
                        1,
                        1,
                        0,
                        retired.keyId(),
                        retired.algorithm(),
                        retired.publicKeyFingerprint(),
                        null
                );

        String signature =
                crypto.sign(
                        privateKey,
                        hasher.signingPayload(
                                material
                        )
                );

        GovernanceAssuranceReceiptCheckpoint checkpoint =
                checkpoint(
                        1,
                        material,
                        signature,
                        hasher.checkpointHash(
                                material,
                                signature
                        ),
                        createdAt
                );

        when(
                checkpointRepository.findChain()
        ).thenReturn(
                List.of(
                        checkpoint
                )
        );

        when(
                ledgerService.verify()
        ).thenReturn(
                ledgerVerification(
                        true,
                        1
                )
        );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        ledgerEntry
                )
        );

        when(
                ledgerRepository.findByLedgerNumber(
                        1
                )
        ).thenReturn(
                Optional.of(
                        ledgerEntry
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        retired.keyId()
                )
        ).thenReturn(
                Optional.of(
                        retired
                )
        );

        GovernanceAssuranceReceiptCheckpointVerificationResponse result =
                service.verify();

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.verifiedCheckpointCount()
        ).isEqualTo(1);
    }

    @Test
    void tamperedCheckpointSignatureIsDetected() {
        Instant createdAt =
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                );

        GovernanceAssuranceReceiptLedgerEntry ledgerEntry =
                ledgerEntry(
                        1,
                        "VALID"
                );

        GovernanceTrustAnchor anchor =
                activeAnchor();

        GovernanceAssuranceReceiptCheckpointMaterial material =
                new GovernanceAssuranceReceiptCheckpointMaterial(
                        1,
                        1,
                        ledgerEntry.ledgerHash(),
                        1,
                        1,
                        0,
                        anchor.keyId(),
                        anchor.algorithm(),
                        anchor.publicKeyFingerprint(),
                        null
                );

        GovernanceAssuranceReceiptCheckpoint checkpoint =
                checkpoint(
                        1,
                        material,
                        Base64
                                .getEncoder()
                                .encodeToString(
                                        new byte[64]
                                ),
                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                        createdAt
                );

        when(
                checkpointRepository.findChain()
        ).thenReturn(
                List.of(
                        checkpoint
                )
        );

        when(
                ledgerService.verify()
        ).thenReturn(
                ledgerVerification(
                        true,
                        1
                )
        );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        ledgerEntry
                )
        );

        when(
                ledgerRepository.findByLedgerNumber(
                        1
                )
        ).thenReturn(
                Optional.of(
                        ledgerEntry
                )
        );

        when(
                trustAnchorRepository.findTrustAnchor(
                        anchor.keyId()
                )
        ).thenReturn(
                Optional.of(
                        anchor
                )
        );

        GovernanceAssuranceReceiptCheckpointVerificationResponse result =
                service.verify();

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.message()
        ).contains(
                "signature verification failed"
        );
    }

    private GovernanceTrustAnchor activeAnchor() {
        Instant now =
                Instant.now();

        return new GovernanceTrustAnchor(
                "checkpoint-key",
                "ED25519",
                publicKey,
                fingerprint,
                "ACTIVE",
                now.minusSeconds(10),
                now,
                null
        );
    }

    private GovernanceAssuranceReceiptLedgerVerificationResponse ledgerVerification(
            boolean valid,
            int count
    ) {
        return new GovernanceAssuranceReceiptLedgerVerificationResponse(
                valid,
                count,
                valid ? count : 0,
                null,
                null,
                null,
                null,
                valid
                        ? "Valid."
                        : "Invalid.",
                Instant.now()
        );
    }

    private GovernanceAssuranceReceiptLedgerEntry ledgerEntry(
            int number,
            String result
    ) {
        return new GovernanceAssuranceReceiptLedgerEntry(
                UUID.randomUUID(),
                number,
                UUID.randomUUID(),
                number,
                result,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                number == 1
                        ? null
                        : "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                GovernanceAssuranceReceiptLedgerHasher.LEDGER_VERSION,
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                ).plusSeconds(
                        number
                )
        );
    }

    private GovernanceAssuranceReceiptCheckpoint checkpoint(
            int checkpointNumber,

            GovernanceAssuranceReceiptCheckpointMaterial material,

            String signature,
            String checkpointHash,

            Instant createdAt
    ) {
        return new GovernanceAssuranceReceiptCheckpoint(
                UUID.randomUUID(),

                checkpointNumber,

                material.coveredLedgerNumber(),
                material.coveredLedgerHash(),

                material.entryCount(),
                material.validReceiptCount(),
                material.invalidReceiptCount(),

                material.keyId(),
                material.algorithm(),
                material.publicKeyFingerprint(),

                GovernanceAssuranceReceiptCheckpointHasher.CHECKPOINT_VERSION,

                signature,

                material.previousCheckpointHash(),
                checkpointHash,

                createdAt
        );
    }
}
