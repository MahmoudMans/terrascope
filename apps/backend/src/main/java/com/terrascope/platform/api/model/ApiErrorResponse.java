package com.terrascope.platform.api.model;

import java.time.Instant;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
}
