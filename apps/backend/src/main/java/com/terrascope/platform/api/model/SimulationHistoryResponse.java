package com.terrascope.platform.api.model;

import java.util.List;

public record SimulationHistoryResponse(
        int totalCount,
        int extremeCount,
        int severeCount,
        double averageRiskScore,
        List<SimulationHistoryItem> runs
) {
}
