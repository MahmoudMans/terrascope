package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record ResilienceCertificationHistoryResponse(
        UUID incidentId,
        List<IncidentResilienceCertification> certifications
) {
}
