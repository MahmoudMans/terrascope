package com.terrascope.platform.domain;

import java.util.UUID;

public record ResilienceEvidenceAnchor(
        UUID id,
        int packageNumber,
        String manifestVersion,
        String manifestHash
) {
}
