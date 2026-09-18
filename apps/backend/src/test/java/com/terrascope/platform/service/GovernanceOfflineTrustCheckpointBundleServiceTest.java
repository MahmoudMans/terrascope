package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptCheckpoint;
import com.terrascope.platform.api.model.GovernanceOfflineTrustReceiptLedgerEntry;
import com.terrascope.platform.api.model.VerifyGovernanceOfflineTrustCheckpointBundleRequest;

import com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointBundleMaterial;

import com.terrascope.platform.repository.GovernanceOfflineTrustCheckpointBundleRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptCheckpointRepository;
import com.terrascope.platform.repository.GovernanceOfflineTrustReceiptLedgerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

import java.time.Instant;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;

class GovernanceOfflineTrustCheckpointBundleServiceTest {

    private GovernanceOfflineTrustCheckpointBundleCodec codec;

    private GovernanceOfflineTrustReceiptCheckpointHasher checkpointHasher;

    private GovernanceOfflineTrustReceiptLedgerHasher ledgerHasher;

    private GovernanceOfflineTrustCheckpointBundleService service;

    @BeforeEach
    void setUp() {
        codec =
                new GovernanceOfflineTrustCheckpointBundleCodec(
                        new ObjectMapper()
                );

        checkpointHasher =
                new GovernanceOfflineTrustReceiptCheckpointHasher();

        ledgerHasher =
                new GovernanceOfflineTrustReceiptLedgerHasher();

        service =
                new GovernanceOfflineTrustCheckpointBundleService(
                        mock(
                                GovernanceOfflineTrustCheckpointBundleRepository.class
                        ),

                        mock(
                                GovernanceOfflineTrustReceiptCheckpointRepository.class
                        ),

                        mock(
                                GovernanceOfflineTrustReceiptLedgerRepository.class
                        ),

                        mock(
                                GovernanceOfflineTrustReceiptCheckpointService.class
                        ),

                        codec,

                        checkpointHasher,

                        ledgerHasher
                );
    }

    @Test
    void validPortableBundleVerifiesAgainstExternalSigningKeyPin() throws Exception {
        Fixture fixture =
                fixture();

        var result =
                service.verify(
                        new VerifyGovernanceOfflineTrustCheckpointBundleRequest(
                                codec.envelopeJson(
                                        fixture.envelope()
                                ),

                                fixture.fingerprint()
                        )
                );

        assertThat(
                result.valid()
        ).isTrue();

        assertThat(
                result.bundleHashValid()
        ).isTrue();

        assertThat(
                result.canonicalPayloadValid()
        ).isTrue();

        assertThat(
                result.ledgerValid()
        ).isTrue();

        assertThat(
                result.checkpointValid()
        ).isTrue();

        assertThat(
                result.signingKeyPinValid()
        ).isTrue();

        assertThat(
                result.verifiedLedgerEntryCount()
        ).isEqualTo(
                1
        );
    }

    @Test
    void wrongExternalPinIsRejectedWithoutBreakingCryptographicEvidence() throws Exception {
        Fixture fixture =
                fixture();

        var result =
                service.verify(
                        new VerifyGovernanceOfflineTrustCheckpointBundleRequest(
                                codec.envelopeJson(
                                        fixture.envelope()
                                ),

                                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
                        )
                );

        assertThat(
                result.valid()
        ).isFalse();

        assertThat(
                result.bundleHashValid()
        ).isTrue();

        assertThat(
                result.canonicalPayloadValid()
        ).isTrue();

        assertThat(
                result.ledgerValid()
        ).isTrue();

        assertThat(
                result.checkpointValid()
        ).isTrue();

        assertThat(
                result.signingKeyPinValid()
        ).isFalse();
    }

    private Fixture fixture() throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "Ed25519"
                );

        KeyPair keyPair =
                generator.generateKeyPair();

        String publicKeyBase64 =
                Base64
                        .getEncoder()
                        .encodeToString(
                                keyPair
                                        .getPublic()
                                        .getEncoded()
                        );

        String fingerprint =
                checkpointHasher.fingerprint(
                        keyPair
                                .getPublic()
                                .getEncoded()
                );

        UUID receiptId =
                UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"
                );

        String receiptHash =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        String receiptPayloadHash =
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        String ledgerHash =
                ledgerHasher.ledgerHash(
                        1,

                        receiptId,

                        1,

                        "VALID",

                        receiptHash,

                        receiptPayloadHash,

                        null
                );

        GovernanceOfflineTrustReceiptLedgerEntry ledgerEntry =
                new GovernanceOfflineTrustReceiptLedgerEntry(
                        UUID.fromString(
                                "22222222-2222-2222-2222-222222222222"
                        ),

                        1,

                        receiptId,

                        1,

                        "VALID",

                        receiptHash,

                        receiptPayloadHash,

                        null,

                        GovernanceOfflineTrustReceiptLedgerHasher.LEDGER_VERSION,

                        ledgerHash,

                        Instant.parse(
                                "2030-01-01T00:00:00Z"
                        )
                );

        String signingPayload =
                checkpointHasher.signingPayload(
                        1,

                        1,

                        ledgerHash,

                        1,

                        1,

                        0,

                        "key-1",

                        fingerprint,

                        null
                );

        Signature signer =
                Signature.getInstance(
                        "Ed25519"
                );

        signer.initSign(
                keyPair.getPrivate()
        );

        signer.update(
                signingPayload.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        String signatureBase64 =
                Base64
                        .getEncoder()
                        .encodeToString(
                                signer.sign()
                        );

        String checkpointHash =
                checkpointHasher.checkpointHash(
                        signingPayload,
                        signatureBase64
                );

        GovernanceOfflineTrustReceiptCheckpoint checkpoint =
                new GovernanceOfflineTrustReceiptCheckpoint(
                        UUID.fromString(
                                "33333333-3333-3333-3333-333333333333"
                        ),

                        1,

                        1,

                        ledgerHash,

                        1,

                        1,

                        0,

                        "key-1",

                        "ED25519",

                        fingerprint,

                        GovernanceOfflineTrustReceiptCheckpointHasher.CHECKPOINT_VERSION,

                        signatureBase64,

                        null,

                        checkpointHash,

                        Instant.parse(
                                "2030-01-01T00:01:00Z"
                        )
                );

        GovernanceOfflineTrustCheckpointBundleMaterial material =
                new GovernanceOfflineTrustCheckpointBundleMaterial(
                        GovernanceOfflineTrustCheckpointBundleCodec.BUNDLE_VERSION,

                        checkpoint,

                        List.of(
                                ledgerEntry
                        ),

                        publicKeyBase64,

                        fingerprint
                );

        return new Fixture(
                codec.encode(
                        material
                ),

                fingerprint
        );
    }

    private record Fixture(
            com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointPortableEnvelope envelope,

            String fingerprint
    ) {
    }
}
