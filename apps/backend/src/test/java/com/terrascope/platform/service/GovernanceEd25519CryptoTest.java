package com.terrascope.platform.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceEd25519CryptoTest {

    private final GovernanceEd25519Crypto crypto =
            new GovernanceEd25519Crypto();

    @Test
    void signsAndVerifiesCanonicalPayload() throws Exception {
        KeyPair pair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        String privateKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPrivate()
                                        .getEncoded()
                        );

        String publicKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPublic()
                                        .getEncoded()
                        );

        byte[] payload =
                "governance-closure"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        String signature =
                crypto.sign(
                        privateKey,
                        payload
                );

        assertThat(
                crypto.verify(
                        publicKey,
                        payload,
                        signature
                )
        ).isTrue();

        assertThat(
                Base64
                        .getDecoder()
                        .decode(
                                signature
                        )
        ).hasSize(64);
    }

    @Test
    void tamperedPayloadFailsAndFingerprintIsStable() throws Exception {
        KeyPair pair =
                KeyPairGenerator
                        .getInstance(
                                "Ed25519"
                        )
                        .generateKeyPair();

        String privateKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPrivate()
                                        .getEncoded()
                        );

        String publicKey =
                Base64
                        .getEncoder()
                        .encodeToString(
                                pair.getPublic()
                                        .getEncoded()
                        );

        byte[] original =
                "original"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        byte[] tampered =
                "tampered"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        String signature =
                crypto.sign(
                        privateKey,
                        original
                );

        assertThat(
                crypto.verify(
                        publicKey,
                        tampered,
                        signature
                )
        ).isFalse();

        String fingerprint =
                crypto.publicKeyFingerprint(
                        publicKey
                );

        assertThat(
                fingerprint
        ).matches(
                "[0-9a-f]{64}"
        );

        assertThat(
                crypto.publicKeyFingerprint(
                        publicKey
                )
        ).isEqualTo(
                fingerprint
        );
    }
}
