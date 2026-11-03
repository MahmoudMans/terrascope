package com.terrascope.platform.activity;

import com.terrascope.platform.security.Role;

import java.time.Instant;
import java.util.UUID;

public record ActivityEntry(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        String actorUsername,
        String actorDisplayName,
        Role actorRole,
        String method,
        String route,
        String action,
        String entityType,
        String entityId,
        int statusCode
) {
}
