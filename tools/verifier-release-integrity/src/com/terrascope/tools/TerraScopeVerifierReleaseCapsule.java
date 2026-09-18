package com.terrascope.tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import java.util.regex.Pattern;

import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class TerraScopeVerifierReleaseCapsule {

    private static final String TOOL_VERSION =
            "TS-VERIFIER-RELEASE-CAPSULE-TOOL-V1";

    private static final String CAPSULE_VERSION =
            "TS-VERIFIER-RELEASE-CAPSULE-V1";

    private static final String ALGORITHM =
            "ED25519";

    private static final long FIXED_ZIP_TIME =
            946684800000L;

    private static final Pattern SHA256 =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private static final String MANIFEST_ENTRY =
            "capsule.manifest";

    private static final String ARTIFACT_ENTRY =
            "payload/terrascope-checkpoint-bundle-verifier.jar";

    private static final String RELEASE_MANIFEST_ENTRY =
            "payload/release.manifest";

    private static final String AUTHORITY_LINEAGE_ENTRY =
            "payload/release-authority.lineage";

    private static final String TRANSPARENCY_LEDGER_ENTRY =
            "payload/release-transparency.log";

    private static final String TRUST_STATE_ENTRY =
            "payload/trust-state.manifest";

    private static final String RELEASE_VERIFIER_ENTRY =
            "tools/terrascope-verifier-release-verifier.jar";

    private static final String TRANSPARENCY_VERIFIER_ENTRY =
            "tools/terrascope-verifier-release-transparency.jar";

    private static final String AUTHORITY_VERIFIER_ENTRY =
            "tools/terrascope-verifier-release-authority-lineage.jar";

    private static final String TRUST_STATE_VERIFIER_ENTRY =
            "tools/terrascope-verifier-release-trust-state.jar";

    private static final Set<String> EXPECTED_ENTRIES =
            Set.of(
                    MANIFEST_ENTRY,

                    ARTIFACT_ENTRY,

                    RELEASE_MANIFEST_ENTRY,

                    AUTHORITY_LINEAGE_ENTRY,

                    TRANSPARENCY_LEDGER_ENTRY,

                    TRUST_STATE_ENTRY,

                    RELEASE_VERIFIER_ENTRY,

                    TRANSPARENCY_VERIFIER_ENTRY,

                    AUTHORITY_VERIFIER_ENTRY,

                    TRUST_STATE_VERIFIER_ENTRY
            );

    private static final String[] MANIFEST_PREFIXES = {
            "capsuleVersion=",
            "artifactName=",
            "artifactSha256=",
            "releaseManifestSha256=",
            "authorityLineageSha256=",
            "transparencyLedgerSha256=",
            "trustStateSha256=",
            "releaseVerifierSha256=",
            "transparencyVerifierSha256=",
            "authorityVerifierSha256=",
            "trustStateVerifierSha256=",
            "releaseRootKeyId=",
            "releaseRootPublicKeyX509Base64=",
            "releaseRootKeyFingerprint=",
            "algorithm=",
            "signatureBase64="
    };

    private TerraScopeVerifierReleaseCapsule() {
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
                        "Mode is required: create or verify."
                );
            }

            switch (
                    args[0]
            ) {
                case "create" ->
                        create(
                                CreateConfiguration.parse(
                                        tail(
                                                args
                                        )
                                )
                        );

                case "verify" -> {
                    Verification verification =
                            verify(
                                    VerifyConfiguration.parse(
                                            tail(
                                                    args
                                            )
                                    )
                            );

                    print(
                            verification
                    );

                    exitCode =
                            verification.valid()
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
                Verification verification =
                        Verification.failure(
                                safeMessage(
                                        exception
                                )
                        );

                print(
                        verification
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

    private static void create(
            CreateConfiguration configuration
    ) throws Exception {
        String rootKeyId =
                singleLine(
                        configuration.releaseRootKeyId(),
                        "release root key ID"
                );

        String trustedRoot =
                normalizeSha256File(
                        configuration.rootPin(),
                        "release root pin"
                );

        byte[] rootPrivateBytes =
                Base64
                        .getDecoder()
                        .decode(
                                Files.readString(
                                                configuration.releaseRootPrivateKey(),
                                                StandardCharsets.UTF_8
                                        )
                                        .trim()
                        );

        String rootPublicBase64 =
                Files.readString(
                                configuration.releaseRootPublicKey(),
                                StandardCharsets.UTF_8
                        )
                        .trim();

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

        if (
                !trustedRoot.equals(
                        rootFingerprint
                )
        ) {
            throw new IllegalArgumentException(
                    "Release root public key does not match the independently supplied root pin."
            );
        }

        String artifactName =
                configuration
                        .artifact()
                        .getFileName()
                        .toString();

        if (
                !"terrascope-checkpoint-bundle-verifier.jar"
                        .equals(
                                artifactName
                        )
        ) {
            throw new IllegalArgumentException(
                    "Checkpoint verifier artifact must use the canonical filename."
            );
        }

        Map<String, byte[]> entries =
                new TreeMap<>();

        entries.put(
                ARTIFACT_ENTRY,

                Files.readAllBytes(
                        configuration.artifact()
                )
        );

        entries.put(
                RELEASE_MANIFEST_ENTRY,

                Files.readAllBytes(
                        configuration.releaseManifest()
                )
        );

        entries.put(
                AUTHORITY_LINEAGE_ENTRY,

                Files.readAllBytes(
                        configuration.authorityLineage()
                )
        );

        entries.put(
                TRANSPARENCY_LEDGER_ENTRY,

                Files.readAllBytes(
                        configuration.transparencyLedger()
                )
        );

        entries.put(
                TRUST_STATE_ENTRY,

                Files.readAllBytes(
                        configuration.trustState()
                )
        );

        entries.put(
                RELEASE_VERIFIER_ENTRY,

                Files.readAllBytes(
                        configuration.releaseVerifierJar()
                )
        );

        entries.put(
                TRANSPARENCY_VERIFIER_ENTRY,

                Files.readAllBytes(
                        configuration.transparencyVerifierJar()
                )
        );

        entries.put(
                AUTHORITY_VERIFIER_ENTRY,

                Files.readAllBytes(
                        configuration.authorityVerifierJar()
                )
        );

        entries.put(
                TRUST_STATE_VERIFIER_ENTRY,

                Files.readAllBytes(
                        configuration.trustStateVerifierJar()
                )
        );

        String payload =
                manifestPayload(
                        artifactName,

                        sha256(
                                entries.get(
                                        ARTIFACT_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        RELEASE_MANIFEST_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        AUTHORITY_LINEAGE_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        TRANSPARENCY_LEDGER_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        TRUST_STATE_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        RELEASE_VERIFIER_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        TRANSPARENCY_VERIFIER_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        AUTHORITY_VERIFIER_ENTRY
                                )
                        ),

                        sha256(
                                entries.get(
                                        TRUST_STATE_VERIFIER_ENTRY
                                )
                        ),

                        rootKeyId,

                        rootPublicBase64,

                        rootFingerprint
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
                    "Capsule root-signing key pair does not match."
            );
        }

        String manifest =
                payload

                + "signatureBase64="
                + signatureBase64
                + "\n";

        entries.put(
                MANIFEST_ENTRY,

                manifest.getBytes(
                        StandardCharsets.UTF_8
                )
        );

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

        writeDeterministicZip(
                configuration.output(),
                entries
        );

        System.out.println(
                "CAPSULE_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "CAPSULE_VERSION="
                + CAPSULE_VERSION
        );

        System.out.println(
                "ENTRY_COUNT="
                + entries.size()
        );

        System.out.println(
                "ROOT_KEY_FINGERPRINT="
                + rootFingerprint
        );

        System.out.println(
                "CAPSULE_SHA256="
                + sha256(
                        Files.readAllBytes(
                                configuration.output()
                        )
                )
        );

        System.out.println(
                "SIGNATURE_SELF_VERIFICATION=PASS"
        );

        System.out.println(
                "CREATE_RESULT=PASS"
        );
    }

    private static Verification verify(
            VerifyConfiguration configuration
    ) throws Exception {
        boolean structureValid =
                false;

        boolean manifestShapeValid =
                false;

        boolean rootFingerprintValid =
                false;

        boolean rootPinValid =
                false;

        boolean signatureValid =
                false;

        boolean entryHashesValid =
                false;

        boolean trustStateValid =
                false;

        boolean authorityLineageValid =
                false;

        boolean releaseValid =
                false;

        boolean transparencyValid =
                false;

        String rootFingerprint =
                null;

        String capsuleSha256 =
                sha256(
                        Files.readAllBytes(
                                configuration.capsule()
                        )
                );

        String message;

        Map<String, byte[]> entries =
                new HashMap<>();

        String[] values =
                new String[
                        MANIFEST_PREFIXES.length
                ];

        try (
                ZipFile zipFile =
                        new ZipFile(
                                configuration
                                        .capsule()
                                        .toFile()
                        )
        ) {
            List<String> names =
                    zipFile
                            .stream()
                            .map(
                                    ZipEntry::getName
                            )
                            .toList();

            Set<String> uniqueNames =
                    new HashSet<>(
                            names
                    );

            structureValid =
                    names.size()
                    == EXPECTED_ENTRIES.size()

                    && uniqueNames.size()
                       == EXPECTED_ENTRIES.size()

                    && uniqueNames.equals(
                            EXPECTED_ENTRIES
                    );

            if (
                    !structureValid
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
                        false,

                        false,

                        capsuleSha256,
                        null,

                        "Release capsule structure or entry set is invalid."
                );
            }

            for (
                    String name
                    : EXPECTED_ENTRIES
            ) {
                ZipEntry entry =
                        zipFile.getEntry(
                                name
                        );

                if (
                        entry == null

                        || entry.isDirectory()
                ) {
                    return Verification.failure(
                            "Release capsule contains an invalid required entry."
                    );
                }

                try (
                        InputStream input =
                                zipFile.getInputStream(
                                        entry
                                )
                ) {
                    entries.put(
                            name,
                            input.readAllBytes()
                    );
                }
            }
        }

        List<String> manifestLines =
                new String(
                        entries.get(
                                MANIFEST_ENTRY
                        ),
                        StandardCharsets.UTF_8
                )
                        .lines()
                        .toList();

        manifestShapeValid =
                manifestLines.size()
                == MANIFEST_PREFIXES.length;

        if (
                manifestShapeValid
        ) {
            for (
                    int index = 0;
                    index < MANIFEST_PREFIXES.length;
                    index++
            ) {
                String line =
                        manifestLines.get(
                                index
                        );

                String prefix =
                        MANIFEST_PREFIXES[index];

                if (
                        !line.startsWith(
                                prefix
                        )
                ) {
                    manifestShapeValid =
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
                    manifestShapeValid =
                            false;

                    break;
                }
            }
        }

        if (
                !manifestShapeValid
        ) {
            return new Verification(
                    false,

                    structureValid,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,

                    false,

                    capsuleSha256,
                    null,

                    "Release capsule manifest canonical structure is invalid."
            );
        }

        String capsuleVersion =
                values[0];

        String artifactName =
                values[1];

        String artifactSha =
                values[2]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String releaseManifestSha =
                values[3]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String authorityLineageSha =
                values[4]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String transparencyLedgerSha =
                values[5]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String trustStateSha =
                values[6]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String releaseVerifierSha =
                values[7]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String transparencyVerifierSha =
                values[8]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String authorityVerifierSha =
                values[9]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String trustStateVerifierSha =
                values[10]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String rootKeyId =
                values[11];

        String rootPublicBase64 =
                values[12];

        String declaredRootFingerprint =
                values[13]
                        .toLowerCase(
                                Locale.ROOT
                        );

        String algorithm =
                values[14];

        String signatureBase64 =
                values[15];

        manifestShapeValid =
                CAPSULE_VERSION.equals(
                        capsuleVersion
                )

                && "terrascope-checkpoint-bundle-verifier.jar"
                        .equals(
                                artifactName
                        )

                && !rootKeyId.isBlank()

                && ALGORITHM.equals(
                        algorithm
                )

                && SHA256.matcher(
                        artifactSha
                ).matches()

                && SHA256.matcher(
                        releaseManifestSha
                ).matches()

                && SHA256.matcher(
                        authorityLineageSha
                ).matches()

                && SHA256.matcher(
                        transparencyLedgerSha
                ).matches()

                && SHA256.matcher(
                        trustStateSha
                ).matches()

                && SHA256.matcher(
                        releaseVerifierSha
                ).matches()

                && SHA256.matcher(
                        transparencyVerifierSha
                ).matches()

                && SHA256.matcher(
                        authorityVerifierSha
                ).matches()

                && SHA256.matcher(
                        trustStateVerifierSha
                ).matches()

                && SHA256.matcher(
                        declaredRootFingerprint
                ).matches();

        if (
                !manifestShapeValid
        ) {
            return new Verification(
                    false,

                    structureValid,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,

                    false,

                    capsuleSha256,
                    null,

                    "Release capsule manifest declaration is invalid."
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
                    "Release capsule contains an invalid release-root public key."
            );
        }

        rootFingerprint =
                sha256(
                        rootPublicBytes
                );

        rootFingerprintValid =
                rootFingerprint.equals(
                        declaredRootFingerprint
                );

        String trustedRoot =
                normalizeSha256File(
                        configuration.rootPin(),
                        "release root pin"
                );

        rootPinValid =
                rootFingerprintValid

                && trustedRoot.equals(
                        rootFingerprint
                );

        entryHashesValid =
                artifactSha.equals(
                        sha256(
                                entries.get(
                                        ARTIFACT_ENTRY
                                )
                        )
                )

                && releaseManifestSha.equals(
                        sha256(
                                entries.get(
                                        RELEASE_MANIFEST_ENTRY
                                )
                        )
                )

                && authorityLineageSha.equals(
                        sha256(
                                entries.get(
                                        AUTHORITY_LINEAGE_ENTRY
                                )
                        )
                )

                && transparencyLedgerSha.equals(
                        sha256(
                                entries.get(
                                        TRANSPARENCY_LEDGER_ENTRY
                                )
                        )
                )

                && trustStateSha.equals(
                        sha256(
                                entries.get(
                                        TRUST_STATE_ENTRY
                                )
                        )
                )

                && releaseVerifierSha.equals(
                        sha256(
                                entries.get(
                                        RELEASE_VERIFIER_ENTRY
                                )
                        )
                )

                && transparencyVerifierSha.equals(
                        sha256(
                                entries.get(
                                        TRANSPARENCY_VERIFIER_ENTRY
                                )
                        )
                )

                && authorityVerifierSha.equals(
                        sha256(
                                entries.get(
                                        AUTHORITY_VERIFIER_ENTRY
                                )
                        )
                )

                && trustStateVerifierSha.equals(
                        sha256(
                                entries.get(
                                        TRUST_STATE_VERIFIER_ENTRY
                                )
                        )
                );

        String payload =
                String.join(
                        "\n",

                        manifestLines.subList(
                                0,
                                15
                        )
                )
                + "\n";

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

        if (
                !rootFingerprintValid
        ) {
            message =
                    "Release capsule root public-key fingerprint is invalid.";

            return result(
                    structureValid,
                    manifestShapeValid,
                    false,
                    false,
                    signatureValid,
                    entryHashesValid,

                    false,
                    false,
                    false,
                    false,

                    capsuleSha256,
                    rootFingerprint,

                    message
            );
        }

        if (
                !rootPinValid
        ) {
            message =
                    "Release capsule root key does not match the independently trusted root pin.";

            return result(
                    structureValid,
                    manifestShapeValid,
                    true,
                    false,
                    signatureValid,
                    entryHashesValid,

                    false,
                    false,
                    false,
                    false,

                    capsuleSha256,
                    rootFingerprint,

                    message
            );
        }

        if (
                !signatureValid
        ) {
            message =
                    "Release capsule root Ed25519 signature is invalid.";

            return result(
                    structureValid,
                    manifestShapeValid,
                    true,
                    true,
                    false,
                    entryHashesValid,

                    false,
                    false,
                    false,
                    false,

                    capsuleSha256,
                    rootFingerprint,

                    message
            );
        }

        if (
                !entryHashesValid
        ) {
            message =
                    "Release capsule contains an entry whose SHA-256 does not match the root-signed capsule manifest.";

            return result(
                    structureValid,
                    manifestShapeValid,
                    true,
                    true,
                    true,
                    false,

                    false,
                    false,
                    false,
                    false,

                    capsuleSha256,
                    rootFingerprint,

                    message
            );
        }

        Path extractionRoot =
                Files.createTempDirectory(
                        "terrascope-release-capsule."
                );

        try {
            for (
                    Map.Entry<String, byte[]> entry
                    : entries.entrySet()
            ) {
                if (
                        MANIFEST_ENTRY.equals(
                                entry.getKey()
                        )
                ) {
                    continue;
                }

                Path target =
                        extractionRoot.resolve(
                                entry.getKey()
                        );

                Path parent =
                        target.getParent();

                if (
                        parent != null
                ) {
                    Files.createDirectories(
                            parent
                    );
                }

                Files.write(
                        target,
                        entry.getValue()
                );
            }

            Path derivedAuthorityHead =
                    extractionRoot.resolve(
                            "derived-authority-head.sha256"
                    );

            Path derivedTransparencyKey =
                    extractionRoot.resolve(
                            "derived-transparency-key.sha256"
                    );

            Path derivedTransparencyHead =
                    extractionRoot.resolve(
                            "derived-transparency-head.sha256"
                    );

            Path derivedReleaseKey =
                    extractionRoot.resolve(
                            "derived-release-key.sha256"
                    );

            CommandResult trustStateResult =
                    runJavaJar(
                            extractionRoot.resolve(
                                    TRUST_STATE_VERIFIER_ENTRY
                            ),

                            List.of(
                                    "verify",

                                    "--state",
                                    extractionRoot
                                            .resolve(
                                                    TRUST_STATE_ENTRY
                                            )
                                            .toString(),

                                    "--root-pin",
                                    configuration
                                            .rootPin()
                                            .toString(),

                                    "--state-head-pin",
                                    configuration
                                            .stateHeadPin()
                                            .toString(),

                                    "--release-authority-head-output",
                                    derivedAuthorityHead
                                            .toString(),

                                    "--transparency-key-output",
                                    derivedTransparencyKey
                                            .toString(),

                                    "--transparency-head-output",
                                    derivedTransparencyHead
                                            .toString()
                            )
                    );

            trustStateValid =
                    trustStateResult.exitCode()
                    == 0;

            System.out.println(
                    "TRUST_STATE_SUBPROCESS_EXIT="
                    + trustStateResult.exitCode()
            );

            if (
                    !trustStateValid
            ) {
                System.out.println(
                        "TRUST_STATE_SUBPROCESS_OUTPUT="
                        + oneLine(
                                trustStateResult.output()
                        )
                );
            }

            if (
                    trustStateValid
            ) {
                CommandResult authorityResult =
                        runJavaJar(
                                extractionRoot.resolve(
                                        AUTHORITY_VERIFIER_ENTRY
                                ),

                                List.of(
                                        "verify",

                                        "--lineage",
                                        extractionRoot
                                            .resolve(
                                                    AUTHORITY_LINEAGE_ENTRY
                                            )
                                            .toString(),

                                        "--root-pin",
                                        configuration
                                            .rootPin()
                                            .toString(),

                                        "--head-pin",
                                        derivedAuthorityHead
                                            .toString(),

                                        "--terminal-pin-output",
                                        derivedReleaseKey
                                            .toString()
                                )
                        );

                authorityLineageValid =
                        authorityResult.exitCode()
                        == 0;

                System.out.println(
                        "AUTHORITY_SUBPROCESS_EXIT="
                        + authorityResult.exitCode()
                );

                if (
                        !authorityLineageValid
                ) {
                    System.out.println(
                            "AUTHORITY_SUBPROCESS_OUTPUT="
                            + oneLine(
                                    authorityResult.output()
                            )
                    );
                }
            }

            if (
                    trustStateValid

                    && authorityLineageValid
            ) {
                CommandResult releaseResult =
                        runJavaJar(
                                extractionRoot.resolve(
                                        RELEASE_VERIFIER_ENTRY
                                ),

                                List.of(
                                        "--artifact",
                                        extractionRoot
                                            .resolve(
                                                    ARTIFACT_ENTRY
                                            )
                                            .toString(),

                                        "--manifest",
                                        extractionRoot
                                            .resolve(
                                                    RELEASE_MANIFEST_ENTRY
                                            )
                                            .toString(),

                                        "--release-key-pin",
                                        derivedReleaseKey
                                            .toString()
                                )
                        );

                releaseValid =
                        releaseResult.exitCode()
                        == 0;

                System.out.println(
                        "RELEASE_SUBPROCESS_EXIT="
                        + releaseResult.exitCode()
                );

                if (
                        !releaseValid
                ) {
                    System.out.println(
                            "RELEASE_SUBPROCESS_OUTPUT="
                            + oneLine(
                                    releaseResult.output()
                            )
                    );
                }
            }

            if (
                    trustStateValid

                    && authorityLineageValid

                    && releaseValid
            ) {
                CommandResult transparencyResult =
                        runJavaJar(
                                extractionRoot.resolve(
                                        TRANSPARENCY_VERIFIER_ENTRY
                                ),

                                List.of(
                                        "verify",

                                        "--artifact",
                                        extractionRoot
                                            .resolve(
                                                    ARTIFACT_ENTRY
                                            )
                                            .toString(),

                                        "--manifest",
                                        extractionRoot
                                            .resolve(
                                                    RELEASE_MANIFEST_ENTRY
                                            )
                                            .toString(),

                                        "--release-key-pin",
                                        derivedReleaseKey
                                            .toString(),

                                        "--ledger",
                                        extractionRoot
                                            .resolve(
                                                    TRANSPARENCY_LEDGER_ENTRY
                                            )
                                            .toString(),

                                        "--transparency-key-pin",
                                        derivedTransparencyKey
                                            .toString(),

                                        "--head-pin",
                                        derivedTransparencyHead
                                            .toString()
                                )
                        );

                transparencyValid =
                        transparencyResult.exitCode()
                        == 0;

                System.out.println(
                        "TRANSPARENCY_SUBPROCESS_EXIT="
                        + transparencyResult.exitCode()
                );

                if (
                        !transparencyValid
                ) {
                    System.out.println(
                            "TRANSPARENCY_SUBPROCESS_OUTPUT="
                            + oneLine(
                                    transparencyResult.output()
                            )
                    );
                }
            }

        } finally {
            deleteRecursively(
                    extractionRoot
            );
        }

        boolean valid =
                structureValid

                && manifestShapeValid

                && rootFingerprintValid

                && rootPinValid

                && signatureValid

                && entryHashesValid

                && trustStateValid

                && authorityLineageValid

                && releaseValid

                && transparencyValid;

        if (
                !trustStateValid
        ) {
            message =
                    "Release capsule trust-state verification failed.";

        } else if (
                !authorityLineageValid
        ) {
            message =
                    "Release capsule authority-lineage verification failed.";

        } else if (
                !releaseValid
        ) {
            message =
                    "Release capsule release-manifest verification failed.";

        } else if (
                !transparencyValid
        ) {
            message =
                    "Release capsule transparency verification failed.";

        } else {
            message =
                    "Portable verifier release capsule authenticated successfully from the independently trusted root pin and current trust-state hash.";
        }

        return new Verification(
                valid,

                structureValid,

                manifestShapeValid,

                rootFingerprintValid,

                rootPinValid,

                signatureValid,

                entryHashesValid,

                trustStateValid,

                authorityLineageValid,

                releaseValid,

                transparencyValid,

                capsuleSha256,

                rootFingerprint,

                message
        );
    }

    private static Verification result(
            boolean structureValid,

            boolean manifestShapeValid,

            boolean rootFingerprintValid,

            boolean rootPinValid,

            boolean signatureValid,

            boolean entryHashesValid,

            boolean trustStateValid,

            boolean authorityLineageValid,

            boolean releaseValid,

            boolean transparencyValid,

            String capsuleSha256,

            String rootFingerprint,

            String message
    ) {
        return new Verification(
                false,

                structureValid,

                manifestShapeValid,

                rootFingerprintValid,

                rootPinValid,

                signatureValid,

                entryHashesValid,

                trustStateValid,

                authorityLineageValid,

                releaseValid,

                transparencyValid,

                capsuleSha256,

                rootFingerprint,

                message
        );
    }

    private static String manifestPayload(
            String artifactName,

            String artifactSha,

            String releaseManifestSha,

            String authorityLineageSha,

            String transparencyLedgerSha,

            String trustStateSha,

            String releaseVerifierSha,

            String transparencyVerifierSha,

            String authorityVerifierSha,

            String trustStateVerifierSha,

            String rootKeyId,

            String rootPublicBase64,

            String rootFingerprint
    ) {
        return """
                capsuleVersion=%s
                artifactName=%s
                artifactSha256=%s
                releaseManifestSha256=%s
                authorityLineageSha256=%s
                transparencyLedgerSha256=%s
                trustStateSha256=%s
                releaseVerifierSha256=%s
                transparencyVerifierSha256=%s
                authorityVerifierSha256=%s
                trustStateVerifierSha256=%s
                releaseRootKeyId=%s
                releaseRootPublicKeyX509Base64=%s
                releaseRootKeyFingerprint=%s
                algorithm=%s
                """
                .formatted(
                        CAPSULE_VERSION,

                        artifactName,

                        artifactSha,

                        releaseManifestSha,

                        authorityLineageSha,

                        transparencyLedgerSha,

                        trustStateSha,

                        releaseVerifierSha,

                        transparencyVerifierSha,

                        authorityVerifierSha,

                        trustStateVerifierSha,

                        rootKeyId,

                        rootPublicBase64,

                        rootFingerprint,

                        ALGORITHM
                );
    }

    private static void writeDeterministicZip(
            Path output,
            Map<String, byte[]> entries
    ) throws Exception {
        try (
                OutputStream fileOutput =
                        Files.newOutputStream(
                                output
                        );

                ZipOutputStream zipOutput =
                        new ZipOutputStream(
                                fileOutput,
                                StandardCharsets.UTF_8
                        )
        ) {
            zipOutput.setComment(
                    null
            );

            for (
                    Map.Entry<String, byte[]> entry
                    : entries.entrySet()
            ) {
                byte[] bytes =
                        entry.getValue();

                CRC32 crc =
                        new CRC32();

                crc.update(
                        bytes
                );

                ZipEntry zipEntry =
                        new ZipEntry(
                                entry.getKey()
                        );

                zipEntry.setTime(
                        FIXED_ZIP_TIME
                );

                zipEntry.setMethod(
                        ZipEntry.STORED
                );

                zipEntry.setSize(
                        bytes.length
                );

                zipEntry.setCompressedSize(
                        bytes.length
                );

                zipEntry.setCrc(
                        crc.getValue()
                );

                zipOutput.putNextEntry(
                        zipEntry
                );

                zipOutput.write(
                        bytes
                );

                zipOutput.closeEntry();
            }
        }
    }

    private static CommandResult runJavaJar(
            Path jar,
            List<String> arguments
    ) throws Exception {
        Path javaBinary =
                Path.of(
                        System.getProperty(
                                "java.home"
                        ),

                        "bin",

                        isWindows()
                                ? "java.exe"
                                : "java"
                );

        List<String> command =
                new ArrayList<>();

        command.add(
                javaBinary.toString()
        );

        command.add(
                "-jar"
        );

        command.add(
                jar.toString()
        );

        command.addAll(
                arguments
        );

        ProcessBuilder builder =
                new ProcessBuilder(
                        command
                );

        builder.redirectErrorStream(
                true
        );

        Process process =
                builder.start();

        byte[] outputBytes;

        try (
                InputStream input =
                        process.getInputStream()
        ) {
            outputBytes =
                    input.readAllBytes();
        }

        int exitCode =
                process.waitFor();

        return new CommandResult(
                exitCode,

                new String(
                        outputBytes,
                        StandardCharsets.UTF_8
                )
        );
    }

    private static boolean isWindows() {
        return System
                .getProperty(
                        "os.name",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .contains(
                        "win"
                );
    }

    private static void deleteRecursively(
            Path root
    ) {
        if (
                root == null

                || !Files.exists(
                        root
                )
        ) {
            return;
        }

        try (
                var paths =
                        Files.walk(
                                root
                        )
        ) {
            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(
                                            path
                                    );

                                } catch (
                                        Exception ignored
                                ) {
                                }
                            }
                    );

        } catch (
                Exception ignored
        ) {
        }
    }

    private static String oneLine(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        return value
                .replace(
                        '\n',
                        ' '
                )
                .replace(
                        '\r',
                        ' '
                )
                .trim();
    }

    private static void print(
            Verification result
    ) {
        System.out.println(
                "CAPSULE_TOOL_VERSION="
                + TOOL_VERSION
        );

        System.out.println(
                "CAPSULE_STRUCTURE_VALID="
                + result.structureValid()
        );

        System.out.println(
                "CAPSULE_MANIFEST_VALID="
                + result.manifestShapeValid()
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
                "CAPSULE_SIGNATURE_VALID="
                + result.signatureValid()
        );

        System.out.println(
                "CAPSULE_ENTRY_HASHES_VALID="
                + result.entryHashesValid()
        );

        System.out.println(
                "TRUST_STATE_VALID="
                + result.trustStateValid()
        );

        System.out.println(
                "AUTHORITY_LINEAGE_VALID="
                + result.authorityLineageValid()
        );

        System.out.println(
                "RELEASE_VALID="
                + result.releaseValid()
        );

        System.out.println(
                "TRANSPARENCY_VALID="
                + result.transparencyValid()
        );

        System.out.println(
                "CAPSULE_SHA256="
                + value(
                        result.capsuleSha256()
                )
        );

        System.out.println(
                "ROOT_KEY_FINGERPRINT="
                + value(
                        result.rootFingerprint()
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

    private record CommandResult(
            int exitCode,
            String output
    ) {
    }

    private record Verification(
            boolean valid,

            boolean structureValid,

            boolean manifestShapeValid,

            boolean rootFingerprintValid,

            boolean rootPinValid,

            boolean signatureValid,

            boolean entryHashesValid,

            boolean trustStateValid,

            boolean authorityLineageValid,

            boolean releaseValid,

            boolean transparencyValid,

            String capsuleSha256,

            String rootFingerprint,

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
                    false,
                    false,

                    null,
                    null,

                    message
            );
        }
    }

    private record CreateConfiguration(
            Path artifact,

            Path releaseManifest,

            Path authorityLineage,

            Path transparencyLedger,

            Path trustState,

            Path releaseVerifierJar,

            Path transparencyVerifierJar,

            Path authorityVerifierJar,

            Path trustStateVerifierJar,

            String releaseRootKeyId,

            Path releaseRootPrivateKey,

            Path releaseRootPublicKey,

            Path rootPin,

            Path output
    ) {

        static CreateConfiguration parse(
                String[] args
        ) {
            Map<String, String> options =
                    parseOptions(
                            args
                    );

            return new CreateConfiguration(
                    requiredFile(
                            options,
                            "--artifact"
                    ),

                    requiredFile(
                            options,
                            "--release-manifest"
                    ),

                    requiredFile(
                            options,
                            "--authority-lineage"
                    ),

                    requiredFile(
                            options,
                            "--transparency-ledger"
                    ),

                    requiredFile(
                            options,
                            "--trust-state"
                    ),

                    requiredFile(
                            options,
                            "--release-verifier-jar"
                    ),

                    requiredFile(
                            options,
                            "--transparency-verifier-jar"
                    ),

                    requiredFile(
                            options,
                            "--authority-verifier-jar"
                    ),

                    requiredFile(
                            options,
                            "--trust-state-verifier-jar"
                    ),

                    requiredValue(
                            options,
                            "--release-root-key-id"
                    ),

                    requiredFile(
                            options,
                            "--release-root-private-key"
                    ),

                    requiredFile(
                            options,
                            "--release-root-public-key"
                    ),

                    requiredFile(
                            options,
                            "--root-pin"
                    ),

                    Path.of(
                            requiredValue(
                                    options,
                                    "--output"
                            )
                    )
            );
        }
    }

    private record VerifyConfiguration(
            Path capsule,

            Path rootPin,

            Path stateHeadPin
    ) {

        static VerifyConfiguration parse(
                String[] args
        ) {
            Map<String, String> options =
                    parseOptions(
                            args
                    );

            return new VerifyConfiguration(
                    requiredFile(
                            options,
                            "--capsule"
                    ),

                    requiredFile(
                            options,
                            "--root-pin"
                    ),

                    requiredFile(
                            options,
                            "--state-head-pin"
                    )
            );
        }
    }

    private static Map<String, String> parseOptions(
            String[] args
    ) {
        if (
                args.length % 2
                != 0
        ) {
            throw new IllegalArgumentException(
                    "Every option requires exactly one value."
            );
        }

        Map<String, String> result =
                new LinkedHashMap<>();

        for (
                int index = 0;
                index < args.length;
                index += 2
        ) {
            String option =
                    args[index];

            String value =
                    args[index + 1];

            if (
                    !option.startsWith(
                            "--"
                    )
            ) {
                throw new IllegalArgumentException(
                        "Invalid option: "
                        + option
                );
            }

            if (
                    result.put(
                            option,
                            value
                    )
                    != null
            ) {
                throw new IllegalArgumentException(
                        "Duplicate option: "
                        + option
                );
            }
        }

        return result;
    }

    private static String requiredValue(
            Map<String, String> options,
            String option
    ) {
        String value =
                options.get(
                        option
                );

        if (
                value == null

                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    option
                    + " is required."
            );
        }

        return value;
    }

    private static Path requiredFile(
            Map<String, String> options,
            String option
    ) {
        Path path =
                Path.of(
                        requiredValue(
                                options,
                                option
                        )
                );

        if (
                !Files.isRegularFile(
                        path
                )
        ) {
            throw new IllegalArgumentException(
                    option
                    + " must reference an existing file."
            );
        }

        return path;
    }
}
