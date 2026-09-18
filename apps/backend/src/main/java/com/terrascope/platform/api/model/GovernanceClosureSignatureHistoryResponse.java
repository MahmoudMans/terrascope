package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceClosureSignatureHistoryResponse(
        List<GovernanceClosureSignature> signatures
) {
}
