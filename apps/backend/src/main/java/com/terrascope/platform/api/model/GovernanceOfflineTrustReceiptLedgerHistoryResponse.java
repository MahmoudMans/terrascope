package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceOfflineTrustReceiptLedgerHistoryResponse(
        List<GovernanceOfflineTrustReceiptLedgerEntry> entries
) {
}
