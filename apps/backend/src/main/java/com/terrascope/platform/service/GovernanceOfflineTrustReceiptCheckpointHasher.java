package com.terrascope.platform.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HexFormat;

@Component
public class GovernanceOfflineTrustReceiptCheckpointHasher {

    public static final String CHECKPOINT_VERSION =
            "TS-OFFLINE-TRUST-RECEIPT-CHECKPOINT-V1";

    public static final String ALGORITHM =
            "ED25519";

    public String signingPayload(
            int checkpointNumber,

            int coveredLedgerNumber,

            String coveredLedgerHash,

            int entryCount,

            int validReceiptCount,

            int invalidReceiptCount,

            String keyId,

            String publicKeyFingerprint,

            String previousCheckpointHash
    ) {
        return """
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
    }

    public String checkpointHash(
            String signingPayload,
            String signatureBase64
    ) {
        return sha256(
                signingPayload

                + "signatureBase64="
                + signatureBase64
                + "\n"
        );
    }

    public String fingerprint(
            byte[] x509PublicKey
    ) {
        return sha256(
                x509PublicKey
        );
    }

    public String sha256(
            String value
    ) {
        return sha256(
                value.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    public String sha256(
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
                NoSuchAlgorithmException exception
        ) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }
}
