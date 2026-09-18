package com.terrascope.tools;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import java.util.regex.Pattern;

public final class TerraScopeVerifierReleaseAuthorityLineage {

    private static final String TOOL_VERSION =
            "TS-VERIFIER-RELEASE-AUTHORITY-LINEAGE-TOOL-V1";

    private static final String LINEAGE_VERSION =
            "TS-VERIFIER-RELEASE-AUTHORITY-LINEAGE-V1";

    private static final Pattern SHA256 =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private static final String[] PREFIXES = {
            "lineageVersion=",
            "sequence=",
            "outgoingKeyId=",
            "outgoingPublicKeyX509Base64=",
            "outgoingKeyFingerprint=",
            "replacementKeyId=",
            "replacementPublicKeyX509Base64=",
            "replacementKeyFingerprint=",
            "previousTransitionHash=",
            "signatureBase64=",
            "transitionHash="
    };

    private TerraScopeVerifierReleaseAuthorityLineage() {
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
                        "Mode is required: append or verify."
                );
            }

            switch (
                    args[0]
            ) {
                case "append" ->
                        append(
                                AppendConfiguration.parse(
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

    private static void append(
            AppendConfiguration configuration
    ) throws Exception {
        String trustedRoot =
                normalizeSha256File(
                        configuration.rootPin(),
                        "release root key pin"
                );

        byte[] outgoingPrivateBytes =
                Base64
                        .getDecoder()
                        .decode(
                                Files.readString(
                                                configuration.outgoingPrivateKey(),
                                                StandardCharsets.UTF_8
                                        )
                                        .trim()
                        );

        String outgoingPublicBase64 =
                Files.readString(
                                configuration.outgoingPublicKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

        byte[] outgoingPublicBytes =
                Base64
                        .getDecoder()
                        .decode(
                                outgoingPublicBase64
                        );

        String replacementPublicBase64 =
                Files.readString(
                                configuration.replacementPublicKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

        byte[] replacementPublicBytes =
                Base64
                        .getDecoder()
                        .decode(
                                replacementPublicBase64
                        );

        String outgoingFingerprint =
                sha256(
                        outgoingPublicBytes
                );

        String replacementFingerprint =
                sha256(
                        replacementPublicBytes
                );

        String outgoingKeyId =
                singleLine(
                        configuration.outgoingKeyId(),
                        "outgoing key ID"
                );

        String replacementKeyId =
                singleLine(
                        configuration.replacementKeyId(),
                        "replacement key ID"
                );

        if (
                outgoingKeyId.equals(
                        replacementKeyId
                )
        ) {
            throw new IllegalArgumentException(
                    "Outgoing and replacement key IDs must differ."
            );
        }

        if (
                outgoingFingerprint.equals(
                        replacementFingerprint
                )
        ) {
            throw new IllegalArgumentException(
                    "Outgoing and replacement public keys must differ."
            );
        }

        List<Transition> existing =
                readLineage(
                        configuration.lineage()
                );

        if (
                existing.isEmpty()
        ) {
            if (
                    !trustedRoot.equals(
                            outgoingFingerprint
                    )
            ) {
                throw new IllegalArgumentException(
                        "First outgoing key does not match the independently trusted release root pin."
                );
            }

        } else {
            Verification existingVerification =
                    verifyEntries(
                            existing,
                            trustedRoot
                    );

            if (
                    !existingVerification.valid()
            ) {
                throw new IllegalArgumentException(
                        "Existing release authority lineage is invalid: "
                        + existingVerification.message()
                );
            }

            if (
                    !outgoingFingerprint.equals(
                            existingVerification.terminalKeyFingerprint()
                    )

                    || !outgoingKeyId.equals(
                            existingVerification.terminalKeyId()
                    )
            ) {
                throw new IllegalArgumentException(
                        "Outgoing key does not match the verified terminal release authority."
                );
            }
        }

        int sequence =
                existing.size()
                + 1;

        String previousTransitionHash =
                existing.isEmpty()
                        ? null
                        : existing
                                .getLast()
                                .transitionHash();

        String payload =
                signingPayload(
                        sequence,

                        outgoingKeyId,

                        outgoingPublicBase64,

                        outgoingFingerprint,

                        replacementKeyId,

                        replacementPublicBase64,

                        replacementFingerprint,

                        previousTransitionHash
                );

        KeyFactory keyFactory =
                KeyFactory.getInstance(
                        "Ed25519"
                );

        PrivateKey privateKey =
                keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(
                                outgoingPrivateBytes
                        )
                );

        PublicKey publicKey =
                keyFactory.generatePublic(
                        new X509EncodedKeySpec(
                                outgoingPublicBytes
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
                    "Outgoing release private/public key pair does not match."
            );
        }

        String transitionHash =
                sha256(
                        payload

                        + "signatureBase64="
                        + signatureBase64
                        + "\n"
                );

        String block =
                payload

                + "signatureBase64="
                + signatureBase64
                + "\n"

                + "transitionHash="
                + transitionHash
                + "\n\n";

        Path parent =
                configuration
                        .lineage()
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
                configuration.lineage(),
                block,
                StandardCharsets.UTF_8,

                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        System.out.println(
                "AUTHORITY_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "SEQUENCE="
                + sequence
        );

        System.out.println(
                "OUTGOING_KEY_ID="
                + outgoingKeyId
        );

        System.out.println(
                "OUTGOING_KEY_FINGERPRINT="
                + outgoingFingerprint
        );

        System.out.println(
                "REPLACEMENT_KEY_ID="
                + replacementKeyId
        );

        System.out.println(
                "REPLACEMENT_KEY_FINGERPRINT="
                + replacementFingerprint
        );

        System.out.println(
                "PREVIOUS_TRANSITION_HASH="
                + (
                    previousTransitionHash == null
                            ? "GENESIS"
                            : previousTransitionHash
                )
        );

        System.out.println(
                "TRANSITION_HASH="
                + transitionHash
        );

        System.out.println(
                "APPEND_RESULT=PASS"
        );
    }

    private static Verification verify(
            VerifyConfiguration configuration
    ) throws Exception {
        String rootPin =
                normalizeSha256File(
                        configuration.rootPin(),
                        "release root key pin"
                );

        List<Transition> transitions =
                readLineage(
                        configuration.lineage()
                );

        Verification result =
                verifyEntries(
                        transitions,
                        rootPin
                );

        if (
                result.valid()

                && configuration.terminalPinOutput() != null
        ) {
            Path parent =
                    configuration
                            .terminalPinOutput()
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
                    configuration.terminalPinOutput(),

                    result.terminalKeyFingerprint()
                    + "\n",

                    StandardCharsets.UTF_8
            );
        }

        return result;
    }

    private static Verification verifyEntries(
            List<Transition> transitions,
            String trustedRoot
    ) {
        if (
                transitions.isEmpty()
        ) {
            return Verification.failure(
                    "Release authority lineage contains no signed transition."
            );
        }

        int expectedSequence =
                1;

        int verified =
                0;

        boolean rootPinValid =
                false;

        boolean continuityValid =
                true;

        String expectedPreviousHash =
                null;

        String expectedOutgoingKeyId =
                null;

        String expectedOutgoingFingerprint =
                null;

        String terminalKeyId =
                null;

        String terminalFingerprint =
                null;

        String headHash =
                null;

        for (
                Transition transition
                : transitions
        ) {
            if (
                    !LINEAGE_VERSION.equals(
                            transition.lineageVersion()
                    )
            ) {
                return invalid(
                        rootPinValid,
                        false,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Release authority lineage version is unsupported."
                );
            }

            if (
                    transition.sequence()
                    != expectedSequence
            ) {
                return invalid(
                        rootPinValid,
                        false,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Release authority transition sequence is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            transition.previousTransitionHash(),
                            expectedPreviousHash
                    )
            ) {
                return invalid(
                        rootPinValid,
                        false,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Release authority previous-transition hash linkage is invalid."
                );
            }

            byte[] outgoingPublicBytes;

            byte[] replacementPublicBytes;

            try {
                outgoingPublicBytes =
                        Base64
                                .getDecoder()
                                .decode(
                                        transition.outgoingPublicKeyX509Base64()
                                );

                replacementPublicBytes =
                        Base64
                                .getDecoder()
                                .decode(
                                        transition.replacementPublicKeyX509Base64()
                                );

            } catch (
                    Exception exception
            ) {
                return invalid(
                        rootPinValid,
                        false,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Release authority lineage contains invalid public-key encoding."
                );
            }

            String calculatedOutgoingFingerprint;

            String calculatedReplacementFingerprint;

            try {
                calculatedOutgoingFingerprint =
                        sha256(
                                outgoingPublicBytes
                        );

                calculatedReplacementFingerprint =
                        sha256(
                                replacementPublicBytes
                        );

            } catch (
                    Exception exception
            ) {
                return invalid(
                        rootPinValid,
                        false,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Unable to fingerprint release authority public keys."
                );
            }

            if (
                    !calculatedOutgoingFingerprint.equals(
                            transition.outgoingKeyFingerprint()
                    )

                    || !calculatedReplacementFingerprint.equals(
                            transition.replacementKeyFingerprint()
                    )
            ) {
                return invalid(
                        rootPinValid,
                        false,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Release authority public-key fingerprint verification failed."
                );
            }

            if (
                    expectedSequence == 1
            ) {
                rootPinValid =
                        trustedRoot.equals(
                                calculatedOutgoingFingerprint
                        );

                if (
                        !rootPinValid
                ) {
                    return invalid(
                            false,
                            false,
                            transitions.size(),
                            verified,
                            terminalKeyId,
                            terminalFingerprint,
                            headHash,
                            "First release authority does not match the independently trusted root pin."
                    );
                }

            } else {
                if (
                        !transition
                                .outgoingKeyId()
                                .equals(
                                        expectedOutgoingKeyId
                                )

                        || !calculatedOutgoingFingerprint.equals(
                                expectedOutgoingFingerprint
                        )
                ) {
                    continuityValid =
                            false;

                    return invalid(
                            rootPinValid,
                            false,
                            transitions.size(),
                            verified,
                            terminalKeyId,
                            terminalFingerprint,
                            headHash,
                            "Release authority successor continuity is invalid."
                    );
                }
            }

            String payload =
                    signingPayload(
                            transition.sequence(),

                            transition.outgoingKeyId(),

                            transition.outgoingPublicKeyX509Base64(),

                            transition.outgoingKeyFingerprint(),

                            transition.replacementKeyId(),

                            transition.replacementPublicKeyX509Base64(),

                            transition.replacementKeyFingerprint(),

                            transition.previousTransitionHash()
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
                                                outgoingPublicBytes
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
                                                transition.signatureBase64()
                                        )
                        );

            } catch (
                    Exception exception
            ) {
                signatureValid =
                        false;
            }

            if (
                    !signatureValid
            ) {
                return invalid(
                        rootPinValid,
                        continuityValid,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Release authority transition Ed25519 signature verification failed."
                );
            }

            String calculatedTransitionHash;

            try {
                calculatedTransitionHash =
                        sha256(
                                payload

                                + "signatureBase64="
                                + transition.signatureBase64()
                                + "\n"
                        );

            } catch (
                    Exception exception
            ) {
                return invalid(
                        rootPinValid,
                        continuityValid,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Unable to calculate release authority transition hash."
                );
            }

            if (
                    !calculatedTransitionHash.equals(
                            transition.transitionHash()
                    )
            ) {
                return invalid(
                        rootPinValid,
                        continuityValid,
                        transitions.size(),
                        verified,
                        terminalKeyId,
                        terminalFingerprint,
                        headHash,
                        "Release authority transition SHA-256 verification failed."
                );
            }

            expectedPreviousHash =
                    transition.transitionHash();

            expectedOutgoingKeyId =
                    transition.replacementKeyId();

            expectedOutgoingFingerprint =
                    transition.replacementKeyFingerprint();

            terminalKeyId =
                    transition.replacementKeyId();

            terminalFingerprint =
                    transition.replacementKeyFingerprint();

            headHash =
                    transition.transitionHash();

            expectedSequence++;

            verified++;
        }

        return new Verification(
                true,

                rootPinValid,

                continuityValid,

                transitions.size(),

                verified,

                terminalKeyId,

                terminalFingerprint,

                headHash,

                "Release authority lineage is valid."
        );
    }

    private static Verification invalid(
            boolean rootPinValid,

            boolean continuityValid,

            int transitionCount,

            int verifiedTransitionCount,

            String terminalKeyId,

            String terminalKeyFingerprint,

            String headHash,

            String message
    ) {
        return new Verification(
                false,

                rootPinValid,

                continuityValid,

                transitionCount,

                verifiedTransitionCount,

                terminalKeyId,

                terminalKeyFingerprint,

                headHash,

                message
        );
    }

    private static String signingPayload(
            int sequence,

            String outgoingKeyId,

            String outgoingPublicKeyX509Base64,

            String outgoingKeyFingerprint,

            String replacementKeyId,

            String replacementPublicKeyX509Base64,

            String replacementKeyFingerprint,

            String previousTransitionHash
    ) {
        return """
                lineageVersion=%s
                sequence=%d
                outgoingKeyId=%s
                outgoingPublicKeyX509Base64=%s
                outgoingKeyFingerprint=%s
                replacementKeyId=%s
                replacementPublicKeyX509Base64=%s
                replacementKeyFingerprint=%s
                previousTransitionHash=%s
                """
                .formatted(
                        LINEAGE_VERSION,

                        sequence,

                        outgoingKeyId,

                        outgoingPublicKeyX509Base64,

                        outgoingKeyFingerprint,

                        replacementKeyId,

                        replacementPublicKeyX509Base64,

                        replacementKeyFingerprint,

                        previousTransitionHash == null
                                ? "GENESIS"
                                : previousTransitionHash
                );
    }

    private static List<Transition> readLineage(
            Path lineage
    ) throws Exception {
        if (
                !Files.exists(
                        lineage
                )
        ) {
            return List.of();
        }

        List<String> lines =
                Files.readAllLines(
                        lineage,
                        StandardCharsets.UTF_8
                );

        List<Transition> result =
                new ArrayList<>();

        List<String> block =
                new ArrayList<>();

        for (
                String line
                : lines
        ) {
            if (
                    line.isEmpty()
            ) {
                if (
                        !block.isEmpty()
                ) {
                    result.add(
                            parseBlock(
                                    block
                            )
                    );

                    block =
                            new ArrayList<>();
                }

                continue;
            }

            block.add(
                    line
            );
        }

        if (
                !block.isEmpty()
        ) {
            result.add(
                    parseBlock(
                            block
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static Transition parseBlock(
            List<String> lines
    ) {
        if (
                lines.size()
                != PREFIXES.length
        ) {
            throw new IllegalArgumentException(
                    "Release authority transition must contain exactly eleven canonical lines."
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
                throw new IllegalArgumentException(
                        "Release authority transition field order is invalid."
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
                        "Release authority transition contains a blank canonical field."
                );
            }
        }

        int sequence;

        try {
            sequence =
                    Integer.parseInt(
                            values[1]
                    );

        } catch (
                NumberFormatException exception
        ) {
            throw new IllegalArgumentException(
                    "Release authority transition sequence is invalid."
            );
        }

        String outgoingFingerprint =
                values[4]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String replacementFingerprint =
                values[7]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String previousTransitionHash =
                "GENESIS".equals(
                        values[8]
                )
                        ? null
                        : values[8]
                                .toLowerCase(
                                        Locale.ROOT
                                );

        String transitionHash =
                values[10]
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                !SHA256.matcher(
                        outgoingFingerprint
                ).matches()

                || !SHA256.matcher(
                        replacementFingerprint
                ).matches()

                || (
                    previousTransitionHash != null

                    && !SHA256.matcher(
                            previousTransitionHash
                    ).matches()
                )

                || !SHA256.matcher(
                        transitionHash
                ).matches()
        ) {
            throw new IllegalArgumentException(
                    "Release authority transition contains malformed SHA-256 data."
            );
        }

        return new Transition(
                values[0],

                sequence,

                values[2],

                values[3],

                outgoingFingerprint,

                values[5],

                values[6],

                replacementFingerprint,

                previousTransitionHash,

                values[9],

                transitionHash
        );
    }

    private static void print(
            Verification result
    ) {
        System.out.println(
                "AUTHORITY_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "ROOT_PIN_VALID="
                + result.rootPinValid()
        );

        System.out.println(
                "SUCCESSOR_CONTINUITY_VALID="
                + result.continuityValid()
        );

        System.out.println(
                "LINEAGE_VALID="
                + result.valid()
        );

        System.out.println(
                "TRANSITION_COUNT="
                + result.transitionCount()
        );

        System.out.println(
                "VERIFIED_TRANSITION_COUNT="
                + result.verifiedTransitionCount()
        );

        System.out.println(
                "TERMINAL_KEY_ID="
                + value(
                        result.terminalKeyId()
                )
        );

        System.out.println(
                "TERMINAL_KEY_FINGERPRINT="
                + value(
                        result.terminalKeyFingerprint()
                )
        );

        System.out.println(
                "LINEAGE_HEAD_HASH="
                + value(
                        result.headHash()
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

    private static String value(
            Object value
    ) {
        return value == null
                ? "<null>"
                : value.toString();
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

    private record Transition(
            String lineageVersion,

            int sequence,

            String outgoingKeyId,

            String outgoingPublicKeyX509Base64,

            String outgoingKeyFingerprint,

            String replacementKeyId,

            String replacementPublicKeyX509Base64,

            String replacementKeyFingerprint,

            String previousTransitionHash,

            String signatureBase64,

            String transitionHash
    ) {
    }

    private record Verification(
            boolean valid,

            boolean rootPinValid,

            boolean continuityValid,

            int transitionCount,

            int verifiedTransitionCount,

            String terminalKeyId,

            String terminalKeyFingerprint,

            String headHash,

            String message
    ) {

        static Verification failure(
                String message
        ) {
            return new Verification(
                    false,

                    false,
                    false,

                    0,
                    0,

                    null,
                    null,
                    null,

                    message
            );
        }
    }

    private record AppendConfiguration(
            Path lineage,

            Path rootPin,

            String outgoingKeyId,

            Path outgoingPrivateKey,

            Path outgoingPublicKey,

            String replacementKeyId,

            Path replacementPublicKey
    ) {

        static AppendConfiguration parse(
                String[] args
        ) {
            Path lineage =
                    null;

            Path rootPin =
                    null;

            String outgoingKeyId =
                    null;

            Path outgoingPrivateKey =
                    null;

            Path outgoingPublicKey =
                    null;

            String replacementKeyId =
                    null;

            Path replacementPublicKey =
                    null;

            for (
                    int index = 0;
                    index < args.length;
                    index++
            ) {
                switch (
                        args[index]
                ) {
                    case "--lineage" -> {
                        index = valueIndex(args, index);
                        lineage = Path.of(args[index]);
                    }

                    case "--root-pin" -> {
                        index = valueIndex(args, index);
                        rootPin = Path.of(args[index]);
                    }

                    case "--outgoing-key-id" -> {
                        index = valueIndex(args, index);
                        outgoingKeyId = args[index];
                    }

                    case "--outgoing-private-key" -> {
                        index = valueIndex(args, index);
                        outgoingPrivateKey = Path.of(args[index]);
                    }

                    case "--outgoing-public-key" -> {
                        index = valueIndex(args, index);
                        outgoingPublicKey = Path.of(args[index]);
                    }

                    case "--replacement-key-id" -> {
                        index = valueIndex(args, index);
                        replacementKeyId = args[index];
                    }

                    case "--replacement-public-key" -> {
                        index = valueIndex(args, index);
                        replacementPublicKey = Path.of(args[index]);
                    }

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown append argument: "
                                    + args[index]
                            );
                }
            }

            if (
                    lineage == null
            ) {
                throw new IllegalArgumentException(
                        "--lineage is required."
                );
            }

            requireFile(
                    rootPin,
                    "--root-pin"
            );

            requireFile(
                    outgoingPrivateKey,
                    "--outgoing-private-key"
            );

            requireFile(
                    outgoingPublicKey,
                    "--outgoing-public-key"
            );

            requireFile(
                    replacementPublicKey,
                    "--replacement-public-key"
            );

            if (
                    outgoingKeyId == null
                    || outgoingKeyId.isBlank()
            ) {
                throw new IllegalArgumentException(
                        "--outgoing-key-id is required."
                );
            }

            if (
                    replacementKeyId == null
                    || replacementKeyId.isBlank()
            ) {
                throw new IllegalArgumentException(
                        "--replacement-key-id is required."
                );
            }

            return new AppendConfiguration(
                    lineage,
                    rootPin,
                    outgoingKeyId,
                    outgoingPrivateKey,
                    outgoingPublicKey,
                    replacementKeyId,
                    replacementPublicKey
            );
        }
    }

    private record VerifyConfiguration(
            Path lineage,

            Path rootPin,

            Path terminalPinOutput
    ) {

        static VerifyConfiguration parse(
                String[] args
        ) {
            Path lineage =
                    null;

            Path rootPin =
                    null;

            Path terminalPinOutput =
                    null;

            for (
                    int index = 0;
                    index < args.length;
                    index++
            ) {
                switch (
                        args[index]
                ) {
                    case "--lineage" -> {
                        index = valueIndex(args, index);
                        lineage = Path.of(args[index]);
                    }

                    case "--root-pin" -> {
                        index = valueIndex(args, index);
                        rootPin = Path.of(args[index]);
                    }

                    case "--terminal-pin-output" -> {
                        index = valueIndex(args, index);
                        terminalPinOutput = Path.of(args[index]);
                    }

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown verify argument: "
                                    + args[index]
                            );
                }
            }

            requireFile(
                    lineage,
                    "--lineage"
            );

            requireFile(
                    rootPin,
                    "--root-pin"
            );

            return new VerifyConfiguration(
                    lineage,
                    rootPin,
                    terminalPinOutput
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
