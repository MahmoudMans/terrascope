package com.terrascope.platform.security;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String username,
        String displayName,
        Role role,
        String passwordHash,
        boolean enabled,
        int tokenVersion,
        Instant createdAt,
        Instant lastLoginAt
) {

    @Override
    public String toString() {
        return "UserAccount[id="
                + id
                + ", username="
                + username
                + ", role="
                + role
                + ", enabled="
                + enabled
                + "]";
    }
}
