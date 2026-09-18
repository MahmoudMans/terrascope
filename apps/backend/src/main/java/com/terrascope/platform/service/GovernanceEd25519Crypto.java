package com.terrascope.platform.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class GovernanceEd25519Crypto {

    public static final String ALGORITHM =
            "ED25519";

    public String publicKeyFingerprint(
            String publicKeyX509Base64
    ) {
        try {
            byte[] encoded =
                    Base64
                            .getDecoder()
                            .decode(
                                    publicKeyX509Base64
                            );

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            digest.digest(
                                    encoded
                            )
                    );

        } catch (
                Exception exception
        ) {
            throw new IllegalArgumentException(
                    "Unable to fingerprint Ed25519 public key.",
                    exception
            );
        }
    }

    public String sign(
            String privateKeyPkcs8Base64,
            byte[] payload
    ) {
        try {
            PrivateKey privateKey =
                    KeyFactory
                            .getInstance(
                                    "Ed25519"
                            )
                            .generatePrivate(
                                    new PKCS8EncodedKeySpec(
                                            Base64
                                                    .getDecoder()
                                                    .decode(
                                                            privateKeyPkcs8Base64
                                                    )
                                    )
                            );

            Signature signature =
                    Signature.getInstance(
                            "Ed25519"
                    );

            signature.initSign(
                    privateKey
            );

            signature.update(
                    payload
            );

            return Base64
                    .getEncoder()
                    .encodeToString(
                            signature.sign()
                    );

        } catch (
                Exception exception
        ) {
            throw new IllegalArgumentException(
                    "Unable to sign governance payload with Ed25519.",
                    exception
            );
        }
    }

    public boolean verify(
            String publicKeyX509Base64,
            byte[] payload,
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
                                            Base64
                                                    .getDecoder()
                                                    .decode(
                                                            publicKeyX509Base64
                                                    )
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
                    payload
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
}
