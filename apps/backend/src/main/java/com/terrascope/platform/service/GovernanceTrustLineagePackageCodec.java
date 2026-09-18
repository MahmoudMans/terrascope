package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceTrustLineagePortableEnvelope;
import com.terrascope.platform.domain.GovernanceTrustLineagePortablePayload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GovernanceTrustLineagePackageCodec {

    public static final String PACKAGE_VERSION =
            "TS-GOVERNANCE-TRUST-LINEAGE-PACKAGE-V1";

    public static final String ENVELOPE_VERSION =
            "TS-GOVERNANCE-TRUST-LINEAGE-ENVELOPE-V1";

    private final JsonMapper mapper =
            JsonMapper
                    .builder()
                    .enable(
                            MapperFeature.SORT_PROPERTIES_ALPHABETICALLY
                    )
                    .enable(
                            SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS
                    )
                    .build();

    public String encodePayload(
            GovernanceTrustLineagePortablePayload payload
    ) {
        try {
            return mapper.writeValueAsString(
                    payload
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode canonical trust-lineage package.",
                    exception
            );
        }
    }

    public GovernanceTrustLineagePortablePayload decodePayload(
            String payloadJson
    ) {
        try {
            return mapper.readValue(
                    payloadJson,
                    GovernanceTrustLineagePortablePayload.class
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalArgumentException(
                    "Portable trust-lineage payload is not valid JSON.",
                    exception
            );
        }
    }

    public byte[] encodeEnvelope(
            GovernanceTrustLineagePortableEnvelope envelope
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
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode portable trust-lineage envelope.",
                    exception
            );
        }
    }

    public GovernanceTrustLineagePortableEnvelope decodeEnvelope(
            byte[] envelopeBytes
    ) {
        try {
            return mapper.readValue(
                    envelopeBytes,
                    GovernanceTrustLineagePortableEnvelope.class
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalArgumentException(
                    "Portable trust-lineage envelope is not valid JSON.",
                    exception
            );
        }
    }

    public boolean isCanonicalPayload(
            String payloadJson
    ) {
        try {
            GovernanceTrustLineagePortablePayload decoded =
                    decodePayload(
                            payloadJson
                    );

            return encodePayload(
                    decoded
            ).equals(
                    payloadJson
            );

        } catch (
                IllegalArgumentException exception
        ) {
            return false;
        }
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
