package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptDocument;
import com.terrascope.platform.domain.GovernanceOfflineTrustVerificationReceiptMaterial;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GovernanceOfflineTrustVerificationReceiptCodecTest {

    private final GovernanceOfflineTrustVerificationReceiptCodec codec =
            new GovernanceOfflineTrustVerificationReceiptCodec();

    @Test
    void receiptMaterialIsCanonicalAndHashStable() {
        GovernanceOfflineTrustVerificationReceiptMaterial material =
                material(
                        true
                );

        String first =
                codec.encodeMaterial(
                        material
                );

        String second =
                codec.encodeMaterial(
                        material
                );

        assertThat(
                first
        ).isEqualTo(
                second
        );

        assertThat(
                codec.receiptHash(
                        material
                )
        ).matches(
                "[0-9a-f]{64}"
        );
    }

    @Test
    void canonicalReceiptRoundTrips() {
        GovernanceOfflineTrustVerificationReceiptMaterial material =
                material(
                        true
                );

        GovernanceOfflineTrustVerificationReceiptDocument document =
                document(
                        material,
                        codec.receiptHash(
                                material
                        )
                );

        String json =
                codec.encodeDocument(
                        document
                );

        assertThat(
                codec.isCanonicalDocument(
                        json
                )
        ).isTrue();

        assertThat(
                codec.decode(
                        json
                )
        ).isEqualTo(
                document
        );
    }

    private GovernanceOfflineTrustVerificationReceiptMaterial material(
            boolean valid
    ) {
        return new GovernanceOfflineTrustVerificationReceiptMaterial(
                GovernanceOfflineTrustVerificationReceiptCodec.VERIFIER_VERSION,

                "11111111-1111-1111-1111-111111111111",
                1,

                "{\"envelope\":true}",

                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",

                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",

                valid,

                true,
                true,
                true,
                true,

                2,
                2,
                3,

                "key-1",
                "key-3",

                "Portable trust lineage verified against the externally supplied root fingerprint.",

                "2030-01-01T00:00:00Z"
        );
    }

    private GovernanceOfflineTrustVerificationReceiptDocument document(
            GovernanceOfflineTrustVerificationReceiptMaterial material,
            String receiptHash
    ) {
        return new GovernanceOfflineTrustVerificationReceiptDocument(
                material.verifierVersion(),

                material.sourcePackageId(),
                material.sourcePackageNumber(),

                material.envelopeJson(),
                material.envelopeHash(),
                material.packageHash(),

                material.trustedRootFingerprint(),
                material.presentedRootFingerprint(),

                material.valid(),

                material.packageHashValid(),
                material.canonicalPayloadValid(),
                material.lineageValid(),
                material.rootPinValid(),

                material.transitionCount(),
                material.verifiedTransitionCount(),
                material.trustAnchorCount(),

                material.rootKeyId(),
                material.terminalKeyId(),

                material.message(),
                material.verifiedAt(),

                receiptHash
        );
    }
}
