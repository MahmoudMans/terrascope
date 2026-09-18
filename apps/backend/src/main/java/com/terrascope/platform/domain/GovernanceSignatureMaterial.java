package com.terrascope.platform.domain;

import java.util.UUID;

public record GovernanceSignatureMaterial(
        UUID certificateId,
        int certificateNumber,

        String certificateHash,
        String dossierHash,

        String keyId,
        String algorithm,
        String publicKeyFingerprint
) {
}
