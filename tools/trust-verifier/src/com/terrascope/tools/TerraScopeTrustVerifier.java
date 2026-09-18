package com.terrascope.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

import java.security.spec.X509EncodedKeySpec;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class TerraScopeTrustVerifier {

    private static final String VERIFIER_VERSION =
            "TS-STANDALONE-TRUST-VERIFIER-V1";

    private static final String ENVELOPE_VERSION =
            "TS-GOVERNANCE-TRUST-LINEAGE-ENVELOPE-V1";

    private static final String PACKAGE_VERSION =
            "TS-GOVERNANCE-TRUST-LINEAGE-PACKAGE-V1";

    private static final String TRANSITION_VERSION =
            "TS-GOVERNANCE-TRUST-TRANSITION-V1";

    private static final String ALGORITHM =
            "ED25519";

    private static final Pattern SHA256 =
            Pattern.compile(
                    "^[0-9a-f]{64}$"
            );

    private TerraScopeTrustVerifier() {
    }

    public static void main(
            String[] args
    ) throws Exception {
        Arguments arguments =
                Arguments.parse(
                        args
                );

        String envelopeJson =
                Files.readString(
                        arguments.envelope(),
                        StandardCharsets.UTF_8
                );

        byte[] envelopeBytes =
                envelopeJson.getBytes(
                        StandardCharsets.UTF_8
                );

        String trustedRootFingerprint =
                Files.readString(
                        arguments.rootPin(),
                        StandardCharsets.UTF_8
                )
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        Verification verification;

        try {
            verification =
                    verify(
                            envelopeJson,
                            envelopeBytes,
                            trustedRootFingerprint
                    );

        } catch (
                Exception exception
        ) {
            verification =
                    Verification.failure(
                            sha256(
                                    envelopeBytes
                            ),

                            null,
                            null,

                            null,

                            false,
                            false,
                            false,
                            false,

                            0,
                            0,
                            0,

                            trustedRootFingerprint,
                            null,

                            null,
                            null,

                            "Portable trust lineage could not be verified: "
                                    + exception.getMessage()
                    );
        }

        String verifiedAt =
                Instant.now()
                        .toString();

        Map<String, Object> material =
                new LinkedHashMap<>();

        // EXACT declaration order of
        // GovernanceOfflineTrustVerificationReceiptMaterial.
        material.put(
                "verifierVersion",
                VERIFIER_VERSION
        );

        material.put(
                "sourcePackageId",
                verification.sourcePackageId()
        );

        material.put(
                "sourcePackageNumber",
                verification.sourcePackageNumber()
        );

        material.put(
                "envelopeJson",
                envelopeJson
        );

        material.put(
                "envelopeHash",
                verification.envelopeHash()
        );

        material.put(
                "packageHash",
                verification.packageHash()
        );

        material.put(
                "trustedRootFingerprint",
                verification.trustedRootFingerprint()
        );

        material.put(
                "presentedRootFingerprint",
                verification.presentedRootFingerprint()
        );

        material.put(
                "valid",
                verification.valid()
        );

        material.put(
                "packageHashValid",
                verification.packageHashValid()
        );

        material.put(
                "canonicalPayloadValid",
                verification.canonicalPayloadValid()
        );

        material.put(
                "lineageValid",
                verification.lineageValid()
        );

        material.put(
                "rootPinValid",
                verification.rootPinValid()
        );

        material.put(
                "transitionCount",
                verification.transitionCount()
        );

        material.put(
                "verifiedTransitionCount",
                verification.verifiedTransitionCount()
        );

        material.put(
                "trustAnchorCount",
                verification.trustAnchorCount()
        );

        material.put(
                "rootKeyId",
                verification.rootKeyId()
        );

        material.put(
                "terminalKeyId",
                verification.terminalKeyId()
        );

        material.put(
                "message",
                verification.message()
        );

        material.put(
                "verifiedAt",
                verifiedAt
        );

        String materialJson =
                Json.writeCanonical(
                        material
                );

        String receiptHash =
                sha256(
                        materialJson
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        Map<String, Object> document =
                new LinkedHashMap<>(
                        material
                );

        // EXACT final field of
        // GovernanceOfflineTrustVerificationReceiptDocument.
        document.put(
                "receiptHash",
                receiptHash
        );

        String receiptJson =
                Json.writeCanonical(
                        document
                );

        Files.writeString(
                arguments.receipt(),
                receiptJson,
                StandardCharsets.UTF_8
        );

        print(
                verification,
                receiptHash
        );

        if (
                !verification.valid()
        ) {
            System.exit(
                    2
            );
        }
    }

    private static Verification verify(
            String envelopeJson,
            byte[] envelopeBytes,
            String trustedRootFingerprint
    ) throws Exception {
        String envelopeHash =
                sha256(
                        envelopeBytes
                );

        if (
                !SHA256
                        .matcher(
                                trustedRootFingerprint
                        )
                        .matches()
        ) {
            return Verification.failure(
                    envelopeHash,

                    null,
                    null,

                    null,

                    false,
                    false,
                    false,
                    false,

                    0,
                    0,
                    0,

                    trustedRootFingerprint,
                    null,

                    null,
                    null,

                    "Trusted root fingerprint must be a 64-character lowercase SHA-256 value."
            );
        }

        Object parsedEnvelope =
                Json.parse(
                        envelopeJson
                );

        Map<String, Object> envelope =
                object(
                        parsedEnvelope
                );

        String sourcePackageId =
                nullableString(
                        envelope.get(
                                "sourcePackageId"
                        )
                );

        Integer sourcePackageNumber =
                nullableInteger(
                        envelope.get(
                                "sourcePackageNumber"
                        )
                );

        String packageHash =
                nullableString(
                        envelope.get(
                                "packageHash"
                        )
                );

        if (
                !ENVELOPE_VERSION.equals(
                        requiredString(
                                envelope,
                                "envelopeVersion"
                        )
                )
        ) {
            return Verification.failure(
                    envelopeHash,

                    sourcePackageId,
                    sourcePackageNumber,

                    packageHash,

                    false,
                    false,
                    false,
                    false,

                    0,
                    0,
                    0,

                    trustedRootFingerprint,
                    null,

                    null,
                    null,

                    "Portable trust lineage failed cryptographic verification: Unsupported trust-lineage envelope version."
            );
        }

        String payloadJson =
                requiredString(
                        envelope,
                        "payloadJson"
                );

        boolean packageHashValid =
                packageHash != null

                && sha256(
                        payloadJson.getBytes(
                                StandardCharsets.UTF_8
                        )
                ).equals(
                        packageHash
                );

        if (
                !packageHashValid
        ) {
            return Verification.failure(
                    envelopeHash,

                    sourcePackageId,
                    sourcePackageNumber,

                    packageHash,

                    false,
                    false,
                    false,
                    false,

                    0,
                    0,
                    0,

                    trustedRootFingerprint,
                    null,

                    null,
                    null,

                    "Portable trust lineage failed cryptographic verification: Portable trust-lineage package hash does not match its payload."
            );
        }

        Object parsedPayload =
                Json.parse(
                        payloadJson
                );

        Map<String, Object> payload =
                object(
                        parsedPayload
                );

        String presentedRootFingerprint =
                nullableString(
                        payload.get(
                                "rootPublicKeyFingerprint"
                        )
                );

        boolean rootPinValid =
                Objects.equals(
                        trustedRootFingerprint,
                        presentedRootFingerprint
                );

        String reencodedPayload =
                Json.writeCanonical(
                        parsedPayload
                );

        boolean canonicalPayloadValid =
                payloadJson.equals(
                        reencodedPayload
                )

                && Json.hasExpectedTrustLineageRecordOrder(
                        parsedPayload
                );

        if (
                !canonicalPayloadValid
        ) {
            System.out.println(
                    "CANONICAL_DIAGNOSTIC_ORIGINAL_SHA="
                            + sha256(
                                    payloadJson.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
            );

            System.out.println(
                    "CANONICAL_DIAGNOSTIC_REENCODED_SHA="
                            + sha256(
                                    reencodedPayload.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
            );

            System.out.println(
                    "CANONICAL_DIAGNOSTIC_FIRST_DIFF="
                            + firstDifference(
                                    payloadJson,
                                    reencodedPayload
                            )
            );

            System.out.println(
                    "CANONICAL_DIAGNOSTIC_ROOT_KEYS="
                            + Json.keys(
                                    parsedPayload
                            )
            );

            return Verification.failure(
                    envelopeHash,

                    sourcePackageId,
                    sourcePackageNumber,

                    packageHash,

                    true,
                    false,
                    false,
                    rootPinValid,

                    count(
                            payload.get(
                                    "transitions"
                            )
                    ),

                    0,

                    count(
                            payload.get(
                                    "trustAnchors"
                            )
                    ),

                    trustedRootFingerprint,
                    presentedRootFingerprint,

                    nullableString(
                            payload.get(
                                    "rootKeyId"
                            )
                    ),

                    nullableString(
                            payload.get(
                                    "terminalKeyId"
                            )
                    ),

                    "Portable trust lineage failed cryptographic verification: Portable trust-lineage payload is not canonical."
            );
        }

        if (
                !PACKAGE_VERSION.equals(
                        requiredString(
                                payload,
                                "packageVersion"
                        )
                )

                || !PACKAGE_VERSION.equals(
                        requiredString(
                                envelope,
                                "packageVersion"
                        )
                )
        ) {
            return Verification.failure(
                    envelopeHash,

                    sourcePackageId,
                    sourcePackageNumber,

                    packageHash,

                    true,
                    true,
                    false,
                    rootPinValid,

                    count(
                            payload.get(
                                    "transitions"
                            )
                    ),

                    0,

                    count(
                            payload.get(
                                    "trustAnchors"
                            )
                    ),

                    trustedRootFingerprint,
                    presentedRootFingerprint,

                    nullableString(
                            payload.get(
                                    "rootKeyId"
                            )
                    ),

                    nullableString(
                            payload.get(
                                    "terminalKeyId"
                            )
                    ),

                    "Portable trust lineage failed cryptographic verification: Unsupported portable trust-lineage package version."
            );
        }

        List<Object> transitions =
                list(
                        payload.get(
                                "transitions"
                        )
                );

        List<Object> anchors =
                list(
                        payload.get(
                                "trustAnchors"
                        )
                );

        String rootKeyId =
                requiredString(
                        payload,
                        "rootKeyId"
                );

        String terminalKeyId =
                requiredString(
                        payload,
                        "terminalKeyId"
                );

        String rootFingerprint =
                requiredString(
                        payload,
                        "rootPublicKeyFingerprint"
                );

        String terminalFingerprint =
                requiredString(
                        payload,
                        "terminalPublicKeyFingerprint"
                );

        if (
                transitions.isEmpty()

                || anchors.size() < 2
        ) {
            return lineageFailure(
                    envelopeHash,

                    sourcePackageId,
                    sourcePackageNumber,

                    packageHash,

                    rootPinValid,

                    transitions.size(),
                    0,
                    anchors.size(),

                    trustedRootFingerprint,
                    presentedRootFingerprint,

                    rootKeyId,
                    terminalKeyId,

                    "Portable trust-lineage package has insufficient transition or trust-anchor evidence."
            );
        }

        Map<String, Map<String, Object>> anchorsByKey =
                new LinkedHashMap<>();

        for (
                Object anchorValue
                : anchors
        ) {
            Map<String, Object> anchor =
                    object(
                            anchorValue
                    );

            String keyId =
                    requiredString(
                            anchor,
                            "keyId"
                    );

            String algorithm =
                    requiredString(
                            anchor,
                            "algorithm"
                    );

            String publicKey =
                    requiredString(
                            anchor,
                            "publicKeyX509Base64"
                    );

            String fingerprint =
                    requiredString(
                            anchor,
                            "publicKeyFingerprint"
                    );

            if (
                    !ALGORITHM.equals(
                            algorithm
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        0,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage package contains invalid trust-anchor metadata."
                );
            }

            byte[] publicKeyBytes;

            try {
                publicKeyBytes =
                        Base64
                                .getDecoder()
                                .decode(
                                        publicKey
                                );

            } catch (
                    IllegalArgumentException exception
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        0,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage package contains an invalid Ed25519 public key."
                );
            }

            String calculatedFingerprint =
                    sha256(
                            publicKeyBytes
                    );

            if (
                    !calculatedFingerprint.equals(
                            fingerprint
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        0,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage trust-anchor fingerprint verification failed."
                );
            }

            if (
                    anchorsByKey.put(
                            keyId,
                            anchor
                    ) != null
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        0,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage package contains duplicate trust-anchor key IDs."
                );
            }
        }

        Map<String, Object> first =
                object(
                        transitions.getFirst()
                );

        Map<String, Object> last =
                object(
                        transitions.getLast()
                );

        if (
                !rootKeyId.equals(
                        requiredString(
                                first,
                                "outgoingKeyId"
                        )
                )

                || !rootFingerprint.equals(
                        requiredString(
                                first,
                                "outgoingPublicKeyFingerprint"
                        )
                )
        ) {
            return lineageFailure(
                    envelopeHash,
                    sourcePackageId,
                    sourcePackageNumber,
                    packageHash,
                    rootPinValid,
                    transitions.size(),
                    0,
                    anchors.size(),
                    trustedRootFingerprint,
                    presentedRootFingerprint,
                    rootKeyId,
                    terminalKeyId,
                    "Portable trust-lineage root does not match the first signed transition."
            );
        }

        if (
                !terminalKeyId.equals(
                        requiredString(
                                last,
                                "replacementKeyId"
                        )
                )

                || !terminalFingerprint.equals(
                        requiredString(
                                last,
                                "replacementPublicKeyFingerprint"
                        )
                )
        ) {
            return lineageFailure(
                    envelopeHash,
                    sourcePackageId,
                    sourcePackageNumber,
                    packageHash,
                    rootPinValid,
                    transitions.size(),
                    0,
                    anchors.size(),
                    trustedRootFingerprint,
                    presentedRootFingerprint,
                    rootKeyId,
                    terminalKeyId,
                    "Portable trust-lineage terminal key does not match the final signed transition."
            );
        }

        String expectedPreviousHash =
                null;

        String previousReplacementKeyId =
                null;

        String previousReplacementFingerprint =
                null;

        int expectedTransitionNumber =
                1;

        int verifiedTransitionCount =
                0;

        for (
                Object transitionValue
                : transitions
        ) {
            Map<String, Object> transition =
                    object(
                            transitionValue
                    );

            int transitionNumber =
                    integer(
                            transition,
                            "transitionNumber"
                    );

            String outgoingKeyId =
                    requiredString(
                            transition,
                            "outgoingKeyId"
                    );

            String outgoingFingerprint =
                    requiredString(
                            transition,
                            "outgoingPublicKeyFingerprint"
                    );

            String replacementKeyId =
                    requiredString(
                            transition,
                            "replacementKeyId"
                    );

            String replacementFingerprint =
                    requiredString(
                            transition,
                            "replacementPublicKeyFingerprint"
                    );

            String algorithm =
                    requiredString(
                            transition,
                            "algorithm"
                    );

            String reason =
                    requiredString(
                            transition,
                            "reason"
                    );

            String transitionVersion =
                    requiredString(
                            transition,
                            "transitionVersion"
                    );

            String signatureBase64 =
                    requiredString(
                            transition,
                            "signatureBase64"
                    );

            String previousTransitionHash =
                    nullableString(
                            transition.get(
                                    "previousTransitionHash"
                            )
                    );

            String transitionHash =
                    requiredString(
                            transition,
                            "transitionHash"
                    );

            if (
                    transitionNumber
                    != expectedTransitionNumber
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage transition sequence is not contiguous from one."
                );
            }

            if (
                    !Objects.equals(
                            previousTransitionHash,
                            expectedPreviousHash
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage previous transition hash does not match."
                );
            }

            if (
                    previousReplacementKeyId != null

                    && (
                        !previousReplacementKeyId
                                .equals(
                                        outgoingKeyId
                                )

                        || !previousReplacementFingerprint
                                .equals(
                                        outgoingFingerprint
                                )
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage successor continuity is broken."
                );
            }

            Map<String, Object> outgoingAnchor =
                    anchorsByKey.get(
                            outgoingKeyId
                    );

            Map<String, Object> replacementAnchor =
                    anchorsByKey.get(
                            replacementKeyId
                    );

            if (
                    outgoingAnchor == null

                    || replacementAnchor == null
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage transition references a missing public trust anchor."
                );
            }

            if (
                    !outgoingFingerprint.equals(
                            requiredString(
                                    outgoingAnchor,
                                    "publicKeyFingerprint"
                            )
                    )

                    || !replacementFingerprint.equals(
                            requiredString(
                                    replacementAnchor,
                                    "publicKeyFingerprint"
                            )
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage transition fingerprints do not match embedded trust anchors."
                );
            }

            if (
                    !ALGORITHM.equals(
                            algorithm
                    )

                    || !TRANSITION_VERSION.equals(
                            transitionVersion
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage transition algorithm or version is unsupported."
                );
            }

            String signingPayload =
                    transitionSigningPayload(
                            transitionNumber,

                            outgoingKeyId,
                            outgoingFingerprint,

                            replacementKeyId,
                            replacementFingerprint,

                            algorithm,

                            reason,

                            previousTransitionHash
                    );

            if (
                    !verifyEd25519(
                            requiredString(
                                    outgoingAnchor,
                                    "publicKeyX509Base64"
                            ),

                            signingPayload
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    ),

                            signatureBase64
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage Ed25519 transition signature verification failed."
                );
            }

            String calculatedTransitionHash =
                    sha256(
                            (
                                signingPayload

                                + "signatureBase64="
                                + signatureBase64
                                + "\n"
                            )
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            if (
                    !calculatedTransitionHash.equals(
                            transitionHash
                    )
            ) {
                return lineageFailure(
                        envelopeHash,
                        sourcePackageId,
                        sourcePackageNumber,
                        packageHash,
                        rootPinValid,
                        transitions.size(),
                        verifiedTransitionCount,
                        anchors.size(),
                        trustedRootFingerprint,
                        presentedRootFingerprint,
                        rootKeyId,
                        terminalKeyId,
                        "Portable trust-lineage transition hash does not match its signed payload."
                );
            }

            expectedPreviousHash =
                    transitionHash;

            previousReplacementKeyId =
                    replacementKeyId;

            previousReplacementFingerprint =
                    replacementFingerprint;

            expectedTransitionNumber++;

            verifiedTransitionCount++;
        }

        if (
                !rootPinValid
        ) {
            return new Verification(
                    envelopeHash,

                    sourcePackageId,
                    sourcePackageNumber,

                    packageHash,

                    false,

                    true,
                    true,
                    true,
                    false,

                    transitions.size(),
                    verifiedTransitionCount,
                    anchors.size(),

                    trustedRootFingerprint,
                    presentedRootFingerprint,

                    rootKeyId,
                    terminalKeyId,

                    "Portable trust-lineage root does not match the externally trusted root fingerprint."
            );
        }

        return new Verification(
                envelopeHash,

                sourcePackageId,
                sourcePackageNumber,

                packageHash,

                true,

                true,
                true,
                true,
                true,

                transitions.size(),
                verifiedTransitionCount,
                anchors.size(),

                trustedRootFingerprint,
                presentedRootFingerprint,

                rootKeyId,
                terminalKeyId,

                "Portable trust lineage verified against the externally supplied root fingerprint."
        );
    }

    private static Verification lineageFailure(
            String envelopeHash,

            String sourcePackageId,
            Integer sourcePackageNumber,

            String packageHash,

            boolean rootPinValid,

            int transitionCount,
            int verifiedTransitionCount,
            int trustAnchorCount,

            String trustedRootFingerprint,
            String presentedRootFingerprint,

            String rootKeyId,
            String terminalKeyId,

            String message
    ) {
        return new Verification(
                envelopeHash,

                sourcePackageId,
                sourcePackageNumber,

                packageHash,

                false,

                true,
                true,
                false,
                rootPinValid,

                transitionCount,
                verifiedTransitionCount,
                trustAnchorCount,

                trustedRootFingerprint,
                presentedRootFingerprint,

                rootKeyId,
                terminalKeyId,

                "Portable trust lineage failed cryptographic verification: "
                        + message
        );
    }

    private static String transitionSigningPayload(
            int transitionNumber,

            String outgoingKeyId,
            String outgoingFingerprint,

            String replacementKeyId,
            String replacementFingerprint,

            String algorithm,

            String reason,

            String previousTransitionHash
    ) {
        return """
                transitionVersion=%s
                transitionNumber=%d
                outgoingKeyId=%s
                outgoingPublicKeyFingerprint=%s
                replacementKeyId=%s
                replacementPublicKeyFingerprint=%s
                algorithm=%s
                reason=%s
                previousTransitionHash=%s
                """
                .formatted(
                        TRANSITION_VERSION,

                        transitionNumber,

                        outgoingKeyId,
                        outgoingFingerprint,

                        replacementKeyId,
                        replacementFingerprint,

                        algorithm,

                        reason,

                        previousTransitionHash == null
                                ? "GENESIS"
                                : previousTransitionHash
                );
    }

    private static boolean verifyEd25519(
            String publicKeyX509Base64,
            byte[] payload,
            String signatureBase64
    ) {
        try {
            byte[] publicKeyBytes =
                    Base64
                            .getDecoder()
                            .decode(
                                    publicKeyX509Base64
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

            Signature verifier =
                    Signature.getInstance(
                            "Ed25519"
                    );

            verifier.initVerify(
                    publicKey
            );

            verifier.update(
                    payload
            );

            return verifier.verify(
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

    private static int firstDifference(
            String left,
            String right
    ) {
        int limit =
                Math.min(
                        left.length(),
                        right.length()
                );

        for (
                int index = 0;
                index < limit;
                index++
        ) {
            if (
                    left.charAt(
                            index
                    )
                    != right.charAt(
                            index
                    )
            ) {
                return index;
            }
        }

        return left.length()
               == right.length()
                ? -1
                : limit;
    }

    private static void print(
            Verification verification,
            String receiptHash
    ) {
        System.out.println(
                "VERIFIER_VERSION="
                        + VERIFIER_VERSION
        );

        System.out.println(
                "ENVELOPE_HASH="
                        + verification.envelopeHash()
        );

        System.out.println(
                "PACKAGE_HASH="
                        + nullText(
                                verification.packageHash()
                        )
        );

        System.out.println(
                "TRUSTED_ROOT_FINGERPRINT="
                        + nullText(
                                verification.trustedRootFingerprint()
                        )
        );

        System.out.println(
                "PRESENTED_ROOT_FINGERPRINT="
                        + nullText(
                                verification.presentedRootFingerprint()
                        )
        );

        System.out.println(
                "PACKAGE_HASH_VALID="
                        + verification.packageHashValid()
        );

        System.out.println(
                "CANONICAL_PAYLOAD_VALID="
                        + verification.canonicalPayloadValid()
        );

        System.out.println(
                "LINEAGE_VALID="
                        + verification.lineageValid()
        );

        System.out.println(
                "ROOT_PIN_VALID="
                        + verification.rootPinValid()
        );

        System.out.println(
                "TRANSITION_COUNT="
                        + verification.transitionCount()
        );

        System.out.println(
                "VERIFIED_TRANSITION_COUNT="
                        + verification.verifiedTransitionCount()
        );

        System.out.println(
                "TRUST_ANCHOR_COUNT="
                        + verification.trustAnchorCount()
        );

        System.out.println(
                "ROOT_KEY_ID="
                        + nullText(
                                verification.rootKeyId()
                        )
        );

        System.out.println(
                "TERMINAL_KEY_ID="
                        + nullText(
                                verification.terminalKeyId()
                        )
        );

        System.out.println(
                "RECEIPT_HASH="
                        + receiptHash
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(
            Object value
    ) {
        if (
                !(value instanceof Map<?, ?>)
        ) {
            throw new IllegalArgumentException(
                    "Expected JSON object."
            );
        }

        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(
            Object value
    ) {
        if (
                !(value instanceof List<?>)
        ) {
            throw new IllegalArgumentException(
                    "Expected JSON array."
            );
        }

        return (List<Object>) value;
    }

    private static String requiredString(
            Map<String, Object> value,
            String key
    ) {
        Object field =
                value.get(
                        key
                );

        if (
                !(field instanceof String string)
        ) {
            throw new IllegalArgumentException(
                    "Missing string property: "
                            + key
            );
        }

        return string;
    }

    private static String nullableString(
            Object value
    ) {
        if (
                value == null
        ) {
            return null;
        }

        if (
                value instanceof String string
        ) {
            return string;
        }

        throw new IllegalArgumentException(
                "Expected nullable string."
        );
    }

    private static int integer(
            Map<String, Object> value,
            String key
    ) {
        Object field =
                value.get(
                        key
                );

        if (
                field instanceof Json.JsonNumber number
        ) {
            return Integer.parseInt(
                    number.raw()
            );
        }

        if (
                field instanceof Number number
        ) {
            return number.intValue();
        }

        throw new IllegalArgumentException(
                "Missing integer property: "
                        + key
        );
    }

    private static Integer nullableInteger(
            Object value
    ) {
        if (
                value == null
        ) {
            return null;
        }

        if (
                value instanceof Json.JsonNumber number
        ) {
            return Integer.parseInt(
                    number.raw()
            );
        }

        if (
                value instanceof Number number
        ) {
            return number.intValue();
        }

        throw new IllegalArgumentException(
                "Expected nullable integer."
        );
    }

    private static int count(
            Object value
    ) {
        return value instanceof List<?> list
                ? list.size()
                : 0;
    }

    private static String nullText(
            String value
    ) {
        return value == null
                ? "NULL"
                : value;
    }

    private record Arguments(
            Path envelope,
            Path rootPin,
            Path receipt
    ) {
        static Arguments parse(
                String[] args
        ) {
            Path envelope =
                    null;

            Path rootPin =
                    null;

            Path receipt =
                    null;

            for (
                    int index = 0;
                    index < args.length;
                    index++
            ) {
                switch (
                    args[index]
                ) {
                    case "--envelope" ->
                            envelope =
                                    Path.of(
                                            next(
                                                    args,
                                                    ++index,
                                                    "--envelope"
                                            )
                                    );

                    case "--root-pin" ->
                            rootPin =
                                    Path.of(
                                            next(
                                                    args,
                                                    ++index,
                                                    "--root-pin"
                                            )
                                    );

                    case "--receipt" ->
                            receipt =
                                    Path.of(
                                            next(
                                                    args,
                                                    ++index,
                                                    "--receipt"
                                            )
                                    );

                    default ->
                            throw new IllegalArgumentException(
                                    "Unknown argument: "
                                            + args[index]
                            );
                }
            }

            if (
                    envelope == null
                    || rootPin == null
                    || receipt == null
            ) {
                throw new IllegalArgumentException(
                        "Usage: java -jar terrascope-trust-verifier.jar "
                                + "--envelope <file> "
                                + "--root-pin <file> "
                                + "--receipt <file>"
                );
            }

            return new Arguments(
                    envelope,
                    rootPin,
                    receipt
            );
        }

        private static String next(
                String[] args,
                int index,
                String option
        ) {
            if (
                    index >= args.length
            ) {
                throw new IllegalArgumentException(
                        "Missing value for "
                                + option
                );
            }

            return args[index];
        }
    }

    private record Verification(
            String envelopeHash,

            String sourcePackageId,
            Integer sourcePackageNumber,

            String packageHash,

            boolean valid,

            boolean packageHashValid,
            boolean canonicalPayloadValid,
            boolean lineageValid,
            boolean rootPinValid,

            int transitionCount,
            int verifiedTransitionCount,
            int trustAnchorCount,

            String trustedRootFingerprint,
            String presentedRootFingerprint,

            String rootKeyId,
            String terminalKeyId,

            String message
    ) {
        static Verification failure(
                String envelopeHash,

                String sourcePackageId,
                Integer sourcePackageNumber,

                String packageHash,

                boolean packageHashValid,
                boolean canonicalPayloadValid,
                boolean lineageValid,
                boolean rootPinValid,

                int transitionCount,
                int verifiedTransitionCount,
                int trustAnchorCount,

                String trustedRootFingerprint,
                String presentedRootFingerprint,

                String rootKeyId,
                String terminalKeyId,

                String message
        ) {
            return new Verification(
                    envelopeHash,

                    sourcePackageId,
                    sourcePackageNumber,

                    packageHash,

                    false,

                    packageHashValid,
                    canonicalPayloadValid,
                    lineageValid,
                    rootPinValid,

                    transitionCount,
                    verifiedTransitionCount,
                    trustAnchorCount,

                    trustedRootFingerprint,
                    presentedRootFingerprint,

                    rootKeyId,
                    terminalKeyId,

                    message
            );
        }
    }

    private static final class Json {

        private Json() {
        }

        static Object parse(
                String text
        ) {
            Parser parser =
                    new Parser(
                            text
                    );

            Object result =
                    parser.parseValue();

            parser.skipWhitespace();

            if (
                    !parser.end()
            ) {
                throw new IllegalArgumentException(
                        "Unexpected trailing JSON content."
                );
            }

            return result;
        }

        static String writeCanonical(
                Object value
        ) {
            StringBuilder builder =
                    new StringBuilder();

            write(
                    value,
                    builder
            );

            return builder.toString();
        }

        static String keys(
                Object value
        ) {
            if (
                    !(value instanceof Map<?, ?> map)
            ) {
                return "NOT_OBJECT";
            }

            List<String> keys =
                    new ArrayList<>();

            for (
                    Object key
                    : map.keySet()
            ) {
                keys.add(
                        String.valueOf(
                                key
                        )
                );
            }

            return String.join(
                    ",",
                    keys
            );
        }

        static boolean hasExpectedTrustLineageRecordOrder(
                Object value
        ) {
            if (
                    !(value instanceof Map<?, ?> root)
            ) {
                return false;
            }

            if (
                    !keysEqual(
                            root,

                            List.of(
                                    "packageVersion",
                                    "rootKeyId",
                                    "rootPublicKeyFingerprint",
                                    "terminalKeyId",
                                    "terminalPublicKeyFingerprint",
                                    "trustAnchors",
                                    "transitions"
                            )
                    )
            ) {
                return false;
            }

            Object anchorsValue =
                    root.get(
                            "trustAnchors"
                    );

            if (
                    !(anchorsValue instanceof List<?> anchors)
            ) {
                return false;
            }

            List<String> anchorFields =
                    List.of(
                            "keyId",
                            "algorithm",
                            "publicKeyX509Base64",
                            "publicKeyFingerprint",
                            "status",
                            "supersededByKeyId",
                            "retirementReason",
                            "createdAt",
                            "lastUsedAt",
                            "retiredAt"
                    );

            for (
                    Object anchorValue
                    : anchors
            ) {
                if (
                        !(anchorValue instanceof Map<?, ?> anchor)

                        || !keysEqual(
                                anchor,
                                anchorFields
                        )
                ) {
                    return false;
                }
            }

            Object transitionsValue =
                    root.get(
                            "transitions"
                    );

            if (
                    !(transitionsValue instanceof List<?> transitions)
            ) {
                return false;
            }

            List<String> transitionFields =
                    List.of(
                            "id",
                            "transitionNumber",
                            "outgoingKeyId",
                            "outgoingPublicKeyFingerprint",
                            "replacementKeyId",
                            "replacementPublicKeyFingerprint",
                            "algorithm",
                            "reason",
                            "transitionVersion",
                            "signatureBase64",
                            "previousTransitionHash",
                            "transitionHash",
                            "createdAt"
                    );

            for (
                    Object transitionValue
                    : transitions
            ) {
                if (
                        !(transitionValue instanceof Map<?, ?> transition)

                        || !keysEqual(
                                transition,
                                transitionFields
                        )
                ) {
                    return false;
                }
            }

            return true;
        }

        private static boolean keysEqual(
                Map<?, ?> value,
                List<String> expected
        ) {
            List<String> actual =
                    new ArrayList<>();

            for (
                    Object key
                    : value.keySet()
            ) {
                actual.add(
                        String.valueOf(
                                key
                        )
                );
            }

            return actual.equals(
                    expected
            );
        }

        private static void write(
                Object value,
                StringBuilder builder
        ) {
            if (
                    value == null
            ) {
                builder.append(
                        "null"
                );

                return;
            }

            if (
                    value instanceof String string
            ) {
                writeString(
                        string,
                        builder
                );

                return;
            }

            if (
                    value instanceof Boolean bool
            ) {
                builder.append(
                        bool
                                ? "true"
                                : "false"
                );

                return;
            }

            if (
                    value instanceof JsonNumber number
            ) {
                builder.append(
                        number.raw()
                );

                return;
            }

            if (
                    value instanceof Number number
            ) {
                builder.append(
                        number
                );

                return;
            }

            if (
                    value instanceof Map<?, ?> map
            ) {
                builder.append(
                        '{'
                );

                boolean first =
                        true;

                // Intentionally preserve LinkedHashMap insertion order.
                // Jackson 3 Record creator properties retain declaration order.
                for (
                        Map.Entry<?, ?> entry
                        : map.entrySet()
                ) {
                    if (
                            !first
                    ) {
                        builder.append(
                                ','
                        );
                    }

                    first =
                            false;

                    writeString(
                            String.valueOf(
                                    entry.getKey()
                            ),
                            builder
                    );

                    builder.append(
                            ':'
                    );

                    write(
                            entry.getValue(),
                            builder
                    );
                }

                builder.append(
                        '}'
                );

                return;
            }

            if (
                    value instanceof List<?> list
            ) {
                builder.append(
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
                        builder.append(
                                ','
                        );
                    }

                    write(
                            list.get(
                                    index
                            ),
                            builder
                    );
                }

                builder.append(
                        ']'
                );

                return;
            }

            throw new IllegalArgumentException(
                    "Unsupported JSON value type: "
                            + value.getClass()
            );
        }

        private static void writeString(
                String value,
                StringBuilder builder
        ) {
            builder.append(
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
                            builder.append(
                                    "\\\""
                            );

                    case '\\' ->
                            builder.append(
                                    "\\\\"
                            );

                    case '\b' ->
                            builder.append(
                                    "\\b"
                            );

                    case '\f' ->
                            builder.append(
                                    "\\f"
                            );

                    case '\n' ->
                            builder.append(
                                    "\\n"
                            );

                    case '\r' ->
                            builder.append(
                                    "\\r"
                            );

                    case '\t' ->
                            builder.append(
                                    "\\t"
                            );

                    default -> {
                        if (
                                character < 0x20
                        ) {
                            builder.append(
                                    String.format(
                                            Locale.ROOT,
                                            "\\u%04x",
                                            (int) character
                                    )
                            );

                        } else {
                            builder.append(
                                    character
                            );
                        }
                    }
                }
            }

            builder.append(
                    '"'
            );
        }

        private record JsonNumber(
                String raw
        ) {
        }

        private static final class Parser {

            private final String text;

            private int index;

            private Parser(
                    String text
            ) {
                this.text =
                        text;
            }

            private boolean end() {
                return index
                       >= text.length();
            }

            private void skipWhitespace() {
                while (
                        !end()

                        && Character.isWhitespace(
                                text.charAt(
                                        index
                                )
                        )
                ) {
                    index++;
                }
            }

            private Object parseValue() {
                skipWhitespace();

                if (
                        end()
                ) {
                    throw new IllegalArgumentException(
                            "Unexpected end of JSON."
                    );
                }

                char character =
                        text.charAt(
                                index
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
                        expect(
                                "true"
                        );

                        yield Boolean.TRUE;
                    }

                    case 'f' -> {
                        expect(
                                "false"
                        );

                        yield Boolean.FALSE;
                    }

                    case 'n' -> {
                        expect(
                                "null"
                        );

                        yield null;
                    }

                    default ->
                            parseNumber();
                };
            }

            private Map<String, Object> parseObject() {
                consume(
                        '{'
                );

                skipWhitespace();

                Map<String, Object> object =
                        new LinkedHashMap<>();

                if (
                        peek(
                                '}'
                        )
                ) {
                    consume(
                            '}'
                    );

                    return object;
                }

                while (
                        true
                ) {
                    skipWhitespace();

                    String key =
                            parseString();

                    if (
                            object.containsKey(
                                    key
                            )
                    ) {
                        throw new IllegalArgumentException(
                                "Duplicate JSON object key: "
                                        + key
                        );
                    }

                    skipWhitespace();

                    consume(
                            ':'
                    );

                    Object fieldValue =
                            parseValue();

                    object.put(
                            key,
                            fieldValue
                    );

                    skipWhitespace();

                    if (
                            peek(
                                    '}'
                            )
                    ) {
                        consume(
                                '}'
                        );

                        return object;
                    }

                    consume(
                            ','
                    );
                }
            }

            private List<Object> parseArray() {
                consume(
                        '['
                );

                skipWhitespace();

                List<Object> result =
                        new ArrayList<>();

                if (
                        peek(
                                ']'
                        )
                ) {
                    consume(
                            ']'
                    );

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
                            peek(
                                    ']'
                            )
                    ) {
                        consume(
                                ']'
                        );

                        return result;
                    }

                    consume(
                            ','
                    );
                }
            }

            private String parseString() {
                consume(
                        '"'
                );

                StringBuilder builder =
                        new StringBuilder();

                while (
                        !end()
                ) {
                    char character =
                            text.charAt(
                                    index++
                            );

                    if (
                            character == '"'
                    ) {
                        return builder.toString();
                    }

                    if (
                            character != '\\'
                    ) {
                        builder.append(
                                character
                        );

                        continue;
                    }

                    if (
                            end()
                    ) {
                        throw new IllegalArgumentException(
                                "Invalid JSON escape."
                        );
                    }

                    char escaped =
                            text.charAt(
                                    index++
                            );

                    switch (
                        escaped
                    ) {
                        case '"' ->
                                builder.append(
                                        '"'
                                );

                        case '\\' ->
                                builder.append(
                                        '\\'
                                );

                        case '/' ->
                                builder.append(
                                        '/'
                                );

                        case 'b' ->
                                builder.append(
                                        '\b'
                                );

                        case 'f' ->
                                builder.append(
                                        '\f'
                                );

                        case 'n' ->
                                builder.append(
                                        '\n'
                                );

                        case 'r' ->
                                builder.append(
                                        '\r'
                                );

                        case 't' ->
                                builder.append(
                                        '\t'
                                );

                        case 'u' -> {
                            if (
                                    index + 4
                                    > text.length()
                            ) {
                                throw new IllegalArgumentException(
                                        "Invalid Unicode JSON escape."
                                );
                            }

                            String hex =
                                    text.substring(
                                            index,
                                            index + 4
                                    );

                            builder.append(
                                    (char) Integer.parseInt(
                                            hex,
                                            16
                                    )
                            );

                            index += 4;
                        }

                        default ->
                                throw new IllegalArgumentException(
                                        "Unsupported JSON escape."
                                );
                    }
                }

                throw new IllegalArgumentException(
                        "Unterminated JSON string."
                );
            }

            private JsonNumber parseNumber() {
                int start =
                        index;

                if (
                        peek(
                                '-'
                        )
                ) {
                    index++;
                }

                int integerStart =
                        index;

                while (
                        !end()

                        && Character.isDigit(
                                text.charAt(
                                        index
                                )
                        )
                ) {
                    index++;
                }

                if (
                        index == integerStart
                ) {
                    throw new IllegalArgumentException(
                            "Invalid JSON number."
                    );
                }

                if (
                        !end()

                        && text.charAt(
                                index
                        ) == '.'
                ) {
                    index++;

                    int decimalStart =
                            index;

                    while (
                            !end()

                            && Character.isDigit(
                                    text.charAt(
                                            index
                                    )
                            )
                    ) {
                        index++;
                    }

                    if (
                            index == decimalStart
                    ) {
                        throw new IllegalArgumentException(
                                "Invalid JSON decimal."
                        );
                    }
                }

                if (
                        !end()

                        && (
                            text.charAt(
                                    index
                            ) == 'e'

                            || text.charAt(
                                    index
                            ) == 'E'
                        )
                ) {
                    index++;

                    if (
                            !end()

                            && (
                                text.charAt(
                                        index
                                ) == '+'

                                || text.charAt(
                                        index
                                ) == '-'
                            )
                    ) {
                        index++;
                    }

                    int exponentStart =
                            index;

                    while (
                            !end()

                            && Character.isDigit(
                                    text.charAt(
                                            index
                                    )
                            )
                    ) {
                        index++;
                    }

                    if (
                            index == exponentStart
                    ) {
                        throw new IllegalArgumentException(
                                "Invalid JSON exponent."
                        );
                    }
                }

                return new JsonNumber(
                        text.substring(
                                start,
                                index
                        )
                );
            }

            private void expect(
                    String expected
            ) {
                if (
                        !text.startsWith(
                                expected,
                                index
                        )
                ) {
                    throw new IllegalArgumentException(
                            "Unexpected JSON token."
                    );
                }

                index +=
                        expected.length();
            }

            private boolean peek(
                    char expected
            ) {
                return !end()

                       && text.charAt(
                               index
                       ) == expected;
            }

            private void consume(
                    char expected
            ) {
                skipWhitespace();

                if (
                        end()

                        || text.charAt(
                                index
                        ) != expected
                ) {
                    throw new IllegalArgumentException(
                            "Expected JSON character: "
                                    + expected
                    );
                }

                index++;
            }
        }
    }
}
