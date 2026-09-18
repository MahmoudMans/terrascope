package com.terrascope.platform.service;

import com.terrascope.platform.domain.GovernanceClosureDossierMaterial;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class GovernanceClosureHasher {

    public static final String DOSSIER_VERSION =
            "TS-GOVERNANCE-CLOSURE-DOSSIER-V1";

    public static final String CERTIFICATE_VERSION =
            "TS-GOVERNANCE-CLOSURE-CERTIFICATE-V1";

    private static final String GENESIS =
            "GENESIS";

    public String dossierHash(
            GovernanceClosureDossierMaterial material
    ) {
        String canonical =
                String.format(
                        Locale.ROOT,

                        """
                        dossierVersion=%s
                        benefitSnapshotId=%s
                        benefitSnapshotNumber=%d
                        band=%s
                        closureReady=%s
                        totalObjectiveCount=%d
                        completedObjectiveCount=%d
                        validatedObjectiveCount=%d
                        achievedCount=%d
                        validationCoveragePct=%.1f
                        realizedBenefitRatePct=%.1f
                        averageScoreDelta=%.1f
                        averageAssuranceDebtDelta=%.1f
                        """,

                        DOSSIER_VERSION,

                        material.benefitSnapshotId(),
                        material.benefitSnapshotNumber(),

                        material.band().name(),
                        material.closureReady(),

                        material.totalObjectiveCount(),
                        material.completedObjectiveCount(),
                        material.validatedObjectiveCount(),
                        material.achievedCount(),

                        material.validationCoveragePct(),
                        material.realizedBenefitRatePct(),

                        material.averageScoreDelta(),
                        material.averageAssuranceDebtDelta()
                );

        return sha256(
                canonical
        );
    }

    public String certificateHash(
            int certificateNumber,
            String dossierHash,
            String previousCertificateHash
    ) {
        String previous =
                previousCertificateHash == null
                        ? GENESIS
                        : previousCertificateHash;

        String canonical =
                """
                certificateVersion=%s
                certificateNumber=%d
                dossierHash=%s
                previousCertificateHash=%s
                """
                .formatted(
                        CERTIFICATE_VERSION,
                        certificateNumber,
                        dossierHash,
                        previous
                );

        return sha256(
                canonical
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
