package com.terrascope.platform.api.model;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[****]";
    }
}
