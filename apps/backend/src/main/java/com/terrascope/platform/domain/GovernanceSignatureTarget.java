package com.terrascope.platform.domain;

import java.util.UUID;

public record GovernanceSignatureTarget(
        UUID certificateId,
        int certificateNumber,
        String certificateHash,
        String dossierHash
) {
}
