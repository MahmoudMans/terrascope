package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record IncidentSitrepHistoryResponse(
        UUID incidentId,
        List<IncidentSituationReport> reports
) {
}
