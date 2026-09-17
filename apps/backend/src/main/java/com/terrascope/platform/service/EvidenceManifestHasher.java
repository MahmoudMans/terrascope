package com.terrascope.platform.service;

import com.terrascope.platform.domain.ResilienceEvidenceManifest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class EvidenceManifestHasher {

    public static final String MANIFEST_VERSION =
            "TS-RESILIENCE-EVIDENCE-V1";

    public String hash(
            ResilienceEvidenceManifest manifest
    ) {
        String canonical =
                canonical(
                        manifest
                );

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            canonical.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            hash
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
            ResilienceEvidenceManifest manifest
    ) {
        return String.format(
                Locale.ROOT,
                """
                manifestVersion=%s
                incidentId=%s
                certificationId=%s
                certificationNumber=%d
                certificationReadinessScore=%.1f
                certificationCount=%d
                afterActionReviewCount=%d
                sitrepCount=%d
                improvementCount=%d
                exerciseCount=%d
                correctiveActionCount=%d
                timelineEventCount=%d
                """,
                MANIFEST_VERSION,
                manifest.incidentId(),
                manifest.certificationId(),
                manifest.certificationNumber(),
                manifest.certificationReadinessScore(),
                manifest.certificationCount(),
                manifest.afterActionReviewCount(),
                manifest.sitrepCount(),
                manifest.improvementCount(),
                manifest.exerciseCount(),
                manifest.correctiveActionCount(),
                manifest.timelineEventCount()
        );
    }
}
