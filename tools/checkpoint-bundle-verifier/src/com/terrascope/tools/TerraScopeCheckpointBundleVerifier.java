package com.terrascope.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.X509EncodedKeySpec;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import java.util.regex.Pattern;

public final class TerraScopeCheckpointBundleVerifier {

    private static final String VERIFIER_VERSION =
            "TS-STANDALONE-CHECKPOINT-BUNDLE-VERIFIER-V1";

    private static final String ENVELOPE_VERSION =
            "TS-OFFLINE-TRUST-CHECKPOINT-ENVELOPE-V1";

    private static final String BUNDLE_VERSION =
            "TS-OFFLINE-TRUST-CHECKPOINT-BUNDLE-V1";

    private static final String LEDGER_VERSION =
            "TS-OFFLINE-TRUST-RECEIPT-LEDGER-V1";

    private static final String CHECKPOINT_VERSION =
            "TS-OFFLINE-TRUST-RECEIPT-CHECKPOINT-V1";

    private static final String ALGORITHM =
            "ED25519";

    private static final Pattern SHA256 =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private static final Pattern INTEGER =
            Pattern.compile(
                    "-?(?:0|[1-9][0-9]*)"
            );

    private static final List<String> ENVELOPE_KEYS =
            List.of(
                    "envelopeVersion",
                    "bundleHash",
                    "payloadJson"
            );

    private static final List<String> MATERIAL_KEYS =
            List.of(
                    "bundleVersion",
                    "checkpoint",
                    "ledgerEntries",
                    "signingPublicKeyX509Base64",
                    "signingPublicKeyFingerprint"
            );

    private static final List<String> CHECKPOINT_KEYS =
            List.of(
                    "id",
                    "checkpointNumber",
                    "coveredLedgerNumber",
                    "coveredLedgerHash",
                    "entryCount",
                    "validReceiptCount",
                    "invalidReceiptCount",
                    "keyId",
                    "algorithm",
                    "publicKeyFingerprint",
                    "checkpointVersion",
                    "signatureBase64",
                    "previousCheckpointHash",
                    "checkpointHash",
                    "createdAt"
            );

    private static final List<String> LEDGER_KEYS =
            List.of(
                    "id",
                    "ledgerNumber",
                    "receiptId",
                    "receiptImportNumber",
                    "verificationResult",
                    "receiptHash",
                    "receiptPayloadHash",
                    "previousLedgerHash",
                    "ledgerVersion",
                    "ledgerHash",
                    "createdAt"
            );

    private TerraScopeCheckpointBundleVerifier() {
    }

    public static void main(
            String[] args
    ) {
        int exitCode;

        try {
            Configuration configuration =
                    Configuration.parse(
                            args
                    );

            String envelopeJson =
                    Files.readString(
                            configuration.bundle(),
                            StandardCharsets.UTF_8
                    );

            String trustedFingerprint =
                    Files.readString(
                                    configuration.signingKeyPin(),
                                    StandardCharsets.UTF_8
                            )
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            Verification verification =
                    verify(
                            envelopeJson,
                            trustedFingerprint
                    );

            print(
                    verification
            );

            exitCode =
                    verification.valid()
                            ? 0
                            : 2;

        } catch (
                Exception exception
        ) {
            Verification verification =
                    Verification.failure(
                            "Standalone checkpoint bundle verification failed: "
                                    + safeMessage(
                                            exception
                                    )
                    );

            print(
                    verification
            );

            exitCode =
                    2;
        }

        System.exit(
                exitCode
        );
    }

    private static Verification verify(
            String envelopeJson,
            String trustedFingerprint
    ) {
        boolean envelopeVersionValid =
                false;

        boolean bundleHashValid =
                false;

        boolean canonicalPayloadValid =
                false;

        boolean ledgerValid =
                false;

        boolean checkpointValid =
                false;

        boolean signingKeyPinValid =
                false;

        int ledgerEntryCount =
                0;

        int verifiedLedgerEntryCount =
                0;

        Integer checkpointNumber =
                null;

        Integer coveredLedgerNumber =
                null;

        String bundleHash =
                null;

        String checkpointHash =
                null;

        String signingKeyFingerprint =
                null;

        String message;

        try {
            Map<String, Object> envelope =
                    object(
                            Json.parse(
                                    envelopeJson
                            ),
                            "envelope"
                    );

            if (
                    !keyOrder(
                            envelope
                    ).equals(
                            ENVELOPE_KEYS
                    )
            ) {
                throw new VerificationException(
                        "Portable checkpoint envelope fields are not canonical."
                );
            }

            String envelopeVersion =
                    string(
                            envelope,
                            "envelopeVersion"
                    );

            envelopeVersionValid =
                    ENVELOPE_VERSION.equals(
                            envelopeVersion
                    );

            bundleHash =
                    string(
                            envelope,
                            "bundleHash"
                    );

            String payloadJson =
                    string(
                            envelope,
                            "payloadJson"
                    );

            bundleHashValid =
                    SHA256.matcher(
                            bundleHash
                    ).matches()

                    && bundleHash.equals(
                            sha256(
                                    payloadJson
                            )
                    );

            Map<String, Object> material =
                    object(
                            Json.parse(
                                    payloadJson
                            ),
                            "payload"
                    );

            List<Object> ledgerEntries =
                    array(
                            material.get(
                                    "ledgerEntries"
                            ),
                            "ledgerEntries"
                    );

            Map<String, Object> checkpoint =
                    object(
                            material.get(
                                    "checkpoint"
                            ),
                            "checkpoint"
                    );

            ledgerEntryCount =
                    ledgerEntries.size();

            checkpointNumber =
                    integer(
                            checkpoint,
                            "checkpointNumber"
                    );

            coveredLedgerNumber =
                    integer(
                            checkpoint,
                            "coveredLedgerNumber"
                    );

            checkpointHash =
                    string(
                            checkpoint,
                            "checkpointHash"
                    );

            boolean materialShapeValid =
                    keyOrder(
                            material
                    ).equals(
                            MATERIAL_KEYS
                    )

                    && keyOrder(
                            checkpoint
                    ).equals(
                            CHECKPOINT_KEYS
                    );

            for (
                    Object rawEntry
                    : ledgerEntries
            ) {
                Map<String, Object> entry =
                        object(
                                rawEntry,
                                "ledger entry"
                        );

                if (
                        !keyOrder(
                                entry
                        ).equals(
                                LEDGER_KEYS
                        )
                ) {
                    materialShapeValid =
                            false;
                }
            }

            canonicalPayloadValid =
                    materialShapeValid

                    && BUNDLE_VERSION.equals(
                            string(
                                    material,
                                    "bundleVersion"
                            )
                    )

                    && Json.write(
                                    material
                            )
                            .equals(
                                    payloadJson
                            );

            String publicKeyBase64 =
                    string(
                            material,
                            "signingPublicKeyX509Base64"
                    );

            signingKeyFingerprint =
                    string(
                            material,
                            "signingPublicKeyFingerprint"
                    )
                            .toLowerCase(
                                    Locale.ROOT
                            );

            byte[] publicKeyBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    publicKeyBase64
                            );

            String calculatedFingerprint =
                    sha256(
                            publicKeyBytes
                    );

            boolean embeddedFingerprintValid =
                    calculatedFingerprint.equals(
                            signingKeyFingerprint
                    )

                    && calculatedFingerprint.equals(
                            string(
                                    checkpoint,
                                    "publicKeyFingerprint"
                            )
                                    .toLowerCase(
                                            Locale.ROOT
                                    )
                    );

            signingKeyPinValid =
                    trustedFingerprint != null

                    && SHA256.matcher(
                            trustedFingerprint
                    ).matches()

                    && trustedFingerprint.equals(
                            calculatedFingerprint
                    );

            LedgerResult ledgerResult =
                    verifyLedger(
                            ledgerEntries
                    );

            ledgerValid =
                    ledgerResult.valid();

            verifiedLedgerEntryCount =
                    ledgerResult.verified();

            checkpointValid =
                    ledgerValid

                    && embeddedFingerprintValid

                    && verifyCheckpoint(
                            checkpoint,
                            ledgerEntries,

                            publicKeyBytes
                    );

            boolean valid =
                    envelopeVersionValid

                    && bundleHashValid

                    && canonicalPayloadValid

                    && ledgerValid

                    && checkpointValid

                    && signingKeyPinValid;

            if (
                    !envelopeVersionValid
            ) {
                message =
                        "Portable checkpoint envelope version is unsupported.";

            } else if (
                    !bundleHashValid
            ) {
                message =
                        "Portable checkpoint bundle SHA-256 does not match its payload.";

            } else if (
                    !canonicalPayloadValid
            ) {
                message =
                        "Portable checkpoint payload is not canonical.";

            } else if (
                    !ledgerValid
            ) {
                message =
                        ledgerResult.message();

            } else if (
                    !checkpointValid
            ) {
                message =
                        "Portable checkpoint Ed25519 or checkpoint-hash verification failed.";

            } else if (
                    !signingKeyPinValid
            ) {
                message =
                        "Portable checkpoint signing key does not match the independently trusted fingerprint.";

            } else {
                message =
                        "Portable checkpoint bundle verified entirely offline against the independently supplied signing-key fingerprint.";
            }

            return new Verification(
                    valid,

                    envelopeVersionValid,

                    bundleHashValid,

                    canonicalPayloadValid,

                    ledgerValid,

                    checkpointValid,

                    signingKeyPinValid,

                    ledgerEntryCount,

                    verifiedLedgerEntryCount,

                    checkpointNumber,

                    coveredLedgerNumber,

                    bundleHash,

                    checkpointHash,

                    signingKeyFingerprint,

                    message
            );

        } catch (
                Exception exception
        ) {
            message =
                    "Portable checkpoint bundle verification failed: "
                            + safeMessage(
                                    exception
                            );

            return new Verification(
                    false,

                    envelopeVersionValid,

                    bundleHashValid,

                    canonicalPayloadValid,

                    ledgerValid,

                    checkpointValid,

                    signingKeyPinValid,

                    ledgerEntryCount,

                    verifiedLedgerEntryCount,

                    checkpointNumber,

                    coveredLedgerNumber,

                    bundleHash,

                    checkpointHash,

                    signingKeyFingerprint,

                    message
            );
        }
    }

    private static LedgerResult verifyLedger(
            List<Object> ledgerEntries
    ) {
        if (
                ledgerEntries.isEmpty()
        ) {
            return new LedgerResult(
                    false,
                    0,
                    "Portable checkpoint ledger evidence is empty."
            );
        }

        int expectedLedgerNumber =
                1;

        int verified =
                0;

        String expectedPreviousHash =
                null;

        for (
                Object rawEntry
                : ledgerEntries
        ) {
            Map<String, Object> entry =
                    object(
                            rawEntry,
                            "ledger entry"
                    );

            int ledgerNumber =
                    integer(
                            entry,
                            "ledgerNumber"
                    );

            if (
                    ledgerNumber
                    != expectedLedgerNumber
            ) {
                return new LedgerResult(
                        false,
                        verified,
                        "Portable checkpoint ledger numbering is not contiguous."
                );
            }

            String previousLedgerHash =
                    nullableString(
                            entry,
                            "previousLedgerHash"
                    );

            if (
                    !Objects.equals(
                            previousLedgerHash,
                            expectedPreviousHash
                    )
            ) {
                return new LedgerResult(
                        false,
                        verified,
                        "Portable checkpoint ledger previous-hash linkage is invalid."
                );
            }

            String ledgerVersion =
                    string(
                            entry,
                            "ledgerVersion"
                    );

            if (
                    !LEDGER_VERSION.equals(
                            ledgerVersion
                    )
            ) {
                return new LedgerResult(
                        false,
                        verified,
                        "Portable checkpoint ledger version is unsupported."
                );
            }

            String verificationResult =
                    string(
                            entry,
                            "verificationResult"
                    );

            if (
                    !"VALID".equals(
                            verificationResult
                    )

                    && !"INVALID".equals(
                            verificationResult
                    )
            ) {
                return new LedgerResult(
                        false,
                        verified,
                        "Portable checkpoint ledger verification result is invalid."
                );
            }

            String receiptId =
                    string(
                            entry,
                            "receiptId"
                    );

            int receiptImportNumber =
                    integer(
                            entry,
                            "receiptImportNumber"
                    );

            String receiptHash =
                    string(
                            entry,
                            "receiptHash"
                    );

            String receiptPayloadHash =
                    string(
                            entry,
                            "receiptPayloadHash"
                    );

            String ledgerHash =
                    string(
                            entry,
                            "ledgerHash"
                    );

            if (
                    !SHA256.matcher(
                            receiptHash
                    ).matches()

                    || !SHA256.matcher(
                            receiptPayloadHash
                    ).matches()

                    || !SHA256.matcher(
                            ledgerHash
                    ).matches()
            ) {
                return new LedgerResult(
                        false,
                        verified,
                        "Portable checkpoint ledger contains a malformed SHA-256 value."
                );
            }

            String calculatedLedgerHash =
                    sha256(
                            """
                            ledgerVersion=%s
                            ledgerNumber=%d
                            receiptId=%s
                            receiptImportNumber=%d
                            verificationResult=%s
                            receiptHash=%s
                            receiptPayloadHash=%s
                            previousLedgerHash=%s
                            """
                                    .formatted(
                                            LEDGER_VERSION,

                                            ledgerNumber,

                                            receiptId,

                                            receiptImportNumber,

                                            verificationResult,

                                            receiptHash,

                                            receiptPayloadHash,

                                            previousLedgerHash == null
                                                    ? "GENESIS"
                                                    : previousLedgerHash
                                    )
                    );

            if (
                    !calculatedLedgerHash.equals(
                            ledgerHash
                    )
            ) {
                return new LedgerResult(
                        false,
                        verified,
                        "Portable checkpoint ledger SHA-256 verification failed."
                );
            }

            expectedPreviousHash =
                    ledgerHash;

            expectedLedgerNumber++;

            verified++;
        }

        return new LedgerResult(
                true,
                verified,
                "Portable checkpoint ledger is valid."
        );
    }

    private static boolean verifyCheckpoint(
            Map<String, Object> checkpoint,

            List<Object> ledgerEntries,

            byte[] publicKeyBytes
    ) {
        try {
            String checkpointVersion =
                    string(
                            checkpoint,
                            "checkpointVersion"
                    );

            String algorithm =
                    string(
                            checkpoint,
                            "algorithm"
                    );

            if (
                    !CHECKPOINT_VERSION.equals(
                            checkpointVersion
                    )

                    || !ALGORITHM.equals(
                            algorithm
                    )
            ) {
                return false;
            }

            int checkpointNumber =
                    integer(
                            checkpoint,
                            "checkpointNumber"
                    );

            int coveredLedgerNumber =
                    integer(
                            checkpoint,
                            "coveredLedgerNumber"
                    );

            String coveredLedgerHash =
                    string(
                            checkpoint,
                            "coveredLedgerHash"
                    );

            int entryCount =
                    integer(
                            checkpoint,
                            "entryCount"
                    );

            int validReceiptCount =
                    integer(
                            checkpoint,
                            "validReceiptCount"
                    );

            int invalidReceiptCount =
                    integer(
                            checkpoint,
                            "invalidReceiptCount"
                    );

            String keyId =
                    string(
                            checkpoint,
                            "keyId"
                    );

            String publicKeyFingerprint =
                    string(
                            checkpoint,
                            "publicKeyFingerprint"
                    );

            String signatureBase64 =
                    string(
                            checkpoint,
                            "signatureBase64"
                    );

            String previousCheckpointHash =
                    nullableString(
                            checkpoint,
                            "previousCheckpointHash"
                    );

            String checkpointHash =
                    string(
                            checkpoint,
                            "checkpointHash"
                    );

            if (
                    checkpointNumber < 1

                    || coveredLedgerNumber < 1

                    || entryCount < 1

                    || validReceiptCount < 0

                    || invalidReceiptCount < 0

                    || validReceiptCount
                       + invalidReceiptCount
                       != entryCount

                    || !SHA256.matcher(
                            coveredLedgerHash
                    ).matches()

                    || !SHA256.matcher(
                            publicKeyFingerprint
                    ).matches()

                    || !SHA256.matcher(
                            checkpointHash
                    ).matches()

                    || (
                        previousCheckpointHash != null

                        && !SHA256.matcher(
                                previousCheckpointHash
                        ).matches()
                    )
            ) {
                return false;
            }

            if (
                    ledgerEntries.size()
                    != entryCount
            ) {
                return false;
            }

            Map<String, Object> head =
                    object(
                            ledgerEntries.get(
                                    ledgerEntries.size() - 1
                            ),
                            "ledger head"
                    );

            if (
                    integer(
                            head,
                            "ledgerNumber"
                    )
                    != coveredLedgerNumber

                    || !string(
                            head,
                            "ledgerHash"
                    ).equals(
                            coveredLedgerHash
                    )
            ) {
                return false;
            }

            int calculatedValidCount =
                    0;

            for (
                    Object rawEntry
                    : ledgerEntries
            ) {
                Map<String, Object> entry =
                        object(
                                rawEntry,
                                "ledger entry"
                        );

                if (
                        "VALID".equals(
                                string(
                                        entry,
                                        "verificationResult"
                                )
                        )
                ) {
                    calculatedValidCount++;
                }
            }

            int calculatedInvalidCount =
                    ledgerEntries.size()
                    - calculatedValidCount;

            if (
                    calculatedValidCount
                    != validReceiptCount

                    || calculatedInvalidCount
                       != invalidReceiptCount
            ) {
                return false;
            }

            String signingPayload =
                    """
                    checkpointVersion=%s
                    checkpointNumber=%d
                    coveredLedgerNumber=%d
                    coveredLedgerHash=%s
                    entryCount=%d
                    validReceiptCount=%d
                    invalidReceiptCount=%d
                    keyId=%s
                    algorithm=%s
                    publicKeyFingerprint=%s
                    previousCheckpointHash=%s
                    """
                            .formatted(
                                    CHECKPOINT_VERSION,

                                    checkpointNumber,

                                    coveredLedgerNumber,

                                    coveredLedgerHash,

                                    entryCount,

                                    validReceiptCount,

                                    invalidReceiptCount,

                                    keyId,

                                    ALGORITHM,

                                    publicKeyFingerprint,

                                    previousCheckpointHash == null
                                            ? "GENESIS"
                                            : previousCheckpointHash
                            );

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
                    signingPayload.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            boolean signatureValid =
                    signature.verify(
                            Base64
                                    .getDecoder()
                                    .decode(
                                            signatureBase64
                                    )
                    );

            if (
                    !signatureValid
            ) {
                return false;
            }

            String calculatedCheckpointHash =
                    sha256(
                            signingPayload

                            + "signatureBase64="
                            + signatureBase64
                            + "\n"
                    );

            return calculatedCheckpointHash.equals(
                    checkpointHash
            );

        } catch (
                Exception exception
        ) {
            return false;
        }
    }

    private static void print(
            Verification verification
    ) {
        System.out.println(
                "VERIFIER_VERSION="
                + VERIFIER_VERSION
        );

        System.out.println(
                "ENVELOPE_VERSION_VALID="
                + verification.envelopeVersionValid()
        );

        System.out.println(
                "BUNDLE_HASH_VALID="
                + verification.bundleHashValid()
        );

        System.out.println(
                "CANONICAL_PAYLOAD_VALID="
                + verification.canonicalPayloadValid()
        );

        System.out.println(
                "LEDGER_VALID="
                + verification.ledgerValid()
        );

        System.out.println(
                "CHECKPOINT_VALID="
                + verification.checkpointValid()
        );

        System.out.println(
                "SIGNING_KEY_PIN_VALID="
                + verification.signingKeyPinValid()
        );

        System.out.println(
                "LEDGER_ENTRY_COUNT="
                + verification.ledgerEntryCount()
        );

        System.out.println(
                "VERIFIED_LEDGER_ENTRY_COUNT="
                + verification.verifiedLedgerEntryCount()
        );

        System.out.println(
                "CHECKPOINT_NUMBER="
                + value(
                        verification.checkpointNumber()
                )
        );

        System.out.println(
                "COVERED_LEDGER_NUMBER="
                + value(
                        verification.coveredLedgerNumber()
                )
        );

        System.out.println(
                "BUNDLE_HASH="
                + value(
                        verification.bundleHash()
                )
        );

        System.out.println(
                "CHECKPOINT_HASH="
                + value(
                        verification.checkpointHash()
                )
        );

        System.out.println(
                "SIGNING_KEY_FINGERPRINT="
                + value(
                        verification.signingKeyFingerprint()
                )
        );

        System.out.println(
                "VERIFICATION_RESULT="
                + (
                    verification.valid()
                            ? "VALID"
                            : "INVALID"
                )
        );

        System.out.println(
                "MESSAGE="
                + verification.message()
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

    private static String sha256(
            String value
    ) {
        return sha256(
                value.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private static String sha256(
            byte[] value
    ) {
        try {
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
                    );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }

    private static List<String> keyOrder(
            Map<String, Object> object
    ) {
        return new ArrayList<>(
                object.keySet()
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(
            Object value,
            String label
    ) {
        if (
                !(value instanceof Map<?, ?>)
        ) {
            throw new VerificationException(
                    label
                    + " must be a JSON object."
            );
        }

        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(
            Object value,
            String label
    ) {
        if (
                !(value instanceof List<?>)
        ) {
            throw new VerificationException(
                    label
                    + " must be a JSON array."
            );
        }

        return (List<Object>) value;
    }

    private static String string(
            Map<String, Object> object,
            String key
    ) {
        Object value =
                object.get(
                        key
                );

        if (
                !(value instanceof String string)
        ) {
            throw new VerificationException(
                    key
                    + " must be a JSON string."
            );
        }

        return string;
    }

    private static String nullableString(
            Map<String, Object> object,
            String key
    ) {
        Object value =
                object.get(
                        key
                );

        if (
                value == null
        ) {
            return null;
        }

        if (
                !(value instanceof String string)
        ) {
            throw new VerificationException(
                    key
                    + " must be a JSON string or null."
            );
        }

        return string;
    }

    private static int integer(
            Map<String, Object> object,
            String key
    ) {
        Object value =
                object.get(
                        key
                );

        if (
                !(value instanceof JsonNumber number)
        ) {
            throw new VerificationException(
                    key
                    + " must be a JSON integer."
            );
        }

        if (
                !INTEGER.matcher(
                        number.lexeme()
                ).matches()
        ) {
            throw new VerificationException(
                    key
                    + " must use canonical integer notation."
            );
        }

        try {
            return Integer.parseInt(
                    number.lexeme()
            );

        } catch (
                NumberFormatException exception
        ) {
            throw new VerificationException(
                    key
                    + " is outside the supported integer range."
            );
        }
    }

    private record Configuration(
            Path bundle,
            Path signingKeyPin
    ) {

        static Configuration parse(
                String[] args
        ) {
            Path bundle =
                    null;

            Path pin =
                    null;

            for (
                    int index = 0;
                    index < args.length;
                    index++
            ) {
                switch (
                        args[index]
                ) {
                    case "--bundle" -> {
                        index =
                                requireValue(
                                        args,
                                        index
                                );

                        bundle =
                                Path.of(
                                        args[index]
                                );
                    }

                    case "--signing-key-pin" -> {
                        index =
                                requireValue(
                                        args,
                                        index
                                );

                        pin =
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

            if (
                    bundle == null
            ) {
                throw new IllegalArgumentException(
                        "--bundle is required."
                );
            }

            if (
                    pin == null
            ) {
                throw new IllegalArgumentException(
                        "--signing-key-pin is required."
                );
            }

            if (
                    !Files.isRegularFile(
                            bundle
                    )
            ) {
                throw new IllegalArgumentException(
                        "Bundle file does not exist: "
                        + bundle
                );
            }

            if (
                    !Files.isRegularFile(
                            pin
                    )
            ) {
                throw new IllegalArgumentException(
                        "Signing-key pin file does not exist: "
                        + pin
                );
            }

            return new Configuration(
                    bundle,
                    pin
            );
        }

        private static int requireValue(
                String[] args,
                int index
        ) {
            int valueIndex =
                    index + 1;

            if (
                    valueIndex
                    >= args.length
            ) {
                throw new IllegalArgumentException(
                        "Missing value for "
                        + args[index]
                );
            }

            return valueIndex;
        }
    }

    private record Verification(
            boolean valid,

            boolean envelopeVersionValid,

            boolean bundleHashValid,

            boolean canonicalPayloadValid,

            boolean ledgerValid,

            boolean checkpointValid,

            boolean signingKeyPinValid,

            int ledgerEntryCount,

            int verifiedLedgerEntryCount,

            Integer checkpointNumber,

            Integer coveredLedgerNumber,

            String bundleHash,

            String checkpointHash,

            String signingKeyFingerprint,

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

    private record LedgerResult(
            boolean valid,
            int verified,
            String message
    ) {
    }

    private record JsonNumber(
            String lexeme
    ) {
    }

    private static final class VerificationException
            extends RuntimeException {

        VerificationException(
                String message
        ) {
            super(
                    message
            );
        }
    }

    private static final class Json {

        private Json() {
        }

        static Object parse(
                String json
        ) {
            Parser parser =
                    new Parser(
                            json
                    );

            Object value =
                    parser.parseValue();

            parser.skipWhitespace();

            if (
                    !parser.atEnd()
            ) {
                throw new VerificationException(
                        "Unexpected trailing JSON content."
                );
            }

            return value;
        }

        static String write(
                Object value
        ) {
            StringBuilder output =
                    new StringBuilder();

            writeValue(
                    output,
                    value
            );

            return output.toString();
        }

        private static void writeValue(
                StringBuilder output,
                Object value
        ) {
            if (
                    value == null
            ) {
                output.append(
                        "null"
                );

                return;
            }

            if (
                    value instanceof String string
            ) {
                writeString(
                        output,
                        string
                );

                return;
            }

            if (
                    value instanceof Boolean bool
            ) {
                output.append(
                        bool
                                ? "true"
                                : "false"
                );

                return;
            }

            if (
                    value instanceof JsonNumber number
            ) {
                output.append(
                        number.lexeme()
                );

                return;
            }

            if (
                    value instanceof Map<?, ?> map
            ) {
                output.append(
                        '{'
                );

                boolean first =
                        true;

                for (
                        Map.Entry<?, ?> entry
                        : map.entrySet()
                ) {
                    if (
                            !first
                    ) {
                        output.append(
                                ','
                        );
                    }

                    first =
                            false;

                    writeString(
                            output,
                            entry
                                    .getKey()
                                    .toString()
                    );

                    output.append(
                            ':'
                    );

                    writeValue(
                            output,
                            entry.getValue()
                    );
                }

                output.append(
                        '}'
                );

                return;
            }

            if (
                    value instanceof List<?> list
            ) {
                output.append(
                        '['
                );

                for (
                        int index = 0;
                        index < list.size();
                        index++
                ) {
                    if (
                            index > 0
                    ) {
                        output.append(
                                ','
                        );
                    }

                    writeValue(
                            output,
                            list.get(
                                    index
                            )
                    );
                }

                output.append(
                        ']'
                );

                return;
            }

            throw new VerificationException(
                    "Unsupported JSON value type."
            );
        }

        private static void writeString(
                StringBuilder output,
                String value
        ) {
            output.append(
                    '"'
            );

            for (
                    int index = 0;
                    index < value.length();
                    index++
            ) {
                char character =
                        value.charAt(
                                index
                        );

                switch (
                        character
                ) {
                    case '"' ->
                            output.append(
                                    "\\\""
                            );

                    case '\\' ->
                            output.append(
                                    "\\\\"
                            );

                    case '\b' ->
                            output.append(
                                    "\\b"
                            );

                    case '\f' ->
                            output.append(
                                    "\\f"
                            );

                    case '\n' ->
                            output.append(
                                    "\\n"
                            );

                    case '\r' ->
                            output.append(
                                    "\\r"
                            );

                    case '\t' ->
                            output.append(
                                    "\\t"
                            );

                    default -> {
                        if (
                                character
                                < 0x20
                        ) {
                            output.append(
                                    "\\u"
                            );

                            output.append(
                                    String.format(
                                            Locale.ROOT,
                                            "%04x",
                                            (int) character
                                    )
                            );

                        } else {
                            output.append(
                                    character
                            );
                        }
                    }
                }
            }

            output.append(
                    '"'
            );
        }

        private static final class Parser {

            private final String input;

            private int position;

            private Parser(
                    String input
            ) {
                this.input =
                        input;

                this.position =
                        0;
            }

            Object parseValue() {
                skipWhitespace();

                if (
                        atEnd()
                ) {
                    throw new VerificationException(
                            "Unexpected end of JSON."
                    );
                }

                char character =
                        input.charAt(
                                position
                        );

                return switch (
                        character
                ) {
                    case '{' ->
                            parseObject();

                    case '[' ->
                            parseArray();

                    case '"' ->
                            parseString();

                    case 't' -> {
                        expectLiteral(
                                "true"
                        );

                        yield Boolean.TRUE;
                    }

                    case 'f' -> {
                        expectLiteral(
                                "false"
                        );

                        yield Boolean.FALSE;
                    }

                    case 'n' -> {
                        expectLiteral(
                                "null"
                        );

                        yield null;
                    }

                    default -> {
                        if (
                                character == '-'

                                || Character.isDigit(
                                        character
                                )
                        ) {
                            yield parseNumber();
                        }

                        throw new VerificationException(
                                "Unexpected JSON token at character "
                                + position
                                + "."
                        );
                    }
                };
            }

            private Map<String, Object> parseObject() {
                expect(
                        '{'
                );

                skipWhitespace();

                LinkedHashMap<String, Object> result =
                        new LinkedHashMap<>();

                if (
                        consume(
                                '}'
                        )
                ) {
                    return result;
                }

                while (
                        true
                ) {
                    skipWhitespace();

                    if (
                            peek()
                            != '"'
                    ) {
                        throw new VerificationException(
                                "JSON object key must be a string."
                        );
                    }

                    String key =
                            parseString();

                    if (
                            result.containsKey(
                                    key
                            )
                    ) {
                        throw new VerificationException(
                                "Duplicate JSON object key: "
                                + key
                        );
                    }

                    skipWhitespace();

                    expect(
                            ':'
                    );

                    Object value =
                            parseValue();

                    result.put(
                            key,
                            value
                    );

                    skipWhitespace();

                    if (
                            consume(
                                    '}'
                            )
                    ) {
                        return result;
                    }

                    expect(
                            ','
                    );
                }
            }

            private List<Object> parseArray() {
                expect(
                        '['
                );

                skipWhitespace();

                List<Object> result =
                        new ArrayList<>();

                if (
                        consume(
                                ']'
                        )
                ) {
                    return result;
                }

                while (
                        true
                ) {
                    result.add(
                            parseValue()
                    );

                    skipWhitespace();

                    if (
                            consume(
                                    ']'
                            )
                    ) {
                        return result;
                    }

                    expect(
                            ','
                    );
                }
            }

            private String parseString() {
                expect(
                        '"'
                );

                StringBuilder result =
                        new StringBuilder();

                while (
                        !atEnd()
                ) {
                    char character =
                            input.charAt(
                                    position++
                            );

                    if (
                            character == '"'
                    ) {
                        return result.toString();
                    }

                    if (
                            character == '\\'
                    ) {
                        if (
                                atEnd()
                        ) {
                            throw new VerificationException(
                                    "Incomplete JSON escape."
                            );
                        }

                        char escaped =
                                input.charAt(
                                        position++
                                );

                        switch (
                                escaped
                        ) {
                            case '"' ->
                                    result.append(
                                            '"'
                                    );

                            case '\\' ->
                                    result.append(
                                            '\\'
                                    );

                            case '/' ->
                                    result.append(
                                            '/'
                                    );

                            case 'b' ->
                                    result.append(
                                            '\b'
                                    );

                            case 'f' ->
                                    result.append(
                                            '\f'
                                    );

                            case 'n' ->
                                    result.append(
                                            '\n'
                                    );

                            case 'r' ->
                                    result.append(
                                            '\r'
                                    );

                            case 't' ->
                                    result.append(
                                            '\t'
                                    );

                            case 'u' ->
                                    result.append(
                                            parseUnicode()
                                    );

                            default ->
                                    throw new VerificationException(
                                            "Unsupported JSON escape."
                                    );
                        }

                    } else {
                        if (
                                character
                                < 0x20
                        ) {
                            throw new VerificationException(
                                    "Unescaped JSON control character."
                            );
                        }

                        result.append(
                                character
                        );
                    }
                }

                throw new VerificationException(
                        "Unterminated JSON string."
                );
            }

            private char parseUnicode() {
                if (
                        position + 4
                        > input.length()
                ) {
                    throw new VerificationException(
                            "Incomplete Unicode JSON escape."
                    );
                }

                String hexadecimal =
                        input.substring(
                                position,
                                position + 4
                        );

                position +=
                        4;

                try {
                    return (char) Integer.parseInt(
                            hexadecimal,
                            16
                    );

                } catch (
                        NumberFormatException exception
                ) {
                    throw new VerificationException(
                            "Invalid Unicode JSON escape."
                    );
                }
            }

            private JsonNumber parseNumber() {
                int start =
                        position;

                if (
                        peek()
                        == '-'
                ) {
                    position++;
                }

                if (
                        atEnd()
                ) {
                    throw new VerificationException(
                            "Incomplete JSON number."
                    );
                }

                if (
                        peek()
                        == '0'
                ) {
                    position++;

                } else {
                    if (
                            !isDigitOneToNine(
                                    peek()
                            )
                    ) {
                        throw new VerificationException(
                                "Invalid JSON number."
                        );
                    }

                    while (
                            !atEnd()

                            && Character.isDigit(
                                    peek()
                            )
                    ) {
                        position++;
                    }
                }

                if (
                        !atEnd()

                        && peek()
                           == '.'
                ) {
                    position++;

                    requireDigit();

                    while (
                            !atEnd()

                            && Character.isDigit(
                                    peek()
                            )
                    ) {
                        position++;
                    }
                }

                if (
                        !atEnd()

                        && (
                            peek() == 'e'
                            || peek() == 'E'
                        )
                ) {
                    position++;

                    if (
                            !atEnd()

                            && (
                                peek() == '+'
                                || peek() == '-'
                            )
                    ) {
                        position++;
                    }

                    requireDigit();

                    while (
                            !atEnd()

                            && Character.isDigit(
                                    peek()
                            )
                    ) {
                        position++;
                    }
                }

                return new JsonNumber(
                        input.substring(
                                start,
                                position
                        )
                );
            }

            private void requireDigit() {
                if (
                        atEnd()

                        || !Character.isDigit(
                                peek()
                        )
                ) {
                    throw new VerificationException(
                            "Invalid JSON number."
                    );
                }
            }

            private boolean isDigitOneToNine(
                    char character
            ) {
                return character >= '1'
                        && character <= '9';
            }

            private void expectLiteral(
                    String literal
            ) {
                if (
                        !input.startsWith(
                                literal,
                                position
                        )
                ) {
                    throw new VerificationException(
                            "Invalid JSON literal."
                    );
                }

                position +=
                        literal.length();
            }

            private void expect(
                    char expected
            ) {
                skipWhitespace();

                if (
                        atEnd()

                        || input.charAt(
                                position
                        )
                           != expected
                ) {
                    throw new VerificationException(
                            "Expected JSON character '"
                            + expected
                            + "'."
                    );
                }

                position++;
            }

            private boolean consume(
                    char expected
            ) {
                skipWhitespace();

                if (
                        !atEnd()

                        && input.charAt(
                                position
                        )
                           == expected
                ) {
                    position++;

                    return true;
                }

                return false;
            }

            private char peek() {
                if (
                        atEnd()
                ) {
                    throw new VerificationException(
                            "Unexpected end of JSON."
                    );
                }

                return input.charAt(
                        position
                );
            }

            private void skipWhitespace() {
                while (
                        !atEnd()
                ) {
                    char character =
                            input.charAt(
                                    position
                            );

                    if (
                            character == ' '

                            || character == '\n'

                            || character == '\r'

                            || character == '\t'
                    ) {
                        position++;

                    } else {
                        return;
                    }
                }
            }

            private boolean atEnd() {
                return position
                        >= input.length();
            }
        }
    }
}
