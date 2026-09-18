package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceAssuranceBundlePayload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GovernanceAssuranceBundleCodec {

    public static final String BUNDLE_VERSION =
            "TS-GOVERNANCE-ASSURANCE-BUNDLE-V1";

    private final JsonMapper objectMapper;

    public GovernanceAssuranceBundleCodec() {
        this.objectMapper =
                JsonMapper
                        .builder()
                        .enable(
                                MapperFeature.SORT_PROPERTIES_ALPHABETICALLY
                        )
                        .enable(
                                SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS
                        )
                        .build();
    }

    public String encode(
            GovernanceAssuranceBundlePayload payload
    ) {
        try {
            return objectMapper.writeValueAsString(
                    payload
            );

        } catch (
                RuntimeException exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode governance assurance bundle.",
                    exception
            );
        }
    }

    public GovernanceAssuranceBundlePayload decode(
            String payloadJson
    ) {
        try {
            return objectMapper.readValue(
                    payloadJson,
                    GovernanceAssuranceBundlePayload.class
            );

        } catch (
                RuntimeException exception
        ) {
            throw new IllegalArgumentException(
                    "Governance assurance bundle payload is invalid.",
                    exception
            );
        }
    }

    public String hash(
            String payloadJson
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
                                    payloadJson.getBytes(
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

    public boolean canonical(
            String payloadJson
    ) {
        GovernanceAssuranceBundlePayload decoded =
                decode(
                        payloadJson
                );

        return encode(
                decoded
        ).equals(
                payloadJson
        );
    }
}
