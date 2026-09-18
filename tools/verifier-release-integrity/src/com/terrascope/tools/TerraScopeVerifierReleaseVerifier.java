package com.terrascope.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.X509EncodedKeySpec;

import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import java.util.regex.Pattern;

public final class TerraScopeVerifierReleaseVerifier {

    private static final String RELEASE_VERIFIER_VERSION =
            "TS-VERIFIER-RELEASE-INTEGRITY-V1";

    private static final String MANIFEST_VERSION =
            "TS-VERIFIER-RELEASE-MANIFEST-V1";

    private static final String ARTIFACT_VERSION =
            "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1";

    private static final String ALGORITHM =
            "ED25519";

    private static final Pattern SHA256 =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private static final String[] PREFIXES = {
            "manifestVersion=",
            "artifactName=",
            "artifactVersion=",
            "artifactSha256=",
            "releaseKeyId=",
            "algorithm=",
            "releasePublicKeyX509Base64=",
            "releasePublicKeyFingerprint=",
            "signatureBase64="
    };

    private TerraScopeVerifierReleaseVerifier() {
    }

    public static void main(
            String[] args
    ) {
        int exitCode;

        try {
            Verification result =
                    verify(
                            Configuration.parse(
                                    args
                            )
                    );

            print(
                    result
            );

            exitCode =
                    result.valid()
                            ? 0
                            : 2;

        } catch (
                Exception exception
        ) {
            Verification result =
                    Verification.failure(
                            safeMessage(
                                    exception
                            )
                    );

            print(
                    result
            );

            exitCode =
                    2;
        }

        System.exit(
                exitCode
        );
    }

    private static Verification verify(
            Configuration configuration
    ) {
        boolean manifestShapeValid =
                false;

        boolean artifactNameValid =
                false;

        boolean artifactVersionValid =
                false;

        boolean artifactHashValid =
                false;

        boolean publicKeyFingerprintValid =
                false;

        boolean releaseKeyPinValid =
                false;

        boolean signatureValid =
                false;

        String artifactSha256 =
                null;

        String releaseKeyFingerprint =
                null;

        String message;

        try {
            List<String> lines =
                    Files.readAllLines(
                            configuration.manifest(),
                            StandardCharsets.UTF_8
                    );

            if (
                    lines.size()
                    != PREFIXES.length
            ) {
                throw new IllegalArgumentException(
                        "Release manifest must contain exactly nine canonical lines."
                );
            }

            String[] values =
                    new String[
                            PREFIXES.length
                    ];

            for (
                    int index = 0;
                    index < PREFIXES.length;
                    index++
            ) {
                String line =
                        lines.get(
                                index
                        );

                String prefix =
                        PREFIXES[index];

                if (
                        !line.startsWith(
                                prefix
                        )
                ) {
                    throw new IllegalArgumentException(
                            "Release manifest field order or field name is invalid at line "
                            + (
                                index + 1
                            )
                            + "."
                    );
                }

                values[index] =
                        line.substring(
                                prefix.length()
                        );

                if (
                        values[index].isBlank()
                ) {
                    throw new IllegalArgumentException(
                            "Release manifest contains a blank field at line "
                            + (
                                index + 1
                            )
                            + "."
                    );
                }
            }

            String manifestVersion =
                    values[0];

            String artifactName =
                    values[1];

            String artifactVersion =
                    values[2];

            String expectedArtifactSha256 =
                    values[3]
                            .toLowerCase(
                                    Locale.ROOT
                            );

            String releaseKeyId =
                    values[4];

            String algorithm =
                    values[5];

            String publicKeyBase64 =
                    values[6];

            String expectedPublicKeyFingerprint =
                    values[7]
                            .toLowerCase(
                                    Locale.ROOT
                            );

            String signatureBase64 =
                    values[8];

            manifestShapeValid =
                    MANIFEST_VERSION.equals(
                            manifestVersion
                    )

                    && ALGORITHM.equals(
                            algorithm
                    )

                    && !releaseKeyId.isBlank()

                    && SHA256.matcher(
                            expectedArtifactSha256
                    ).matches()

                    && SHA256.matcher(
                            expectedPublicKeyFingerprint
                    ).matches();

            artifactNameValid =
                    artifactName.equals(
                            configuration
                                    .artifact()
                                    .getFileName()
                                    .toString()
                    );

            artifactVersionValid =
                    ARTIFACT_VERSION.equals(
                            artifactVersion
                    );

            artifactSha256 =
                    sha256(
                            Files.readAllBytes(
                                    configuration.artifact()
                            )
                    );

            artifactHashValid =
                    artifactSha256.equals(
                            expectedArtifactSha256
                    );

            byte[] publicKeyBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    publicKeyBase64
                            );

            releaseKeyFingerprint =
                    sha256(
                            publicKeyBytes
                    );

            publicKeyFingerprintValid =
                    releaseKeyFingerprint.equals(
                            expectedPublicKeyFingerprint
                    );

            String trustedPin =
                    Files.readString(
                                    configuration.releaseKeyPin(),
                                    StandardCharsets.UTF_8
                            )
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            releaseKeyPinValid =
                    SHA256.matcher(
                            trustedPin
                    ).matches()

                    && trustedPin.equals(
                            releaseKeyFingerprint
                    );

            String payload =
                    String.join(
                            "\n",
                            lines.subList(
                                    0,
                                    8
                            )
                    )
                    + "\n";

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

            signatureValid =
                    signature.verify(
                            Base64
                                    .getDecoder()
                                    .decode(
                                            signatureBase64
                                    )
                    );

            boolean valid =
                    manifestShapeValid

                    && artifactNameValid

                    && artifactVersionValid

                    && artifactHashValid

                    && publicKeyFingerprintValid

                    && releaseKeyPinValid

                    && signatureValid;

            if (
                    !manifestShapeValid
            ) {
                message =
                        "Verifier release manifest structure or declared algorithm is invalid.";

            } else if (
                    !artifactNameValid
            ) {
                message =
                        "Verifier artifact filename does not match the signed release manifest.";

            } else if (
                    !artifactVersionValid
            ) {
                message =
                        "Verifier artifact version is unsupported.";

            } else if (
                    !artifactHashValid
            ) {
                message =
                        "Verifier artifact SHA-256 does not match the signed release manifest.";

            } else if (
                    !publicKeyFingerprintValid
            ) {
                message =
                        "Embedded release public-key fingerprint is invalid.";

            } else if (
                    !releaseKeyPinValid
            ) {
                message =
                        "Release key does not match the independently trusted release-key fingerprint.";

            } else if (
                    !signatureValid
            ) {
                message =
                        "Verifier release manifest Ed25519 signature is invalid.";

            } else {
                message =
                        "Standalone checkpoint verifier release authenticated successfully against the independently supplied release-key fingerprint.";
            }

            return new Verification(
                    valid,

                    manifestShapeValid,

                    artifactNameValid,

                    artifactVersionValid,

                    artifactHashValid,

                    publicKeyFingerprintValid,

                    releaseKeyPinValid,

                    signatureValid,

                    artifactSha256,

                    releaseKeyFingerprint,

                    message
            );

        } catch (
                Exception exception
        ) {
            message =
                    "Verifier release authentication failed: "
                            + safeMessage(
                                    exception
                            );

            return new Verification(
                    false,

                    manifestShapeValid,

                    artifactNameValid,

                    artifactVersionValid,

                    artifactHashValid,

                    publicKeyFingerprintValid,

                    releaseKeyPinValid,

                    signatureValid,

                    artifactSha256,

                    releaseKeyFingerprint,

                    message
            );
        }
    }

    private static void print(
            Verification result
    ) {
        System.out.println(
                "RELEASE_VERIFIER_VERSION="
                + RELEASE_VERIFIER_VERSION
        );

        System.out.println(
                "MANIFEST_SHAPE_VALID="
                + result.manifestShapeValid()
        );

        System.out.println(
                "ARTIFACT_NAME_VALID="
                + result.artifactNameValid()
        );

        System.out.println(
                "ARTIFACT_VERSION_VALID="
                + result.artifactVersionValid()
        );

        System.out.println(
                "ARTIFACT_HASH_VALID="
                + result.artifactHashValid()
        );

        System.out.println(
                "PUBLIC_KEY_FINGERPRINT_VALID="
                + result.publicKeyFingerprintValid()
        );

        System.out.println(
                "RELEASE_KEY_PIN_VALID="
                + result.releaseKeyPinValid()
        );

        System.out.println(
                "SIGNATURE_VALID="
                + result.signatureValid()
        );

        System.out.println(
                "ARTIFACT_SHA256="
                + value(
                        result.artifactSha256()
                )
        );

        System.out.println(
                "RELEASE_KEY_FINGERPRINT="
                + value(
                        result.releaseKeyFingerprint()
                )
        );

        System.out.println(
                "VERIFICATION_RESULT="
                + (
                    result.valid()
                            ? "VALID"
                            : "INVALID"
                )
        );

        System.out.println(
                "MESSAGE="
                + result.message()
        );
    }

    private static String value(
            String value
    ) {
        return value == null
                ? "<null>"
                : value;
    }

    private static String sha256(
            byte[] bytes
    ) throws Exception {
        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        return HexFormat
                .of()
                .formatHex(
                        digest.digest(
                                bytes
                        )
                )
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private static String safeMessage(
            Exception exception
    ) {
        String message =
                exception.getMessage();

        return message == null
                || message.isBlank()
                ? exception
                        .getClass()
                        .getSimpleName()
                : message;
    }

    private record Verification(
            boolean valid,

            boolean manifestShapeValid,

            boolean artifactNameValid,

            boolean artifactVersionValid,

            boolean artifactHashValid,

            boolean publicKeyFingerprintValid,

            boolean releaseKeyPinValid,

            boolean signatureValid,

            String artifactSha256,

            String releaseKeyFingerprint,

            String message
    ) {

        static Verification failure(
                String message
        ) {
            return new Verification(
                    false,

                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,

                    null,
                    null,

                    message
            );
        }
    }

    private record Configuration(
            Path artifact,
            Path manifest,
            Path releaseKeyPin
    ) {

        static Configuration parse(
                String[] args
        ) {
            Path artifact =
                    null;

            Path manifest =
                    null;

            Path releaseKeyPin =
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

                    case "--manifest" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        manifest =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--release-key-pin" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        releaseKeyPin =
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
                    manifest,
                    "--manifest"
            );

            requireFile(
                    releaseKeyPin,
                    "--release-key-pin"
            );

            return new Configuration(
                    artifact,
                    manifest,
                    releaseKeyPin
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
