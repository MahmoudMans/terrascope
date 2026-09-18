package com.terrascope.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public final class TerraScopeVerifierReleaseSigner {

    private static final String MANIFEST_VERSION =
            "TS-VERIFIER-RELEASE-MANIFEST-V1";

    private static final String ARTIFACT_VERSION =
            "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1";

    private static final String ALGORITHM =
            "ED25519";

    private TerraScopeVerifierReleaseSigner() {
    }

    public static void main(
            String[] args
    ) throws Exception {
        Configuration configuration =
                Configuration.parse(
                        args
                );

        String releaseKeyId =
                singleLine(
                        configuration.releaseKeyId(),
                        "release key ID"
                );

        byte[] artifactBytes =
                Files.readAllBytes(
                        configuration.artifact()
                );

        String artifactSha256 =
                sha256(
                        artifactBytes
                );

        String artifactName =
                singleLine(
                        configuration
                                .artifact()
                                .getFileName()
                                .toString(),

                        "artifact name"
                );

        String privateKeyBase64 =
                Files.readString(
                                configuration.privateKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

        String publicKeyBase64 =
                Files.readString(
                                configuration.publicKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

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

        String publicKeyFingerprint =
                sha256(
                        publicKeyBytes
                );

        String payload =
                canonicalPayload(
                        artifactName,
                        artifactSha256,
                        releaseKeyId,
                        publicKeyBase64,
                        publicKeyFingerprint
                );

        Signature signer =
                Signature.getInstance(
                        "Ed25519"
                );

        signer.initSign(
                privateKey
        );

        signer.update(
                payload.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        String signatureBase64 =
                Base64
                        .getEncoder()
                        .encodeToString(
                                signer.sign()
                        );

        // Require the supplied public key to match the private key
        // before emitting any release manifest.
        Signature selfVerifier =
                Signature.getInstance(
                        "Ed25519"
                );

        selfVerifier.initVerify(
                publicKey
        );

        selfVerifier.update(
                payload.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        if (
                !selfVerifier.verify(
                        Base64
                                .getDecoder()
                                .decode(
                                        signatureBase64
                                )
                )
        ) {
            throw new IllegalArgumentException(
                    "Release public key does not match the supplied private key."
            );
        }

        String manifest =
                payload

                + "signatureBase64="
                + signatureBase64
                + "\n";

        Files.writeString(
                configuration.output(),
                manifest,
                StandardCharsets.UTF_8
        );

        System.out.println(
                "MANIFEST_VERSION="
                + MANIFEST_VERSION
        );

        System.out.println(
                "ARTIFACT_NAME="
                + artifactName
        );

        System.out.println(
                "ARTIFACT_VERSION="
                + ARTIFACT_VERSION
        );

        System.out.println(
                "ARTIFACT_SHA256="
                + artifactSha256
        );

        System.out.println(
                "RELEASE_KEY_ID="
                + releaseKeyId
        );

        System.out.println(
                "RELEASE_KEY_FINGERPRINT="
                + publicKeyFingerprint
        );

        System.out.println(
                "ALGORITHM="
                + ALGORITHM
        );

        System.out.println(
                "SIGNATURE_SELF_VERIFICATION=PASS"
        );

        System.out.println(
                "RELEASE_MANIFEST="
                + configuration.output()
        );
    }

    private static String canonicalPayload(
            String artifactName,
            String artifactSha256,
            String releaseKeyId,
            String publicKeyBase64,
            String publicKeyFingerprint
    ) {
        return """
                manifestVersion=%s
                artifactName=%s
                artifactVersion=%s
                artifactSha256=%s
                releaseKeyId=%s
                algorithm=%s
                releasePublicKeyX509Base64=%s
                releasePublicKeyFingerprint=%s
                """
                .formatted(
                        MANIFEST_VERSION,

                        artifactName,

                        ARTIFACT_VERSION,

                        artifactSha256,

                        releaseKeyId,

                        ALGORITHM,

                        publicKeyBase64,

                        publicKeyFingerprint
                );
    }

    private static String singleLine(
            String value,
            String label
    ) {
        if (
                value == null

                || value.isBlank()

                || value.indexOf(
                        '\n'
                ) >= 0

                || value.indexOf(
                        '\r'
                ) >= 0

                || value.indexOf(
                        '='
                ) >= 0
        ) {
            throw new IllegalArgumentException(
                    label
                    + " must be a non-empty single-line value without '='."
            );
        }

        return value.trim();
    }

    private static String sha256(
            byte[] value
    ) throws Exception {
        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        return HexFormat
                .of()
                .formatHex(
                        digest.digest(
                                value
                        )
                )
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private record Configuration(
            Path artifact,
            Path privateKey,
            Path publicKey,
            String releaseKeyId,
            Path output
    ) {

        static Configuration parse(
                String[] args
        ) {
            Path artifact =
                    null;

            Path privateKey =
                    null;

            Path publicKey =
                    null;

            String releaseKeyId =
                    null;

            Path output =
                    null;

            for (
                    int index = 0;
                    index < args.length;
                    index++
            ) {
                switch (
                        args[index]
                ) {
                    case "--artifact" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        artifact =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--private-key" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        privateKey =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--public-key" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        publicKey =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--release-key-id" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        releaseKeyId =
                                args[index];
                    }

                    case "--output" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        output =
                                Path.of(
                                        args[index]
                                );
                    }

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown argument: "
                                    + args[index]
                            );
                }
            }

            requireFile(
                    artifact,
                    "--artifact"
            );

            requireFile(
                    privateKey,
                    "--private-key"
            );

            requireFile(
                    publicKey,
                    "--public-key"
            );

            if (
                    releaseKeyId == null

                    || releaseKeyId.isBlank()
            ) {
                throw new IllegalArgumentException(
                        "--release-key-id is required."
                );
            }

            if (
                    output == null
            ) {
                throw new IllegalArgumentException(
                        "--output is required."
                );
            }

            return new Configuration(
                    artifact,
                    privateKey,
                    publicKey,
                    releaseKeyId,
                    output
            );
        }

        private static int valueIndex(
                String[] args,
                int current
        ) {
            int next =
                    current + 1;

            if (
                    next >= args.length
            ) {
                throw new IllegalArgumentException(
                        "Missing value for "
                        + args[current]
                );
            }

            return next;
        }

        private static void requireFile(
                Path path,
                String option
        ) {
            if (
                    path == null

                    || !Files.isRegularFile(
                            path
                    )
            ) {
                throw new IllegalArgumentException(
                        option
                        + " must reference an existing file."
                );
            }
        }
    }
}
