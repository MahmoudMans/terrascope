package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceTrustAnchorTransitionMaterial;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GovernanceTrustAnchorTransitionHasher {

    public static final String TRANSITION_VERSION =
            "TS-GOVERNANCE-TRUST-TRANSITION-V1";

    private static final String GENESIS =
            "GENESIS";

    public byte[] signingPayload(
            GovernanceTrustAnchorTransitionMaterial material
    ) {
        return canonical(
                material
        ).getBytes(
                StandardCharsets.UTF_8
        );
    }

    public String transitionHash(
            GovernanceTrustAnchorTransitionMaterial material,
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
            GovernanceTrustAnchorTransitionMaterial material
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

                        material.transitionNumber(),

                        material.outgoingKeyId(),
                        material.outgoingPublicKeyFingerprint(),

                        material.replacementKeyId(),
                        material.replacementPublicKeyFingerprint(),

                        material.algorithm(),

                        material.reason(),

                        material.previousTransitionHash() == null
                                ? GENESIS
                                : material.previousTransitionHash()
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
