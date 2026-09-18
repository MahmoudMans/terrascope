package com.terrascope.platform.service;

import com.terrascope.platform.api.model.GovernanceOfflineTrustVerificationReceipt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class GovernanceOfflineTrustReceiptLedgerHasher {

    public static final String LEDGER_VERSION =
            "TS-OFFLINE-TRUST-RECEIPT-LEDGER-V1";

    public String receiptPayloadHash(
            GovernanceOfflineTrustVerificationReceipt receipt
    ) {
        return sha256(
                receiptPayload(
                        receipt
                )
        );
    }

    public String receiptPayload(
            GovernanceOfflineTrustVerificationReceipt receipt
    ) {
        return """
                receiptId=%s
                importNumber=%d
                verifierVersion=%s
                receiptHash=%s
                sourcePackageId=%s
                sourcePackageNumber=%s
                envelopeHash=%s
                packageHash=%s
                trustedRootFingerprint=%s
                presentedRootFingerprint=%s
                verificationResult=%s
                packageHashValid=%s
                canonicalPayloadValid=%s
                lineageValid=%s
                rootPinValid=%s
                transitionCount=%d
                verifiedTransitionCount=%d
                trustAnchorCount=%d
                rootKeyId=%s
                terminalKeyId=%s
                offlineVerifiedAt=%s
                importedAt=%s
                """
                .formatted(
                        receipt.id(),

                        receipt.importNumber(),

                        receipt.verifierVersion(),

                        receipt.receiptHash(),

                        value(
                                receipt.sourcePackageId()
                        ),

                        value(
                                receipt.sourcePackageNumber()
                        ),

                        receipt.envelopeHash(),

                        value(
                                receipt.packageHash()
                        ),

                        receipt.trustedRootFingerprint(),

                        value(
                                receipt.presentedRootFingerprint()
                        ),

                        receipt.verificationResult(),

                        receipt.packageHashValid(),

                        receipt.canonicalPayloadValid(),

                        receipt.lineageValid(),

                        receipt.rootPinValid(),

                        receipt.transitionCount(),

                        receipt.verifiedTransitionCount(),

                        receipt.trustAnchorCount(),

                        value(
                                receipt.rootKeyId()
                        ),

                        value(
                                receipt.terminalKeyId()
                        ),

                        receipt.offlineVerifiedAt(),

                        receipt.importedAt()
                );
    }

    public String ledgerHash(
            int ledgerNumber,

            UUID receiptId,

            int receiptImportNumber,

            String verificationResult,

            String receiptHash,

            String receiptPayloadHash,

            String previousLedgerHash
    ) {
        return sha256(
                """
                ledgerVersion=%s
                ledgerNumber=%d
                receiptId=%s
                receiptImportNumber=%d
                verificationResult=%s
                receiptHash=%s
                receiptPayloadHash=%s
                previousLedgerHash=%s
                """
                        .formatted(
                                LEDGER_VERSION,

                                ledgerNumber,

                                receiptId,

                                receiptImportNumber,

                                verificationResult,

                                receiptHash,

                                receiptPayloadHash,

                                previousLedgerHash == null
                                        ? "GENESIS"
                                        : previousLedgerHash
                        )
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

    private String value(
            Object value
    ) {
        return value == null
                ? "<null>"
                : value.toString();
    }
}
