package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptDocument;
import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptMaterial;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GovernanceOfflineTrustVerificationReceiptCodec {

    public static final String VERIFIER_VERSION =
            "TS-STANDALONE-TRUST-VERIFIER-V1";

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

    public GovernanceOfflineTrustVerificationReceiptDocument decode(
            String receiptJson
    ) {
        try {
            return mapper.readValue(
                    receiptJson,
                    GovernanceOfflineTrustVerificationReceiptDocument.class
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalArgumentException(
                    "Offline trust verification receipt is not valid JSON.",
                    exception
            );
        }
    }

    public String encodeDocument(
            GovernanceOfflineTrustVerificationReceiptDocument document
    ) {
        try {
            return mapper.writeValueAsString(
                    document
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode offline trust verification receipt.",
                    exception
            );
        }
    }

    public String encodeMaterial(
            GovernanceOfflineTrustVerificationReceiptMaterial material
    ) {
        try {
            return mapper.writeValueAsString(
                    material
            );

        } catch (
                Exception exception
        ) {
            throw new IllegalStateException(
                    "Unable to encode offline trust verification receipt material.",
                    exception
            );
        }
    }

    public GovernanceOfflineTrustVerificationReceiptMaterial material(
            GovernanceOfflineTrustVerificationReceiptDocument document
    ) {
        return new GovernanceOfflineTrustVerificationReceiptMaterial(
                document.verifierVersion(),

                document.sourcePackageId(),
                document.sourcePackageNumber(),

                document.envelopeJson(),
                document.envelopeHash(),
                document.packageHash(),

                document.trustedRootFingerprint(),
                document.presentedRootFingerprint(),

                document.valid(),

                document.packageHashValid(),
                document.canonicalPayloadValid(),
                document.lineageValid(),
                document.rootPinValid(),

                document.transitionCount(),
                document.verifiedTransitionCount(),
                document.trustAnchorCount(),

                document.rootKeyId(),
                document.terminalKeyId(),

                document.message(),
                document.verifiedAt()
        );
    }

    public String receiptHash(
            GovernanceOfflineTrustVerificationReceiptMaterial material
    ) {
        return sha256(
                encodeMaterial(
                        material
                )
        );
    }

    public boolean isCanonicalDocument(
            String receiptJson
    ) {
        try {
            GovernanceOfflineTrustVerificationReceiptDocument document =
                    decode(
                            receiptJson
                    );

            return encodeDocument(
                    document
            ).equals(
                    receiptJson
            );

        } catch (
                IllegalArgumentException exception
        ) {
            return false;
        }
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
