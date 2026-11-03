package com.terrascope.platform.api.model;

public record UpdateUserRequest(
        String displayName,
        String role,
        Boolean enabled
) {
}
