package com.terrascope.platform.api.model;

import com.terrascope.platform.activity.ActivityEntry;
import com.terrascope.platform.security.Role;

import java.time.Instant;
import java.util.UUID;

public record ActivityEntryView(
        UUID id,
        Instant occurredAt,
        String actorUsername,
        String actorDisplayName,
        Role actorRole,
        String action,
        String entityType,
        String entityId,
        String method
) {

    public static ActivityEntryView from(
            ActivityEntry entry
    ) {
        return new ActivityEntryView(
                entry.id(),
                entry.occurredAt(),
                entry.actorUsername(),
                entry.actorDisplayName(),
                entry.actorRole(),
                entry.action(),
                entry.entityType(),
                entry.entityId(),
                entry.method()
        );
    }
}
