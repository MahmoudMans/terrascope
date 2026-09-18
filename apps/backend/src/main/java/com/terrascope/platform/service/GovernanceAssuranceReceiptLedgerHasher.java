package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceAssuranceVerificationReceipt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class GovernanceAssuranceReceiptLedgerHasher {

    public static final String LEDGER_VERSION =
            "TS-ASSURANCE-RECEIPT-LEDGER-V1";

    private static final String GENESIS =
            "GENESIS";

    public String receiptPayloadHash(
            GovernanceAssuranceVerificationReceipt receipt
    ) {
        String canonical =
                String.format(
                        Locale.ROOT,

                        """
                        receiptId=%s
                        receiptNumber=%d
                        sourceBundleId=%s
                        sourceBundleNumber=%s
                        envelopeVersion=%s
                        bundleVersion=%s
                        envelopeHash=%s
                        bundleHash=%s
                        verificationResult=%s
                        hashValid=%s
                        canonicalPayloadValid=%s
                        portableVerificationValid=%s
                        signatureCount=%d
                        verifiedSignatureCount=%d
                        trustAnchorCount=%d
                        retiredAnchorCount=%d
                        lifecycleEventCount=%d
                        message=%s
                        verifiedAt=%s
                        """,

                        receipt.id(),
                        receipt.receiptNumber(),

                        nullable(
                                receipt.sourceBundleId()
                        ),

                        nullable(
                                receipt.sourceBundleNumber()
                        ),

                        nullable(
                                receipt.envelopeVersion()
                        ),

                        nullable(
                                receipt.bundleVersion()
                        ),

                        receipt.envelopeHash(),

                        nullable(
                                receipt.bundleHash()
                        ),

                        receipt.verificationResult(),

                        receipt.hashValid(),
                        receipt.canonicalPayloadValid(),
                        receipt.portableVerificationValid(),

                        receipt.signatureCount(),
                        receipt.verifiedSignatureCount(),

                        receipt.trustAnchorCount(),
                        receipt.retiredAnchorCount(),
                        receipt.lifecycleEventCount(),

                        receipt.message(),

                        receipt.verifiedAt()
                );

        return sha256(
                canonical
        );
    }

    public String ledgerHash(
            int ledgerNumber,
            UUID receiptId,
            String receiptPayloadHash,
            String previousLedgerHash
    ) {
        String canonical =
                """
                ledgerVersion=%s
                ledgerNumber=%d
                receiptId=%s
                receiptPayloadHash=%s
                previousLedgerHash=%s
                """
                .formatted(
                        LEDGER_VERSION,
                        ledgerNumber,
                        receiptId,
                        receiptPayloadHash,

                        previousLedgerHash == null
                                ? GENESIS
                                : previousLedgerHash
                );

        return sha256(
                canonical
        );
    }

    private String nullable(
            Object value
    ) {
        return value == null
                ? "NULL"
                : value.toString();
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
