package com.terrascope.platform.api.model;

import java.util.List;

public record RegionalAnalysisHistoryResponse(
        int totalCount,
        int criticalCount,
        int highCount,
        double averageActivityIndex,
        List<RegionalAnalysisResult> runs
) {
}
