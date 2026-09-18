package com.terrascope.platform.api.model;

import java.util.List;

public record BenefitRealizationHistoryResponse(
        List<BenefitRealizationSnapshot> snapshots
) {
}
