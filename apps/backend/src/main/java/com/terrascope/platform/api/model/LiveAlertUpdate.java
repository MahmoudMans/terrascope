package com.terrascope.platform.api.model;

import java.time.Instant;

public record LiveAlertUpdate(
        int newlyTriggeredCount,
        int newAlertCount,
        Instant evaluatedAt
) {
}
