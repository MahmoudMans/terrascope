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

public final class TerraScopeVerifierReleaseTransparency {

    private static final String TOOL_VERSION =
            "TS-VERIFIER-RELEASE-TRANSPARENCY-TOOL-V1";

    private static final String LEDGER_VERSION =
            "TS-VERIFIER-RELEASE-TRANSPARENCY-V1";

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

    private static final String[] MANIFEST_PREFIXES = {
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

    private static final String[] LEDGER_PREFIXES = {
            "ledgerVersion=",
            "sequence=",
            "artifactName=",
            "artifactSha256=",
            "releaseManifestSha256=",
            "releaseKeyFingerprint=",
            "transparencyKeyId=",
            "transparencyPublicKeyX509Base64=",
            "transparencyKeyFingerprint=",
            "previousEntryHash=",
            "signatureBase64=",
            "entryHash="
    };

    private TerraScopeVerifierReleaseTransparency() {
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
        ReleaseVerification release =
                verifyRelease(
                        configuration.artifact(),
                        configuration.manifest(),
                        configuration.releaseKeyPin()
                );

        if (
                !release.valid()
        ) {
            throw new IllegalArgumentException(
                    "Release manifest must authenticate before transparency append: "
                    + release.message()
            );
        }

        String transparencyKeyId =
                singleLine(
                        configuration.transparencyKeyId(),
                        "transparency key ID"
                );

        byte[] privateKeyBytes =
                Base64
                        .getDecoder()
                        .decode(
                                Files.readString(
                                                configuration.transparencyPrivateKey(),
                                                StandardCharsets.UTF_8
                                        )
                                        .trim()
                        );

        String publicKeyBase64 =
                Files.readString(
                                configuration.transparencyPublicKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

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

        String transparencyFingerprint =
                sha256(
                        publicKeyBytes
                );

        List<LedgerEntry> existing =
                readLedger(
                        configuration.ledger()
                );

        LedgerVerification existingVerification =
                verifyLedger(
                        existing,
                        transparencyFingerprint
                );

        if (
                !existingVerification.valid()
        ) {
            throw new IllegalArgumentException(
                    "Existing transparency ledger is invalid: "
                    + existingVerification.message()
            );
        }

        int sequence =
                existing.size()
                + 1;

        String previousEntryHash =
                existing.isEmpty()
                        ? null
                        : existing
                                .getLast()
                                .entryHash();

        String artifactName =
                configuration
                        .artifact()
                        .getFileName()
                        .toString();

        String artifactSha =
                sha256(
                        Files.readAllBytes(
                                configuration.artifact()
                        )
                );

        String manifestSha =
                sha256(
                        Files.readAllBytes(
                                configuration.manifest()
                        )
                );

        String payload =
                entrySigningPayload(
                        sequence,

                        artifactName,

                        artifactSha,

                        manifestSha,

                        release.releaseKeyFingerprint(),

                        transparencyKeyId,

                        publicKeyBase64,

                        transparencyFingerprint,

                        previousEntryHash
                );

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

        String signatureBase64 =
                Base64
                        .getEncoder()
                        .encodeToString(
                                signature.sign()
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
                    "Transparency private/public key pair does not match."
            );
        }

        String entryHash =
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

                + "entryHash="
                + entryHash
                + "\n\n";

        Path parent =
                configuration
                        .ledger()
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
                configuration.ledger(),
                block,
                StandardCharsets.UTF_8,

                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        System.out.println(
                "TRANSPARENCY_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "SEQUENCE="
                + sequence
        );

        System.out.println(
                "ARTIFACT_SHA256="
                + artifactSha
        );

        System.out.println(
                "RELEASE_MANIFEST_SHA256="
                + manifestSha
        );

        System.out.println(
                "RELEASE_KEY_FINGERPRINT="
                + release.releaseKeyFingerprint()
        );

        System.out.println(
                "TRANSPARENCY_KEY_FINGERPRINT="
                + transparencyFingerprint
        );

        System.out.println(
                "PREVIOUS_ENTRY_HASH="
                + (
                    previousEntryHash == null
                            ? "GENESIS"
                            : previousEntryHash
                )
        );

        System.out.println(
                "ENTRY_HASH="
                + entryHash
        );

        System.out.println(
                "APPEND_RESULT=PASS"
        );
    }

    private static Verification verify(
            VerifyConfiguration configuration
    ) throws Exception {
        ReleaseVerification release =
                verifyRelease(
                        configuration.artifact(),
                        configuration.manifest(),
                        configuration.releaseKeyPin()
                );

        String transparencyPin =
                normalizeSha256File(
                        configuration.transparencyKeyPin(),
                        "transparency key pin"
                );

        String expectedHead =
                normalizeSha256File(
                        configuration.headPin(),
                        "transparency head pin"
                );

        List<LedgerEntry> entries =
                readLedger(
                        configuration.ledger()
                );

        LedgerVerification ledger =
                verifyLedger(
                        entries,
                        transparencyPin
                );

        boolean headPinValid =
                !entries.isEmpty()

                && expectedHead.equals(
                        entries
                                .getLast()
                                .entryHash()
                );

        String artifactSha =
                sha256(
                        Files.readAllBytes(
                                configuration.artifact()
                        )
                );

        String manifestSha =
                sha256(
                        Files.readAllBytes(
                                configuration.manifest()
                        )
                );

        String artifactName =
                configuration
                        .artifact()
                        .getFileName()
                        .toString();

        boolean latestBindingValid =
                !entries.isEmpty()

                && entries
                        .getLast()
                        .artifactName()
                        .equals(
                                artifactName
                        )

                && entries
                        .getLast()
                        .artifactSha256()
                        .equals(
                                artifactSha
                        )

                && entries
                        .getLast()
                        .releaseManifestSha256()
                        .equals(
                                manifestSha
                        )

                && release.releaseKeyFingerprint() != null

                && entries
                        .getLast()
                        .releaseKeyFingerprint()
                        .equals(
                                release.releaseKeyFingerprint()
                        );

        boolean valid =
                release.valid()

                && ledger.valid()

                && headPinValid

                && latestBindingValid;

        String message;

        if (
                !release.valid()
        ) {
            message =
                    release.message();

        } else if (
                !ledger.valid()
        ) {
            message =
                    ledger.message();

        } else if (
                !headPinValid
        ) {
            message =
                    "Transparency ledger head does not match the independently trusted current-head fingerprint.";

        } else if (
                !latestBindingValid
        ) {
            message =
                    "Presented verifier release is authentic but is not the latest transparently logged release.";

        } else {
            message =
                    "Verifier release is authentic, transparently logged, and matches the independently pinned current transparency head.";
        }

        return new Verification(
                valid,

                release.manifestValid(),

                release.artifactHashValid(),

                release.signatureValid(),

                release.releaseKeyPinValid(),

                ledger.valid(),

                ledger.transparencyKeyPinValid(),

                headPinValid,

                latestBindingValid,

                entries.size(),

                ledger.verifiedEntries(),

                entries.isEmpty()
                        ? null
                        : entries
                                .getLast()
                                .entryHash(),

                release.artifactSha256(),

                release.releaseKeyFingerprint(),

                message
        );
    }

    private static ReleaseVerification verifyRelease(
            Path artifact,
            Path manifest,
            Path releaseKeyPin
    ) throws Exception {
        List<String> lines =
                Files.readAllLines(
                        manifest,
                        StandardCharsets.UTF_8
                );

        boolean shapeValid =
                lines.size()
                == MANIFEST_PREFIXES.length;

        String[] values =
                new String[
                        MANIFEST_PREFIXES.length
                ];

        if (
                shapeValid
        ) {
            for (
                    int index = 0;
                    index < MANIFEST_PREFIXES.length;
                    index++
            ) {
                String line =
                        lines.get(
                                index
                        );

                String prefix =
                        MANIFEST_PREFIXES[index];

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
            }
        }

        if (
                !shapeValid
        ) {
            return ReleaseVerification.failure(
                    "Verifier release manifest shape is invalid."
            );
        }

        String manifestVersion =
                values[0];

        String artifactName =
                values[1];

        String artifactVersion =
                values[2];

        String expectedArtifactSha =
                values[3]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String algorithm =
                values[5];

        String releasePublicKeyBase64 =
                values[6];

        String declaredReleaseFingerprint =
                values[7]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String signatureBase64 =
                values[8];

        boolean manifestValid =
                MANIFEST_VERSION.equals(
                        manifestVersion
                )

                && ARTIFACT_VERSION.equals(
                        artifactVersion
                )

                && ALGORITHM.equals(
                        algorithm
                )

                && artifactName.equals(
                        artifact
                                .getFileName()
                                .toString()
                )

                && SHA256.matcher(
                        expectedArtifactSha
                ).matches()

                && SHA256.matcher(
                        declaredReleaseFingerprint
                ).matches();

        String actualArtifactSha =
                sha256(
                        Files.readAllBytes(
                                artifact
                        )
                );

        boolean artifactHashValid =
                expectedArtifactSha.equals(
                        actualArtifactSha
                );

        byte[] releasePublicKeyBytes;

        try {
            releasePublicKeyBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    releasePublicKeyBase64
                            );

        } catch (
                Exception exception
        ) {
            return new ReleaseVerification(
                    false,
                    manifestValid,
                    artifactHashValid,
                    false,
                    false,
                    actualArtifactSha,
                    null,
                    "Verifier release manifest contains an invalid release public key."
            );
        }

        String calculatedReleaseFingerprint =
                sha256(
                        releasePublicKeyBytes
                );

        boolean fingerprintValid =
                calculatedReleaseFingerprint.equals(
                        declaredReleaseFingerprint
                );

        String trustedReleasePin =
                normalizeSha256File(
                        releaseKeyPin,
                        "release key pin"
                );

        boolean releaseKeyPinValid =
                fingerprintValid

                && trustedReleasePin.equals(
                        calculatedReleaseFingerprint
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

        boolean signatureValid;

        try {
            PublicKey publicKey =
                    KeyFactory
                            .getInstance(
                                    "Ed25519"
                            )
                            .generatePublic(
                                    new X509EncodedKeySpec(
                                            releasePublicKeyBytes
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

        } catch (
                Exception exception
        ) {
            signatureValid =
                    false;
        }

        boolean valid =
                manifestValid

                && artifactHashValid

                && fingerprintValid

                && releaseKeyPinValid

                && signatureValid;

        String message;

        if (
                !manifestValid
        ) {
            message =
                    "Verifier release manifest declaration is invalid.";

        } else if (
                !artifactHashValid
        ) {
            message =
                    "Verifier artifact SHA-256 does not match the signed release manifest.";

        } else if (
                !fingerprintValid
        ) {
            message =
                    "Verifier release public-key fingerprint is invalid.";

        } else if (
                !releaseKeyPinValid
        ) {
            message =
                    "Verifier release key does not match the independently trusted release-key pin.";

        } else if (
                !signatureValid
        ) {
            message =
                    "Verifier release manifest Ed25519 signature is invalid.";

        } else {
            message =
                    "Verifier release manifest is authentic.";
        }

        return new ReleaseVerification(
                valid,
                manifestValid,
                artifactHashValid,
                signatureValid,
                releaseKeyPinValid,
                actualArtifactSha,
                calculatedReleaseFingerprint,
                message
        );
    }

    private static LedgerVerification verifyLedger(
            List<LedgerEntry> entries,
            String trustedTransparencyFingerprint
    ) {
        if (
                entries.isEmpty()
        ) {
            return new LedgerVerification(
                    true,
                    true,
                    0,
                    "Transparency ledger is empty."
            );
        }

        int expectedSequence =
                1;

        int verified =
                0;

        String expectedPreviousHash =
                null;

        boolean transparencyKeyPinValid =
                true;

        for (
                LedgerEntry entry
                : entries
        ) {
            if (
                    !LEDGER_VERSION.equals(
                            entry.ledgerVersion()
                    )
            ) {
                return ledgerFailure(
                        transparencyKeyPinValid,
                        verified,
                        "Transparency ledger version is unsupported."
                );
            }

            if (
                    entry.sequence()
                    != expectedSequence
            ) {
                return ledgerFailure(
                        transparencyKeyPinValid,
                        verified,
                        "Transparency ledger sequence is not contiguous."
                );
            }

            if (
                    !Objects.equals(
                            entry.previousEntryHash(),
                            expectedPreviousHash
                    )
            ) {
                return ledgerFailure(
                        transparencyKeyPinValid,
                        verified,
                        "Transparency ledger previous-entry linkage is invalid."
                );
            }

            if (
                    !SHA256.matcher(
                            entry.artifactSha256()
                    ).matches()

                    || !SHA256.matcher(
                            entry.releaseManifestSha256()
                    ).matches()

                    || !SHA256.matcher(
                            entry.releaseKeyFingerprint()
                    ).matches()

                    || !SHA256.matcher(
                            entry.transparencyKeyFingerprint()
                    ).matches()

                    || !SHA256.matcher(
                            entry.entryHash()
                    ).matches()
            ) {
                return ledgerFailure(
                        transparencyKeyPinValid,
                        verified,
                        "Transparency ledger contains malformed SHA-256 data."
                );
            }

            byte[] publicKeyBytes;

            try {
                publicKeyBytes =
                        Base64
                                .getDecoder()
                                .decode(
                                        entry.transparencyPublicKeyX509Base64()
                                );

            } catch (
                    Exception exception
            ) {
                return ledgerFailure(
                        false,
                        verified,
                        "Transparency ledger contains an invalid public key."
                );
            }

            String calculatedFingerprint;

            try {
                calculatedFingerprint =
                        sha256(
                                publicKeyBytes
                        );

            } catch (
                    Exception exception
            ) {
                return ledgerFailure(
                        false,
                        verified,
                        "Unable to fingerprint transparency public key."
                );
            }

            if (
                    !calculatedFingerprint.equals(
                            entry.transparencyKeyFingerprint()
                    )

                    || !calculatedFingerprint.equals(
                            trustedTransparencyFingerprint
                    )
            ) {
                transparencyKeyPinValid =
                        false;

                return ledgerFailure(
                        false,
                        verified,
                        "Transparency signing key does not match the independently trusted transparency-key pin."
                );
            }

            String payload =
                    entrySigningPayload(
                            entry.sequence(),

                            entry.artifactName(),

                            entry.artifactSha256(),

                            entry.releaseManifestSha256(),

                            entry.releaseKeyFingerprint(),

                            entry.transparencyKeyId(),

                            entry.transparencyPublicKeyX509Base64(),

                            entry.transparencyKeyFingerprint(),

                            entry.previousEntryHash()
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
                                                entry.signatureBase64()
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
                return ledgerFailure(
                        transparencyKeyPinValid,
                        verified,
                        "Transparency ledger Ed25519 signature verification failed."
                );
            }

            String calculatedEntryHash;

            try {
                calculatedEntryHash =
                        sha256(
                                payload

                                + "signatureBase64="
                                + entry.signatureBase64()
                                + "\n"
                        );

            } catch (
                    Exception exception
            ) {
                return ledgerFailure(
                        transparencyKeyPinValid,
                        verified,
                        "Unable to calculate transparency entry hash."
                );
            }

            if (
                    !calculatedEntryHash.equals(
                            entry.entryHash()
                    )
            ) {
                return ledgerFailure(
                        transparencyKeyPinValid,
                        verified,
                        "Transparency ledger entry SHA-256 verification failed."
                );
            }

            expectedPreviousHash =
                    entry.entryHash();

            expectedSequence++;

            verified++;
        }

        return new LedgerVerification(
                true,
                transparencyKeyPinValid,
                verified,
                "Transparency ledger is valid."
        );
    }

    private static LedgerVerification ledgerFailure(
            boolean transparencyKeyPinValid,
            int verified,
            String message
    ) {
        return new LedgerVerification(
                false,
                transparencyKeyPinValid,
                verified,
                message
        );
    }

    private static String entrySigningPayload(
            int sequence,

            String artifactName,

            String artifactSha256,

            String releaseManifestSha256,

            String releaseKeyFingerprint,

            String transparencyKeyId,

            String transparencyPublicKeyX509Base64,

            String transparencyKeyFingerprint,

            String previousEntryHash
    ) {
        return """
                ledgerVersion=%s
                sequence=%d
                artifactName=%s
                artifactSha256=%s
                releaseManifestSha256=%s
                releaseKeyFingerprint=%s
                transparencyKeyId=%s
                transparencyPublicKeyX509Base64=%s
                transparencyKeyFingerprint=%s
                previousEntryHash=%s
                """
                .formatted(
                        LEDGER_VERSION,

                        sequence,

                        artifactName,

                        artifactSha256,

                        releaseManifestSha256,

                        releaseKeyFingerprint,

                        transparencyKeyId,

                        transparencyPublicKeyX509Base64,

                        transparencyKeyFingerprint,

                        previousEntryHash == null
                                ? "GENESIS"
                                : previousEntryHash
                );
    }

    private static List<LedgerEntry> readLedger(
            Path ledger
    ) throws Exception {
        if (
                !Files.exists(
                        ledger
                )
        ) {
            return List.of();
        }

        List<String> lines =
                Files.readAllLines(
                        ledger,
                        StandardCharsets.UTF_8
                );

        List<LedgerEntry> result =
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
                            parseLedgerBlock(
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
                    parseLedgerBlock(
                            block
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static LedgerEntry parseLedgerBlock(
            List<String> lines
    ) {
        if (
                lines.size()
                != LEDGER_PREFIXES.length
        ) {
            throw new IllegalArgumentException(
                    "Transparency ledger entry must contain exactly twelve canonical lines."
            );
        }

        String[] values =
                new String[
                        LEDGER_PREFIXES.length
                ];

        for (
                int index = 0;
                index < LEDGER_PREFIXES.length;
                index++
        ) {
            String prefix =
                    LEDGER_PREFIXES[index];

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
                        "Transparency ledger field order is invalid."
                );
            }

            values[index] =
                    line.substring(
                            prefix.length()
                    );
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
                    "Transparency ledger sequence is invalid."
            );
        }

        return new LedgerEntry(
                values[0],

                sequence,

                values[2],

                values[3]
                        .toLowerCase(
                                Locale.ROOT
                        ),

                values[4]
                        .toLowerCase(
                                Locale.ROOT
                        ),

                values[5]
                        .toLowerCase(
                                Locale.ROOT
                        ),

                values[6],

                values[7],

                values[8]
                        .toLowerCase(
                                Locale.ROOT
                        ),

                "GENESIS".equals(
                        values[9]
                )
                        ? null
                        : values[9]
                                .toLowerCase(
                                        Locale.ROOT
                                ),

                values[10],

                values[11]
                        .toLowerCase(
                                Locale.ROOT
                        )
        );
    }

    private static void print(
            Verification result
    ) {
        System.out.println(
                "TRANSPARENCY_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "RELEASE_MANIFEST_VALID="
                + result.releaseManifestValid()
        );

        System.out.println(
                "ARTIFACT_HASH_VALID="
                + result.artifactHashValid()
        );

        System.out.println(
                "RELEASE_SIGNATURE_VALID="
                + result.releaseSignatureValid()
        );

        System.out.println(
                "RELEASE_KEY_PIN_VALID="
                + result.releaseKeyPinValid()
        );

        System.out.println(
                "TRANSPARENCY_LEDGER_VALID="
                + result.transparencyLedgerValid()
        );

        System.out.println(
                "TRANSPARENCY_KEY_PIN_VALID="
                + result.transparencyKeyPinValid()
        );

        System.out.println(
                "HEAD_PIN_VALID="
                + result.headPinValid()
        );

        System.out.println(
                "LATEST_RELEASE_BINDING_VALID="
                + result.latestReleaseBindingValid()
        );

        System.out.println(
                "LEDGER_ENTRY_COUNT="
                + result.ledgerEntryCount()
        );

        System.out.println(
                "VERIFIED_LEDGER_ENTRY_COUNT="
                + result.verifiedLedgerEntryCount()
        );

        System.out.println(
                "HEAD_HASH="
                + value(
                        result.headHash()
                )
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

    private record LedgerEntry(
            String ledgerVersion,

            int sequence,

            String artifactName,

            String artifactSha256,

            String releaseManifestSha256,

            String releaseKeyFingerprint,

            String transparencyKeyId,

            String transparencyPublicKeyX509Base64,

            String transparencyKeyFingerprint,

            String previousEntryHash,

            String signatureBase64,

            String entryHash
    ) {
    }

    private record ReleaseVerification(
            boolean valid,

            boolean manifestValid,

            boolean artifactHashValid,

            boolean signatureValid,

            boolean releaseKeyPinValid,

            String artifactSha256,

            String releaseKeyFingerprint,

            String message
    ) {

        static ReleaseVerification failure(
                String message
        ) {
            return new ReleaseVerification(
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

    private record LedgerVerification(
            boolean valid,

            boolean transparencyKeyPinValid,

            int verifiedEntries,

            String message
    ) {
    }

    private record Verification(
            boolean valid,

            boolean releaseManifestValid,

            boolean artifactHashValid,

            boolean releaseSignatureValid,

            boolean releaseKeyPinValid,

            boolean transparencyLedgerValid,

            boolean transparencyKeyPinValid,

            boolean headPinValid,

            boolean latestReleaseBindingValid,

            int ledgerEntryCount,

            int verifiedLedgerEntryCount,

            String headHash,

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
            Path artifact,

            Path manifest,

            Path releaseKeyPin,

            Path transparencyPrivateKey,

            Path transparencyPublicKey,

            String transparencyKeyId,

            Path ledger
    ) {

        static AppendConfiguration parse(
                String[] args
        ) {
            Path artifact =
                    null;

            Path manifest =
                    null;

            Path releaseKeyPin =
                    null;

            Path privateKey =
                    null;

            Path publicKey =
                    null;

            String keyId =
                    null;

            Path ledger =
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
                        index = valueIndex(args, index);
                        artifact = Path.of(args[index]);
                    }

                    case "--manifest" -> {
                        index = valueIndex(args, index);
                        manifest = Path.of(args[index]);
                    }

                    case "--release-key-pin" -> {
                        index = valueIndex(args, index);
                        releaseKeyPin = Path.of(args[index]);
                    }

                    case "--transparency-private-key" -> {
                        index = valueIndex(args, index);
                        privateKey = Path.of(args[index]);
                    }

                    case "--transparency-public-key" -> {
                        index = valueIndex(args, index);
                        publicKey = Path.of(args[index]);
                    }

                    case "--transparency-key-id" -> {
                        index = valueIndex(args, index);
                        keyId = args[index];
                    }

                    case "--ledger" -> {
                        index = valueIndex(args, index);
                        ledger = Path.of(args[index]);
                    }

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown append argument: "
                                    + args[index]
                            );
                }
            }

            requireFile(artifact, "--artifact");
            requireFile(manifest, "--manifest");
            requireFile(releaseKeyPin, "--release-key-pin");
            requireFile(privateKey, "--transparency-private-key");
            requireFile(publicKey, "--transparency-public-key");

            if (
                    keyId == null
                    || keyId.isBlank()
            ) {
                throw new IllegalArgumentException(
                        "--transparency-key-id is required."
                );
            }

            if (
                    ledger == null
            ) {
                throw new IllegalArgumentException(
                        "--ledger is required."
                );
            }

            return new AppendConfiguration(
                    artifact,
                    manifest,
                    releaseKeyPin,
                    privateKey,
                    publicKey,
                    keyId,
                    ledger
            );
        }
    }

    private record VerifyConfiguration(
            Path artifact,

            Path manifest,

            Path releaseKeyPin,

            Path ledger,

            Path transparencyKeyPin,

            Path headPin
    ) {

        static VerifyConfiguration parse(
                String[] args
        ) {
            Path artifact =
                    null;

            Path manifest =
                    null;

            Path releaseKeyPin =
                    null;

            Path ledger =
                    null;

            Path transparencyKeyPin =
                    null;

            Path headPin =
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
                        index = valueIndex(args, index);
                        artifact = Path.of(args[index]);
                    }

                    case "--manifest" -> {
                        index = valueIndex(args, index);
                        manifest = Path.of(args[index]);
                    }

                    case "--release-key-pin" -> {
                        index = valueIndex(args, index);
                        releaseKeyPin = Path.of(args[index]);
                    }

                    case "--ledger" -> {
                        index = valueIndex(args, index);
                        ledger = Path.of(args[index]);
                    }

                    case "--transparency-key-pin" -> {
                        index = valueIndex(args, index);
                        transparencyKeyPin = Path.of(args[index]);
                    }

                    case "--head-pin" -> {
                        index = valueIndex(args, index);
                        headPin = Path.of(args[index]);
                    }

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown verify argument: "
                                    + args[index]
                            );
                }
            }

            requireFile(artifact, "--artifact");
            requireFile(manifest, "--manifest");
            requireFile(releaseKeyPin, "--release-key-pin");
            requireFile(ledger, "--ledger");
            requireFile(transparencyKeyPin, "--transparency-key-pin");
            requireFile(headPin, "--head-pin");

            return new VerifyConfiguration(
                    artifact,
                    manifest,
                    releaseKeyPin,
                    ledger,
                    transparencyKeyPin,
                    headPin
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
