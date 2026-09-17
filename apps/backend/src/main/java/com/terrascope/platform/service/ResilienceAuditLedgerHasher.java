package com.terrascope.platform.service;

import com.terrascope.platform.domain.ResilienceAuditLedgerMaterial;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ResilienceAuditLedgerHasher {

    public static final String LEDGER_VERSION =
            "TS-RESILIENCE-AUDIT-LEDGER-V1";

    private static final String GENESIS =
            "GENESIS";

    public String hash(
            ResilienceAuditLedgerMaterial material
    ) {
        String canonical =
                canonical(
                        material
                );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            digest.digest(
                                    canonical.getBytes(
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

    String canonical(
            ResilienceAuditLedgerMaterial material
    ) {
        String previous =
                material.previousReceiptHash()
                        == null
                        ? GENESIS
                        : material.previousReceiptHash();

        return """
                ledgerVersion=%s
                incidentId=%s
                receiptNumber=%d
                evidencePackageId=%s
                evidencePackageNumber=%d
                packageManifestVersion=%s
                packageManifestHash=%s
                previousReceiptHash=%s
                """
                .formatted(
                        LEDGER_VERSION,
                        material.incidentId(),
                        material.receiptNumber(),
                        material.evidencePackageId(),
                        material.evidencePackageNumber(),
                        material.packageManifestVersion(),
                        material.packageManifestHash(),
                        previous
                );
    }
}
