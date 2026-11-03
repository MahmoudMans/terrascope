package com.terrascope.platform.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String username,
        String displayName,
        Role role
) {
}
