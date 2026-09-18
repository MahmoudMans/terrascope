package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceAssuranceVerificationReceiptHistoryResponse(
        List<GovernanceAssuranceVerificationReceipt> receipts
) {
}
