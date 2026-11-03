package com.terrascope.platform.api.model;

import java.time.Instant;

public record LoginResponse(
        String token,
        Instant expiresAt,
        UserSummary user
) {

    @Override
    public String toString() {
        return "LoginResponse[user="
                + user
                + ", expiresAt="
                + expiresAt
                + ", token=****]";
    }
}
