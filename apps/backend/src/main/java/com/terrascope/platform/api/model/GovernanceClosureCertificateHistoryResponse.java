package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceClosureCertificateHistoryResponse(
        List<GovernanceClosureCertificate> certificates
) {
}
