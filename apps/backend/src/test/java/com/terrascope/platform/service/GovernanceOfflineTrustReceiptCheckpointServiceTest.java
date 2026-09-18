package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerVerificationResponse;

import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptCheckpointRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptCheckpointRepository.TrustAnchorEvidence;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptLedgerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.env.Environment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.time.Instant;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceOfflineTrustReceiptCheckpointServiceTest {

    private GovernanceOfflineTrustReceiptCheckpointRepository repository;

    private GovernanceOfflineTrustReceiptLedgerRepository ledgerRepository;

    private GovernanceOfflineTrustReceiptLedgerService ledgerService;

    private GovernanceOfflineTrustReceiptCheckpointHasher hasher;

    private Environment environment;

    private GovernanceOfflineTrustReceiptCheckpointService service;

    @BeforeEach
    void setUp() {
        repository =
                mock(
                        GovernanceOfflineTrustReceiptCheckpointRepository.class
                );

        ledgerRepository =
                mock(
                        GovernanceOfflineTrustReceiptLedgerRepository.class
                );

        ledgerService =
                mock(
                        GovernanceOfflineTrustReceiptLedgerService.class
                );

        hasher =
                new GovernanceOfflineTrustReceiptCheckpointHasher();

        environment =
                mock(
                        Environment.class
                );

        service =
                new GovernanceOfflineTrustReceiptCheckpointService(
                        repository,
                        ledgerRepository,
                        ledgerService,
                        hasher,
                        environment
                );
    }

    @Test
    void createsSignedCheckpointForVerifiedLedger() throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        KeyPair keyPair =
                generator.generateKeyPair();

        String privateKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                keyPair
                                        .getPrivate()
                                        .getEncoded()
                        );

        String publicKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                keyPair
                                        .getPublic()
                                        .getEncoded()
                        );

        String fingerprint =
                hasher.fingerprint(
                        keyPair
                                .getPublic()
                                .getEncoded()
                );

        when(
                environment.getProperty(
                        "TERRASCOPE_GOVERNANCE_SIGNING_KEY_ID"
                )
        ).thenReturn(
                "key-1"
        );

        when(
                environment.getProperty(
                        "TERRASCOPE_GOVERNANCE_SIGNING_PRIVATE_KEY_PKCS8_B64"
                )
        ).thenReturn(
                privateKey
        );

        when(
                environment.getProperty(
                        "TERRASCOPE_GOVERNANCE_SIGNING_PUBLIC_KEY_X509_B64"
                )
        ).thenReturn(
                publicKey
        );

        GovernanceOfflineTrustReceiptLedgerEntry first =
                ledgerEntry(
                        1,
                        "VALID",
                        null,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        GovernanceOfflineTrustReceiptLedgerEntry second =
                ledgerEntry(
                        2,
                        "INVALID",
                        first.ledgerHash(),
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                );

        when(
                ledgerService.verify()
        ).thenReturn(
                new GovernanceOfflineTrustReceiptLedgerVerificationResponse(
                        true,

                        2,
                        2,

                        null,

                        second.ledgerHash(),

                        "valid",

                        Instant.now()
                )
        );

        when(
                ledgerRepository.findChain()
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        when(
                repository.findByCoveredLedgerAndKey(
                        2,
                        "key-1"
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                repository.findTrustAnchor(
                        "key-1"
                )
        ).thenReturn(
                Optional.of(
                        new TrustAnchorEvidence(
                                "key-1",

                                "ED25519",

                                publicKey,

                                fingerprint,

                                "ACTIVE",

                                Instant.now()
                                        .minusSeconds(
                                                60
                                        ),

                                null
                        )
                )
        );

        when(
                repository.nextCheckpointNumber()
        ).thenReturn(
                1
        );

        when(
                repository.findLatest()
        ).thenReturn(
                Optional.empty()
        );

        when(
                repository.save(
                        any()
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(
                                0
                        )
        );

        GovernanceOfflineTrustReceiptCheckpoint result =
                service.create();

        assertThat(
                result.checkpointNumber()
        ).isEqualTo(
                1
        );

        assertThat(
                result.coveredLedgerNumber()
        ).isEqualTo(
                2
        );

        assertThat(
                result.entryCount()
        ).isEqualTo(
                2
        );

        assertThat(
                result.validReceiptCount()
        ).isEqualTo(
                1
        );

        assertThat(
                result.invalidReceiptCount()
        ).isEqualTo(
                1
        );

        assertThat(
                result.keyId()
        ).isEqualTo(
                "key-1"
        );

        assertThat(
                result.signatureBase64()
        ).isNotBlank();

        assertThat(
                result.checkpointHash()
        ).matches(
                "[0-9a-f]{64}"
        );
    }

    @Test
    void invalidLedgerBlocksCheckpointVerification() {
        when(
                ledgerService.verify()
        ).thenReturn(
                new GovernanceOfflineTrustReceiptLedgerVerificationResponse(
                        false,

                        2,
                        0,

                        1,

                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                        "invalid",

                        Instant.now()
                )
        );

        when(
                repository.findChain()
        ).thenReturn(
                List.of()
        );

        var result =
                service.verify();

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.verifiedCheckpointCount()
        ).isZero();

        assertThat(
                result.message()
        ).contains(
                "ledger is invalid"
        );
    }

    private GovernanceOfflineTrustReceiptLedgerEntry ledgerEntry(
            int number,
            String result,
            String previousHash,
            String ledgerHash
    ) {
        return new GovernanceOfflineTrustReceiptLedgerEntry(
                UUID.randomUUID(),

                number,

                UUID.randomUUID(),

                number,

                result,

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",

                previousHash,

                "TS-OFFLINE-TRUST-RECEIPT-LEDGER-V1",

                ledgerHash,

                Instant.now()
        );
    }
}
