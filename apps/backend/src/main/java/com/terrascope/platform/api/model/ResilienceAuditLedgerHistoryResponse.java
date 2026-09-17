package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record ResilienceAuditLedgerHistoryResponse(
        UUID incidentId,
        List<ResilienceAuditReceipt> receipts
) {
}
