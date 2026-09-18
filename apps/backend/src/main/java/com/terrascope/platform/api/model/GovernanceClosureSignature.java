package com.terrascope.platform.api.model;

import java.time.Instant;
import java.util.UUID;

public record GovernanceClosureSignature(
        UUID id,

        UUID certificateId,
        int certificateNumber,

        String keyId,
        String algorithm,

        String publicKeyFingerprint,

        String signatureVersion,

        String certificateHash,
        String dossierHash,

        String signatureBase64,

        Instant createdAt
) {
}
