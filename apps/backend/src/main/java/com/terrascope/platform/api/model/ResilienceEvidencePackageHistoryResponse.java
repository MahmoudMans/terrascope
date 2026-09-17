package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record ResilienceEvidencePackageHistoryResponse(
        UUID incidentId,
        List<ResilienceEvidencePackage> packages
) {
}
