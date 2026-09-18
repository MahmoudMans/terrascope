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
import java.util.List;
import java.util.Locale;

import java.util.regex.Pattern;

public final class TerraScopeVerifierReleaseTrustState {

    private static final String TOOL_VERSION =
            "TS-VERIFIER-RELEASE-TRUST-STATE-TOOL-V1";

    private static final String TRUST_STATE_VERSION =
            "TS-VERIFIER-RELEASE-TRUST-STATE-V1";

    private static final String CHECKPOINT_VERIFIER_VERSION =
            "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1";

    private static final String ALGORITHM =
            "ED25519";

    private static final Pattern SHA256 =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private static final String[] PREFIXES = {
            "trustStateVersion=",
            "stateSequence=",
            "releaseRootKeyId=",
            "releaseRootPublicKeyX509Base64=",
            "releaseRootKeyFingerprint=",
            "releaseAuthorityHeadHash=",
            "transparencyKeyFingerprint=",
            "transparencyHeadHash=",
            "checkpointVerifierArtifactVersion=",
            "algorithm=",
            "signatureBase64=",
            "stateHash="
    };

    private TerraScopeVerifierReleaseTrustState() {
    }

    public static void main(
            String[] args
    ) {
        int exitCode =
                0;

        try {
            if (
                    args.length == 0
            ) {
                throw new IllegalArgumentException(
                        "Mode is required: issue or verify."
                );
            }

            switch (
                    args[0]
            ) {
                case "issue" ->
                        issue(
                                IssueConfiguration.parse(
                                        tail(
                                                args
                                        )
                                )
                        );

                case "verify" -> {
                    Verification result =
                            verify(
                                    VerifyConfiguration.parse(
                                            tail(
                                                    args
                                            )
                                    )
                            );

                    print(
                            result
                    );

                    exitCode =
                            result.valid()
                                    ? 0
                                    : 2;
                }

                default ->
                        throw new IllegalArgumentException(
                                "Unsupported mode: "
                                + args[0]
                        );
            }

        } catch (
                Exception exception
        ) {
            if (
                    args.length > 0

                    && "verify".equals(
                            args[0]
                    )
            ) {
                Verification failure =
                        Verification.failure(
                                safeMessage(
                                        exception
                                )
                        );

                print(
                        failure
                );

                exitCode =
                        2;

            } else {
                exception.printStackTrace(
                        System.err
                );

                exitCode =
                        1;
            }
        }

        System.exit(
                exitCode
        );
    }

    private static void issue(
            IssueConfiguration configuration
    ) throws Exception {
        if (
                configuration.stateSequence()
                < 1
        ) {
            throw new IllegalArgumentException(
                    "Trust-state sequence must be at least 1."
            );
        }

        String rootKeyId =
                singleLine(
                        configuration.releaseRootKeyId(),
                        "release root key ID"
                );

        String rootPrivateBase64 =
                Files.readString(
                                configuration.releaseRootPrivateKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

        String rootPublicBase64 =
                Files.readString(
                                configuration.releaseRootPublicKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

        byte[] rootPrivateBytes =
                Base64
                        .getDecoder()
                        .decode(
                                rootPrivateBase64
                        );

        byte[] rootPublicBytes =
                Base64
                        .getDecoder()
                        .decode(
                                rootPublicBase64
                        );

        String rootFingerprint =
                sha256(
                        rootPublicBytes
                );

        String authorityHead =
                normalizeSha256File(
                        configuration.releaseAuthorityHeadPin(),
                        "release authority head"
                );

        String transparencyFingerprint =
                normalizeSha256File(
                        configuration.transparencyKeyPin(),
                        "transparency key fingerprint"
                );

        String transparencyHead =
                normalizeSha256File(
                        configuration.transparencyHeadPin(),
                        "transparency head"
                );

        String payload =
                signingPayload(
                        configuration.stateSequence(),

                        rootKeyId,

                        rootPublicBase64,

                        rootFingerprint,

                        authorityHead,

                        transparencyFingerprint,

                        transparencyHead
                );

        KeyFactory keyFactory =
                KeyFactory.getInstance(
                        "Ed25519"
                );

        PrivateKey privateKey =
                keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(
                                rootPrivateBytes
                        )
                );

        PublicKey publicKey =
                keyFactory.generatePublic(
                        new X509EncodedKeySpec(
                                rootPublicBytes
                        )
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
                    "Release root private/public key pair does not match."
            );
        }

        String stateHash =
                sha256(
                        payload

                        + "signatureBase64="
                        + signatureBase64
                        + "\n"
                );

        String state =
                payload

                + "signatureBase64="
                + signatureBase64
                + "\n"

                + "stateHash="
                + stateHash
                + "\n";

        Path parent =
                configuration
                        .output()
                        .toAbsolutePath()
                        .getParent();

        if (
                parent != null
        ) {
            Files.createDirectories(
                    parent
            );
        }

        Files.writeString(
                configuration.output(),
                state,
                StandardCharsets.UTF_8
        );

        System.out.println(
                "TRUST_STATE_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "TRUST_STATE_VERSION="
                + TRUST_STATE_VERSION
        );

        System.out.println(
                "STATE_SEQUENCE="
                + configuration.stateSequence()
        );

        System.out.println(
                "RELEASE_ROOT_KEY_ID="
                + rootKeyId
        );

        System.out.println(
                "RELEASE_ROOT_KEY_FINGERPRINT="
                + rootFingerprint
        );

        System.out.println(
                "RELEASE_AUTHORITY_HEAD_HASH="
                + authorityHead
        );

        System.out.println(
                "TRANSPARENCY_KEY_FINGERPRINT="
                + transparencyFingerprint
        );

        System.out.println(
                "TRANSPARENCY_HEAD_HASH="
                + transparencyHead
        );

        System.out.println(
                "STATE_HASH="
                + stateHash
        );

        System.out.println(
                "SIGNATURE_SELF_VERIFICATION=PASS"
        );

        System.out.println(
                "ISSUE_RESULT=PASS"
        );
    }

    private static Verification verify(
            VerifyConfiguration configuration
    ) throws Exception {
        List<String> lines =
                Files.readAllLines(
                        configuration.state(),
                        StandardCharsets.UTF_8
                );

        boolean shapeValid =
                lines.size()
                == PREFIXES.length;

        String[] values =
                new String[
                        PREFIXES.length
                ];

        if (
                shapeValid
        ) {
            for (
                    int index = 0;
                    index < PREFIXES.length;
                    index++
            ) {
                String prefix =
                        PREFIXES[index];

                String line =
                        lines.get(
                                index
                        );

                if (
                        !line.startsWith(
                                prefix
                        )
                ) {
                    shapeValid =
                            false;

                    break;
                }

                values[index] =
                        line.substring(
                                prefix.length()
                        );

                if (
                        values[index].isBlank()
                ) {
                    shapeValid =
                            false;

                    break;
                }
            }
        }

        if (
                !shapeValid
        ) {
            return Verification.failure(
                    "Release trust-state canonical structure is invalid."
            );
        }

        String version =
                values[0];

        int sequence;

        try {
            sequence =
                    Integer.parseInt(
                            values[1]
                    );

        } catch (
                NumberFormatException exception
        ) {
            return Verification.failure(
                    "Release trust-state sequence is invalid."
            );
        }

        String rootKeyId =
                values[2];

        String rootPublicBase64 =
                values[3];

        String declaredRootFingerprint =
                values[4]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String authorityHead =
                values[5]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String transparencyFingerprint =
                values[6]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String transparencyHead =
                values[7]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String artifactVersion =
                values[8];

        String algorithm =
                values[9];

        String signatureBase64 =
                values[10];

        String declaredStateHash =
                values[11]
                        .toLowerCase(
                                Locale.ROOT
                        );

        shapeValid =
                TRUST_STATE_VERSION.equals(
                        version
                )

                && sequence >= 1

                && !rootKeyId.isBlank()

                && CHECKPOINT_VERIFIER_VERSION.equals(
                        artifactVersion
                )

                && ALGORITHM.equals(
                        algorithm
                )

                && SHA256.matcher(
                        declaredRootFingerprint
                ).matches()

                && SHA256.matcher(
                        authorityHead
                ).matches()

                && SHA256.matcher(
                        transparencyFingerprint
                ).matches()

                && SHA256.matcher(
                        transparencyHead
                ).matches()

                && SHA256.matcher(
                        declaredStateHash
                ).matches();

        if (
                !shapeValid
        ) {
            return Verification.failure(
                    "Release trust-state declaration is invalid."
            );
        }

        byte[] rootPublicBytes;

        try {
            rootPublicBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    rootPublicBase64
                            );

        } catch (
                Exception exception
        ) {
            return Verification.failure(
                    "Release trust-state contains an invalid release-root public key."
            );
        }

        String calculatedRootFingerprint =
                sha256(
                        rootPublicBytes
                );

        boolean rootFingerprintValid =
                calculatedRootFingerprint.equals(
                        declaredRootFingerprint
                );

        String trustedRoot =
                normalizeSha256File(
                        configuration.rootPin(),
                        "release root pin"
                );

        boolean rootPinValid =
                rootFingerprintValid

                && trustedRoot.equals(
                        calculatedRootFingerprint
                );

        String payload =
                signingPayload(
                        sequence,

                        rootKeyId,

                        rootPublicBase64,

                        declaredRootFingerprint,

                        authorityHead,

                        transparencyFingerprint,

                        transparencyHead
                );

        boolean signatureValid;

        try {
            PublicKey publicKey =
                    KeyFactory
                            .getInstance(
                                    "Ed25519"
                            )
                            .generatePublic(
                                    new X509EncodedKeySpec(
                                            rootPublicBytes
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
                    payload.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            signatureValid =
                    verifier.verify(
                            Base64
                                    .getDecoder()
                                    .decode(
                                            signatureBase64
                                    )
                    );

        } catch (
                Exception exception
        ) {
            signatureValid =
                    false;
        }

        String calculatedStateHash =
                sha256(
                        payload

                        + "signatureBase64="
                        + signatureBase64
                        + "\n"
                );

        boolean stateHashValid =
                calculatedStateHash.equals(
                        declaredStateHash
                );

        String trustedStateHead =
                normalizeSha256File(
                        configuration.stateHeadPin(),
                        "current trust-state head"
                );

        boolean stateHeadPinValid =
                trustedStateHead.equals(
                        calculatedStateHash
                )

                && stateHashValid;

        boolean valid =
                shapeValid

                && rootFingerprintValid

                && rootPinValid

                && signatureValid

                && stateHashValid

                && stateHeadPinValid;

        String message;

        if (
                !rootFingerprintValid
        ) {
            message =
                    "Release trust-state root public-key fingerprint is invalid.";

        } else if (
                !rootPinValid
        ) {
            message =
                    "Release trust-state root key does not match the independently trusted root pin.";

        } else if (
                !signatureValid
        ) {
            message =
                    "Release trust-state Ed25519 signature is invalid.";

        } else if (
                !stateHashValid
        ) {
            message =
                    "Release trust-state SHA-256 is invalid.";

        } else if (
                !stateHeadPinValid
        ) {
            message =
                    "Release trust-state is authentic but is not the independently pinned current trust state.";

        } else {
            message =
                    "Release trust-state is authentic and matches the independently pinned current trust-state hash.";
        }

        Verification result =
                new Verification(
                        valid,

                        shapeValid,

                        rootFingerprintValid,

                        rootPinValid,

                        signatureValid,

                        stateHashValid,

                        stateHeadPinValid,

                        sequence,

                        calculatedRootFingerprint,

                        authorityHead,

                        transparencyFingerprint,

                        transparencyHead,

                        calculatedStateHash,

                        message
                );

        if (
                result.valid()
        ) {
            writeOptional(
                    configuration.releaseAuthorityHeadOutput(),
                    authorityHead
            );

            writeOptional(
                    configuration.transparencyKeyOutput(),
                    transparencyFingerprint
            );

            writeOptional(
                    configuration.transparencyHeadOutput(),
                    transparencyHead
            );
        }

        return result;
    }

    private static String signingPayload(
            int sequence,

            String rootKeyId,

            String rootPublicKeyX509Base64,

            String rootFingerprint,

            String authorityHead,

            String transparencyFingerprint,

            String transparencyHead
    ) {
        return """
                trustStateVersion=%s
                stateSequence=%d
                releaseRootKeyId=%s
                releaseRootPublicKeyX509Base64=%s
                releaseRootKeyFingerprint=%s
                releaseAuthorityHeadHash=%s
                transparencyKeyFingerprint=%s
                transparencyHeadHash=%s
                checkpointVerifierArtifactVersion=%s
                algorithm=%s
                """
                .formatted(
                        TRUST_STATE_VERSION,

                        sequence,

                        rootKeyId,

                        rootPublicKeyX509Base64,

                        rootFingerprint,

                        authorityHead,

                        transparencyFingerprint,

                        transparencyHead,

                        CHECKPOINT_VERIFIER_VERSION,

                        ALGORITHM
                );
    }

    private static void writeOptional(
            Path output,
            String value
    ) throws Exception {
        if (
                output == null
        ) {
            return;
        }

        Path parent =
                output
                        .toAbsolutePath()
                        .getParent();

        if (
                parent != null
        ) {
            Files.createDirectories(
                    parent
            );
        }

        Files.writeString(
                output,

                value
                + "\n",

                StandardCharsets.UTF_8
        );
    }

    private static void print(
            Verification result
    ) {
        System.out.println(
                "TRUST_STATE_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "TRUST_STATE_SHAPE_VALID="
                + result.shapeValid()
        );

        System.out.println(
                "ROOT_FINGERPRINT_VALID="
                + result.rootFingerprintValid()
        );

        System.out.println(
                "ROOT_PIN_VALID="
                + result.rootPinValid()
        );

        System.out.println(
                "SIGNATURE_VALID="
                + result.signatureValid()
        );

        System.out.println(
                "STATE_HASH_VALID="
                + result.stateHashValid()
        );

        System.out.println(
                "STATE_HEAD_PIN_VALID="
                + result.stateHeadPinValid()
        );

        System.out.println(
                "STATE_SEQUENCE="
                + result.sequence()
        );

        System.out.println(
                "RELEASE_ROOT_KEY_FINGERPRINT="
                + value(
                        result.rootFingerprint()
                )
        );

        System.out.println(
                "RELEASE_AUTHORITY_HEAD_HASH="
                + value(
                        result.releaseAuthorityHeadHash()
                )
        );

        System.out.println(
                "TRANSPARENCY_KEY_FINGERPRINT="
                + value(
                        result.transparencyKeyFingerprint()
                )
        );

        System.out.println(
                "TRANSPARENCY_HEAD_HASH="
                + value(
                        result.transparencyHeadHash()
                )
        );

        System.out.println(
                "STATE_HASH="
                + value(
                        result.stateHash()
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

    private static String normalizeSha256File(
            Path file,
            String label
    ) throws Exception {
        String value =
                Files.readString(
                                file,
                                StandardCharsets.UTF_8
                        )
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                !SHA256.matcher(
                        value
                ).matches()
        ) {
            throw new IllegalArgumentException(
                    label
                    + " must contain exactly one SHA-256 hexadecimal value."
            );
        }

        return value;
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
            String value
    ) throws Exception {
        return sha256(
                value.getBytes(
                        StandardCharsets.UTF_8
                )
        );
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

    private static String value(
            Object value
    ) {
        return value == null
                ? "<null>"
                : value.toString();
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

    private static String[] tail(
            String[] args
    ) {
        String[] result =
                new String[
                        args.length - 1
                ];

        System.arraycopy(
                args,
                1,
                result,
                0,
                result.length
        );

        return result;
    }

    private record Verification(
            boolean valid,

            boolean shapeValid,

            boolean rootFingerprintValid,

            boolean rootPinValid,

            boolean signatureValid,

            boolean stateHashValid,

            boolean stateHeadPinValid,

            int sequence,

            String rootFingerprint,

            String releaseAuthorityHeadHash,

            String transparencyKeyFingerprint,

            String transparencyHeadHash,

            String stateHash,

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

                    0,

                    null,
                    null,
                    null,
                    null,
                    null,

                    message
            );
        }
    }

    private record IssueConfiguration(
            int stateSequence,

            String releaseRootKeyId,

            Path releaseRootPrivateKey,

            Path releaseRootPublicKey,

            Path releaseAuthorityHeadPin,

            Path transparencyKeyPin,

            Path transparencyHeadPin,

            Path output
    ) {

        static IssueConfiguration parse(
                String[] args
        ) {
            Integer sequence =
                    null;

            String rootKeyId =
                    null;

            Path rootPrivate =
                    null;

            Path rootPublic =
                    null;

            Path authorityHead =
                    null;

            Path transparencyKey =
                    null;

            Path transparencyHead =
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
                    case "--state-sequence" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        sequence =
                                Integer.parseInt(
                                        args[index]
                                );
                    }

                    case "--release-root-key-id" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        rootKeyId =
                                args[index];
                    }

                    case "--release-root-private-key" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        rootPrivate =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--release-root-public-key" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        rootPublic =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--release-authority-head-pin" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        authorityHead =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--transparency-key-pin" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        transparencyKey =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--transparency-head-pin" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        transparencyHead =
                                Path.of(
                                        args[index]
                                );
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
                                    "Unknown issue argument: "
                                    + args[index]
                            );
                }
            }

            if (
                    sequence == null
            ) {
                throw new IllegalArgumentException(
                        "--state-sequence is required."
                );
            }

            if (
                    rootKeyId == null

                    || rootKeyId.isBlank()
            ) {
                throw new IllegalArgumentException(
                        "--release-root-key-id is required."
                );
            }

            requireFile(
                    rootPrivate,
                    "--release-root-private-key"
            );

            requireFile(
                    rootPublic,
                    "--release-root-public-key"
            );

            requireFile(
                    authorityHead,
                    "--release-authority-head-pin"
            );

            requireFile(
                    transparencyKey,
                    "--transparency-key-pin"
            );

            requireFile(
                    transparencyHead,
                    "--transparency-head-pin"
            );

            if (
                    output == null
            ) {
                throw new IllegalArgumentException(
                        "--output is required."
                );
            }

            return new IssueConfiguration(
                    sequence,

                    rootKeyId,

                    rootPrivate,

                    rootPublic,

                    authorityHead,

                    transparencyKey,

                    transparencyHead,

                    output
            );
        }
    }

    private record VerifyConfiguration(
            Path state,

            Path rootPin,

            Path stateHeadPin,

            Path releaseAuthorityHeadOutput,

            Path transparencyKeyOutput,

            Path transparencyHeadOutput
    ) {

        static VerifyConfiguration parse(
                String[] args
        ) {
            Path state =
                    null;

            Path rootPin =
                    null;

            Path stateHeadPin =
                    null;

            Path authorityOutput =
                    null;

            Path transparencyKeyOutput =
                    null;

            Path transparencyHeadOutput =
                    null;

            for (
                    int index = 0;
                    index < args.length;
                    index++
            ) {
                switch (
                        args[index]
                ) {
                    case "--state" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        state =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--root-pin" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        rootPin =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--state-head-pin" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        stateHeadPin =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--release-authority-head-output" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        authorityOutput =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--transparency-key-output" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        transparencyKeyOutput =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--transparency-head-output" -> {
                        index =
                                valueIndex(
                                        args,
                                        index
                                );

                        transparencyHeadOutput =
                                Path.of(
                                        args[index]
                                );
                    }

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown verify argument: "
                                    + args[index]
                            );
                }
            }

            requireFile(
                    state,
                    "--state"
            );

            requireFile(
                    rootPin,
                    "--root-pin"
            );

            requireFile(
                    stateHeadPin,
                    "--state-head-pin"
            );

            return new VerifyConfiguration(
                    state,

                    rootPin,

                    stateHeadPin,

                    authorityOutput,

                    transparencyKeyOutput,

                    transparencyHeadOutput
            );
        }
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
