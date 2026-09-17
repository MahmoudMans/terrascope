package com.terrascope.platform.api.model;

import java.util.List;
import java.util.UUID;

public record CommandPulseHistoryResponse(
        UUID incidentId,
        List<CommandPulseSnapshot> snapshots
) {
}
