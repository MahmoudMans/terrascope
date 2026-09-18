package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceOfflineTrustVerificationReceiptHistoryResponse(
        List<GovernanceOfflineTrustVerificationReceipt> receipts
) {
}
