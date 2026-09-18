package com.terrascope.platform.api.model;

import java.util.List;

public record GovernanceAssuranceReceiptLedgerHistoryResponse(
        List<GovernanceAssuranceReceiptLedgerEntry> entries
) {
}
