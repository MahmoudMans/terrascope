package com.terrascope.platform.api.model;

import com.terrascope.platform.security.Role;
import com.terrascope.platform.security.UserAccount;

import java.time.Instant;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String username,
        String displayName,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant lastLoginAt
) {

    public static UserSummary from(
            UserAccount account
    ) {
        return new UserSummary(
                account.id(),
                account.username(),
                account.displayName(),
                account.role(),
                account.enabled(),
                account.createdAt(),
                account.lastLoginAt()
        );
    }
}
