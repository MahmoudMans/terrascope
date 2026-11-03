package com.terrascope.platform.api.model;

public record CreateUserRequest(
        String username,
        String displayName,
        String role,
        String password
) {

    @Override
    public String toString() {
        return "CreateUserRequest[username="
                + username
                + ", role="
                + role
                + ", password=****]";
    }
}
