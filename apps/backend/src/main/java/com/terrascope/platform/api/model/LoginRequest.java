package com.terrascope.platform.api.model;

public record LoginRequest(
        String username,
        String password
) {

    @Override
    public String toString() {
        return "LoginRequest[username="
                + username
                + ", password=****]";
    }
}
