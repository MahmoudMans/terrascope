package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceAssuranceReceiptCheckpointMaterial;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GovernanceAssuranceReceiptCheckpointHasher {

    public static final String CHECKPOINT_VERSION =
            "TS-ASSURANCE-RECEIPT-CHECKPOINT-V1";

    private static final String GENESIS =
            "GENESIS";

    public byte[] signingPayload(
            GovernanceAssuranceReceiptCheckpointMaterial material
    ) {
        return canonical(
                material
        ).getBytes(
                StandardCharsets.UTF_8
        );
    }

    public String checkpointHash(
            GovernanceAssuranceReceiptCheckpointMaterial material,
            String signatureBase64
    ) {
        return sha256(
                canonical(
                        material
                )
                + "signatureBase64="
                + signatureBase64
                + "\n"
        );
    }

    private String canonical(
            GovernanceAssuranceReceiptCheckpointMaterial material
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

                        material.checkpointNumber(),

                        material.coveredLedgerNumber(),
                        material.coveredLedgerHash(),

                        material.entryCount(),
                        material.validReceiptCount(),
                        material.invalidReceiptCount(),

                        material.keyId(),
                        material.algorithm(),
                        material.publicKeyFingerprint(),

                        material.previousCheckpointHash() == null
                                ? GENESIS
                                : material.previousCheckpointHash()
                );
    }

    private String sha256(
            String value
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
                                    value.getBytes(
                                            StandardCharsets.UTF_8
                                    )
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
