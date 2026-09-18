package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceAssurancePortableEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GovernanceAssuranceEnvelopeCodec {

    public static final String ENVELOPE_VERSION =
            "TS-GOVERNANCE-AIRGAP-ENVELOPE-V1";

    private final JsonMapper mapper;

    public GovernanceAssuranceEnvelopeCodec() {
        this.mapper =
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

    public byte[] encode(
            GovernanceAssurancePortableEnvelope envelope
    ) {
        try {
            return mapper
                    .writeValueAsString(
                            envelope
                    )
                    .getBytes(
                            StandardCharsets.UTF_8
                    );

        } catch (
                RuntimeException exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode portable governance assurance envelope.",
                    exception
            );
        }
    }

    public GovernanceAssurancePortableEnvelope decode(
            byte[] bytes
    ) {
        try {
            return mapper.readValue(
                    bytes,
                    GovernanceAssurancePortableEnvelope.class
            );

        } catch (
                RuntimeException exception
        ) {
            throw new IllegalArgumentException(
                    "Portable governance assurance envelope is invalid.",
                    exception
            );
        }
    }

    public String hash(
            byte[] bytes
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
                                    bytes
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
