package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointBundleMaterial;
import com.terrascope.platform.domain.GovernanceOfflineTrustCheckpointPortableEnvelope;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HexFormat;

@Component
public class GovernanceOfflineTrustCheckpointBundleCodec {

    public static final String BUNDLE_VERSION =
            "TS-OFFLINE-TRUST-CHECKPOINT-BUNDLE-V1";

    public static final String ENVELOPE_VERSION =
            "TS-OFFLINE-TRUST-CHECKPOINT-ENVELOPE-V1";

    private final ObjectMapper objectMapper;

    public GovernanceOfflineTrustCheckpointBundleCodec(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public GovernanceOfflineTrustCheckpointPortableEnvelope encode(
            GovernanceOfflineTrustCheckpointBundleMaterial material
    ) {
        String payloadJson =
                materialJson(
                        material
                );

        return new GovernanceOfflineTrustCheckpointPortableEnvelope(
                ENVELOPE_VERSION,

                sha256(
                        payloadJson
                ),

                payloadJson
        );
    }

    public String envelopeJson(
            GovernanceOfflineTrustCheckpointPortableEnvelope envelope
    ) {
        try {
            return objectMapper.writeValueAsString(
                    envelope
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode portable offline trust checkpoint envelope.",
                    exception
            );
        }
    }

    public GovernanceOfflineTrustCheckpointPortableEnvelope decodeEnvelope(
            String envelopeJson
    ) {
        try {
            return objectMapper.readValue(
                    envelopeJson,
                    GovernanceOfflineTrustCheckpointPortableEnvelope.class
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalArgumentException(
                    "Portable offline trust checkpoint envelope is not valid JSON.",
                    exception
            );
        }
    }

    public GovernanceOfflineTrustCheckpointBundleMaterial decodeMaterial(
            String payloadJson
    ) {
        try {
            return objectMapper.readValue(
                    payloadJson,
                    GovernanceOfflineTrustCheckpointBundleMaterial.class
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalArgumentException(
                    "Portable offline trust checkpoint payload is not valid.",
                    exception
            );
        }
    }

    public String materialJson(
            GovernanceOfflineTrustCheckpointBundleMaterial material
    ) {
        try {
            return objectMapper.writeValueAsString(
                    material
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode portable offline trust checkpoint payload.",
                    exception
            );
        }
    }

    public String sha256(
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
